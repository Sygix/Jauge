/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import fr.sygix.jauge.model.Money
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.Spend
import fr.sygix.jauge.model.SpendKind
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.model.WindowKind
import fr.sygix.jauge.store.Settings
import fr.sygix.jauge.store.TextTone
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h914dp-xxhdpi")
class WidgetPreviewTest {

    private val outDir = File(System.getProperty("jauge.previews") ?: "build/widget-previews").apply { mkdirs() }

    private class Preview(private val settings: Settings, private val content: @Composable () -> Unit) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact
        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { WidgetSurface(settings) { content() } }
    }

    private data class Variant(val name: String, val settings: Settings, val wallpaper: IntArray, val night: Boolean)

    private val darkWallpaper = intArrayOf(0xFF203054.toInt(), 0xFF58346E.toInt(), 0xFF181A28.toInt())
    private val lightWallpaper = intArrayOf(0xFFF3E7D8.toInt(), 0xFFD9E4F2.toInt(), 0xFFEEF1E6.toInt())

    private val variants = listOf(
        Variant("clair", Settings(textTone = TextTone.LIGHT), darkWallpaper, night = true),
        Variant("sombre", Settings(textTone = TextTone.DARK), lightWallpaper, night = false),
        Variant("fond", Settings(widgetBackground = true), darkWallpaper, night = true),
    )

    private val matrixWidths = listOf(140, 180, 220, 260, 330)
    private val matrixHeights = listOf(50, 70, 90, 120, 160, 220)

    private val providerSizes = listOf(
        listOf(DpSize(70.dp, 70.dp), DpSize(150.dp, 70.dp), DpSize(150.dp, 150.dp)),
        listOf(DpSize(230.dp, 70.dp)),
        listOf(DpSize(330.dp, 70.dp)),
        listOf(DpSize(330.dp, 150.dp)),
        listOf(DpSize(330.dp, 220.dp)),
    )

    private val overviewSizes = listOf(
        listOf(DpSize(150.dp, 70.dp), DpSize(150.dp, 150.dp)),
        listOf(DpSize(330.dp, 70.dp)),
        listOf(DpSize(330.dp, 150.dp)),
        listOf(DpSize(330.dp, 230.dp)),
    )

    @Test
    fun rendersEveryWidgetAtEverySize() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val now = System.currentTimeMillis()
        val snapshots = demo(now)
        val models = Provider.entries.map { WidgetModel(context, it, true, snapshots.getValue(it), false, now) }
        variants.forEach { variant ->
            RuntimeEnvironment.setQualifiers(if (variant.night) "+night" else "+notnight")
            models.forEach { model ->
                sheet("${model.provider.name.lowercase()}-${variant.name}", providerSizes, variant) { ProviderContent(model) }
            }
            sheet("overview-${variant.name}", overviewSizes, variant) { OverviewContent(models) }
            sheet("states-${variant.name}", listOf(listOf(DpSize(150.dp, 150.dp), DpSize(150.dp, 70.dp))), variant) {
                ProviderContent(WidgetModel(context, Provider.CODEX, false, null, false, now))
            }
        }
        val matrix = matrixHeights.map { h -> matrixWidths.map { w -> DpSize(w.dp, h.dp) } }
        sheet("overview-matrix", matrix, variants.first()) { OverviewContent(models) }
        sheet("claude-matrix", matrix, variants.first()) { ProviderContent(models.first()) }
        assertTrue(File(outDir, "overview-matrix.png").length() > 0)
    }

    private fun sheet(name: String, rows: List<List<DpSize>>, variant: Variant, content: @Composable () -> Unit) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val density = context.resources.displayMetrics.density
        val gap = (14 * density).roundToInt()
        val rendered = rows.map { row -> row.map { render(context, it, variant.settings, content) } }
        val width = rendered.maxOf { row -> row.sumOf { it.width } + gap * (row.size + 1) }
        val height = rendered.sumOf { row -> row.maxOf { it.height } } + gap * (rendered.size + 1)
        val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawPaint(
            Paint().apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), variant.wallpaper, null, Shader.TileMode.CLAMP)
            },
        )
        val outline = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x66FFFFFF
        }
        var y = gap
        rendered.forEach { row ->
            var x = gap
            row.forEach { bitmap ->
                canvas.drawRect(x - 1f, y - 1f, x + bitmap.width + 1f, y + bitmap.height + 1f, outline)
                canvas.drawBitmap(bitmap, x.toFloat(), y.toFloat(), null)
                x += bitmap.width + gap
            }
            y += row.maxOf { it.height } + gap
        }
        File(outDir, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @OptIn(ExperimentalGlanceApi::class)
    private fun render(context: Context, size: DpSize, settings: Settings, content: @Composable () -> Unit): Bitmap = runBlocking {
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt()
        val height = (size.height.value * density).roundToInt()
        val views = Preview(settings, content).compose(context, size = size)
        val view = views.apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun demo(now: Long): Map<Provider, UsageSnapshot> {
        val minute = 60_000L
        return listOf(
            UsageSnapshot(
                provider = Provider.CLAUDE,
                fetchedAtMs = now - 3 * minute,
                plan = "Team · Max 5x",
                windows = listOf(
                    UsageWindow(WindowKind.SESSION, 7.0, now + 133 * minute),
                    UsageWindow(WindowKind.WEEKLY, 47.0, now + 13 * 60 * minute),
                    UsageWindow(WindowKind.WEEKLY_MODEL, 14.0, now + 13 * 60 * minute, "Fable"),
                ),
                spend = Spend(SpendKind.OVERAGE, used = Money(42.0, "EUR"), limit = Money(700.0, "EUR"), percent = 6.0),
            ),
            UsageSnapshot(
                provider = Provider.CODEX,
                fetchedAtMs = now - minute / 2,
                plan = "Plus",
                windows = listOf(
                    UsageWindow(WindowKind.SESSION, 82.0, now + 65 * minute),
                    UsageWindow(WindowKind.WEEKLY, 34.0, now + 4 * 24 * 60 * minute),
                ),
                spend = Spend(SpendKind.CREDITS, balance = Money(12.5, "USD")),
            ),
            UsageSnapshot(
                provider = Provider.NOUS,
                fetchedAtMs = now - 12 * minute,
                plan = "Pro",
                windows = listOf(UsageWindow(WindowKind.MONTHLY, 93.0, now + 21 * 24 * 60 * minute)),
                spend = Spend(SpendKind.PERIOD, used = Money(18.6, "USD"), limit = Money(20.0, "USD"), percent = 93.0, balance = Money(3.25, "USD")),
            ),
        ).associateBy { it.provider }
    }
}

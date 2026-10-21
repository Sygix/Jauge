/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.LocalContext
import fr.sygix.jauge.R
import fr.sygix.jauge.graph
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.ui.Format
import fr.sygix.jauge.ui.MainActivity
import kotlinx.coroutines.flow.first

class OverviewWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.graph.store
        val initialSnapshots = store.snapshots.first()
        val initialConnected = store.connected.first()
        val initialSettings = store.settings.first()
        provideContent {
            val snapshots by store.snapshots.collectAsState(initialSnapshots)
            val connected by store.connected.collectAsState(initialConnected)
            val settings by store.settings.collectAsState(initialSettings)
            val now = System.currentTimeMillis()
            val models = Provider.entries.filter { it in connected }.map {
                WidgetModel(context, it, true, snapshots[it], settings.showRemaining, now)
            }
            WidgetSurface(settings) { OverviewContent(models) }
        }
    }
}

private object Heights {
    val header = 24.dp
    val mini = 16.dp
    val line = 24.dp
    val lineWithDetail = 38.dp
    val block = 44.dp
    val blockWithSpend = 70.dp
    val gap = 6.dp
}

private enum class ListStyle { MINI, LINE, LINE_DETAIL, BLOCK, BLOCK_SPEND }

@Composable
internal fun OverviewContent(models: List<WidgetModel>) {
    val size = LocalSize.current
    val padding = when {
        size.height < 72.dp -> 6.dp
        size.height < 110.dp -> 9.dp
        else -> 12.dp
    }
    val inner = DpSize(size.width - padding * 2, size.height - padding * 2)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground(LocalWidgetColors.current)
            .clickable(actionStartActivity<MainActivity>())
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        when {
            models.isEmpty() -> Empty(inner)
            inner.height < maxOf(72.dp, Heights.mini * models.size) -> RingStrip(models, inner)
            else -> ProviderList(models, inner)
        }
    }
}

@Composable
private fun Empty(inner: DpSize) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Provider.entries.forEach {
                Dot(it.accent)
                Spacer(GlanceModifier.width(4.dp))
            }
        }
        if (inner.height >= 30.dp) {
            Spacer(GlanceModifier.height(4.dp))
            Label(LocalContext.current.getString(if (inner.width >= 170.dp) R.string.widget_tap_connect_all else R.string.widget_connect), size = 11.sp)
        }
    }
}

@Composable
private fun RingStrip(models: List<WidgetModel>, inner: DpSize) {
    val cellWidth = inner.width / models.size
    val ring = minOf(inner.height, 64.dp, cellWidth - 4.dp)
    val textWidth = cellWidth - ring - 8.dp
    val lines = (inner.height.value / 15f).toInt().coerceAtMost(3)
    val withText = textWidth >= 52.dp && lines >= 2
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        models.forEach { model ->
            Row(
                modifier = GlanceModifier.width(cellWidth),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = if (withText) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                if (model.snapshot?.primary == null && model.snapshot?.spend != null) {
                    Column(modifier = GlanceModifier.width(if (withText) ring else cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Dot(model.provider.accent, 6.dp)
                        Label(Format.spend(model.context, model.snapshot.spend), size = 10.sp, bold = true, muted = false)
                    }
                } else RingGauge(model, ring, showCaption = false)
                if (withText) {
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.width(textWidth)) {
                        Label(model.provider.shortName, size = 11.sp, bold = true, muted = false)
                        val primary = model.snapshot?.primary
                        val reset = primary?.let { model.resetIn(it) }
                        when {
                            model.snapshot?.needsLogin == true -> Label(model.string(R.string.widget_relogin), size = 10.sp, color = LocalWidgetColors.current.error)
                            reset != null -> Label(reset, size = 10.sp)
                        }
                        if (lines >= 3) model.snapshot?.secondary?.let { Label(model.short(it), size = 10.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderList(models: List<WidgetModel>, inner: DpSize) {
    val count = models.size
    val gap = if (count > 1) minOf(Heights.gap, ((inner.height - Heights.mini * count) / (count - 1)).coerceAtLeast(0.dp)) else 0.dp
    val gaps = gap * (count - 1)
    val sideBySide = (inner.width - 12.dp) / 2 >= 124.dp
    fun fits(row: Dp, header: Boolean) = row * count + gaps + (if (header) Heights.header else 0.dp) <= inner.height
    val styles = buildList {
        if (sideBySide) {
            add(ListStyle.BLOCK_SPEND)
            add(ListStyle.BLOCK)
        }
        add(ListStyle.LINE_DETAIL)
        add(ListStyle.LINE)
        add(ListStyle.MINI)
    }
    val showHeader = inner.width >= 150.dp
    val withHeader = styles.firstOrNull { showHeader && fits(height(it), true) }
    val withoutHeader = styles.firstOrNull { fits(height(it), false) } ?: ListStyle.MINI
    val style = if (withHeader != null && withHeader.ordinal >= withoutHeader.ordinal) withHeader else withoutHeader
    val header = withHeader != null && style == withHeader
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (header) OverviewHeader(models, inner.width)
        Spacer(GlanceModifier.defaultWeight())
        models.forEachIndexed { index, model ->
            Column(modifier = GlanceModifier.padding(top = if (index > 0) gap else 0.dp)) {
                when (style) {
                    ListStyle.MINI -> MiniLine(model, inner.width)
                    ListStyle.LINE -> ProviderLine(model, inner.width, detail = false)
                    ListStyle.LINE_DETAIL -> ProviderLine(model, inner.width, detail = true)
                    ListStyle.BLOCK -> ProviderBlock(model, inner.width, spend = false)
                    ListStyle.BLOCK_SPEND -> ProviderBlock(model, inner.width, spend = true)
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

private fun height(style: ListStyle): Dp = when (style) {
    ListStyle.MINI -> Heights.mini
    ListStyle.LINE -> Heights.line
    ListStyle.LINE_DETAIL -> Heights.lineWithDetail
    ListStyle.BLOCK -> Heights.block
    ListStyle.BLOCK_SPEND -> Heights.blockWithSpend
}

@Composable
private fun OverviewHeader(models: List<WidgetModel>, width: Dp) {
    val latest = models.mapNotNull { it.snapshot?.fetchedAtMs }.maxOrNull()
    Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
        Label("Jauge", size = 13.sp, bold = true, muted = false, modifier = GlanceModifier.defaultWeight())
        if (width >= 200.dp) latest?.let { Label(Format.updated(models.first().context, it, models.first().nowMs), size = 10.sp) }
        RefreshButton(20.dp)
    }
}

@Composable
private fun TrailingValue(model: WidgetModel) {
    val snapshot = model.snapshot
    val primary = snapshot?.primary
    when {
        snapshot?.needsLogin == true -> Label(model.string(R.string.widget_relogin), size = 10.sp, color = LocalWidgetColors.current.error)
        primary != null -> Label(model.pct(primary), size = 12.sp, bold = true, muted = false)
        snapshot?.spend != null -> Label(Format.spend(model.context, snapshot.spend), size = 11.sp, bold = true, muted = false)
        else -> Label("—", size = 11.sp)
    }
}

@Composable
private fun MiniLine(model: WidgetModel, width: Dp) {
    Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
        Dot(model.provider.accent, 6.dp)
        Spacer(GlanceModifier.width(5.dp))
        Label(model.provider.shortName, size = 11.sp, bold = true, muted = false, modifier = GlanceModifier.defaultWeight())
        TrailingValue(model)
    }
}

@Composable
private fun ProviderLine(model: WidgetModel, width: Dp, detail: Boolean) {
    val snapshot = model.snapshot
    val primary = snapshot?.primary
    Column(modifier = GlanceModifier.width(width)) {
        MiniLine(model, width)
        if (primary != null) {
            Spacer(GlanceModifier.height(3.dp))
            Bar(model.shown(primary) / 100, model.color(primary), width, 5.dp)
        }
        if (detail) {
            val parts = listOfNotNull(
                primary?.let { model.resetIn(it) },
                snapshot?.secondary?.let { model.short(it) },
            )
            if (parts.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                Label(parts.joinToString(" · "), size = 10.sp)
            }
        }
    }
}

@Composable
private fun ProviderBlock(model: WidgetModel, width: Dp, spend: Boolean) {
    val snapshot = model.snapshot
    val gap = 12.dp
    val half = (width - gap) / 2
    Column(modifier = GlanceModifier.width(width)) {
        Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
            Dot(model.provider.accent, 7.dp)
            Spacer(GlanceModifier.width(6.dp))
            Label(model.provider.shortName, size = 12.sp, bold = true, muted = false)
            Spacer(GlanceModifier.width(6.dp))
            Label(snapshot?.plan.orEmpty(), size = 10.sp, modifier = GlanceModifier.defaultWeight())
            when {
                snapshot?.needsLogin == true -> Label(model.string(R.string.widget_relogin_long), size = 10.sp, color = LocalWidgetColors.current.error)
                snapshot?.error != null -> Label("⚠", size = 10.sp, color = LocalWidgetColors.current.error)
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        val primary = snapshot?.primary
        val secondary = snapshot?.secondary
        Row(modifier = GlanceModifier.width(width)) {
            if (primary != null) WindowRow(model, primary, if (secondary != null) half else width, compact = true)
            if (secondary != null) {
                Spacer(GlanceModifier.width(gap))
                WindowRow(model, secondary, half, compact = true)
            }
        }
        if ((spend || primary == null) && snapshot?.spend != null) {
            Spacer(GlanceModifier.height(4.dp))
            SpendRow(model, width)
        }
    }
}

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
}

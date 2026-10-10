/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.sygix.jauge.R
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.ui.Format
import kotlin.math.roundToInt

fun fixed(color: Int): ColorProvider = ColorProvider(day = Color(color), night = Color(color))

data class WidgetModel(
    val provider: Provider,
    val connected: Boolean,
    val snapshot: UsageSnapshot?,
    val showRemaining: Boolean,
    val nowMs: Long,
) {
    fun shown(window: UsageWindow): Double = Format.shown(window.usedPercent, showRemaining)

    fun color(window: UsageWindow?): Int = window?.let { Palette.forUsage(provider, it.usedPercent) } ?: provider.accent
}

@Composable
fun RingGauge(model: WidgetModel, size: Dp, showCaption: Boolean = true, captionOverride: String? = null) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).roundToInt().coerceIn(48, 420)
    val outer = model.snapshot?.primary
    val inner = model.snapshot?.secondary
    val stroke = px * 0.095f
    val rings = listOfNotNull(outer, inner).map { window ->
        val color = model.color(window)
        RingSpec((model.shown(window) / 100).toFloat(), color, Palette.soft(color, model.provider))
    }.ifEmpty { listOf(RingSpec(0f, model.provider.accent, model.provider.accentSoft)) }
    val bitmap = GaugeBitmap.render(px, rings, stroke, stroke * 0.35f)
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(provider = ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = outer?.let { Format.percentShort(model.shown(it)) } ?: "—",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = (size.value * if (inner != null) 0.2f else 0.24f).sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (showCaption && size.value >= 64) {
                Text(
                    text = captionOverride ?: outer?.let { Format.countdown(it.resetsAtMs, model.nowMs) } ?: model.provider.shortName,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = (size.value * 0.11f).coerceIn(8f, 12f).sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Bar(fraction: Double, color: Int, width: Dp, height: Dp = 6.dp) {
    val clamped = fraction.coerceIn(0.0, 1.0)
    Box(
        modifier = GlanceModifier.width(width).height(height).background(fixed(Palette.track(color))).cornerRadius(height / 2),
    ) {
        if (clamped > 0.0) {
            val filled = (width.value * clamped.toFloat()).coerceAtLeast(height.value).dp
            Box(modifier = GlanceModifier.width(filled).height(height).background(fixed(color)).cornerRadius(height / 2)) {}
        }
    }
}

@Composable
fun Dot(color: Int, size: Dp = 8.dp) {
    Box(modifier = GlanceModifier.size(size).background(fixed(color)).cornerRadius(size / 2)) {}
}

@Composable
fun Label(text: String, size: TextUnit = 11.sp, bold: Boolean = false, muted: Boolean = true, color: ColorProvider? = null, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color ?: if (muted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
        maxLines = 1,
    )
}

@Composable
fun WindowRow(model: WidgetModel, window: UsageWindow, width: Dp, compact: Boolean = false) {
    val color = model.color(window)
    Column(modifier = GlanceModifier.width(width)) {
        Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
            Label(window.label, size = if (compact) 10.sp else 11.sp, modifier = GlanceModifier.defaultWeight())
            val countdown = Format.countdown(window.resetsAtMs, model.nowMs)
            if (countdown != null && width.value >= 120) {
                Label("↻ $countdown", size = 10.sp)
                Spacer(GlanceModifier.width(6.dp))
            }
            Label(Format.percentShort(model.shown(window)), size = if (compact) 11.sp else 12.sp, bold = true, muted = false)
        }
        Spacer(GlanceModifier.height(3.dp))
        Bar(model.shown(window) / 100, color, width, if (compact) 5.dp else 6.dp)
    }
}

@Composable
fun SpendRow(model: WidgetModel, width: Dp) {
    val spend = model.snapshot?.spend ?: return
    Column(modifier = GlanceModifier.width(width)) {
        Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
            Label(spend.label, modifier = GlanceModifier.defaultWeight())
            Label(Format.spend(spend), bold = true, muted = false)
        }
        val percent = spend.percent
        if (percent != null && spend.enabled) {
            Spacer(GlanceModifier.height(3.dp))
            Bar(percent / 100, Palette.forUsage(model.provider, percent), width, 4.dp)
        }
    }
}

@Composable
fun Header(model: WidgetModel, width: Dp, showRefresh: Boolean = true) {
    val snapshot = model.snapshot
    Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
        Dot(model.provider.accent)
        Spacer(GlanceModifier.width(6.dp))
        Label(model.provider.shortName, size = 13.sp, bold = true, muted = false)
        snapshot?.plan?.let {
            Spacer(GlanceModifier.width(6.dp))
            Label(it, size = 10.sp)
        }
        Spacer(GlanceModifier.defaultWeight())
        StatusText(model)
        if (showRefresh) RefreshButton()
    }
}

@Composable
fun StatusText(model: WidgetModel) {
    val snapshot = model.snapshot ?: return
    val text = when {
        snapshot.needsLogin -> "Reconnexion requise"
        snapshot.error != null -> "⚠ " + Format.updated(snapshot.fetchedAtMs, model.nowMs)
        else -> Format.updated(snapshot.fetchedAtMs, model.nowMs)
    }
    Label(text, size = 10.sp, color = if (snapshot.error != null) GlanceTheme.colors.error else null)
}

@Composable
fun RefreshButton(size: Dp = 22.dp) {
    Box(modifier = GlanceModifier.size(size).clickable(actionRunCallback<RefreshAction>()), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Actualiser",
            modifier = GlanceModifier.size(size * 0.7f),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
fun Placeholder(model: WidgetModel, small: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(model.provider.accent)
            if (!small) {
                Spacer(GlanceModifier.width(6.dp))
                Label(model.provider.shortName, size = 13.sp, bold = true, muted = false)
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Label(
            text = when {
                !model.connected -> if (small) "Connecter" else "Touchez pour connecter"
                else -> "Actualisation…"
            },
            size = 10.sp,
        )
    }
}

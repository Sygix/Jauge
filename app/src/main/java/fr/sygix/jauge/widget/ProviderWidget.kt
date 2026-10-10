/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import fr.sygix.jauge.graph
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.WindowKind
import fr.sygix.jauge.ui.Format
import fr.sygix.jauge.ui.MainActivity
import kotlinx.coroutines.flow.first

abstract class ProviderWidget(private val provider: Provider) : GlanceAppWidget() {

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
            val model = WidgetModel(
                provider = provider,
                connected = provider in connected,
                snapshot = snapshots[provider]?.takeIf { provider in connected },
                showRemaining = settings.showRemaining,
                nowMs = System.currentTimeMillis(),
            )
            GlanceTheme { ProviderContent(model) }
        }
    }
}

@Composable
internal fun ProviderContent(model: WidgetModel) {
    val size = LocalSize.current
    val small = size.width < 100.dp || size.height < 100.dp
    val padding = if (small) 8.dp else 12.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        val inner = DpSize(size.width - padding * 2, size.height - padding * 2)
        when {
            model.snapshot == null || model.snapshot.windows.isEmpty() && model.snapshot.error == null ->
                Placeholder(model, small = size.width < 110.dp)
            size.height < 100.dp && size.width < 100.dp -> Tiny(model, inner)
            size.height < 100.dp && size.width < 180.dp -> Compact(model, inner)
            size.height < 100.dp -> Strip(model, inner)
            size.width < 180.dp -> Square(model, inner)
            else -> Panel(model, inner)
        }
    }
}

@Composable
private fun Tiny(model: WidgetModel, inner: DpSize) {
    RingGauge(model, minOf(inner.width, inner.height), showCaption = false)
}

@Composable
private fun Compact(model: WidgetModel, inner: DpSize) {
    val snapshot = model.snapshot ?: return
    val ring = minOf(inner.height, 72.dp)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        RingGauge(model, ring, showCaption = false)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(model.provider.accent, 6.dp)
                Spacer(GlanceModifier.width(4.dp))
                Label(model.provider.shortName, size = 11.sp, bold = true, muted = false)
            }
            snapshot.primary?.let {
                Label(it.label, size = 10.sp)
                Format.countdown(it.resetsAtMs, model.nowMs)?.let { c -> Label("↻ $c", size = 10.sp) }
            }
            snapshot.secondary?.let { Label("${shortLabel(it.kind)} ${Format.percentShort(model.shown(it))}", size = 10.sp, bold = true, muted = false) }
        }
    }
}

@Composable
private fun Strip(model: WidgetModel, inner: DpSize) {
    val snapshot = model.snapshot ?: return
    val ring = minOf(inner.height, 64.dp)
    val gap = 12.dp
    val barWidth = inner.width - ring - gap
    val showHeader = inner.height >= 76.dp
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        RingGauge(model, ring, showCaption = false)
        Spacer(GlanceModifier.width(gap))
        Column(modifier = GlanceModifier.width(barWidth), verticalAlignment = Alignment.CenterVertically) {
            if (showHeader) {
                Row(modifier = GlanceModifier.width(barWidth), verticalAlignment = Alignment.CenterVertically) {
                    Dot(model.provider.accent, 6.dp)
                    Spacer(GlanceModifier.width(4.dp))
                    Label(model.provider.shortName, size = 11.sp, bold = true, muted = false, modifier = GlanceModifier.defaultWeight())
                    StatusText(model)
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            val windows = listOfNotNull(snapshot.primary, snapshot.secondary)
            windows.forEachIndexed { index, window ->
                if (index > 0) Spacer(GlanceModifier.height(5.dp))
                WindowRow(model, window, barWidth, compact = true)
            }
            if (windows.isEmpty()) snapshot.error?.let { Label(it, size = 11.sp, color = GlanceTheme.colors.error) }
        }
    }
}

@Composable
private fun Square(model: WidgetModel, inner: DpSize) {
    val snapshot = model.snapshot ?: return
    val footer = snapshot.secondary
    val ring = minOf(inner.width, inner.height - 18.dp - if (footer != null) 16.dp else 0.dp)
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = GlanceModifier.width(inner.width), verticalAlignment = Alignment.CenterVertically) {
            Dot(model.provider.accent, 6.dp)
            Spacer(GlanceModifier.width(4.dp))
            Label(model.provider.shortName, size = 11.sp, bold = true, muted = false, modifier = GlanceModifier.defaultWeight())
            RefreshButton(18.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        RingGauge(model, ring)
        Spacer(GlanceModifier.defaultWeight())
        footer?.let {
            Label(
                "${shortLabel(it.kind)} ${Format.percentShort(model.shown(it))}" +
                    (Format.countdown(it.resetsAtMs, model.nowMs)?.let { c -> " · ↻ $c" } ?: ""),
                size = 10.sp,
            )
        }
    }
}

@Composable
private fun Panel(model: WidgetModel, inner: DpSize) {
    val snapshot = model.snapshot ?: return
    val ring = minOf(inner.height - 28.dp, inner.width * 0.36f, 120.dp)
    val showRing = inner.width >= 260.dp && ring >= 72.dp
    val listWidth = if (showRing) inner.width - ring - 14.dp else inner.width
    val available = inner.height - 26.dp
    val rowHeight = 30.dp
    val spendHeight = if (snapshot.spend != null) 30.dp else 0.dp
    val maxRows = ((available - spendHeight).value / rowHeight.value).toInt().coerceAtLeast(1)
    val windows = snapshot.windows.take(maxRows)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(model, inner.width)
        Spacer(GlanceModifier.height(8.dp))
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.width(inner.width), verticalAlignment = Alignment.CenterVertically) {
            if (showRing) {
                RingGauge(model, ring)
                Spacer(GlanceModifier.width(14.dp))
            }
            Column(modifier = GlanceModifier.width(listWidth)) {
                if (windows.isEmpty()) snapshot.error?.let { Label(it, size = 12.sp, color = GlanceTheme.colors.error) }
                windows.forEachIndexed { index, window ->
                    if (index > 0) Spacer(GlanceModifier.height(7.dp))
                    WindowRow(model, window, listWidth)
                }
                if (snapshot.spend != null && available - rowHeight * windows.size >= spendHeight) {
                    Spacer(GlanceModifier.height(8.dp))
                    SpendRow(model, listWidth)
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

private fun shortLabel(kind: WindowKind): String = when (kind) {
    WindowKind.SESSION -> "5 h"
    WindowKind.WEEKLY -> "Sem."
    WindowKind.WEEKLY_MODEL -> "Modèle"
    WindowKind.MONTHLY -> "Mois"
}

class ClaudeWidget : ProviderWidget(Provider.CLAUDE)
class CodexWidget : ProviderWidget(Provider.CODEX)
class NousWidget : ProviderWidget(Provider.NOUS)

class ClaudeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClaudeWidget()
}

class CodexWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CodexWidget()
}

class NousWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NousWidget()
}

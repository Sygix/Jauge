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
                WidgetModel(it, true, snapshots[it], settings.showRemaining, now)
            }
            GlanceTheme { OverviewContent(models) }
        }
    }
}

@Composable
internal fun OverviewContent(models: List<WidgetModel>) {
    val size = LocalSize.current
    val padding = if (size.height < 100.dp) 10.dp else 12.dp
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
            models.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Provider.entries.forEach {
                        Dot(it.accent)
                        Spacer(GlanceModifier.width(4.dp))
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                Label("Touchez pour connecter vos comptes", size = 11.sp)
            }
            size.height < 100.dp -> OverviewStrip(models, inner)
            size.width < 180.dp -> OverviewStack(models, inner)
            else -> OverviewPanel(models, inner)
        }
    }
}

@Composable
private fun OverviewStrip(models: List<WidgetModel>, inner: DpSize) {
    val cellWidth = inner.width / models.size
    val ring = minOf(inner.height, 64.dp, cellWidth)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        models.forEach { model ->
            Row(modifier = GlanceModifier.width(cellWidth), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                if (cellWidth >= ring + 56.dp) {
                    RingGauge(model, ring, showCaption = false)
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Label(model.provider.shortName, size = 11.sp, bold = true, muted = false)
                        model.snapshot?.primary?.let { window ->
                            Format.countdown(window.resetsAtMs, model.nowMs)?.let { Label("↻ $it", size = 10.sp) }
                        }
                        model.snapshot?.secondary?.let { Label("Sem. ${Format.percentShort(model.shown(it))}", size = 10.sp) }
                    }
                } else {
                    RingGauge(model, ring, captionOverride = model.provider.shortName)
                }
            }
        }
    }
}

@Composable
private fun OverviewStack(models: List<WidgetModel>, inner: DpSize) {
    val blockHeight = inner.height / models.size
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        models.forEachIndexed { index, model ->
            if (index > 0) Spacer(GlanceModifier.height(8.dp))
            ProviderLine(model, inner.width, showSecondary = blockHeight >= 56.dp)
        }
    }
}

@Composable
private fun ProviderLine(model: WidgetModel, width: Dp, showSecondary: Boolean) {
    val snapshot = model.snapshot
    val primary = snapshot?.primary
    Column(modifier = GlanceModifier.width(width)) {
        Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
            Dot(model.provider.accent, 6.dp)
            Spacer(GlanceModifier.width(5.dp))
            Label(model.provider.shortName, size = 11.sp, bold = true, muted = false, modifier = GlanceModifier.defaultWeight())
            when {
                snapshot?.needsLogin == true -> Label("Reconnexion", size = 10.sp, color = GlanceTheme.colors.error)
                primary != null -> Label(Format.percentShort(model.shown(primary)), size = 12.sp, bold = true, muted = false)
                else -> Label("—", size = 11.sp)
            }
        }
        Spacer(GlanceModifier.height(3.dp))
        Bar((primary?.let { model.shown(it) } ?: 0.0) / 100, model.color(primary), width, 5.dp)
        if (showSecondary) {
            val secondary = snapshot?.secondary
            val countdown = primary?.let { Format.countdown(it.resetsAtMs, model.nowMs) }
            val parts = listOfNotNull(
                countdown?.let { "↻ $it" },
                secondary?.let { "Sem. ${Format.percentShort(model.shown(it))}" },
            )
            if (parts.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                Label(parts.joinToString(" · "), size = 10.sp)
            }
        }
    }
}

@Composable
private fun OverviewPanel(models: List<WidgetModel>, inner: DpSize) {
    val headerHeight = 22.dp
    val blockHeight = (inner.height - headerHeight) / models.size
    val columnGap = 12.dp
    val half = (inner.width - columnGap) / 2
    val latest = models.mapNotNull { it.snapshot?.fetchedAtMs }.maxOrNull()
    val now = models.first().nowMs
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.width(inner.width), verticalAlignment = Alignment.CenterVertically) {
            Label("Jauge", size = 13.sp, bold = true, muted = false, modifier = GlanceModifier.defaultWeight())
            latest?.let { Label(Format.updated(it, now), size = 10.sp) }
            RefreshButton()
        }
        Spacer(GlanceModifier.defaultWeight())
        models.forEachIndexed { index, model ->
            if (index > 0) Spacer(GlanceModifier.defaultWeight())
            if (blockHeight < 44.dp) {
                ProviderLine(model, inner.width, showSecondary = false)
            } else {
                ProviderBlock(model, inner.width, half, columnGap, showSpend = blockHeight >= 76.dp)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun ProviderBlock(model: WidgetModel, width: Dp, half: Dp, gap: Dp, showSpend: Boolean) {
    val snapshot = model.snapshot
    Column(modifier = GlanceModifier.width(width)) {
        Row(modifier = GlanceModifier.width(width), verticalAlignment = Alignment.CenterVertically) {
            Dot(model.provider.accent, 7.dp)
            Spacer(GlanceModifier.width(6.dp))
            Label(model.provider.shortName, size = 12.sp, bold = true, muted = false)
            snapshot?.plan?.let {
                Spacer(GlanceModifier.width(6.dp))
                Label(it, size = 10.sp)
            }
            Spacer(GlanceModifier.defaultWeight())
            when {
                snapshot?.needsLogin == true -> Label("Reconnexion requise", size = 10.sp, color = GlanceTheme.colors.error)
                snapshot?.error != null -> Label("⚠ ${snapshot.error}", size = 10.sp, color = GlanceTheme.colors.error)
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
        if (showSpend && snapshot?.spend != null) {
            Spacer(GlanceModifier.height(4.dp))
            SpendRow(model, width)
        }
    }
}

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
}

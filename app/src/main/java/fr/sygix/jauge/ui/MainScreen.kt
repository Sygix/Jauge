/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.sygix.jauge.BuildConfig
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.store.Settings
import fr.sygix.jauge.widget.ClaudeWidgetReceiver
import fr.sygix.jauge.widget.CodexWidgetReceiver
import fr.sygix.jauge.widget.NousWidgetReceiver
import fr.sygix.jauge.widget.OverviewWidgetReceiver
import fr.sygix.jauge.widget.Palette
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = rememberNow()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Jauge") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = ui.providers.any { it.connected }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Tout actualiser")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ui.providers, key = { it.provider.name }) { item ->
                ProviderCard(
                    item = item,
                    nowMs = now,
                    showRemaining = ui.settings.showRemaining,
                    refreshing = item.provider in ui.refreshing,
                    onConnect = {
                        when (item.provider) {
                            Provider.NOUS -> viewModel.startDeviceLogin(Provider.NOUS)
                            else -> viewModel.startBrowserLogin(item.provider)?.let { Links.open(context, it, item.provider.accent) }
                        }
                    },
                    onDeviceCode = { viewModel.startDeviceLogin(item.provider) },
                    onRefresh = { viewModel.refresh(item.provider) },
                    onDisconnect = { viewModel.disconnect(item.provider) },
                )
            }
            item { WidgetsCard() }
            item { SettingsCard(ui.settings) { viewModel.updateSettings(it) } }
            item {
                Text(
                    "Jauge ${BuildConfig.VERSION_NAME} · github.com/Sygix/Jauge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    LoginDialog(
        state = login,
        onOpen = { url, provider -> Links.open(context, url, provider.accent) },
        onManual = { Links.open(context, viewModel.startManualClaude(), Provider.CLAUDE.accent) },
        onSubmitCode = viewModel::submitManualCode,
        onRetry = { provider -> viewModel.acknowledgeLogin(); if (provider == Provider.NOUS) viewModel.startDeviceLogin(provider) else viewModel.startBrowserLogin(provider)?.let { Links.open(context, it, provider.accent) } },
        onCancel = viewModel::cancelLogin,
        onDismiss = viewModel::acknowledgeLogin,
    )
}

@Composable
private fun rememberNow(): Long {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            state.longValue = System.currentTimeMillis()
        }
    }
    return state.longValue
}

@Composable
private fun ProviderCard(
    item: ProviderUi,
    nowMs: Long,
    showRemaining: Boolean,
    refreshing: Boolean,
    onConnect: () -> Unit,
    onDeviceCode: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val provider = item.provider
    val snapshot = item.snapshot
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(provider.accent), CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        snapshot?.plan?.let {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = Color(provider.accent).copy(alpha = 0.16f), shape = CircleShape) {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                    snapshot?.account?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            if (!item.connected) {
                Spacer(Modifier.height(12.dp))
                Text(description(provider), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConnect) { Text(if (provider == Provider.NOUS) "Se connecter (code appareil)" else "Se connecter") }
                    if (provider == Provider.CODEX) OutlinedButton(onClick = onDeviceCode) { Text("Code appareil") }
                }
                return@Column
            }

            snapshot?.windows?.forEach { window ->
                Spacer(Modifier.height(14.dp))
                WindowLine(provider, window, nowMs, showRemaining)
            }
            snapshot?.spend?.let { spend ->
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spend.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Format.spend(spend), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                spend.percent?.takeIf { spend.enabled }?.let { percent ->
                    Spacer(Modifier.height(6.dp))
                    Gauge((percent / 100).toFloat(), Color(Palette.forUsage(provider, percent)), 6)
                }
                if (!spend.enabled) Text("Désactivé", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(14.dp))
            val status = when {
                snapshot == null -> "En attente de la première actualisation"
                snapshot.needsLogin -> "Session expirée, reconnectez-vous"
                snapshot.error != null -> "${snapshot.error} · données ${Format.updated(snapshot.fetchedAtMs, nowMs)}"
                else -> "Mis à jour ${Format.updated(snapshot.fetchedAtMs, nowMs)}"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (snapshot?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot?.needsLogin == true) Button(onClick = onConnect) { Text("Se reconnecter") }
                else OutlinedButton(onClick = onRefresh, enabled = !refreshing) { Text("Actualiser") }
                TextButton(onClick = onDisconnect) { Text("Déconnecter") }
            }
        }
    }
}

@Composable
private fun WindowLine(provider: Provider, window: UsageWindow, nowMs: Long, showRemaining: Boolean) {
    val shown = Format.shown(window.usedPercent, showRemaining)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(window.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            Format.percent(shown) + if (showRemaining) " restant" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(6.dp))
    Gauge((shown / 100).toFloat(), Color(Palette.forUsage(provider, window.usedPercent)), 8)
    val countdown = Format.countdown(window.resetsAtMs, nowMs)
    if (countdown != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Réinitialisation dans $countdown · ${Format.resetAt(window.resetsAtMs, nowMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Gauge(fraction: Float, color: Color, heightDp: Int) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

private fun description(provider: Provider): String = when (provider) {
    Provider.CLAUDE -> "Connectez votre compte Claude Pro, Max ou Team pour suivre la session de 5 h, la semaine et le dépassement."
    Provider.CODEX -> "Connectez votre compte ChatGPT pour suivre les limites Codex (5 h, semaine) et vos crédits."
    Provider.NOUS -> "Connectez votre compte Nous Portal (Hermes Agent) pour suivre vos crédits mensuels et vos dépenses."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetsCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Widgets, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Widgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Ajoutez un widget puis redimensionnez-le librement : de 1×1 à 4×3, la mise en page s'adapte.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Vue d'ensemble" to OverviewWidgetReceiver::class.java,
                    "Claude" to ClaudeWidgetReceiver::class.java,
                    "Codex" to CodexWidgetReceiver::class.java,
                    "Nous" to NousWidgetReceiver::class.java,
                ).forEach { (label, receiver) ->
                    OutlinedButton(onClick = { Links.pinWidget(context, receiver) }) { Text(label) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Text("Actualisation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Fréquence de mise à jour des widgets en arrière-plan. Android peut la retarder en économie d'énergie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Settings.REFRESH_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = settings.refreshMinutes == minutes,
                        onClick = { onChange { it.copy(refreshMinutes = minutes) } },
                        label = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} h") },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            SwitchRow(
                title = "Afficher le restant",
                subtitle = "Les jauges indiquent ce qu'il reste plutôt que ce qui est consommé",
                checked = settings.showRemaining,
            ) { checked -> onChange { it.copy(showRemaining = checked) } }
            SwitchRow(
                title = "Wi‑Fi uniquement",
                subtitle = "N'actualiser en arrière-plan que sur un réseau non limité",
                checked = settings.wifiOnly,
            ) { checked -> onChange { it.copy(wifiOnly = checked) } }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

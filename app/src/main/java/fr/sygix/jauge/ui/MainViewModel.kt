/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.sygix.jauge.auth.LoginState
import fr.sygix.jauge.graph
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.store.Settings
import fr.sygix.jauge.sync.RefreshScheduler
import fr.sygix.jauge.widget.JaugeWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderUi(val provider: Provider, val connected: Boolean, val snapshot: UsageSnapshot?)

data class MainUi(
    val providers: List<ProviderUi> = Provider.entries.map { ProviderUi(it, false, null) },
    val settings: Settings = Settings(),
    val refreshing: Set<Provider> = emptySet(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = application.graph
    private val refreshing = MutableStateFlow<Set<Provider>>(emptySet())

    val login: StateFlow<LoginState> = graph.login.state

    val ui: StateFlow<MainUi> = combine(
        graph.store.connected,
        graph.store.snapshots,
        graph.store.settings,
        refreshing,
    ) { connected, snapshots, settings, busy ->
        MainUi(
            providers = Provider.entries.map { ProviderUi(it, it in connected, snapshots[it].takeIf { _ -> it in connected }) },
            settings = settings,
            refreshing = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUi())

    fun refresh(provider: Provider? = null, automatic: Boolean = false) {
        viewModelScope.launch {
            val settings = graph.store.settings.first()
            val targets = (provider?.let { setOf(it) } ?: graph.store.connected.first())
                .filter { !automatic || settings.refreshMinutes(it) != null }.toSet()
            if (targets.isEmpty()) return@launch
            refreshing.value = refreshing.value + targets
            try {
                if (provider == null) graph.sync.syncAll(automatic) else graph.sync.sync(provider)
            } finally {
                refreshing.value = refreshing.value - targets
            }
        }
    }

    fun disconnect(provider: Provider) {
        viewModelScope.launch {
            graph.store.disconnect(provider)
            RefreshScheduler.ensure(getApplication())
            JaugeWidgets.updateAll(getApplication())
        }
    }

    fun startBrowserLogin(provider: Provider): String? = graph.login.startBrowser(provider)

    fun startManualClaude(): String = graph.login.startManualClaude()

    fun submitManualCode(code: String) = graph.login.submitManualCode(code)

    fun startDeviceLogin(provider: Provider) = graph.login.startDevice(provider)

    fun startApiKeyLogin(provider: Provider) = graph.login.startApiKey(provider)

    fun submitApiKey(key: String) = graph.login.submitApiKey(key)

    fun cancelLogin() = graph.login.cancel()

    fun acknowledgeLogin() = graph.login.acknowledge()

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val previous = graph.store.settings.first()
            graph.store.updateSettings(transform)
            val settings = graph.store.settings.first()
            RefreshScheduler.restart(getApplication(), previous, settings)
            JaugeWidgets.updateAll(getApplication())
        }
    }
}

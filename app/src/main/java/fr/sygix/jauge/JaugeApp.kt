/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge

import android.app.Application
import android.content.Context
import fr.sygix.jauge.auth.LoginBroker
import fr.sygix.jauge.provider.ClaudeClient
import fr.sygix.jauge.provider.CodexClient
import fr.sygix.jauge.provider.NousClient
import fr.sygix.jauge.provider.OpenRouterClient
import fr.sygix.jauge.provider.OpenCodeClient
import fr.sygix.jauge.store.JaugeStore
import fr.sygix.jauge.sync.RefreshScheduler
import fr.sygix.jauge.sync.UsageSync
import fr.sygix.jauge.widget.JaugeWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppGraph(context: Context) {
    val store = JaugeStore(context)
    val sync = UsageSync(
        store = store,
        clients = listOf(ClaudeClient(), CodexClient(), NousClient(), OpenRouterClient(), OpenCodeClient()).associateBy { it.provider },
        onUpdated = { JaugeWidgets.updateAll(context) },
    )
    val login = LoginBroker(context, sync)
}

class JaugeApp : Application() {
    lateinit var graph: AppGraph
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        scope.launch { RefreshScheduler.ensure(this@JaugeApp) }
    }
}

val Context.graph: AppGraph get() = (applicationContext as JaugeApp).graph

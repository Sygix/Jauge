/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.sync

import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.store.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class RefreshState(val settings: Settings, val connected: Set<Provider>)

internal class RefreshReconciler {
    private val mutex = Mutex()

    suspend fun run(read: suspend () -> RefreshState, apply: suspend (RefreshState) -> Unit) {
        mutex.withLock { apply(read()) }
    }
}

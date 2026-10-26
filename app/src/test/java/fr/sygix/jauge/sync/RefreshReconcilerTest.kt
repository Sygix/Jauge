/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.sync

import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.store.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshReconcilerTest {
    @Test
    fun anOldDisabledPassCannotCancelANewerEnabledSchedule() = runTest {
        val reconciler = RefreshReconciler()
        var state = RefreshState(Settings(providerRefreshMinutes = mapOf(Provider.CLAUDE to 0)), setOf(Provider.CLAUDE))
        var scheduled = false
        val resume = CompletableDeferred<Unit>()
        val old = launch {
            reconciler.run({ state }) { snapshot ->
                resume.await()
                scheduled = snapshot.settings.refreshMinutes(Provider.CLAUDE) != null
            }
        }
        runCurrent()
        state = state.copy(settings = Settings())
        val enabled = launch {
            reconciler.run({ state }) { snapshot -> scheduled = snapshot.settings.refreshMinutes(Provider.CLAUDE) != null }
        }
        runCurrent()
        resume.complete(Unit)
        old.join()
        enabled.join()
        assertTrue(scheduled)
    }

    @Test
    fun aQueuedPassReadsConnectionStateAfterAcquiringTheLock() = runTest {
        val reconciler = RefreshReconciler()
        var state = RefreshState(Settings(), emptySet())
        val resume = CompletableDeferred<Unit>()
        val first = launch { reconciler.run({ state }) { resume.await() } }
        runCurrent()
        var scheduled = false
        var reads = 0
        val queued = launch {
            reconciler.run({ reads++; state }) { snapshot -> scheduled = Provider.CLAUDE in snapshot.connected }
        }
        runCurrent()
        assertEquals(0, reads)
        state = state.copy(connected = setOf(Provider.CLAUDE))
        resume.complete(Unit)
        first.join()
        queued.join()
        assertTrue(scheduled)
    }
}

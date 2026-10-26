/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.sync

import android.app.Application
import androidx.work.NetworkType
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.store.Settings
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RefreshSchedulerTest {
    @Test
    fun providersHaveIndependentDelaysAndWorkNames() {
        val settings = Settings(refreshMinutes = 30, providerRefreshMinutes = mapOf(Provider.CLAUDE to 5, Provider.CODEX to 240))
        val claude = RefreshScheduler.loopRequest(settings, Provider.CLAUDE)!!.workSpec
        val codex = RefreshScheduler.loopRequest(settings, Provider.CODEX)!!.workSpec
        val nous = RefreshScheduler.loopRequest(settings, Provider.NOUS)!!.workSpec
        assertEquals(TimeUnit.MINUTES.toMillis(5), claude.initialDelay)
        assertEquals(TimeUnit.MINUTES.toMillis(240), codex.initialDelay)
        assertEquals(TimeUnit.MINUTES.toMillis(30), nous.initialDelay)
        assertEquals(Provider.CLAUDE.name, claude.input.getString(RefreshWorker.KEY_PROVIDER))
        assertEquals(Provider.CODEX.name, codex.input.getString(RefreshWorker.KEY_PROVIDER))
        assertTrue(claude.input.getBoolean(RefreshWorker.KEY_LOOP, false))
        assertEquals(Provider.entries.size, Provider.entries.map(RefreshScheduler::loopName).distinct().size)
    }

    @Test
    fun disabledProvidersHaveNoAutomaticWorkRequest() {
        val settings = Settings(providerRefreshMinutes = mapOf(Provider.CLAUDE to 0))
        assertNull(RefreshScheduler.loopRequest(settings, Provider.CLAUDE))
        assertNotNull(RefreshScheduler.loopRequest(settings, Provider.CODEX))
    }

    @Test
    fun wifiOnlyAppliesToEveryProviderSchedule() {
        Provider.entries.forEach { provider ->
            assertEquals(NetworkType.UNMETERED, RefreshScheduler.loopRequest(Settings(wifiOnly = true), provider)!!.workSpec.constraints.requiredNetworkType)
            assertEquals(NetworkType.CONNECTED, RefreshScheduler.loopRequest(Settings(), provider)!!.workSpec.constraints.requiredNetworkType)
        }
    }
}

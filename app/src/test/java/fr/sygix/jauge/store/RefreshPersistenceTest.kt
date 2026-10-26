/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.store

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.sygix.jauge.model.Provider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RefreshPersistenceTest {
    @Test
    fun overridesAndDisabledModeSurviveStoreRecreationAndCanBeReset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = JaugeStore(context)
        store.updateSettings { Settings(refreshMinutes = 30) }
        assertTrue(store.settings.first().providerRefreshMinutes.isEmpty())
        store.updateSettings { it.copy(providerRefreshMinutes = mapOf(Provider.CLAUDE to 5, Provider.CODEX to 0)) }
        val recreated = JaugeStore(context)
        assertEquals(5, recreated.settings.first().refreshMinutes(Provider.CLAUDE))
        assertNull(recreated.settings.first().refreshMinutes(Provider.CODEX))
        assertEquals(30, recreated.settings.first().refreshMinutes(Provider.NOUS))
        recreated.updateSettings { it.copy(refreshMinutes = 60, providerRefreshMinutes = it.providerRefreshMinutes - Provider.CLAUDE) }
        assertEquals(60, store.settings.first().refreshMinutes(Provider.CLAUDE))
        assertNull(store.settings.first().refreshMinutes(Provider.CODEX))
    }

    @Test
    fun concurrentProviderChangesDoNotLoseOverrides() = runBlocking {
        val store = JaugeStore(ApplicationProvider.getApplicationContext<Application>())
        store.updateSettings { Settings() }
        Provider.entries.map { provider ->
            async { store.updateSettings { it.copy(providerRefreshMinutes = it.providerRefreshMinutes + (provider to 30)) } }
        }.awaitAll()
        assertEquals(Provider.entries.associateWith { 30 }, store.settings.first().providerRefreshMinutes)
    }
}

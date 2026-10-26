/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.store

import fr.sygix.jauge.model.Provider
import org.junit.Assert.*
import org.junit.Test

class RefreshSettingsTest {
    @Test
    fun existingInstallationsInheritTheGlobalInterval() {
        val settings = Settings(refreshMinutes = 30)
        Provider.entries.forEach { assertEquals(30, settings.refreshMinutes(it)) }
    }

    @Test
    fun globalChangesOnlyAffectInheritedIntervals() {
        val settings = Settings(providerRefreshMinutes = mapOf(Provider.CLAUDE to 5, Provider.CODEX to 0))
            .copy(refreshMinutes = 120)
        assertEquals(5, settings.refreshMinutes(Provider.CLAUDE))
        assertNull(settings.refreshMinutes(Provider.CODEX))
        assertEquals(120, settings.refreshMinutes(Provider.NOUS))
    }

    @Test
    fun removingAnOverrideRestoresInheritance() {
        val settings = Settings(refreshMinutes = 60, providerRefreshMinutes = mapOf(Provider.CLAUDE to 0))
        assertEquals(60, settings.copy(providerRefreshMinutes = emptyMap()).refreshMinutes(Provider.CLAUDE))
    }
}

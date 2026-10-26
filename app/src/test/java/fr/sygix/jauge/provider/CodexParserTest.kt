/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexParserTest {

    @Test
    fun weeklyOnlyInPrimaryIsNotASession() {
        val json = parserJson(
            """{"rate_limit": {"primary_window": {"used_percent": 60, "limit_window_seconds": 604800}}, "credits": {"has_credits": false}}""",
        )
        val snapshot = CodexParser.parse(json, 0L, null)
        assertEquals(listOf(WindowKind.WEEKLY), snapshot.windows.map { it.kind })
        assertNull(snapshot.session)
        assertNull(snapshot.spend)
    }
}

/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.WindowKind
import fr.sygix.jauge.net.Http
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexParserTest {

    @Test
    fun labelsWindowsByDuration() {
        val json = Http.json.parseToJsonElement(
            """
            {"plan_type": "plus",
             "rate_limit": {
               "primary_window": {"used_percent": 21, "limit_window_seconds": 18000, "reset_at": 1779846359},
               "secondary_window": {"used_percent": 4, "limit_window_seconds": 604800, "reset_after_seconds": 3600}},
             "credits": {"has_credits": true, "unlimited": false, "balance": "12.50"}}
            """.trimIndent(),
        ).jsonObject
        val snapshot = CodexParser.parse(json, 1_000L, "me@x.y")
        assertEquals("Plus", snapshot.plan)
        assertEquals(21.0, snapshot.session!!.usedPercent, 0.001)
        assertEquals(1779846359000L, snapshot.session!!.resetsAtMs)
        assertEquals(1_000L + 3_600_000L, snapshot.weekly!!.resetsAtMs)
        assertEquals(12.5, snapshot.spend!!.balance!!.amount, 0.001)
    }

    @Test
    fun weeklyOnlyInPrimaryIsNotASession() {
        val json = Http.json.parseToJsonElement(
            """{"rate_limit": {"primary_window": {"used_percent": 60, "limit_window_seconds": 604800}}, "credits": {"has_credits": false}}""",
        ).jsonObject
        val snapshot = CodexParser.parse(json, 0L, null)
        assertEquals(listOf(WindowKind.WEEKLY), snapshot.windows.map { it.kind })
        assertNull(snapshot.session)
        assertNull(snapshot.spend)
    }
}

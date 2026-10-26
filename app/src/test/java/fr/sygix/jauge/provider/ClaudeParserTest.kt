/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClaudeParserTest {

    @Test
    fun fallsBackToLegacyFields() {
        val legacy = """
            {"five_hour": {"utilization": 30.5, "resets_at": "2026-10-05T12:00:00Z"},
             "seven_day": {"utilization": 20, "resets_at": "2026-10-08T12:00:00Z"},
             "seven_day_sonnet": {"utilization": 40, "resets_at": "2026-10-09T12:00:00Z"},
             "extra_usage": {"is_enabled": false, "monthly_limit": 5000, "used_credits": 0, "currency": "USD", "decimal_places": 2}}
        """.trimIndent()
        val snapshot = ClaudeParser.parse(parserJson(legacy), 0L, null, null)
        assertEquals(3, snapshot.windows.size)
        assertEquals(30.5, snapshot.session!!.usedPercent, 0.001)
        assertEquals("Sonnet", snapshot.windows[2].model)
        assertEquals(50.0, snapshot.spend!!.limit!!.amount, 0.001)
        assertFalse(snapshot.spend.enabled)
    }

    @Test
    fun buildsPlanFromProfile() {
        val profile = parserJson(
            """{"account": {"email": "x@y.z"}, "organization": {"organization_type": "claude_team", "rate_limit_tier": "default_claude_max_5x"}}""",
        )
        assertEquals("Team · Max 5x", ClaudeParser.plan(profile))
        assertEquals("x@y.z", ClaudeParser.account(profile))
        val max = parserJson(
            """{"organization": {"organization_type": "claude_max", "rate_limit_tier": "default_claude_max_20x"}}""",
        )
        assertEquals("Max · Max 20x", ClaudeParser.plan(max))
    }
}

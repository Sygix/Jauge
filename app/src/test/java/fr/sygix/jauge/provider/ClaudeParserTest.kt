/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.WindowKind
import fr.sygix.jauge.net.Http
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeParserTest {

    private val usage = """
        {
          "five_hour": {"utilization": 7.0, "resets_at": "2026-10-10T16:29:59.777506+00:00"},
          "seven_day": {"utilization": 47.0, "resets_at": "2026-10-11T01:59:59.777525+00:00"},
          "seven_day_opus": null,
          "extra_usage": {"is_enabled": true, "monthly_limit": 70000, "used_credits": 1234.0, "utilization": null, "currency": "EUR", "decimal_places": 2},
          "limits": [
            {"kind": "session", "group": "session", "percent": 7, "resets_at": "2026-10-10T16:29:59.777506+00:00", "scope": null},
            {"kind": "weekly_all", "group": "weekly", "percent": 47, "resets_at": "2026-10-11T01:59:59.777525+00:00", "scope": null},
            {"kind": "weekly_scoped", "group": "weekly", "percent": 14, "resets_at": "2026-10-11T01:59:59.777722+00:00",
             "scope": {"model": {"id": null, "display_name": "Opus"}, "surface": null}}
          ],
          "spend": {
            "used": {"amount_minor": 1234, "currency": "EUR", "exponent": 2},
            "limit": {"amount_minor": 70000, "currency": "EUR", "exponent": 2},
            "percent": 2, "enabled": true, "balance": null
          }
        }
    """.trimIndent()

    @Test
    fun parsesStructuredLimits() {
        val snapshot = ClaudeParser.parse(Http.json.parseToJsonElement(usage).jsonObject, 0L, "Max 5x", "a@b.c")
        assertEquals(listOf(WindowKind.SESSION, WindowKind.WEEKLY, WindowKind.WEEKLY_MODEL), snapshot.windows.map { it.kind })
        assertEquals(7.0, snapshot.session!!.usedPercent, 0.001)
        assertEquals(1791649799777L, snapshot.session!!.resetsAtMs)
        assertEquals("Semaine · Opus", snapshot.windows[2].label)
        assertEquals("Max 5x", snapshot.plan)
    }

    @Test
    fun parsesSpendInMajorUnits() {
        val spend = ClaudeParser.parse(Http.json.parseToJsonElement(usage).jsonObject, 0L, null, null).spend
        assertNotNull(spend)
        assertEquals(12.34, spend!!.used!!.amount, 0.001)
        assertEquals(700.0, spend.limit!!.amount, 0.001)
        assertEquals("EUR", spend.limit!!.currency)
        assertTrue(spend.enabled)
    }

    @Test
    fun fallsBackToLegacyFields() {
        val legacy = """
            {"five_hour": {"utilization": 30.5, "resets_at": "2026-10-05T12:00:00Z"},
             "seven_day": {"utilization": 20, "resets_at": "2026-10-08T12:00:00Z"},
             "seven_day_sonnet": {"utilization": 40, "resets_at": "2026-10-09T12:00:00Z"},
             "extra_usage": {"is_enabled": false, "monthly_limit": 5000, "used_credits": 0, "currency": "USD", "decimal_places": 2}}
        """.trimIndent()
        val snapshot = ClaudeParser.parse(Http.json.parseToJsonElement(legacy).jsonObject, 0L, null, null)
        assertEquals(3, snapshot.windows.size)
        assertEquals(30.5, snapshot.session!!.usedPercent, 0.001)
        assertEquals("Semaine · Sonnet", snapshot.windows[2].label)
        assertEquals(50.0, snapshot.spend!!.limit!!.amount, 0.001)
        assertFalse(snapshot.spend!!.enabled)
    }

    @Test
    fun buildsPlanFromProfile() {
        val profile = Http.json.parseToJsonElement(
            """{"account": {"email": "x@y.z"}, "organization": {"organization_type": "claude_team", "rate_limit_tier": "default_claude_max_5x"}}""",
        ).jsonObject
        assertEquals("Team · Max 5x", ClaudeParser.plan(profile))
        assertEquals("x@y.z", ClaudeParser.account(profile))
        val max = Http.json.parseToJsonElement(
            """{"organization": {"organization_type": "claude_max", "rate_limit_tier": "default_claude_max_20x"}}""",
        ).jsonObject
        assertEquals("Max · Max 20x", ClaudeParser.plan(max))
    }
}

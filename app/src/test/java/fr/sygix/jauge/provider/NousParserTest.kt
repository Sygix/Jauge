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

class NousParserTest {

    @Test
    fun computesMonthlyUsageAndSpend() {
        val json = Http.json.parseToJsonElement(
            """
            {"user": {"email": "n@r.ai"},
             "subscription": {"plan": "pro", "monthly_credits": 20.0, "credits_remaining": 5.0, "current_period_end": "2026-11-01T00:00:00Z"},
             "paid_service_access": {"allowed": true, "purchased_credits_remaining": 3.25, "member_spend_usd": 15.0, "member_spend_cap_usd": 50.0}}
            """.trimIndent(),
        ).jsonObject
        val snapshot = NousParser.parse(json, 0L)
        val month = snapshot.windows.single()
        assertEquals(WindowKind.MONTHLY, month.kind)
        assertEquals(75.0, month.usedPercent, 0.001)
        assertEquals("Pro", snapshot.plan)
        assertEquals("n@r.ai", snapshot.account)
        assertEquals(30.0, snapshot.spend!!.percent!!, 0.001)
        assertEquals(3.25, snapshot.spend!!.balance!!.amount, 0.001)
        assertNull(snapshot.error)
    }

    @Test
    fun flagsDepletedAccess() {
        val json = Http.json.parseToJsonElement("""{"paid_service_access": {"allowed": false}}""").jsonObject
        assertEquals("Crédits épuisés", NousParser.parse(json, 0L).error)
    }
}

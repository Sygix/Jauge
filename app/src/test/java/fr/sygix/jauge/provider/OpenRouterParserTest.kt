/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterParserTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()

    @Test
    fun unlimitedKeyDoesNotBecomeAnEmptyGauge() {
        val snapshot = OpenRouterParser.parse(parserJson("""{"data":{"usage":12.5,"limit":null,"limit_remaining":null}}"""), parserJson("""{"data":{"total_credits":40,"total_usage":15}}"""), now)
        assertTrue(snapshot.windows.isEmpty())
        assertEquals(12.5, snapshot.spend!!.used!!.amount, 0.001)
        assertEquals(25.0, snapshot.spend.balance!!.amount, 0.001)
        assertNull(snapshot.spend.percent)
    }

    @Test
    fun usesTheEffectiveBudgetWithoutInventingAReset() {
        val snapshot = OpenRouterParser.parse(parserJson("""{"data":{"usage":8,"effective_budget":{"limit_usd":10,"spend_usd":8,"remaining_usd":2,"resets_at":null},"free_model_daily_requests":{"used":0,"limit":0,"remaining":0}}}"""), null, now)
        assertEquals(1, snapshot.windows.size)
        assertEquals(80.0, snapshot.primary!!.usedPercent, 0.001)
        assertNull(snapshot.primary!!.resetsAtMs)
    }

    @Test(expected = IOException::class)
    fun rejectsMissingUsage() {
        OpenRouterParser.parse(parserJson("""{"data":{"label":"unknown"}}"""), null, now)
    }
}

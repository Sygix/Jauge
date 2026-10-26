/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import java.io.IOException
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class OpenCodeParserTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()

    @Test
    fun openCodeApiUsesTheReportedGoProduct() {
        val usage = parserJson("""{"usage":{"rolling":{"percent":25}}}""")
        mapOf("go" to "Go", "go-plus" to "Go Plus").forEach { (product, plan) ->
            val snapshot = OpenCodeParser.parseApi(JsonObject(usage + ("product" to JsonPrimitive(product))), now)
            assertEquals(plan, snapshot.plan)
            assertEquals(25.0, snapshot.session!!.usedPercent, 0.001)
        }
    }

    @Test
    fun openCodeConsoleUsesTheCurrentProductRatherThanTheScheduledRenewal() {
        val access = parserJson("""{"meters":{"fiveHour":{"usedMicroCents":"25","limitMicroCents":"100"}}}""")
        mapOf("go" to "Go", "go-plus" to "Go Plus").forEach { (product, plan) ->
            val renewal = if (product == "go") "go-plus" else "go"
            val go = JsonObject(mapOf("product" to JsonPrimitive(product), "renewalProduct" to JsonPrimitive(renewal), "access" to access))
            assertEquals(plan, OpenCodeParser.parseConsole(go, null, null, now).plan)
        }
    }

    @Test
    fun openCodeDoesNotGuessTheProductFromQuotaOrAnOptionalPrepaidBalance() {
        val access = parserJson("""{"meters":{"fiveHour":{"usedMicroCents":"1","limitMicroCents":"4800000000"}}}""")
        val billing = parserJson("""{"mode":"pay-as-you-go","balanceMicroCents":"100000000"}""")
        val missing = JsonObject(mapOf("access" to access))
        val unknown = JsonObject(missing + ("product" to JsonPrimitive("future-product")))
        listOf(missing, unknown).forEach { go ->
            val snapshot = OpenCodeParser.parseConsole(go, billing, null, now)
            assertNull(snapshot.plan)
            assertNotNull(snapshot.session)
            assertNotNull(snapshot.spend!!.balance)
        }
    }

    @Test
    fun openCodeExpiredGoDoesNotOverrideTheActiveZenBillingMode() {
        val go = parserJson("""{"product":"go-plus","access":null}""")
        val billing = parserJson("""{"mode":"pay-as-you-go","balanceMicroCents":"100000000"}""")
        val snapshot = OpenCodeParser.parseConsole(go, billing, null, now)
        assertEquals("Zen", snapshot.plan)
        assertTrue(snapshot.windows.isEmpty())
        assertEquals(1.0, snapshot.spend!!.balance!!.amount, 0.001)
    }

    @Test
    fun openCodePaygReportsRollingSpendWithoutAFakeQuota() {
        val snapshot = OpenCodeParser.parseConsole(null, parserJson("""{"mode":"pay-as-you-go","balanceMicroCents":"-100000000"}"""), parserJson("""{"totalCostMicroCents":"4200000000"}"""), now)
        assertTrue(snapshot.windows.isEmpty())
        assertEquals(42.0, snapshot.spend!!.used!!.amount, 0.001)
        assertEquals(-1.0, snapshot.spend.balance!!.amount, 0.001)
        assertNull(snapshot.spend.percent)
        assertTrue(snapshot.quotaUnavailable)
        assertEquals("Zen", snapshot.plan)
    }

    @Test(expected = IOException::class)
    fun openCodeMalformedQuotaIsNotAFreeAccount() {
        OpenCodeParser.parseConsole(parserJson("""{"access":{"meters":{}}}"""), null, null, now)
    }
}

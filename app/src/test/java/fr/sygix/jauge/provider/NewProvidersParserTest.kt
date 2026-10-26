/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.WindowKind
import fr.sygix.jauge.net.Http
import java.io.IOException
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class NewProvidersParserTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()
    private fun json(value: String) = Http.json.parseToJsonElement(value).jsonObject

    @Test
    fun openRouterTracksFreeRequestsAndTheExistingKeyBudget() {
        val snapshot = OpenRouterParser.parse(json("""{"data":{"label":"Tracker","usage":35,"usage_monthly":5,"limit":20,"limit_remaining":15,"limit_reset":"monthly","free_model_daily_requests":{"used":12,"limit":50,"remaining":38}}}"""), null, now)
        val free = snapshot.windows.first { it.kind == WindowKind.FREE_DAILY }
        val budget = snapshot.windows.first { it.kind == WindowKind.KEY_BUDGET }
        assertEquals(24.0, free.usedPercent, 0.001)
        assertEquals(12L, free.usedRequests)
        assertEquals(50L, free.requestLimit)
        assertEquals(Instant.parse("2026-10-22T00:00:00Z").toEpochMilli(), free.resetsAtMs)
        assertEquals(25.0, budget.usedPercent, 0.001)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), budget.resetsAtMs)
        assertEquals(35.0, snapshot.spend!!.used!!.amount, 0.001)
    }

    @Test
    fun openRouterUnlimitedKeyDoesNotBecomeAnEmptyGauge() {
        val snapshot = OpenRouterParser.parse(json("""{"data":{"usage":12.5,"limit":null,"limit_remaining":null}}"""), json("""{"data":{"total_credits":40,"total_usage":15}}"""), now)
        assertTrue(snapshot.windows.isEmpty())
        assertEquals(12.5, snapshot.spend!!.used!!.amount, 0.001)
        assertEquals(25.0, snapshot.spend.balance!!.amount, 0.001)
        assertNull(snapshot.spend.percent)
    }

    @Test
    fun openRouterUsesTheEffectiveBudgetWithoutInventingAReset() {
        val snapshot = OpenRouterParser.parse(json("""{"data":{"usage":8,"effective_budget":{"limit_usd":10,"spend_usd":8,"remaining_usd":2,"resets_at":null},"free_model_daily_requests":{"used":0,"limit":0,"remaining":0}}}"""), null, now)
        assertEquals(1, snapshot.windows.size)
        assertEquals(80.0, snapshot.primary!!.usedPercent, 0.001)
        assertNull(snapshot.primary!!.resetsAtMs)
    }

    @Test(expected = IOException::class)
    fun openRouterRejectsMissingUsage() {
        OpenRouterParser.parse(json("""{"data":{"label":"unknown"}}"""), null, now)
    }

    @Test
    fun openCodeApiPercentagesAreNotFractionsAndMissingResetsStayUnknown() {
        val snapshot = OpenCodeParser.parseApi(json("""{"usage":{"rolling":{"percent":0.5},"weekly":{"percent":28,"resetAt":"2026-10-25T12:00:00Z"},"monthly":{"percent":61,"resetAt":"2026-11-01T00:00:00Z"}}}"""), now)
        assertEquals(0.5, snapshot.session!!.usedPercent, 0.001)
        assertNull(snapshot.session!!.resetsAtMs)
        assertEquals(28.0, snapshot.weekly!!.usedPercent, 0.001)
        assertEquals(61.0, snapshot.monthly!!.usedPercent, 0.001)
        assertNull(snapshot.plan)
    }

    @Test
    fun openCodeApiUsesTheReportedGoProduct() {
        val usage = json("""{"usage":{"rolling":{"percent":25}}}""")
        mapOf("go" to "Go", "go-plus" to "Go Plus").forEach { (product, plan) ->
            val snapshot = OpenCodeParser.parseApi(JsonObject(usage + ("product" to JsonPrimitive(product))), now)
            assertEquals(plan, snapshot.plan)
            assertEquals(25.0, snapshot.session!!.usedPercent, 0.001)
        }
    }

    @Test
    fun openCodeConsoleUsesTheCurrentProductRatherThanTheScheduledRenewal() {
        val access = json("""{"meters":{"fiveHour":{"usedMicroCents":"25","limitMicroCents":"100"}}}""")
        mapOf("go" to "Go", "go-plus" to "Go Plus").forEach { (product, plan) ->
            val renewal = if (product == "go") "go-plus" else "go"
            val go = JsonObject(mapOf("product" to JsonPrimitive(product), "renewalProduct" to JsonPrimitive(renewal), "access" to access))
            assertEquals(plan, OpenCodeParser.parseConsole(go, null, null, now).plan)
        }
    }

    @Test
    fun openCodeDoesNotGuessTheProductFromQuotaOrAnOptionalPrepaidBalance() {
        val access = json("""{"meters":{"fiveHour":{"usedMicroCents":"1","limitMicroCents":"4800000000"}}}""")
        val billing = json("""{"mode":"pay-as-you-go","balanceMicroCents":"100000000"}""")
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
        val go = json("""{"product":"go-plus","access":null}""")
        val billing = json("""{"mode":"pay-as-you-go","balanceMicroCents":"100000000"}""")
        val snapshot = OpenCodeParser.parseConsole(go, billing, null, now)
        assertEquals("Zen", snapshot.plan)
        assertTrue(snapshot.windows.isEmpty())
        assertEquals(1.0, snapshot.spend!!.balance!!.amount, 0.001)
    }

    @Test
    fun openCodeConsoleConvertsMicroCentsAndUsesTheBillingPeriodEnd() {
        val go = json("""{"access":{"endsAt":"2026-11-01T00:00:00Z","meters":{"fiveHour":{"usedMicroCents":"250000000","limitMicroCents":"1000000000"},"week":{"usedMicroCents":"1000000000","limitMicroCents":"2000000000","resetsAt":"2026-10-25T12:00:00Z"},"month":{"usedMicroCents":"3000000000","limitMicroCents":"4000000000"}}}}""")
        val billing = json("""{"mode":"pay-as-you-go","balanceMicroCents":"1250000000","availableMicroCents":"99900000000"}""")
        val snapshot = OpenCodeParser.parseConsole(go, billing, null, now)
        assertEquals(25.0, snapshot.session!!.usedPercent, 0.001)
        assertEquals(50.0, snapshot.weekly!!.usedPercent, 0.001)
        assertEquals(75.0, snapshot.monthly!!.usedPercent, 0.001)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), snapshot.monthly!!.resetsAtMs)
        assertEquals(12.5, snapshot.spend!!.balance!!.amount, 0.001)
    }

    @Test
    fun openCodePaygReportsRollingSpendWithoutAFakeQuota() {
        val snapshot = OpenCodeParser.parseConsole(null, json("""{"mode":"pay-as-you-go","balanceMicroCents":"-100000000"}"""), json("""{"totalCostMicroCents":"4200000000"}"""), now)
        assertTrue(snapshot.windows.isEmpty())
        assertEquals(42.0, snapshot.spend!!.used!!.amount, 0.001)
        assertEquals(-1.0, snapshot.spend.balance!!.amount, 0.001)
        assertNull(snapshot.spend.percent)
        assertTrue(snapshot.quotaUnavailable)
        assertEquals("Zen", snapshot.plan)
    }

    @Test(expected = IOException::class)
    fun openCodeMalformedQuotaIsNotAFreeAccount() {
        OpenCodeParser.parseConsole(json("""{"access":{"meters":{}}}"""), null, null, now)
    }
}

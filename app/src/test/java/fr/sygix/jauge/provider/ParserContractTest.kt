/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.Money
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.Spend
import fr.sygix.jauge.model.SpendKind
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.model.WindowKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParserContractTest(private val case: Case) {
    data class Case(
        val name: String,
        val parse: () -> UsageSnapshot,
        val expected: UsageSnapshot,
    ) {
        override fun toString() = name
    }

    private val snapshot = case.parse()

    @Test
    fun parsesMetadata() {
        assertEquals(case.expected.provider, snapshot.provider)
        assertEquals(case.expected.fetchedAtMs, snapshot.fetchedAtMs)
        assertEquals(case.expected.plan, snapshot.plan)
        assertEquals(case.expected.account, snapshot.account)
    }

    @Test
    fun parsesWindows() {
        assertEquals(case.expected.windows.size, snapshot.windows.size)
        case.expected.windows.zip(snapshot.windows).forEach { (expected, actual) ->
            assertEquals(expected.kind, actual.kind)
            assertEquals(expected.usedPercent, actual.usedPercent, 0.001)
            assertEquals(expected.resetsAtMs, actual.resetsAtMs)
            assertEquals(expected.model, actual.model)
            assertEquals(expected.usedRequests, actual.usedRequests)
            assertEquals(expected.requestLimit, actual.requestLimit)
        }
    }

    @Test
    fun parsesSpendInMajorUnits() {
        val expected = case.expected.spend
        val actual = snapshot.spend
        assertEquals(expected?.kind, actual?.kind)
        assertMoney(expected?.used, actual?.used)
        assertMoney(expected?.limit, actual?.limit)
        assertMoney(expected?.balance, actual?.balance)
        if (expected?.percent == null) {
            assertEquals(null, actual?.percent)
        } else {
            assertEquals(expected.percent, requireNotNull(actual?.percent), 0.001)
        }
        assertEquals(expected?.enabled, actual?.enabled)
    }

    @Test
    fun parsesAvailability() {
        assertEquals(case.expected.error, snapshot.error)
        assertEquals(case.expected.needsLogin, snapshot.needsLogin)
        assertEquals(case.expected.quotaUnavailable, snapshot.quotaUnavailable)
    }

    private fun assertMoney(expected: Money?, actual: Money?) {
        assertEquals(expected?.currency, actual?.currency)
        if (expected == null) {
            assertEquals(null, actual)
        } else {
            assertEquals(expected.amount, requireNotNull(actual).amount, 0.001)
        }
    }

    companion object {
        private val now = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()
        private fun epoch(value: String) = Instant.parse(value).toEpochMilli()

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            Case(
                "Claude structured usage",
                { ClaudeParser.parse(parserJson(claudeUsage), 0L, "Max 5x", "a@b.c") },
                UsageSnapshot(
                    Provider.CLAUDE, 0L, plan = "Max 5x", account = "a@b.c",
                    windows = listOf(
                        UsageWindow(WindowKind.SESSION, 7.0, 1791649799777L),
                        UsageWindow(WindowKind.WEEKLY, 47.0, epoch("2026-10-11T01:59:59.777525Z")),
                        UsageWindow(WindowKind.WEEKLY_MODEL, 14.0, epoch("2026-10-11T01:59:59.777722Z"), "Opus"),
                    ),
                    spend = Spend(SpendKind.OVERAGE, Money(12.34, "EUR"), Money(700.0, "EUR"), percent = 2.0),
                ),
            ),
            Case(
                "Codex duration windows and credits",
                {
                    CodexParser.parse(parserJson("""
                        {"plan_type": "plus",
                         "rate_limit": {
                           "primary_window": {"used_percent": 21, "limit_window_seconds": 18000, "reset_at": 1779846359},
                           "secondary_window": {"used_percent": 4, "limit_window_seconds": 604800, "reset_after_seconds": 3600}},
                         "credits": {"has_credits": true, "unlimited": false, "balance": "12.50"}}
                    """), 1_000L, "me@x.y")
                },
                UsageSnapshot(
                    Provider.CODEX, 1_000L, plan = "Plus", account = "me@x.y",
                    windows = listOf(
                        UsageWindow(WindowKind.SESSION, 21.0, 1779846359000L),
                        UsageWindow(WindowKind.WEEKLY, 4.0, 3_601_000L),
                    ),
                    spend = Spend(SpendKind.CREDITS, balance = Money(12.5, "USD")),
                ),
            ),
            Case(
                "Nous monthly usage and top-up",
                {
                    NousParser.parse(parserJson("""
                        {"user": {"email": "n@r.ai"},
                         "subscription": {"plan": "pro", "monthly_credits": 20.0, "credits_remaining": 5.0, "current_period_end": "2026-11-01T00:00:00Z"},
                         "paid_service_access": {"allowed": true, "purchased_credits_remaining": 3.25, "member_spend_usd": 15.0, "member_spend_cap_usd": 50.0}}
                    """), 0L)
                },
                UsageSnapshot(
                    Provider.NOUS, 0L, plan = "Pro", account = "n@r.ai",
                    windows = listOf(UsageWindow(WindowKind.MONTHLY, 75.0, epoch("2026-11-01T00:00:00Z"))),
                    spend = Spend(SpendKind.PERIOD, Money(15.0, "USD"), Money(20.0, "USD"), 75.0, Money(3.25, "USD")),
                ),
            ),
            Case(
                "OpenRouter free requests and key budget",
                { OpenRouterParser.parse(parserJson("""{"data":{"label":"Tracker","usage":35,"usage_monthly":5,"limit":20,"limit_remaining":15,"limit_reset":"monthly","free_model_daily_requests":{"used":12,"limit":50,"remaining":38}}}"""), null, now) },
                UsageSnapshot(
                    Provider.OPENROUTER, now, account = "Tracker",
                    windows = listOf(
                        UsageWindow(WindowKind.KEY_BUDGET, 25.0, epoch("2026-11-01T00:00:00Z")),
                        UsageWindow(WindowKind.FREE_DAILY, 24.0, epoch("2026-10-22T00:00:00Z"), usedRequests = 12L, requestLimit = 50L),
                    ),
                    spend = Spend(SpendKind.KEY_TOTAL, used = Money(35.0, "USD")),
                ),
            ),
            Case(
                "OpenCode API percentages and unknown resets",
                { OpenCodeParser.parseApi(parserJson("""{"usage":{"rolling":{"percent":0.5},"weekly":{"percent":28,"resetAt":"2026-10-25T12:00:00Z"},"monthly":{"percent":61,"resetAt":"2026-11-01T00:00:00Z"}}}"""), now) },
                UsageSnapshot(
                    Provider.OPENCODE, now,
                    windows = listOf(
                        UsageWindow(WindowKind.SESSION, 0.5),
                        UsageWindow(WindowKind.WEEKLY, 28.0, epoch("2026-10-25T12:00:00Z")),
                        UsageWindow(WindowKind.MONTHLY, 61.0, epoch("2026-11-01T00:00:00Z")),
                    ),
                ),
            ),
            Case(
                "OpenCode console micro-cents and billing period",
                {
                    val go = parserJson("""{"access":{"endsAt":"2026-11-01T00:00:00Z","meters":{"fiveHour":{"usedMicroCents":"250000000","limitMicroCents":"1000000000"},"week":{"usedMicroCents":"1000000000","limitMicroCents":"2000000000","resetsAt":"2026-10-25T12:00:00Z"},"month":{"usedMicroCents":"3000000000","limitMicroCents":"4000000000"}}}}""")
                    val billing = parserJson("""{"mode":"pay-as-you-go","balanceMicroCents":"1250000000","availableMicroCents":"99900000000"}""")
                    OpenCodeParser.parseConsole(go, billing, null, now)
                },
                UsageSnapshot(
                    Provider.OPENCODE, now,
                    windows = listOf(
                        UsageWindow(WindowKind.SESSION, 25.0),
                        UsageWindow(WindowKind.WEEKLY, 50.0, epoch("2026-10-25T12:00:00Z")),
                        UsageWindow(WindowKind.MONTHLY, 75.0, epoch("2026-11-01T00:00:00Z")),
                    ),
                    spend = Spend(SpendKind.CREDITS, balance = Money(12.5, "USD")),
                ),
            ),
        ).map { arrayOf<Any>(it) }

        private val claudeUsage = """
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
    }
}

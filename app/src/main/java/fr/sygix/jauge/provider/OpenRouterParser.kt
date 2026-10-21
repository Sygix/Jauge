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
import fr.sygix.jauge.model.UsageError
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.model.WindowKind
import fr.sygix.jauge.net.num
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlinx.serialization.json.JsonObject

object OpenRouterParser {
    fun parse(key: JsonObject, credits: JsonObject?, nowMs: Long): UsageSnapshot {
        val data = key.obj("data") ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
        val usage = data.nonNegative("usage") ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
        val budget = data.obj("effective_budget")
        val windows = buildList {
            val limit = budget?.nonNegative("limit_usd") ?: data.nonNegative("limit")
            val used = if (budget != null) budget.nonNegative("spend_usd") else {
                data.nonNegative("limit_remaining")?.let { remaining -> limit?.minus(remaining)?.coerceAtLeast(0.0) }
                    ?: when (data.str("limit_reset")) {
                        "daily" -> data.nonNegative("usage_daily")
                        "weekly" -> data.nonNegative("usage_weekly")
                        "monthly" -> data.nonNegative("usage_monthly")
                        null -> usage
                        else -> null
                    }
            }
            if (limit != null && limit > 0 && used != null) {
                val reset = if (budget != null) parseInstantMs(budget.str("resets_at")) else resetAt(data.str("limit_reset"), nowMs)
                add(UsageWindow(WindowKind.KEY_BUDGET, used / limit * 100, reset))
            }
            data.obj("free_model_daily_requests")?.let { free ->
                val count = free.nonNegative("used")
                val ceiling = free.nonNegative("limit")
                if (count != null && ceiling != null && ceiling > 0) {
                    add(UsageWindow(WindowKind.FREE_DAILY, count / ceiling * 100, resetAt("daily", nowMs), usedRequests = count.toLong(), requestLimit = ceiling.toLong()))
                }
            }
        }
        val balance = credits?.obj("data")?.let { account ->
            val total = account.nonNegative("total_credits")
            val spent = account.nonNegative("total_usage")
            if (total != null && spent != null) Money(total - spent, "USD") else null
        }
        return UsageSnapshot(
            provider = Provider.OPENROUTER,
            fetchedAtMs = nowMs,
            account = data.str("label"),
            windows = windows,
            spend = Spend(SpendKind.KEY_TOTAL, used = Money(usage, "USD"), balance = balance),
        )
    }

    private fun resetAt(interval: String?, nowMs: Long): Long? {
        val today = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        val date = when (interval) {
            "daily" -> today.plusDays(1)
            "weekly" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            "monthly" -> today.withDayOfMonth(1).plusMonths(1)
            else -> return null
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private fun JsonObject.nonNegative(key: String): Double? = num(key)?.takeIf { it.isFinite() && it >= 0 }
}

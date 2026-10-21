/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.Money
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.Spend
import fr.sygix.jauge.model.SpendKind
import fr.sygix.jauge.model.UsageError
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.model.WindowKind
import fr.sygix.jauge.net.num
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import java.io.IOException
import kotlinx.serialization.json.JsonObject

object OpenCodeParser {
    fun parseApi(usage: JsonObject, nowMs: Long): UsageSnapshot {
        val meters = usage.obj("usage") ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
        val rolling = meters.obj("rolling") ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
        val windows = buildList {
            add(apiWindow(rolling, WindowKind.SESSION, nowMs))
            meters.obj("weekly")?.let { add(apiWindow(it, WindowKind.WEEKLY, nowMs)) }
            meters.obj("monthly")?.let { add(apiWindow(it, WindowKind.MONTHLY, nowMs)) }
        }
        return UsageSnapshot(Provider.OPENCODE, nowMs, plan = goPlan(usage), windows = windows)
    }

    fun parseConsole(go: JsonObject?, billing: JsonObject?, summary: JsonObject?, nowMs: Long): UsageSnapshot {
        val access = go?.obj("access")
        if (go != null && go["access"] != kotlinx.serialization.json.JsonNull && access == null) throw IOException(UsageError.UNSUPPORTED_USAGE)
        val windows = if (access != null) {
            val meters = access.obj("meters") ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
            val rolling = meters.obj("fiveHour") ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
            buildList {
                add(consoleWindow(rolling, WindowKind.SESSION))
                meters.obj("week")?.let { add(consoleWindow(it, WindowKind.WEEKLY)) }
                meters.obj("month")?.let { add(consoleWindow(it, WindowKind.MONTHLY, parseInstantMs(access.str("endsAt")))) }
            }
        } else emptyList()
        val prepaid = billing?.str("mode") == "pay-as-you-go"
        val balance = if (prepaid) billing?.num("balanceMicroCents")?.takeIf { it.isFinite() }?.let { Money(it / 100_000_000, "USD") } else null
        val used = summary?.num("totalCostMicroCents")?.takeIf { it.isFinite() && it >= 0 }?.let { Money(it / 100_000_000, "USD") }
        if (summary != null && used == null) throw IOException(UsageError.UNSUPPORTED_USAGE)
        val spend = when {
            used != null && prepaid -> Spend(SpendKind.LAST_30_DAYS, used = used, balance = balance)
            balance != null -> Spend(SpendKind.CREDITS, balance = balance)
            else -> null
        }
        val plan = if (access != null) goPlan(go) else if (prepaid) "Zen" else null
        return UsageSnapshot(Provider.OPENCODE, nowMs, plan = plan, windows = windows, spend = spend, quotaUnavailable = access == null)
    }

    private fun goPlan(data: JsonObject?): String? = when (data?.str("product")) {
        "go" -> "Go"
        "go-plus" -> "Go Plus"
        else -> null
    }

    private fun apiWindow(data: JsonObject, kind: WindowKind, nowMs: Long): UsageWindow {
        val percent = (data.num("percent") ?: data.num("usagePercent"))?.takeIf { it.isFinite() && it >= 0 }
            ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
        val reset = listOf("resetAt", "resetsAt", "reset_at").firstNotNullOfOrNull { parseInstantMs(data.str(it)) }
            ?: (data.num("resetInSec") ?: data.num("reset_in_seconds"))?.takeIf { it.isFinite() && it >= 0 && it <= 366 * 86400.0 }?.let { nowMs + (it * 1000).toLong() }
        return UsageWindow(kind, percent, reset)
    }

    private fun consoleWindow(data: JsonObject, kind: WindowKind, fallbackReset: Long? = null): UsageWindow {
        val used = data.num("usedMicroCents")?.takeIf { it.isFinite() && it >= 0 }
        val limit = data.num("limitMicroCents")?.takeIf { it.isFinite() && it > 0 }
        if (used == null || limit == null) throw IOException(UsageError.UNSUPPORTED_USAGE)
        return UsageWindow(kind, used / limit * 100, parseInstantMs(data.str("resetsAt")) ?: fallbackReset)
    }
}

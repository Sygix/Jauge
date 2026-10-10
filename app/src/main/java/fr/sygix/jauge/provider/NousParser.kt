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
import fr.sygix.jauge.net.bool
import fr.sygix.jauge.net.num
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import kotlinx.serialization.json.JsonObject

object NousParser {

    fun parse(json: JsonObject, nowMs: Long): UsageSnapshot {
        val subscription = json.obj("subscription")
        val access = json.obj("paid_service_access")
        return UsageSnapshot(
            provider = Provider.NOUS,
            fetchedAtMs = nowMs,
            plan = subscription?.str("plan")?.let(::prettySlug),
            account = json.obj("user")?.str("email"),
            windows = listOfNotNull(monthly(subscription)),
            spend = spend(subscription, access),
            error = if (access?.bool("allowed") == false) UsageError.DEPLETED else null,
        )
    }

    private fun monthly(subscription: JsonObject?): UsageWindow? {
        subscription ?: return null
        val cap = subscription.num("monthly_credits")?.takeIf { it > 0 } ?: return null
        val remaining = subscription.num("credits_remaining") ?: return null
        val used = ((cap - remaining) / cap * 100).coerceIn(0.0, 100.0)
        return UsageWindow(WindowKind.MONTHLY, used, parseInstantMs(subscription.str("current_period_end")))
    }

    private fun spend(subscription: JsonObject?, access: JsonObject?): Spend? {
        val topUp = access?.num("purchased_credits_remaining")?.let { Money(it, "USD") }
        val cap = subscription?.num("monthly_credits")?.takeIf { it > 0 }
        val remaining = subscription?.num("credits_remaining")
        if (cap != null && remaining != null) {
            val used = (cap - remaining).coerceAtLeast(0.0)
            return Spend(
                kind = SpendKind.PERIOD,
                used = Money(used, "USD"),
                limit = Money(cap, "USD"),
                percent = (used / cap * 100).coerceIn(0.0, 100.0),
                balance = topUp,
            )
        }
        val spent = access?.num("member_spend_usd")
        if (spent == null && topUp == null) return null
        val spendCap = access?.num("member_spend_cap_usd")
        return Spend(
            kind = if (spent != null) SpendKind.ROLLING_WEEK else SpendKind.TOP_UP,
            used = spent?.let { Money(it, "USD") },
            limit = spendCap?.let { Money(it, "USD") },
            percent = if (spent != null && spendCap != null && spendCap > 0) spent / spendCap * 100 else null,
            balance = topUp,
        )
    }
}

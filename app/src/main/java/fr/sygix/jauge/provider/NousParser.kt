/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.Money
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.Spend
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
            spend = spend(access),
            error = if (access?.bool("allowed") == false) "Crédits épuisés" else null,
        )
    }

    private fun monthly(subscription: JsonObject?): UsageWindow? {
        subscription ?: return null
        val cap = subscription.num("monthly_credits")?.takeIf { it > 0 } ?: return null
        val remaining = subscription.num("credits_remaining") ?: return null
        val used = ((cap - remaining) / cap * 100).coerceIn(0.0, 100.0)
        return UsageWindow(WindowKind.MONTHLY, Labels.MONTHLY, used, parseInstantMs(subscription.str("current_period_end")))
    }

    private fun spend(access: JsonObject?): Spend? {
        access ?: return null
        val spent = access.num("member_spend_usd")
        val cap = access.num("member_spend_cap_usd")
        val topUp = access.num("purchased_credits_remaining")
        if (spent == null && cap == null && topUp == null) return null
        return Spend(
            label = if (spent != null) "Dépenses" else "Crédits top-up",
            used = spent?.let { Money(it, "USD") },
            limit = cap?.let { Money(it, "USD") },
            percent = if (spent != null && cap != null && cap > 0) spent / cap * 100 else null,
            balance = topUp?.let { Money(it, "USD") },
        )
    }
}

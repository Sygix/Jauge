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
import fr.sygix.jauge.net.asObjectList
import fr.sygix.jauge.net.bool
import fr.sygix.jauge.net.long
import fr.sygix.jauge.net.num
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import kotlinx.serialization.json.JsonObject

object ClaudeParser {

    fun parse(json: JsonObject, nowMs: Long, plan: String?, account: String?): UsageSnapshot = UsageSnapshot(
        provider = Provider.CLAUDE,
        fetchedAtMs = nowMs,
        plan = plan,
        account = account,
        windows = limits(json).ifEmpty { legacyWindows(json) },
        spend = spend(json),
    )

    fun plan(profile: JsonObject): String? {
        val org = profile.obj("organization") ?: return null
        val base = when (org.str("organization_type")) {
            "claude_max" -> "Max"
            "claude_pro" -> "Pro"
            "claude_team" -> "Team"
            "claude_enterprise" -> "Enterprise"
            else -> prettySlug(org.str("organization_type")?.removePrefix("claude_"))
        }
        val tier = org.str("rate_limit_tier").orEmpty()
        val multiplier = Regex("""max_(\d+)x""").find(tier)?.groupValues?.get(1)?.let { "Max ${it}x" }
        return listOfNotNull(base, multiplier?.takeIf { it != base }).joinToString(" · ").ifEmpty { null }
    }

    fun account(profile: JsonObject): String? = profile.obj("account")?.str("email")

    private fun limits(json: JsonObject): List<UsageWindow> = json["limits"].asObjectList().mapNotNull { limit ->
        val percent = limit.num("percent") ?: return@mapNotNull null
        val resets = parseInstantMs(limit.str("resets_at"))
        val model = limit.obj("scope")?.obj("model")?.str("display_name")
        when {
            limit.str("kind") == "session" || limit.str("group") == "session" -> UsageWindow(WindowKind.SESSION, percent, resets)
            limit.str("kind") == "weekly_all" -> UsageWindow(WindowKind.WEEKLY, percent, resets)
            else -> UsageWindow(WindowKind.WEEKLY_MODEL, percent, resets, model ?: prettySlug(limit.str("kind")))
        }
    }.sortedBy { it.kind.ordinal }

    private fun legacyWindows(json: JsonObject): List<UsageWindow> = listOf(
        Triple("five_hour", WindowKind.SESSION, null),
        Triple("seven_day", WindowKind.WEEKLY, null),
        Triple("seven_day_opus", WindowKind.WEEKLY_MODEL, "Opus"),
        Triple("seven_day_sonnet", WindowKind.WEEKLY_MODEL, "Sonnet"),
    ).mapNotNull { (key, kind, model) ->
        val window = json.obj(key) ?: return@mapNotNull null
        val used = window.num("utilization") ?: return@mapNotNull null
        UsageWindow(kind, used, parseInstantMs(window.str("resets_at")), model)
    }

    private fun spend(json: JsonObject): Spend? = modernSpend(json) ?: legacySpend(json)

    private fun modernSpend(json: JsonObject): Spend? {
        val spend = json.obj("spend") ?: return null
        val used = money(spend.obj("used"))
        val limit = money(spend.obj("limit"))
        if (used == null && limit == null) return null
        return Spend(
            kind = SpendKind.OVERAGE,
            used = used,
            limit = limit,
            percent = spend.num("percent") ?: percentOf(used, limit),
            balance = money(spend.obj("balance")),
            enabled = spend.bool("enabled") ?: true,
        )
    }

    private fun legacySpend(json: JsonObject): Spend? {
        val extra = json.obj("extra_usage") ?: return null
        val decimals = extra.long("decimal_places")?.toInt() ?: 2
        val currency = extra.str("currency") ?: "USD"
        val used = extra.num("used_credits")?.let { Money(minorToMajor(it, decimals), currency) }
        val limit = extra.num("monthly_limit")?.let { Money(minorToMajor(it, decimals), currency) }
        if (used == null && limit == null) return null
        return Spend(
            kind = SpendKind.OVERAGE,
            used = used,
            limit = limit,
            percent = extra.num("utilization") ?: percentOf(used, limit),
            enabled = extra.bool("is_enabled") ?: false,
        )
    }

    private fun money(obj: JsonObject?): Money? {
        obj ?: return null
        val minor = obj.num("amount_minor") ?: return null
        val exponent = obj.long("exponent")?.toInt() ?: 2
        return Money(minorToMajor(minor, exponent), obj.str("currency") ?: "USD")
    }

    private fun percentOf(used: Money?, limit: Money?): Double? =
        if (used != null && limit != null && limit.amount > 0) used.amount / limit.amount * 100 else null
}

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
import fr.sygix.jauge.net.long
import fr.sygix.jauge.net.num
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import kotlinx.serialization.json.JsonObject

object CodexParser {

    private const val FIVE_HOURS = 18_000L
    private const val ONE_WEEK = 604_800L

    fun parse(json: JsonObject, nowMs: Long, account: String?): UsageSnapshot {
        val rateLimit = json.obj("rate_limit")
        val windows = listOf("primary_window" to WindowKind.SESSION, "secondary_window" to WindowKind.WEEKLY)
            .mapNotNull { (key, fallback) -> rateLimit?.obj(key)?.let { window(it, fallback, nowMs) } }
            .sortedBy { it.kind.ordinal }
        return UsageSnapshot(
            provider = Provider.CODEX,
            fetchedAtMs = nowMs,
            plan = prettySlug(json.str("plan_type")),
            account = account,
            windows = windows,
            spend = credits(json.obj("credits")),
        )
    }

    private fun window(window: JsonObject, fallback: WindowKind, nowMs: Long): UsageWindow? {
        val used = window.num("used_percent") ?: return null
        val kind = when (window.long("limit_window_seconds")) {
            FIVE_HOURS -> WindowKind.SESSION
            ONE_WEEK -> WindowKind.WEEKLY
            else -> fallback
        }
        val resets = window.long("reset_at")?.times(1000)
            ?: window.long("reset_after_seconds")?.let { nowMs + it * 1000 }
        val label = if (kind == WindowKind.SESSION) Labels.SESSION else Labels.WEEKLY
        return UsageWindow(kind, label, used, resets)
    }

    private fun credits(credits: JsonObject?): Spend? {
        credits ?: return null
        if (credits.bool("has_credits") != true) return null
        if (credits.bool("unlimited") == true) return Spend(label = "Crédits illimités")
        val balance = credits.num("balance") ?: return null
        return Spend(label = "Crédits", balance = Money(balance, "USD"))
    }
}

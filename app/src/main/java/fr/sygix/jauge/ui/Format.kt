/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import fr.sygix.jauge.model.Money
import fr.sygix.jauge.model.Spend
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    private val locale: Locale = Locale.FRANCE
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayTime = DateTimeFormatter.ofPattern("EEE HH:mm", locale)
    private val date = DateTimeFormatter.ofPattern("d MMM", locale)

    fun percent(value: Double): String = "${value.roundToInt().coerceIn(0, 999)} %"

    fun percentShort(value: Double): String = "${value.roundToInt().coerceIn(0, 999)}%"

    fun shown(usedPercent: Double, showRemaining: Boolean): Double =
        if (showRemaining) (100 - usedPercent).coerceIn(0.0, 100.0) else usedPercent.coerceIn(0.0, 100.0)

    fun countdown(targetMs: Long?, nowMs: Long): String? {
        targetMs ?: return null
        val minutes = ((targetMs - nowMs) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "maintenant"
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
            else -> "${minutes / (24 * 60)} j ${(minutes % (24 * 60)) / 60} h"
        }
    }

    fun resetAt(targetMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        targetMs ?: return null
        val target = Instant.ofEpochMilli(targetMs).atZone(zone)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
        return when {
            days == 0L -> target.format(time)
            days in 1..6 -> target.format(dayTime).replaceFirstChar { it.uppercase() }
            else -> target.format(date)
        }
    }

    fun updated(fetchedMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val minutes = (nowMs - fetchedMs) / 60_000
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            else -> Instant.ofEpochMilli(fetchedMs).atZone(zone).format(time)
        }
    }

    fun money(value: Money): String = runCatching {
        NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(value.currency) }.format(value.amount)
    }.getOrElse { "%.2f %s".format(locale, value.amount, value.currency) }

    fun spend(spend: Spend): String = when {
        spend.used != null && spend.limit != null -> "${money(spend.used)} / ${money(spend.limit)}"
        spend.used != null -> money(spend.used)
        spend.balance != null -> money(spend.balance)
        else -> spend.label
    }
}

/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import android.content.Context
import fr.sygix.jauge.R
import fr.sygix.jauge.model.Money
import fr.sygix.jauge.model.Spend
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    private fun clamp(value: Double): Int = value.roundToInt().coerceIn(0, 999)

    fun percent(context: Context, value: Double): String = context.getString(R.string.percent, clamp(value))

    fun percentShort(context: Context, value: Double): String = context.getString(R.string.percent_short, clamp(value))

    fun shown(usedPercent: Double, showRemaining: Boolean): Double =
        if (showRemaining) (100 - usedPercent).coerceIn(0.0, 100.0) else usedPercent.coerceIn(0.0, 100.0)

    fun countdown(context: Context, targetMs: Long?, nowMs: Long): String? {
        targetMs ?: return null
        val minutes = ((targetMs - nowMs) / 60_000).coerceAtLeast(0).toInt()
        return when {
            minutes < 1 -> context.getString(R.string.time_now)
            minutes < 60 -> context.getString(R.string.time_minutes, minutes)
            minutes < 24 * 60 -> context.getString(R.string.time_hours, minutes / 60, minutes % 60)
            else -> context.getString(R.string.time_days, minutes / (24 * 60), (minutes % (24 * 60)) / 60)
        }
    }

    fun resetAt(context: Context, targetMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        targetMs ?: return null
        val locale = locale(context)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val target = Instant.ofEpochMilli(targetMs).atZone(zone)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
        return when {
            days == 0L -> target.format(time)
            days in 1..6 -> target.format(DateTimeFormatter.ofPattern("EEE", locale)).replaceFirstChar { it.uppercase(locale) } + " " + target.format(time)
            else -> target.format(DateTimeFormatter.ofPattern("d MMM", locale))
        }
    }

    fun updated(context: Context, fetchedMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val minutes = ((nowMs - fetchedMs) / 60_000).toInt()
        return when {
            minutes < 1 -> context.getString(R.string.updated_just_now)
            minutes < 60 -> context.getString(R.string.updated_minutes_ago, minutes)
            else -> Instant.ofEpochMilli(fetchedMs).atZone(zone)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(context)))
        }
    }

    fun money(context: Context, value: Money): String {
        val locale = locale(context)
        return runCatching {
            NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(value.currency) }.format(value.amount)
        }.getOrElse { "%.2f %s".format(locale, value.amount, value.currency) }
    }

    fun spend(context: Context, spend: Spend): String = when {
        spend.used != null && spend.limit != null -> "${money(context, spend.used)} / ${money(context, spend.limit)}"
        spend.used != null -> money(context, spend.used)
        spend.balance != null -> money(context, spend.balance)
        else -> Texts.spend(context, spend.kind)
    }
}

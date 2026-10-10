/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import java.time.OffsetDateTime
import kotlin.math.pow

internal object Labels {
    const val SESSION = "Session 5 h"
    const val WEEKLY = "Semaine"
    const val MONTHLY = "Mois"
    fun weeklyModel(model: String) = "Semaine · $model"
}

internal fun parseInstantMs(value: String?): Long? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

internal fun minorToMajor(amount: Double, exponent: Int): Double = amount / 10.0.pow(exponent)

internal fun prettySlug(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    ?.split('_', '-', ' ')?.filter { it.isNotEmpty() }
    ?.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

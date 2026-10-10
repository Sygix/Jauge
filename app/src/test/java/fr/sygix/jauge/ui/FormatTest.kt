/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = ZonedDateTime.of(2026, 10, 10, 14, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun countdowns() {
        assertEquals("45 min", Format.countdown(now + 45 * 60_000, now))
        assertEquals("2 h 05", Format.countdown(now + 125 * 60_000, now))
        assertEquals("3 j 4 h", Format.countdown(now + (3 * 24 + 4) * 3_600_000L, now))
        assertEquals("maintenant", Format.countdown(now - 1, now))
    }

    @Test
    fun resetTimes() {
        assertEquals("16:29", Format.resetAt(now + (2 * 60 + 29) * 60_000, now, zone))
        assertEquals("Dim. 03:59", Format.resetAt(now + (13 * 60 + 59) * 60_000, now, zone))
    }

    @Test
    fun shownPercent() {
        assertEquals(93.0, Format.shown(7.0, showRemaining = true), 0.001)
        assertEquals(100.0, Format.shown(120.0, showRemaining = false), 0.001)
    }
}

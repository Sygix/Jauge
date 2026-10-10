/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.sygix.jauge.model.LoginFailure
import fr.sygix.jauge.model.UsageError
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.model.WindowKind
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FormatTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = ZonedDateTime.of(2026, 10, 10, 14, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun context(qualifiers: String): Context {
        RuntimeEnvironment.setQualifiers(qualifiers)
        return ApplicationProvider.getApplicationContext()
    }

    @Test
    fun frenchCountdowns() {
        val context = context("fr")
        assertEquals("45 min", Format.countdown(context, now + 45 * 60_000, now))
        assertEquals("2 h 05", Format.countdown(context, now + 125 * 60_000, now))
        assertEquals("3 j 4 h", Format.countdown(context, now + (3 * 24 + 4) * 3_600_000L, now))
        assertEquals("maintenant", Format.countdown(context, now - 1, now))
    }

    @Test
    fun englishCountdowns() {
        val context = context("en")
        assertEquals("3 d 4 h", Format.countdown(context, now + (3 * 24 + 4) * 3_600_000L, now))
        assertEquals("now", Format.countdown(context, now - 1, now))
        assertEquals("5 min ago", Format.updated(context, now - 5 * 60_000, now, zone))
    }

    @Test
    fun frenchResetTimes() {
        val context = context("fr")
        assertEquals("16:29", Format.resetAt(context, now + (2 * 60 + 29) * 60_000, now, zone))
        assertEquals("Dim. 03:59", Format.resetAt(context, now + (13 * 60 + 59) * 60_000, now, zone))
    }

    @Test
    fun localizedLabels() {
        val window = UsageWindow(WindowKind.WEEKLY_MODEL, 14.0, model = "Opus")
        assertEquals("Semaine · Opus", Texts.window(context("fr"), window))
        assertEquals("Week · Opus", Texts.window(context("en"), window))
        assertEquals("Service unavailable (503)", Texts.error(context("en"), UsageError.SERVER_PREFIX + 503))
        assertEquals("Le code a expiré, recommencez", Texts.failure(context("fr"), LoginFailure.Expired))
    }

    @Test
    fun shownPercent() {
        assertEquals(93.0, Format.shown(7.0, showRemaining = true), 0.001)
        assertEquals(100.0, Format.shown(120.0, showRemaining = false), 0.001)
    }
}

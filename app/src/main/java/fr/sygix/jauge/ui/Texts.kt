/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import android.content.Context
import fr.sygix.jauge.R
import fr.sygix.jauge.model.LoginFailure
import fr.sygix.jauge.model.SpendKind
import fr.sygix.jauge.model.UsageError
import fr.sygix.jauge.model.UsageWindow
import fr.sygix.jauge.model.WindowKind

object Texts {
    fun window(context: Context, window: UsageWindow): String = when (window.kind) {
        WindowKind.SESSION -> context.getString(R.string.window_session)
        WindowKind.WEEKLY -> context.getString(R.string.window_weekly)
        WindowKind.WEEKLY_MODEL -> context.getString(R.string.window_weekly_model, window.model ?: "?")
        WindowKind.MONTHLY -> context.getString(R.string.window_monthly)
    }

    fun windowShort(context: Context, kind: WindowKind): String = context.getString(
        when (kind) {
            WindowKind.SESSION -> R.string.short_session
            WindowKind.WEEKLY -> R.string.short_weekly
            WindowKind.WEEKLY_MODEL -> R.string.short_model
            WindowKind.MONTHLY -> R.string.short_monthly
        },
    )

    fun spend(context: Context, kind: SpendKind): String = context.getString(
        when (kind) {
            SpendKind.OVERAGE -> R.string.spend_overage
            SpendKind.CREDITS -> R.string.spend_credits
            SpendKind.UNLIMITED -> R.string.spend_unlimited
            SpendKind.PERIOD -> R.string.spend_period
            SpendKind.ROLLING_WEEK -> R.string.spend_rolling_week
            SpendKind.TOP_UP -> R.string.spend_top_up
        },
    )

    fun error(context: Context, code: String): String = when {
        code == UsageError.OFFLINE -> context.getString(R.string.error_offline)
        code == UsageError.NETWORK -> context.getString(R.string.error_network)
        code == UsageError.RATE_LIMITED -> context.getString(R.string.error_rate_limited)
        code == UsageError.RELOGIN -> context.getString(R.string.error_relogin)
        code == UsageError.DEPLETED -> context.getString(R.string.error_depleted)
        code.startsWith(UsageError.SERVER_PREFIX) -> context.getString(R.string.error_server, code.removePrefix(UsageError.SERVER_PREFIX).toIntOrNull() ?: 0)
        code.startsWith(UsageError.HTTP_PREFIX) -> context.getString(R.string.error_http, code.removePrefix(UsageError.HTTP_PREFIX).toIntOrNull() ?: 0)
        else -> code
    }

    fun failure(context: Context, failure: LoginFailure): String = when (failure) {
        is LoginFailure.PortBusy -> context.getString(R.string.failure_port_busy, failure.port)
        LoginFailure.InvalidState -> context.getString(R.string.failure_invalid_state)
        LoginFailure.MissingCode -> context.getString(R.string.failure_missing_code)
        LoginFailure.CodeMismatch -> context.getString(R.string.failure_code_mismatch)
        LoginFailure.Expired -> context.getString(R.string.failure_expired)
        LoginFailure.Denied -> context.getString(R.string.failure_denied)
        is LoginFailure.Http -> failure.detail?.let { context.getString(R.string.failure_http_detail, failure.code, it) }
            ?: context.getString(R.string.failure_http, failure.code)
        is LoginFailure.Other -> error(context, failure.detail)
    }
}

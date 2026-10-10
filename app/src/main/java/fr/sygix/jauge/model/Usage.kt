/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.model

import kotlinx.serialization.Serializable

@Serializable
enum class WindowKind { SESSION, WEEKLY, WEEKLY_MODEL, MONTHLY }

@Serializable
data class UsageWindow(
    val kind: WindowKind,
    val usedPercent: Double,
    val resetsAtMs: Long? = null,
    val model: String? = null,
)

@Serializable
enum class SpendKind { OVERAGE, CREDITS, UNLIMITED, PERIOD, ROLLING_WEEK, TOP_UP }

object UsageError {
    const val OFFLINE = "offline"
    const val NETWORK = "network"
    const val RATE_LIMITED = "rate_limited"
    const val RELOGIN = "relogin"
    const val DEPLETED = "depleted"
    const val SERVER_PREFIX = "server:"
    const val HTTP_PREFIX = "http:"
}

@Serializable
data class Money(val amount: Double, val currency: String)

@Serializable
data class Spend(
    val kind: SpendKind,
    val used: Money? = null,
    val limit: Money? = null,
    val percent: Double? = null,
    val balance: Money? = null,
    val enabled: Boolean = true,
)

@Serializable
data class UsageSnapshot(
    val provider: Provider,
    val fetchedAtMs: Long,
    val plan: String? = null,
    val account: String? = null,
    val windows: List<UsageWindow> = emptyList(),
    val spend: Spend? = null,
    val error: String? = null,
    val needsLogin: Boolean = false,
) {
    val session: UsageWindow? get() = windows.firstOrNull { it.kind == WindowKind.SESSION }
    val weekly: UsageWindow? get() = windows.firstOrNull { it.kind == WindowKind.WEEKLY }
    val monthly: UsageWindow? get() = windows.firstOrNull { it.kind == WindowKind.MONTHLY }
    val primary: UsageWindow? get() = session ?: monthly ?: weekly ?: windows.firstOrNull()
    val secondary: UsageWindow? get() = windows.firstOrNull { it !== primary && it.kind != WindowKind.WEEKLY_MODEL }
        ?: windows.firstOrNull { it !== primary }
}

@Serializable
data class Credentials(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMs: Long? = null,
    val account: String? = null,
    val plan: String? = null,
    val accountId: String? = null,
) {
    fun expiresWithin(nowMs: Long, marginMs: Long): Boolean = expiresAtMs != null && expiresAtMs - nowMs < marginMs
}

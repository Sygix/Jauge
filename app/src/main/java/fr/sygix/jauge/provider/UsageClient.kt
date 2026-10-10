/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageSnapshot
import okhttp3.HttpUrl.Companion.toHttpUrl

interface UsageClient {
    val provider: Provider
    suspend fun refresh(credentials: Credentials): Credentials
    suspend fun fetch(credentials: Credentials, nowMs: Long): UsageSnapshot
}

data class DeviceCode(
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresAtMs: Long,
    val handle: String,
    val secondaryHandle: String? = null,
)

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
    data class Done(val credentials: Credentials) : DevicePoll
}

internal fun buildUrl(base: String, params: Map<String, String>): String =
    base.toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build().toString()

/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.LoginFailedException
import fr.sygix.jauge.model.LoginFailure
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageError
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.net.AuthExpiredException
import fr.sygix.jauge.net.Http
import fr.sygix.jauge.net.HttpException
import fr.sygix.jauge.net.long
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import java.io.IOException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object OpenCodeAuth {
    private const val CONSOLE = "https://opencode.ai/console"
    private const val CLIENT_ID = "opencode-cli"
    private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

    suspend fun requestDeviceCode(): DeviceCode {
        val body = Http.json.encodeToString(JsonObject(mapOf("client_id" to JsonPrimitive(CLIENT_ID), "supports_org_scope" to JsonPrimitive(true))))
        val data = Http.postJson("$CONSOLE/auth/device/code", body.toRequestBody("application/json".toMediaType()))
        val url = data.str("verification_uri_complete") ?: throw IOException(UsageError.RELOGIN)
        val resolved = java.net.URI("$CONSOLE/").resolve(url)
        if (resolved.scheme != "https" || resolved.host != "opencode.ai") throw IOException(UsageError.RELOGIN)
        return DeviceCode(
            userCode = data.str("user_code") ?: throw IOException(UsageError.RELOGIN),
            verificationUrl = resolved.toString(),
            intervalSeconds = (data.long("interval") ?: 5).toInt().coerceIn(1, 30),
            expiresAtMs = System.currentTimeMillis() + (data.long("expires_in") ?: 600).coerceIn(1, 3600) * 1000,
            handle = data.str("device_code") ?: throw IOException(UsageError.RELOGIN),
        )
    }

    suspend fun pollDevice(code: DeviceCode): DevicePoll {
        val data = try {
            Http.postJson("$CONSOLE/auth/device/token", Http.jsonBody(mapOf("grant_type" to DEVICE_GRANT, "device_code" to code.handle, "client_id" to CLIENT_ID)))
        } catch (e: HttpException) {
            val error = e.oauthError
            if (error == "authorization_pending") return DevicePoll.Pending
            if (error == "slow_down") return DevicePoll.SlowDown
            if (error == "access_denied") throw LoginFailedException(LoginFailure.Denied)
            if (error == "expired_token") throw LoginFailedException(LoginFailure.Expired)
            throw e
        }
        return when (data.str("error")) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown
            "access_denied" -> throw LoginFailedException(LoginFailure.Denied)
            "expired_token" -> throw LoginFailedException(LoginFailure.Expired)
            null -> DevicePoll.Done(credentials(data, null))
            else -> throw IOException(UsageError.RELOGIN)
        }
    }

    suspend fun refresh(current: Credentials): Credentials {
        val refresh = current.refreshToken ?: throw AuthExpiredException("API key must be reconnected")
        val data = try {
            Http.postJson("$CONSOLE/auth/device/token", Http.jsonBody(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to CLIENT_ID)))
        } catch (e: HttpException) {
            if (e.code == 401 || e.oauthError == "invalid_grant") throw AuthExpiredException("OpenCode session expired") else throw e
        }
        return credentials(data, current)
    }

    private fun credentials(data: JsonObject, previous: Credentials?): Credentials = Credentials(
        accessToken = data.str("access_token") ?: throw IOException(UsageError.RELOGIN),
        refreshToken = data.str("refresh_token") ?: previous?.refreshToken ?: throw IOException(UsageError.RELOGIN),
        expiresAtMs = System.currentTimeMillis() + (data.long("expires_in") ?: 3600).coerceIn(1, 86400) * 1000,
        accountId = data.str("org_id") ?: previous?.accountId ?: throw IOException(UsageError.UNSUPPORTED_USAGE),
    )
}

class OpenCodeClient(private val baseUrl: String = "https://opencode.ai") : UsageClient {
    override val provider = Provider.OPENCODE

    override suspend fun refresh(credentials: Credentials): Credentials = OpenCodeAuth.refresh(credentials)

    override suspend fun fetch(credentials: Credentials, nowMs: Long): UsageSnapshot {
        val headers = mapOf("Authorization" to "Bearer ${credentials.accessToken}", "User-Agent" to "Jauge")
        if (credentials.refreshToken == null) {
            val api = try {
                Http.getJson("$baseUrl/zen/go/v1/usage", headers)
            } catch (e: HttpException) {
                if (e.code !in setOf(401, 403, 404)) throw e
                null
            }
            if (api != null) return OpenCodeParser.parseApi(api, nowMs)
        }
        try {
            val org = credentials.accountId ?: Http.getJson("$baseUrl/console/api/orgs/current", headers).str("id")
                ?: throw IOException(UsageError.UNSUPPORTED_USAGE)
            val scoped = headers + ("x-org-id" to org)
            val element = Http.getJsonElement("$baseUrl/console/api/go/status", scoped)
            val go = if (element == JsonNull) null else element.jsonObject
            val hasQuota = go?.obj("access") != null
            val billing = if (hasQuota) optionalUsageRead { Http.getJson("$baseUrl/console/api/billing/status", scoped) }
                else Http.getJson("$baseUrl/console/api/billing/status", scoped)
            val summary = if (!hasQuota && billing?.str("mode") == "pay-as-you-go") {
                Http.getJson("$baseUrl/console/api/usage/summary?range=30d", scoped)
            } else null
            val snapshot = OpenCodeParser.parseConsole(go, billing, summary, nowMs)
            if (snapshot.windows.isEmpty() && snapshot.spend == null) throw IOException(UsageError.UNSUPPORTED_USAGE)
            return snapshot
        } catch (e: HttpException) {
            if (e.code == 403) throw IOException(UsageError.PERMISSION) else throw e
        }
    }
}

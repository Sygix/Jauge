/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.auth.Pkce
import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.net.AuthExpiredException
import fr.sygix.jauge.net.Http
import fr.sygix.jauge.net.HttpException
import fr.sygix.jauge.net.Jwt
import fr.sygix.jauge.net.long
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import java.io.IOException
import kotlinx.serialization.json.JsonObject

object CodexAuth {
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val LOOPBACK_PORT = 1455
    const val REDIRECT_URI = "http://localhost:$LOOPBACK_PORT/auth/callback"
    const val DEVICE_URL = "https://auth.openai.com/codex/device"
    private const val ISSUER = "https://auth.openai.com"
    private const val TOKEN_URL = "$ISSUER/oauth/token"
    private const val DEVICE_REDIRECT_URI = "$ISSUER/deviceauth/callback"
    private const val SCOPES = "openid profile email offline_access"
    private const val DEVICE_TTL_MS = 15 * 60 * 1000L

    fun authorizeUrl(pkce: Pkce, state: String): String = buildUrl(
        "$ISSUER/oauth/authorize",
        mapOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "scope" to SCOPES,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
            "id_token_add_organizations" to "true",
            "codex_cli_simplified_flow" to "true",
            "state" to state,
        ),
    )

    suspend fun exchange(code: String, verifier: String, redirectUri: String = REDIRECT_URI): Credentials {
        val json = Http.postJson(
            TOKEN_URL,
            Http.formBody(
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "client_id" to CLIENT_ID,
                    "code_verifier" to verifier,
                ),
            ),
        )
        return credentials(json, null)
    }

    suspend fun requestDeviceCode(): DeviceCode {
        val json = Http.postJson("$ISSUER/api/accounts/deviceauth/usercode", Http.jsonBody(mapOf("client_id" to CLIENT_ID)))
        val userCode = json.str("user_code") ?: json.str("usercode") ?: throw IOException("Code appareil absent")
        val handle = json.str("device_auth_id") ?: throw IOException("device_auth_id absent")
        return DeviceCode(
            userCode = userCode,
            verificationUrl = DEVICE_URL,
            intervalSeconds = (json.long("interval") ?: 5).toInt().coerceAtLeast(3),
            expiresAtMs = parseInstantMs(json.str("expires_at")) ?: (System.currentTimeMillis() + DEVICE_TTL_MS),
            handle = handle,
        )
    }

    suspend fun pollDevice(code: DeviceCode): DevicePoll {
        val json = try {
            Http.postJson(
                "$ISSUER/api/accounts/deviceauth/token",
                Http.jsonBody(mapOf("device_auth_id" to code.handle, "user_code" to code.userCode)),
            )
        } catch (e: HttpException) {
            if (e.code == 403 || e.code == 404) return DevicePoll.Pending
            throw e
        }
        val authorizationCode = json.str("authorization_code") ?: return DevicePoll.Pending
        val verifier = json.str("code_verifier") ?: throw IOException("code_verifier absent")
        return DevicePoll.Done(exchange(authorizationCode, verifier, DEVICE_REDIRECT_URI))
    }

    suspend fun refresh(current: Credentials): Credentials {
        val refreshToken = current.refreshToken ?: throw AuthExpiredException("Jeton de rafraîchissement absent")
        val json = try {
            Http.postJson(
                TOKEN_URL,
                Http.formBody(mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to CLIENT_ID)),
            )
        } catch (e: HttpException) {
            if (e.code in 400..403) throw AuthExpiredException("Session ChatGPT expirée") else throw e
        }
        return credentials(json, current)
    }

    private fun credentials(json: JsonObject, previous: Credentials?): Credentials {
        val access = json.str("access_token") ?: throw IOException("Réponse sans access_token")
        val idClaims = json.str("id_token")?.let(Jwt::claims)
        val authClaims = Jwt.claims(access)?.obj("https://api.openai.com/auth")
            ?: idClaims?.obj("https://api.openai.com/auth")
        return Credentials(
            accessToken = access,
            refreshToken = json.str("refresh_token") ?: previous?.refreshToken,
            expiresAtMs = json.long("expires_in")?.let { System.currentTimeMillis() + it * 1000 } ?: Jwt.expiresAtMs(access),
            account = idClaims?.str("email") ?: previous?.account,
            plan = authClaims?.str("chatgpt_plan_type")?.let(::prettySlug) ?: previous?.plan,
            accountId = authClaims?.str("chatgpt_account_id") ?: previous?.accountId,
        )
    }
}

class CodexClient : UsageClient {
    override val provider = Provider.CODEX

    override suspend fun refresh(credentials: Credentials): Credentials = CodexAuth.refresh(credentials)

    override suspend fun fetch(credentials: Credentials, nowMs: Long): UsageSnapshot {
        val headers = buildMap {
            put("Authorization", "Bearer ${credentials.accessToken}")
            put("User-Agent", "codex-cli")
            credentials.accountId?.let { put("ChatGPT-Account-Id", it) }
            residency(credentials.accessToken)?.let { put("x-openai-internal-codex-residency", it) }
        }
        val usage = Http.getJson(USAGE_URL, headers)
        val snapshot = CodexParser.parse(usage, nowMs, credentials.account)
        return if (snapshot.plan == null) snapshot.copy(plan = credentials.plan) else snapshot
    }

    private fun residency(token: String): String? = Jwt.claims(token)?.obj("https://api.openai.com/auth")?.let {
        it.str("chatgpt_data_residency") ?: it.str("chatgpt_compute_residency")
    }?.takeIf { it.isNotBlank() }

    companion object {
        const val USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
    }
}

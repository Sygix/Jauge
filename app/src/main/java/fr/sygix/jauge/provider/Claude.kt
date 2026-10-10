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
import fr.sygix.jauge.net.long
import fr.sygix.jauge.net.obj
import fr.sygix.jauge.net.str
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody

object ClaudeAuth {
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val MANUAL_REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"
    const val SUCCESS_URL = "https://console.anthropic.com/oauth/code/success?app=claude-code"
    private const val AUTHORIZE_URL = "https://claude.ai/oauth/authorize"
    private const val SCOPES = "org:create_api_key user:profile user:inference"
    private const val TOKEN_USER_AGENT = "axios/1.7.9"
    private val tokenUrls = listOf("https://platform.claude.com/v1/oauth/token", "https://console.anthropic.com/v1/oauth/token")

    fun loopbackRedirectUri(port: Int) = "http://localhost:$port/callback"

    fun authorizeUrl(pkce: Pkce, state: String, redirectUri: String, manual: Boolean): String = buildUrl(
        AUTHORIZE_URL,
        buildMap {
            if (manual) put("code", "true")
            put("client_id", CLIENT_ID)
            put("response_type", "code")
            put("redirect_uri", redirectUri)
            put("scope", SCOPES)
            put("code_challenge", pkce.challenge)
            put("code_challenge_method", "S256")
            put("state", state)
        },
    )

    suspend fun exchange(code: String, state: String, verifier: String, redirectUri: String): Credentials {
        val body = Http.jsonBody(
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to CLIENT_ID,
                "code" to code,
                "state" to state,
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
            ),
        )
        return withProfile(credentials(postToken { body }, null))
    }

    suspend fun refresh(current: Credentials): Credentials {
        val refreshToken = current.refreshToken ?: throw AuthExpiredException("Missing refresh token")
        val form = mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to CLIENT_ID)
        val json = try {
            postToken { Http.formBody(form) }
        } catch (e: HttpException) {
            if (e.code in 400..403) throw AuthExpiredException("Claude session expired") else throw e
        }
        return credentials(json, current).let { if (it.plan == null) withProfile(it) else it }
    }

    private suspend fun postToken(body: () -> RequestBody): JsonObject {
        var last: IOException? = null
        for (url in tokenUrls) {
            try {
                return Http.postJson(url, body(), mapOf("User-Agent" to TOKEN_USER_AGENT))
            } catch (e: HttpException) {
                last = e
                if (e.code != 404 && e.code < 500) throw e
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("Token exchange failed")
    }

    private fun credentials(json: JsonObject, previous: Credentials?): Credentials {
        val access = json.str("access_token") ?: throw IOException("Response without access_token")
        val expiresIn = json.long("expires_in") ?: 3600
        return Credentials(
            accessToken = access,
            refreshToken = json.str("refresh_token") ?: previous?.refreshToken,
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000,
            account = json.obj("account")?.str("email_address") ?: previous?.account,
            plan = previous?.plan,
        )
    }

    private suspend fun withProfile(credentials: Credentials): Credentials = runCatching {
        val profile = Http.getJson(ClaudeClient.PROFILE_URL, ClaudeClient.headers(credentials.accessToken))
        credentials.copy(
            account = ClaudeParser.account(profile) ?: credentials.account,
            plan = ClaudeParser.plan(profile) ?: credentials.plan,
        )
    }.getOrDefault(credentials)
}

class ClaudeClient : UsageClient {
    override val provider = Provider.CLAUDE

    override suspend fun refresh(credentials: Credentials): Credentials = ClaudeAuth.refresh(credentials)

    override suspend fun fetch(credentials: Credentials, nowMs: Long): UsageSnapshot {
        val usage = Http.getJson(USAGE_URL, headers(credentials.accessToken))
        return ClaudeParser.parse(usage, nowMs, credentials.plan, credentials.account)
    }

    companion object {
        const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
        const val PROFILE_URL = "https://api.anthropic.com/api/oauth/profile"

        fun headers(token: String) = mapOf(
            "Authorization" to "Bearer $token",
            "anthropic-beta" to "oauth-2025-04-20",
            "User-Agent" to "claude-code/2.1.0",
        )
    }
}

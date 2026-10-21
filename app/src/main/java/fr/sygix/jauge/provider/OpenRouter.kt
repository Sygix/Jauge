/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.auth.Pkce
import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageError
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.net.AuthExpiredException
import fr.sygix.jauge.net.Http
import fr.sygix.jauge.net.HttpException
import fr.sygix.jauge.net.str
import java.io.IOException

object OpenRouterAuth {
    fun loopbackRedirectUri(port: Int) = "http://localhost:$port/callback"

    fun authorizeUrl(pkce: Pkce, state: String, redirectUri: String): String = buildUrl(
        "https://openrouter.ai/auth",
        mapOf("callback_url" to redirectUri, "code_challenge" to pkce.challenge, "code_challenge_method" to "S256", "state" to state, "key_label" to "Jauge"),
    )

    suspend fun exchange(code: String, verifier: String): Credentials {
        val token = Http.postJson("https://openrouter.ai/api/v1/auth/keys", Http.jsonBody(mapOf("code" to code, "code_verifier" to verifier, "code_challenge_method" to "S256")))
        return Credentials(accessToken = token.str("key") ?: throw IOException(UsageError.RELOGIN))
    }
}

class OpenRouterClient(private val baseUrl: String = "https://openrouter.ai") : UsageClient {
    override val provider = Provider.OPENROUTER

    override suspend fun refresh(credentials: Credentials): Credentials = throw AuthExpiredException("API key must be reconnected")

    override suspend fun fetch(credentials: Credentials, nowMs: Long): UsageSnapshot {
        val headers = mapOf("Authorization" to "Bearer ${credentials.accessToken}")
        val key = try {
            Http.getJson("$baseUrl/api/v1/key", headers)
        } catch (e: HttpException) {
            if (e.code == 403) throw IOException(UsageError.PERMISSION) else throw e
        }
        val credits = optionalUsageRead { Http.getJson("$baseUrl/api/v1/credits", headers) }
        return OpenRouterParser.parse(key, credits, nowMs)
    }
}

internal suspend fun <T> optionalUsageRead(read: suspend () -> T): T? = try {
    read()
} catch (e: HttpException) {
    if (e.code == 401) throw e else null
} catch (e: IOException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

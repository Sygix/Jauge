/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.net.AuthExpiredException
import fr.sygix.jauge.net.Http
import fr.sygix.jauge.net.HttpException
import fr.sygix.jauge.net.Jwt
import fr.sygix.jauge.net.long
import fr.sygix.jauge.net.str
import java.io.IOException
import kotlinx.serialization.json.JsonObject

object NousAuth {
    const val PORTAL = "https://portal.nousresearch.com"
    private const val CLIENT_ID = "hermes-cli"
    private const val SCOPE = "inference:invoke"
    private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

    suspend fun requestDeviceCode(): DeviceCode {
        val json = Http.postJson(
            "$PORTAL/api/oauth/device/code",
            Http.formBody(mapOf("client_id" to CLIENT_ID, "scope" to SCOPE)),
        )
        return DeviceCode(
            userCode = json.str("user_code") ?: throw IOException("Code appareil absent"),
            verificationUrl = json.str("verification_uri_complete") ?: json.str("verification_uri") ?: "$PORTAL/device",
            intervalSeconds = (json.long("interval") ?: 5).toInt().coerceIn(1, 30),
            expiresAtMs = System.currentTimeMillis() + (json.long("expires_in") ?: 900) * 1000,
            handle = json.str("device_code") ?: throw IOException("device_code absent"),
        )
    }

    suspend fun pollDevice(code: DeviceCode): DevicePoll {
        val json = try {
            Http.postJson(
                "$PORTAL/api/oauth/token",
                Http.formBody(mapOf("grant_type" to DEVICE_GRANT, "client_id" to CLIENT_ID, "device_code" to code.handle)),
            )
        } catch (e: HttpException) {
            return when (e.oauthError) {
                "authorization_pending" -> DevicePoll.Pending
                "slow_down" -> DevicePoll.SlowDown
                "expired_token" -> throw IOException("Code expiré, recommencez")
                "access_denied" -> throw IOException("Connexion refusée")
                else -> throw e
            }
        }
        return DevicePoll.Done(credentials(json, null))
    }

    suspend fun refresh(current: Credentials): Credentials {
        val refreshToken = current.refreshToken ?: throw AuthExpiredException("Jeton de rafraîchissement absent")
        val json = try {
            Http.postJson(
                "$PORTAL/api/oauth/token",
                Http.formBody(mapOf("grant_type" to "refresh_token", "client_id" to CLIENT_ID)),
                mapOf("x-nous-refresh-token" to refreshToken),
            )
        } catch (e: HttpException) {
            if (e.code in 400..403) throw AuthExpiredException("Session Nous expirée") else throw e
        }
        return credentials(json, current)
    }

    private fun credentials(json: JsonObject, previous: Credentials?): Credentials {
        val access = json.str("access_token") ?: throw IOException("Réponse sans access_token")
        return Credentials(
            accessToken = access,
            refreshToken = json.str("refresh_token") ?: previous?.refreshToken,
            expiresAtMs = json.long("expires_in")?.let { System.currentTimeMillis() + it * 1000 } ?: Jwt.expiresAtMs(access),
            account = previous?.account,
            plan = previous?.plan,
        )
    }
}

class NousClient : UsageClient {
    override val provider = Provider.NOUS

    override suspend fun refresh(credentials: Credentials): Credentials = NousAuth.refresh(credentials)

    override suspend fun fetch(credentials: Credentials, nowMs: Long): UsageSnapshot {
        val account = Http.getJson("${NousAuth.PORTAL}/api/oauth/account", mapOf("Authorization" to "Bearer ${credentials.accessToken}"))
        return NousParser.parse(account, nowMs)
    }
}

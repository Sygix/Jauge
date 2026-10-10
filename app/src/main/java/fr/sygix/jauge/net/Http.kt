/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.net

import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class HttpException(val code: Int, val body: String) : IOException("HTTP $code") {
    val isAuthError: Boolean get() = code == 401 || code == 403
    val oauthError: String?
        get() = runCatching { Http.json.parseToJsonElement(body).jsonObject.str("error") }.getOrNull()
}

class AuthExpiredException(message: String) : IOException(message)

object Http {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()

    fun jsonBody(obj: Map<String, String>): RequestBody =
        json.encodeToString(JsonObject(obj.mapValues { JsonPrimitive(it.value) })).toRequestBody(jsonType)

    fun formBody(fields: Map<String, String>): RequestBody =
        FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()

    suspend fun getJson(url: String, headers: Map<String, String>): JsonObject =
        execute(Request.Builder().url(url).get().headers(headers).build())

    suspend fun postJson(url: String, body: RequestBody, headers: Map<String, String> = emptyMap()): JsonObject =
        execute(Request.Builder().url(url).post(body).headers(headers).build())

    private fun Request.Builder.headers(headers: Map<String, String>): Request.Builder = apply {
        header("Accept", "application/json")
        headers.forEach { (k, v) -> header(k, v) }
    }

    private suspend fun execute(request: Request): JsonObject = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw HttpException(response.code, text.take(2000))
            if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject
        }
    }
}

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonElement?.asObjectList(): List<JsonObject> = (this as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

object Jwt {
    fun claims(token: String): JsonObject? = runCatching {
        val part = token.split(".")[1]
        val decoded = Base64.getUrlDecoder().decode(part.padEnd(part.length + (4 - part.length % 4) % 4, '='))
        Http.json.parseToJsonElement(String(decoded)).jsonObject
    }.getOrNull()

    fun expiresAtMs(token: String): Long? = claims(token)?.long("exp")?.times(1000)
}

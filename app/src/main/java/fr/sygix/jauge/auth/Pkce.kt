/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class Pkce(val verifier: String, val challenge: String) {
    companion object {
        private val random = SecureRandom()

        fun create(): Pkce {
            val verifier = randomToken(48)
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Pkce(verifier, base64Url(digest))
        }

        fun randomToken(bytes: Int = 32): String = base64Url(ByteArray(bytes).also(random::nextBytes))

        private fun base64Url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }
}

/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.sync

import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.net.AuthExpiredException
import fr.sygix.jauge.net.HttpException
import fr.sygix.jauge.provider.UsageClient
import fr.sygix.jauge.store.JaugeStore
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UsageSync(
    private val store: JaugeStore,
    private val clients: Map<Provider, UsageClient>,
    private val onUpdated: suspend () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val locks = Provider.entries.associateWith { Mutex() }

    suspend fun syncAll() {
        val providers = store.connected.first()
        coroutineScope { providers.map { async { syncProvider(it) } }.awaitAll() }
        onUpdated()
    }

    suspend fun sync(provider: Provider): UsageSnapshot? = syncProvider(provider).also { onUpdated() }

    suspend fun connect(provider: Provider, credentials: Credentials): UsageSnapshot? {
        locks.getValue(provider).withLock { store.saveCredentials(provider, credentials) }
        return sync(provider)
    }

    private suspend fun syncProvider(provider: Provider): UsageSnapshot? = locks.getValue(provider).withLock {
        val client = clients.getValue(provider)
        val stored = store.credentials(provider) ?: return@withLock null
        val now = clock()
        val previous = store.snapshot(provider)
        val snapshot = try {
            fetchWithRefresh(provider, client, stored, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthExpiredException) {
            failed(previous, provider, now, e.message ?: "Reconnexion requise", needsLogin = true)
        } catch (e: Exception) {
            failed(previous, provider, now, describe(e), needsLogin = false)
        }
        store.saveSnapshot(snapshot)
        snapshot
    }

    private suspend fun fetchWithRefresh(provider: Provider, client: UsageClient, stored: Credentials, now: Long): UsageSnapshot {
        var credentials = stored
        if (credentials.expiresWithin(now, REFRESH_MARGIN_MS)) credentials = refreshed(provider, client, credentials)
        return try {
            client.fetch(credentials, now)
        } catch (e: HttpException) {
            if (!e.isAuthError) throw e
            credentials = refreshed(provider, client, credentials)
            try {
                client.fetch(credentials, now)
            } catch (retry: HttpException) {
                if (retry.isAuthError) throw AuthExpiredException("Reconnexion requise") else throw retry
            }
        }
    }

    private suspend fun refreshed(provider: Provider, client: UsageClient, credentials: Credentials): Credentials =
        client.refresh(credentials).also { store.saveCredentials(provider, it) }

    private fun failed(previous: UsageSnapshot?, provider: Provider, now: Long, message: String, needsLogin: Boolean) =
        previous?.copy(error = message, needsLogin = needsLogin)
            ?: UsageSnapshot(provider = provider, fetchedAtMs = now, error = message, needsLogin = needsLogin)

    companion object {
        private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L

        fun describe(e: Throwable): String = when (e) {
            is HttpException -> when {
                e.code == 429 -> "Trop de requêtes (429)"
                e.code >= 500 -> "Service indisponible (${e.code})"
                else -> "Erreur HTTP ${e.code}"
            }
            is UnknownHostException -> "Hors ligne"
            is IOException -> e.message?.takeIf { it.isNotBlank() } ?: "Erreur réseau"
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}

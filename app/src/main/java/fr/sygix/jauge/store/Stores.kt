/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.model.UsageSnapshot
import fr.sygix.jauge.net.Http
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.jaugeStore: DataStore<Preferences> by preferencesDataStore(name = "jauge")

data class Settings(
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
    val showRemaining: Boolean = false,
    val wifiOnly: Boolean = false,
) {
    companion object {
        const val DEFAULT_REFRESH_MINUTES = 15
        val REFRESH_CHOICES = listOf(5, 10, 15, 30, 60, 120, 240)
    }
}

class JaugeStore(context: Context, private val box: SecureBox = SecureBox()) {

    private val store = context.applicationContext.jaugeStore

    private fun credentialsKey(provider: Provider) = stringPreferencesKey("credentials_${provider.name}")
    private fun snapshotKey(provider: Provider) = stringPreferencesKey("snapshot_${provider.name}")
    private val refreshKey = intPreferencesKey("refresh_minutes")
    private val remainingKey = booleanPreferencesKey("show_remaining")
    private val wifiKey = booleanPreferencesKey("wifi_only")

    val settings: Flow<Settings> = store.data.map {
        Settings(
            refreshMinutes = it[refreshKey] ?: Settings.DEFAULT_REFRESH_MINUTES,
            showRemaining = it[remainingKey] ?: false,
            wifiOnly = it[wifiKey] ?: false,
        )
    }

    val snapshots: Flow<Map<Provider, UsageSnapshot>> = store.data.map { prefs ->
        Provider.entries.mapNotNull { provider ->
            prefs[snapshotKey(provider)]?.let { raw ->
                runCatching { Http.json.decodeFromString<UsageSnapshot>(raw) }.getOrNull()?.let { provider to it }
            }
        }.toMap()
    }

    val connected: Flow<Set<Provider>> = store.data.map { prefs ->
        Provider.entries.filter { prefs[credentialsKey(it)] != null }.toSet()
    }

    suspend fun credentials(provider: Provider): Credentials? = store.data.first()[credentialsKey(provider)]
        ?.let(box::decrypt)
        ?.let { runCatching { Http.json.decodeFromString<Credentials>(it) }.getOrNull() }

    suspend fun saveCredentials(provider: Provider, credentials: Credentials) {
        val encrypted = box.encrypt(Http.json.encodeToString(credentials))
        store.edit { it[credentialsKey(provider)] = encrypted }
    }

    suspend fun disconnect(provider: Provider) {
        store.edit {
            it.remove(credentialsKey(provider))
            it.remove(snapshotKey(provider))
        }
    }

    suspend fun snapshot(provider: Provider): UsageSnapshot? = snapshots.first()[provider]

    suspend fun saveSnapshot(snapshot: UsageSnapshot) {
        store.edit { it[snapshotKey(snapshot.provider)] = Http.json.encodeToString(snapshot) }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(settings.first())
        store.edit {
            it[refreshKey] = next.refreshMinutes
            it[remainingKey] = next.showRemaining
            it[wifiKey] = next.wifiOnly
        }
    }
}

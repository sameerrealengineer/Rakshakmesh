package org.rakshakmesh.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.rakshakmesh.BuildConfig

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboarded: Boolean = false,
    val cycloneMode: Boolean = false,
    val relayUrl: String = BuildConfig.DEFAULT_RELAY_URL,
    val mapUrl: String = "",
    /** Last successful alert fetch for the current district (epoch ms), 0 = never. */
    val lastAlertFetch: Long = 0,
    val lastAlertError: String? = null,
)

class SettingsStore(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val cyclone = booleanPreferencesKey("cyclone_mode")
        val relay = stringPreferencesKey("relay_url")
        val map = stringPreferencesKey("map_url")
        val lastFetch = longPreferencesKey("last_alert_fetch")
        val lastError = stringPreferencesKey("last_alert_error")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun get(): AppSettings = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        onboarded = this[K.onboarded] ?: false,
        cycloneMode = this[K.cyclone] ?: false,
        relayUrl = this[K.relay]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_RELAY_URL,
        mapUrl = this[K.map] ?: "",
        lastAlertFetch = this[K.lastFetch] ?: 0,
        lastAlertError = this[K.lastError],
    )

    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[K.onboarded] = v }
    suspend fun setCycloneMode(v: Boolean) = context.dataStore.edit { it[K.cyclone] = v }
    suspend fun setRelayUrl(v: String) = context.dataStore.edit { it[K.relay] = v.trim().trimEnd('/') }
    suspend fun setMapUrl(v: String) = context.dataStore.edit { it[K.map] = v.trim() }

    suspend fun recordFetch(success: Boolean, at: Long, error: String?) = context.dataStore.edit {
        if (success) {
            it[K.lastFetch] = at
            it.remove(K.lastError)
        } else {
            it[K.lastError] = error ?: "error"
        }
    }

    /** District changed: old fetch time no longer applies. */
    suspend fun resetFetch() = context.dataStore.edit {
        it.remove(K.lastFetch)
        it.remove(K.lastError)
    }
}

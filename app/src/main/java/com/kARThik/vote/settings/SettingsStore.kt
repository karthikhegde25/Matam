package com.kARThik.vote.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "matam_settings")

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultMode: String = "SINGLE_PHONE",
    val buzzerEnabled: Boolean = true,
    val buzzerSound: Int = 0,
    val buzzerVolume: Float = 0.8f,
    val vibration: Boolean = true,
    val defaultResultLayout: Int = 0,
    val showPercentages: Boolean = true,
    val btAutoReconnect: Boolean = true,
    val btKeepScreenOn: Boolean = true,
    val deviceId: String = ""
)

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val mode = stringPreferencesKey("default_mode")
        val buzzer = booleanPreferencesKey("buzzer")
        val sound = intPreferencesKey("buzzer_sound")
        val volume = floatPreferencesKey("buzzer_volume")
        val vibration = booleanPreferencesKey("vibration")
        val layout = intPreferencesKey("result_layout")
        val percent = booleanPreferencesKey("show_percent")
        val reconnect = booleanPreferencesKey("bt_reconnect")
        val screenOn = booleanPreferencesKey("bt_screen_on")
        val deviceId = stringPreferencesKey("device_id")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            defaultMode = p[K.mode] ?: "SINGLE_PHONE",
            buzzerEnabled = p[K.buzzer] ?: true,
            buzzerSound = p[K.sound] ?: 0,
            buzzerVolume = p[K.volume] ?: 0.8f,
            vibration = p[K.vibration] ?: true,
            defaultResultLayout = p[K.layout] ?: 0,
            showPercentages = p[K.percent] ?: true,
            btAutoReconnect = p[K.reconnect] ?: true,
            btKeepScreenOn = p[K.screenOn] ?: true,
            deviceId = p[K.deviceId] ?: ""
        )
    }

    /** Stable random identifier for this installation (not tied to any person). */
    suspend fun ensureDeviceId(): String {
        var id = ""
        context.dataStore.edit { p ->
            id = p[K.deviceId] ?: "dev-" + UUID.randomUUID().toString().take(8)
            p[K.deviceId] = id
        }
        return id
    }

    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setDefaultMode(v: String) = context.dataStore.edit { it[K.mode] = v }
    suspend fun setBuzzer(v: Boolean) = context.dataStore.edit { it[K.buzzer] = v }
    suspend fun setSound(v: Int) = context.dataStore.edit { it[K.sound] = v }
    suspend fun setVolume(v: Float) = context.dataStore.edit { it[K.volume] = v }
    suspend fun setVibration(v: Boolean) = context.dataStore.edit { it[K.vibration] = v }
    suspend fun setResultLayout(v: Int) = context.dataStore.edit { it[K.layout] = v }
    suspend fun setShowPercent(v: Boolean) = context.dataStore.edit { it[K.percent] = v }
    suspend fun setReconnect(v: Boolean) = context.dataStore.edit { it[K.reconnect] = v }
    suspend fun setScreenOn(v: Boolean) = context.dataStore.edit { it[K.screenOn] = v }
}

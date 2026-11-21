package com.moneysmart.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "money_smart_settings")

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

data class AppSettings(
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val baseCurrency: String = "SGD",
    val allowedPackages: Set<String> = emptySet(),
    val aiAutoRecordEnabled: Boolean = true,
    val confidenceThreshold: Float = 0.75f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val backupFolderUri: String = "",
    val autoBackupEnabled: Boolean = false,
    val lastBackupTimestamp: Long = 0L,
    val lastRateSyncTimestamp: Long = 0L
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
    }
}

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
        val AI_AUTO_RECORD = booleanPreferencesKey("ai_auto_record")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val LAST_RATE_SYNC_TIMESTAMP = longPreferencesKey("last_rate_sync_timestamp")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            geminiModel = prefs[Keys.GEMINI_MODEL]?.ifBlank { AppSettings.DEFAULT_GEMINI_MODEL }
                ?: AppSettings.DEFAULT_GEMINI_MODEL,
            baseCurrency = prefs[Keys.BASE_CURRENCY]?.ifBlank { "SGD" } ?: "SGD",
            allowedPackages = prefs[Keys.ALLOWED_PACKAGES] ?: emptySet(),
            aiAutoRecordEnabled = prefs[Keys.AI_AUTO_RECORD] ?: true,
            confidenceThreshold = prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0.75f,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            backupFolderUri = prefs[Keys.BACKUP_FOLDER_URI] ?: "",
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TIMESTAMP] ?: 0L,
            lastRateSyncTimestamp = prefs[Keys.LAST_RATE_SYNC_TIMESTAMP] ?: 0L
        )
    }

    suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun setGeminiConfig(apiKey: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GEMINI_API_KEY] = apiKey.trim()
            prefs[Keys.GEMINI_MODEL] = model.trim().ifBlank { AppSettings.DEFAULT_GEMINI_MODEL }
        }
    }

    suspend fun setBaseCurrency(currency: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_CURRENCY] = currency.trim().uppercase().ifBlank { "USD" }
        }
    }

    suspend fun setAllowedPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALLOWED_PACKAGES] = packages
        }
    }

    suspend fun toggleAllowedPackage(packageName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ALLOWED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(packageName) else current.remove(packageName)
            prefs[Keys.ALLOWED_PACKAGES] = current
        }
    }

    suspend fun setAiPreferences(autoRecord: Boolean, confidenceThreshold: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_AUTO_RECORD] = autoRecord
            prefs[Keys.CONFIDENCE_THRESHOLD] = confidenceThreshold.coerceIn(0.3f, 0.99f)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAppLock(enabled: Boolean, pinHash: String, pinSalt: String, biometricEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
            prefs[Keys.PIN_HASH] = pinHash
            prefs[Keys.PIN_SALT] = pinSalt
            prefs[Keys.BIOMETRIC_ENABLED] = biometricEnabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setBackupFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKUP_FOLDER_URI] = uri
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_TIMESTAMP] = timestamp
        }
    }

    suspend fun setLastRateSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_RATE_SYNC_TIMESTAMP] = timestamp
        }
    }
}

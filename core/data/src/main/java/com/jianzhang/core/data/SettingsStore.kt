package com.jianzhang.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.TxnType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "jianzhang-settings")

data class LockGuard(
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L,
)

class SettingsStore(
    private val context: Context,
    private val secretStore: SecretStore,
) {
    private object K {
        val migrated = stringPreferencesKey("legacy_migrated")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockHash = stringPreferencesKey("lock_hash")
        val lockSalt = stringPreferencesKey("lock_salt")
        val theme = stringPreferencesKey("theme")
        val fontScale = floatPreferencesKey("font_scale")
        val fontFamily = stringPreferencesKey("font_family")
        val defaultAccount = longPreferencesKey("default_account")
        val defaultType = stringPreferencesKey("default_type")
        val rememberCategory = booleanPreferencesKey("remember_category")
        val lastCategory = longPreferencesKey("last_category")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val budgetAlert = booleanPreferencesKey("budget_alert")
        val billAlert = booleanPreferencesKey("bill_alert")
        val dailyReminder = booleanPreferencesKey("daily_reminder")
        val weeklyReminder = booleanPreferencesKey("weekly_reminder")
        val weeklyWeekday = intPreferencesKey("weekly_weekday")
        val systemNotify = booleanPreferencesKey("system_notify")
        val aiEndpoint = stringPreferencesKey("ai_endpoint")
        val aiKey = stringPreferencesKey("ai_key")
        val aiModel = stringPreferencesKey("ai_model")
        val aiTemperature = stringPreferencesKey("ai_temperature")
        val aiTimeout = intPreferencesKey("ai_timeout")
        val showSign = booleanPreferencesKey("show_sign")
        val biometric = booleanPreferencesKey("biometric")
        val lockFailedAttempts = intPreferencesKey("lock_failed_attempts")
        val lockUntil = longPreferencesKey("lock_until")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            lockEnabled = p[K.lockEnabled] ?: false,
            lockHash = p[K.lockHash] ?: "",
            lockSalt = p[K.lockSalt] ?: "",
            themeMode = p[K.theme] ?: "dark",
            fontScale = p[K.fontScale] ?: 1f,
            fontFamily = p[K.fontFamily] ?: "default",
            defaultAccountId = p[K.defaultAccount],
            defaultType = runCatching { TxnType.valueOf(p[K.defaultType] ?: "EXPENSE") }.getOrElse { TxnType.EXPENSE },
            rememberLastCategory = p[K.rememberCategory] ?: true,
            lastCategoryId = p[K.lastCategory],
            reminderHour = p[K.reminderHour] ?: 20,
            reminderMinute = p[K.reminderMinute] ?: 0,
            budgetAlert = p[K.budgetAlert] ?: true,
            billAlert = p[K.billAlert] ?: true,
            dailyReminder = p[K.dailyReminder] ?: false,
            weeklyReminder = p[K.weeklyReminder] ?: true,
            weeklyReminderWeekday = p[K.weeklyWeekday] ?: 0,
            systemNotify = p[K.systemNotify] ?: true,
            aiEndpoint = p[K.aiEndpoint] ?: "https://api.deepseek.com/chat/completions",
            aiApiKey = p[K.aiKey]?.let { encrypted ->
                runCatching { String(secretStore.decrypt(encrypted, AI_ALIAS), Charsets.UTF_8) }.getOrElse { "" }
            } ?: "",
            aiModel = p[K.aiModel] ?: "deepseek-chat",
            aiTemperature = p[K.aiTemperature]?.toDoubleOrNull() ?: 0.35,
            aiTimeoutSeconds = p[K.aiTimeout] ?: 90,
            showAmountSign = p[K.showSign] ?: true,
            biometricEnabled = p[K.biometric] ?: false,
        )
    }

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun migrateLegacyIfNeeded() {
        if (context.settingsDataStore.data.first()[K.migrated] == "1") return
        val legacy = context.getSharedPreferences("jz_settings", Context.MODE_PRIVATE)
        val auth = context.getSharedPreferences("jz_auth", Context.MODE_PRIVATE)
        val ai = context.getSharedPreferences("jz_ai_config", Context.MODE_PRIVATE)
        context.settingsDataStore.edit { p ->
            if (legacy.all.isNotEmpty()) {
                p[K.theme] = legacy.getString("theme_mode", "dark") ?: "dark"
                p[K.fontScale] = when (legacy.getString("font_size", "medium")) {
                    "small" -> 0.9f
                    "large" -> 1.15f
                    else -> 1f
                }
                p[K.fontFamily] = legacy.getString("font_family", "default") ?: "default"
                p[K.defaultType] = (legacy.getString("default_type", "expense") ?: "expense").uppercase()
                p[K.rememberCategory] = legacy.getBoolean("remember_category", true)
                val account = legacy.getLong("default_account_id", 0L)
                if (account > 0L) p[K.defaultAccount] = account
                p[K.budgetAlert] = legacy.getBoolean("budget_alert", true)
                p[K.billAlert] = legacy.getBoolean("bill_alert", true)
                p[K.dailyReminder] = legacy.getBoolean("daily_reminder", false)
                p[K.weeklyReminder] = legacy.getBoolean("weekly_reminder", true)
                p[K.weeklyWeekday] = legacy.getInt("weekly_reminder_weekday", 0)
                p[K.systemNotify] = legacy.getBoolean("system_notify", true)
                val time = legacy.getString("daily_reminder_time", "21:00") ?: "21:00"
                p[K.reminderHour] = time.substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 21
                p[K.reminderMinute] = time.substringAfter(':', "00").toIntOrNull()?.coerceIn(0, 59) ?: 0
                val last = legacy.getLong("last_cat", 0L)
                if (last > 0L) p[K.lastCategory] = last
            }
            if (auth.contains("salt") && auth.contains("hash")) {
                p[K.lockEnabled] = true
                p[K.lockSalt] = auth.getString("salt", "") ?: ""
                p[K.lockHash] = "sha256$" + (auth.getString("salt", "") ?: "") + "$" + (auth.getString("hash", "") ?: "")
            }
            if (ai.all.isNotEmpty()) {
                p[K.aiEndpoint] = ai.getString("endpoint", "https://api.deepseek.com/chat/completions")
                    ?: "https://api.deepseek.com/chat/completions"
                p[K.aiModel] = ai.getString("model", "deepseek-chat") ?: "deepseek-chat"
                p[K.aiTemperature] = ai.getFloat("temperature", 0.35f).toString()
                p[K.aiTimeout] = ai.getInt("timeout_secs", 90)
                val key = ai.getString("api_key", "") ?: ""
                if (key.isNotBlank()) p[K.aiKey] = secretStore.encrypt(key.toByteArray(Charsets.UTF_8), AI_ALIAS)
            }
            p[K.migrated] = "1"
        }
    }

    suspend fun setLock(enabled: Boolean, hash: String, salt: String) {
        context.settingsDataStore.edit {
            it[K.lockEnabled] = enabled
            it[K.lockHash] = hash
            it[K.lockSalt] = salt
        }
    }

    suspend fun lockGuard(): LockGuard {
        val preferences = context.settingsDataStore.data.first()
        return LockGuard(
            failedAttempts = preferences[K.lockFailedAttempts] ?: 0,
            lockedUntil = preferences[K.lockUntil] ?: 0L,
        )
    }

    suspend fun setLockGuard(failedAttempts: Int, lockedUntil: Long) {
        context.settingsDataStore.edit {
            it[K.lockFailedAttempts] = failedAttempts.coerceIn(0, 5)
            it[K.lockUntil] = lockedUntil.coerceAtLeast(0L)
        }
    }

    suspend fun clearLock() {
        context.settingsDataStore.edit {
            it[K.lockEnabled] = false
            it[K.lockHash] = ""
            it[K.lockSalt] = ""
            it[K.biometric] = false
            it[K.lockFailedAttempts] = 0
            it[K.lockUntil] = 0L
        }
    }

    suspend fun setTheme(mode: String) = context.settingsDataStore.edit { it[K.theme] = mode }

    suspend fun setFontScale(scale: Float) = context.settingsDataStore.edit { it[K.fontScale] = scale.coerceIn(0.85f, 1.3f) }

    suspend fun setFontFamily(family: String) = context.settingsDataStore.edit { it[K.fontFamily] = family }

    suspend fun setDefaultAccount(id: Long?) {
        context.settingsDataStore.edit { if (id == null) it.remove(K.defaultAccount) else it[K.defaultAccount] = id }
    }

    suspend fun setDefaultType(type: TxnType) = context.settingsDataStore.edit { it[K.defaultType] = type.name }

    suspend fun setRememberCategory(remember: Boolean) = context.settingsDataStore.edit { it[K.rememberCategory] = remember }

    suspend fun setLastCategory(id: Long?) {
        context.settingsDataStore.edit { if (id == null) it.remove(K.lastCategory) else it[K.lastCategory] = id }
    }

    suspend fun setReminderTime(hour: Int, minute: Int): Unit { context.settingsDataStore.edit {
        it[K.reminderHour] = hour.coerceIn(0, 23)
        it[K.reminderMinute] = minute.coerceIn(0, 59)
    } }

    suspend fun setReminderFlags(
        budgetAlert: Boolean,
        billAlert: Boolean,
        dailyReminder: Boolean,
        weeklyReminder: Boolean,
        weeklyWeekday: Int,
        systemNotify: Boolean,
    ): Unit { context.settingsDataStore.edit {
        it[K.budgetAlert] = budgetAlert
        it[K.billAlert] = billAlert
        it[K.dailyReminder] = dailyReminder
        it[K.weeklyReminder] = weeklyReminder
        it[K.weeklyWeekday] = weeklyWeekday.coerceIn(0, 6)
        it[K.systemNotify] = systemNotify
    } }

    suspend fun setAi(endpoint: String, apiKey: String, model: String, temperature: Double, timeout: Int) {
        context.settingsDataStore.edit {
            it[K.aiEndpoint] = endpoint.trim()
            it[K.aiKey] = if (apiKey.isBlank()) it[K.aiKey] ?: "" else secretStore.encrypt(apiKey.trim().toByteArray(Charsets.UTF_8), AI_ALIAS)
            it[K.aiModel] = model.trim()
            it[K.aiTemperature] = temperature.toString()
            it[K.aiTimeout] = timeout.coerceIn(5, 120)
        }
    }

    suspend fun clearAiKey() = context.settingsDataStore.edit { it.remove(K.aiKey) }
    suspend fun setShowSign(show: Boolean) = context.settingsDataStore.edit { it[K.showSign] = show }

    suspend fun setBiometric(enabled: Boolean) = context.settingsDataStore.edit { it[K.biometric] = enabled }

    companion object {
        private const val AI_ALIAS = "jianzhang.ai.key.v1"
    }
}


package com.jianzhang.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.secretDataStore by preferencesDataStore(name = "jianzhang-secrets")

class DatabaseKeyManager(
    private val context: Context,
    private val secretStore: SecretStore,
) {
    private val key = stringPreferencesKey("database_passphrase")

    suspend fun passphrase(): ByteArray {
        val stored = context.secretDataStore.data.first()[key]
        if (stored != null) {
            return runCatching { secretStore.decrypt(stored, DB_ALIAS) }
                .getOrElse { throw IllegalStateException("数据库密钥无法解密，已停止启动以保护本地数据", it) }
        }
        val generated = secretStore.randomBytes(32)
        context.secretDataStore.edit { it[key] = secretStore.encrypt(generated, DB_ALIAS) }
        return generated
    }

    companion object {
        private const val DB_ALIAS = "jianzhang.database.key.v1"
    }
}
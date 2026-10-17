package com.jianzhang.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.data.BackupPasswordRequiredException
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.TxnType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
)

data class PendingEncryptedImport(
    val raw: String,
    val replace: Boolean,
)

private const val MAX_BACKUP_BYTES = 128 * 1024 * 1024

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val message = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val pendingImport = MutableStateFlow<PendingEncryptedImport?>(null)
    val importPasswordError = MutableStateFlow<String?>(null)
    private var pendingExportPassword: String? = null

    val state = combine(
        settingsStore.settings,
        repository.accounts,
        repository.categories,
    ) { settings, accounts, categories ->
        SettingsUiState(settings, accounts, categories, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: String) = run { viewModelScope.launch { settingsStore.setTheme(mode) } }
    fun setFontScale(scale: Float) = run { viewModelScope.launch { settingsStore.setFontScale(scale) } }
    fun setDefaultType(type: TxnType) = run { viewModelScope.launch { settingsStore.setDefaultType(type) } }
    fun setDefaultAccount(id: Long?) = run { viewModelScope.launch { settingsStore.setDefaultAccount(id) } }
    fun setRememberCategory(value: Boolean) = run { viewModelScope.launch { settingsStore.setRememberCategory(value) } }
    fun setShowSign(value: Boolean) = run { viewModelScope.launch { settingsStore.setShowSign(value) } }

    fun saveCategory(id: Long?, type: TxnType, name: String) {
        if (name.isBlank()) {
            error.value = "分类名称不能为空"
            return
        }
        viewModelScope.launch {
            runCatching { repository.saveCategory(id, type, name.trim(), "more-horizontal") }
                .onFailure { error.value = it.message ?: "分类保存失败" }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            runCatching { repository.deleteCategory(category.id) }
                .onSuccess { message.value = "分类已删除，相关流水变为未分类" }
                .onFailure { error.value = it.message ?: "分类删除失败" }
        }
    }

    fun saveAi(endpoint: String, apiKey: String, model: String, temperature: String, timeout: String) {
        val temp = temperature.toDoubleOrNull()?.coerceIn(0.0, 1.5) ?: 0.35
        val seconds = timeout.toIntOrNull()?.coerceIn(5, 120) ?: 90
        if (endpoint.isBlank() || model.isBlank()) {
            error.value = "接口地址与模型名称不能为空"
            return
        }
        viewModelScope.launch {
            runCatching { settingsStore.setAi(endpoint, apiKey, model, temp, seconds) }
                .onSuccess { message.value = "AI 配置已保存" }
                .onFailure { error.value = it.message ?: "AI 配置保存失败" }
        }
    }

    fun clearAiKey() {
        viewModelScope.launch {
            runCatching { settingsStore.clearAiKey() }
                .onSuccess { message.value = "API Key 已清除" }
                .onFailure { error.value = it.message ?: "清除失败" }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.resetAllData() }
                .onSuccess { message.value = "账本已清空" }
                .onFailure { error.value = it.message ?: "清空失败" }
            busy.value = false
        }
    }

    fun beginEncryptedExport(password: String) {
        pendingExportPassword = password
    }

    fun cancelEncryptedExport() {
        pendingExportPassword = null
    }

    fun completeEncryptedExport(uri: Uri) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (password == null) {
            error.value = "导出已取消，请重新操作"
            return
        }
        write(uri, "加密备份已导出") {
            repository.buildEncryptedBackup(password)
        }
    }

    fun exportBackup(uri: Uri) = write(uri, "明文备份已导出（未加密）") {
        repository.buildBackupJson()
    }

    fun exportCsv(uri: Uri) = write(uri, "CSV 已导出") { repository.buildCsv() }

    private fun write(
        uri: Uri,
        successMessage: String,
        build: suspend () -> String,
    ) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val content = build()
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入所选文件")
                }
            }.onSuccess {
                message.value = successMessage
            }.onFailure {
                error.value = it.message ?: "导出失败"
            }
            busy.value = false
        }
    }

    fun importBackup(uri: Uri, replace: Boolean) {
        pendingImport.value = null
        importPasswordError.value = null
        viewModelScope.launch {
            busy.value = true
            val raw = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BACKUP_BYTES) error("备份文件过大")
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray().toString(Charsets.UTF_8)
                    } ?: error("无法读取所选文件")
                }
            }.getOrElse {
                error.value = it.message ?: "无法读取所选文件"
                busy.value = false
                return@launch
            }
            busy.value = false
            processImport(raw, replace, password = null)
        }
    }

    fun submitImportPassword(password: String) {
        val pending = pendingImport.value ?: return
        if (password.isBlank()) {
            importPasswordError.value = "请输入备份口令"
            return
        }
        processImport(pending.raw, pending.replace, password)
    }

    fun cancelImport() {
        pendingImport.value = null
        importPasswordError.value = null
    }

    fun clearImportPasswordError() {
        importPasswordError.value = null
    }

    private fun processImport(raw: String, replace: Boolean, password: String?) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                withContext(Dispatchers.IO) { repository.importBackup(raw, replace, password) }
            }.onSuccess { result ->
                pendingImport.value = null
                importPasswordError.value = null
                message.value = buildString {
                    append("已导入 ${result.transactions} 笔流水、${result.categories} 个分类")
                    if (result.skipped > 0) append("，跳过 ${result.skipped} 条重复")
                }
            }.onFailure { failure ->
                if (failure is BackupPasswordRequiredException) {
                    pendingImport.value = PendingEncryptedImport(raw, replace)
                    importPasswordError.value = null
                } else if (password != null && pendingImport.value != null) {
                    importPasswordError.value = failure.message ?: "导入失败，请重试"
                } else {
                    error.value = failure.message ?: "导入失败，现有数据未被修改"
                }
            }
            busy.value = false
        }
    }

    fun clearMessage() { message.value = null }
    fun clearError() { error.value = null }
}





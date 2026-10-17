package com.jianzhang.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.designsystem.GlassPrimaryButton
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzOutlinedButton
import com.jianzhang.core.designsystem.MetricRow
import com.jianzhang.core.designsystem.PageHeader
import kotlinx.coroutines.launch

private enum class LockDialog { CHANGE, DISABLE, FORGOT, CLEAR_DATA }

@Composable
fun LockScreen(
    gateOnly: Boolean = false,
    onBack: (() -> Unit)? = null,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val settings = settingsState
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<LockDialog?>(null) }

    if (settings == null) {
        Box(
            Modifier.fillMaxSize().background(Jz.colors.bg),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Jz.colors.accent, strokeWidth = 2.dp)
        }
        return
    }

    val setup = !settings.lockEnabled
    val unlockMode = settings.lockEnabled && gateOnly

    Box(
        Modifier
            .fillMaxSize()
            .background(Jz.colors.bg)
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Jz.space.page),
        ) {
            if (onBack != null && !gateOnly) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Jz.colors.ink2)
                    }
                    PageHeader(
                        title = "应用锁",
                        subtitle = if (setup) "为本地账本加一道门" else "密码只保存在本机",
                        modifier = Modifier.weight(1f),
                    )
                }
                JzDivider()
            } else {
                Spacer(Modifier.height(if (gateOnly) 96.dp else 40.dp))
            }

            if (gateOnly) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Jz.colors.surface)
                            .border(1.dp, Jz.colors.lineHard, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Jz.colors.accent, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("简账已锁定", style = MaterialTheme.typography.headlineLarge, color = Jz.colors.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "输入本机密码后查看账目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Jz.colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(34.dp))
                }
            }

            when {
                setup -> SetupCard(
                    password = password,
                    confirm = confirm,
                    showPassword = showPassword,
                    busy = busy,
                    onPassword = { password = it },
                    onConfirm = { confirm = it },
                    onToggleShow = { showPassword = !showPassword },
                    onEnable = { viewModel.enable(password, confirm) },
                )
                unlockMode -> UnlockCard(
                    password = password,
                    showPassword = showPassword,
                    busy = busy,
                    onPassword = { password = it },
                    onToggleShow = { showPassword = !showPassword },
                    onUnlock = {
                        scope.launch {
                            if (viewModel.unlock(password)) password = ""
                        }
                    },
                    onForgot = { dialog = LockDialog.FORGOT },
                )
                else -> ManageCard(
                    onChange = { password = ""; confirm = ""; dialog = LockDialog.CHANGE },
                    onLockNow = viewModel::lockNow,
                    onDisable = { password = ""; dialog = LockDialog.DISABLE },
                    onClear = { dialog = LockDialog.CLEAR_DATA },
                )
            }

            errorText(error)
            Spacer(Modifier.height(48.dp))
        }
    }

    when (dialog) {
        LockDialog.CHANGE -> ChangePasswordDialog(
            busy = busy,
            onDismiss = { dialog = null },
            onSave = { current, next, again ->
                viewModel.changePassword(current, next, again)
                dialog = null
            },
        )
        LockDialog.DISABLE -> PasswordConfirmDialog(
            title = "关闭应用锁",
            body = "输入当前密码后关闭。关闭后打开简账不再要求密码。",
            busy = busy,
            onDismiss = { dialog = null },
            onConfirm = { pwd -> viewModel.disable(pwd); dialog = null },
        )
        LockDialog.FORGOT -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("密码无法找回") },
            text = {
                Text(
                    "应用锁密码不以明文保存，也没有云端找回通道。\n\n" +
                        "如果确实无法解锁，只能移除应用锁并清空全部本地账本数据。请确认你已不需要这些记录。",
                )
            },
            confirmButton = {
                TextButton(onClick = { dialog = LockDialog.CLEAR_DATA }) {
                    Text("清空并重置", color = Jz.colors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("返回") } },
        )
        LockDialog.CLEAR_DATA -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("清空全部数据？") },
            text = { Text("流水、账户、预算、标签、周期规则与应用锁都会被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetEverything(); dialog = null }) {
                    Text("确认清空", color = Jz.colors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
        )
        null -> Unit
    }

    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("完成") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("好") } },
        )
    }
}

@Composable
private fun SetupCard(
    password: String,
    confirm: String,
    showPassword: Boolean,
    busy: Boolean,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onToggleShow: () -> Unit,
    onEnable: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("设置解锁密码", style = MaterialTheme.typography.titleLarge, color = Jz.colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "至少 4 位。密码经 PBKDF2 派生后保存在本机，不上传、不参与数据库加密。",
            style = MaterialTheme.typography.bodyMedium,
            color = Jz.colors.ink2,
        )
        Spacer(Modifier.height(20.dp))
        PasswordField("密码", password, onPassword, showPassword, onToggleShow)
        Spacer(Modifier.height(12.dp))
        PasswordField("再次输入", confirm, onConfirm, showPassword, onToggleShow)
        Spacer(Modifier.height(22.dp))
        GlassPrimaryButton("启用应用锁", onEnable, Modifier.fillMaxWidth(), enabled = !busy)
    }
}

@Composable
private fun UnlockCard(
    password: String,
    showPassword: Boolean,
    busy: Boolean,
    onPassword: (String) -> Unit,
    onToggleShow: () -> Unit,
    onUnlock: () -> Unit,
    onForgot: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PasswordField("输入密码", password, onPassword, showPassword, onToggleShow)
        Spacer(Modifier.height(18.dp))
        GlassPrimaryButton(if (busy) "正在校验" else "解锁", onUnlock, Modifier.fillMaxWidth(), enabled = !busy)
        Spacer(Modifier.height(14.dp))
        Text(
            "忘记密码？",
            style = MaterialTheme.typography.labelLarge,
            color = Jz.colors.ink3,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onForgot).padding(8.dp),
        )
    }
}

@Composable
private fun ManageCard(
    onChange: () -> Unit,
    onLockNow: () -> Unit,
    onDisable: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Jz.colors.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("应用锁已开启", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                Spacer(Modifier.height(3.dp))
                Text("每次冷启动都需要密码", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
            }
            Text("ON", style = MaterialTheme.typography.labelSmall, color = Jz.colors.income, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(18.dp))
        MetricRow("密码校验", "PBKDF2 · 310,000 次")
        MetricRow("存储位置", "仅本机")
        Spacer(Modifier.height(18.dp))
        GlassPrimaryButton("修改密码", onChange, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        JzOutlinedButton("立即锁定", onLockNow, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        JzOutlinedButton("关闭应用锁", onDisable, Modifier.fillMaxWidth())
        Spacer(Modifier.height(22.dp))
        Text(
            "清空全部数据",
            color = Jz.colors.expense,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClear).padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    show: Boolean,
    onToggleShow: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(64)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = onToggleShow?.let { toggle ->
            {
                IconButton(onClick = toggle) {
                    Icon(
                        if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (show) "隐藏密码" else "显示密码",
                        tint = Jz.colors.ink3,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChangePasswordDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                PasswordField("当前密码", current, { current = it }, false)
                Spacer(Modifier.height(10.dp))
                PasswordField("新密码", next, { next = it }, false)
                Spacer(Modifier.height(10.dp))
                PasswordField("再次输入新密码", confirm, { confirm = it }, false)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(current, next, confirm) }, enabled = !busy) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PasswordConfirmDialog(
    title: String,
    body: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink2)
                Spacer(Modifier.height(12.dp))
                PasswordField("当前密码", password, { password = it }, false)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !busy) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun errorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(14.dp))
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = Jz.colors.expense,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}


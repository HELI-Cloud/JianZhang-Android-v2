package com.jianzhang.feature.planning

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzOutlinedButton
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.ReminderItem
import com.jianzhang.core.model.ReminderLevel

private val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun ReminderScreen(
    onBack: () -> Unit = {},
    onOpenRecord: () -> Unit = {},
    viewModel: PlanningViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    var showTime by remember { mutableStateOf(false) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Jz.colors.ink2)
                    }
                    PageHeader("提醒", "在需要的时候提醒，而不是一直打扰", modifier = Modifier.weight(1f))
                }
                JzDivider()
            }
        }
        if (settings.systemNotify && !notificationsGranted) {
            item {
                Column(Modifier.padding(horizontal = Jz.space.page)) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Jz.colors.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("通知权限未开启", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "系统通知开关已打开，但 Android 尚未允许简账发送通知。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Jz.colors.ink3,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        JzOutlinedButton("去开启", onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        })
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("提醒范围")
                FlagLine("预算预警", "接近或超过本月预算时提示", settings.budgetAlert) {
                    viewModel.saveReminderSettings(settings.copy(budgetAlert = it))
                }
                FlagLine("周期账单", "固定收支到期前提示", settings.billAlert) {
                    viewModel.saveReminderSettings(settings.copy(billAlert = it))
                }
                FlagLine("每日记账", "当天没有流水时提示", settings.dailyReminder) {
                    viewModel.saveReminderSettings(settings.copy(dailyReminder = it))
                }
                FlagLine("每周小结", "每周固定一天回顾账本", settings.weeklyReminder) {
                    viewModel.saveReminderSettings(settings.copy(weeklyReminder = it))
                }
                FlagLine("系统通知", "通过通知栏送达提醒", settings.systemNotify) {
                    viewModel.saveReminderSettings(settings.copy(systemNotify = it))
                }
                Row(
                    Modifier.fillMaxWidth().clickable { showTime = true }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("提醒时间", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                        Text("每天在这个时间检查", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
                    }
                    Text(
                        "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                        style = MaterialTheme.typography.titleLarge,
                        color = Jz.colors.accent,
                    )
                }
                JzDivider()
                if (settings.weeklyReminder) {
                    SectionTitle("每周小结日")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weekLabels.forEachIndexed { index, label ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (weekdayIndex(settings.weeklyReminderWeekday) == index) Jz.colors.onAccent else Jz.colors.ink2,
                                modifier = Modifier
                                    .background(
                                        if (weekdayIndex(settings.weeklyReminderWeekday) == index) Jz.colors.accent else Jz.colors.surface,
                                    )
                                    .clickable {
                                        viewModel.saveReminderSettings(
                                            settings.copy(weeklyReminderWeekday = (index + 1) % 7),
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                SectionTitle("待处理", right = if (state.reminders.isEmpty()) null else "${state.reminders.size} 条")
            }
        }
        if (state.reminders.isEmpty()) {
            item {
                EmptyState(
                    title = "暂时没有提醒",
                    body = "预算、周期账单和记账节奏都在正常范围内。",
                    modifier = Modifier.padding(horizontal = Jz.space.page),
                )
            }
        } else {
            items(state.reminders.size) { index ->
                val item = state.reminders[index]
                ReminderCard(
                    item = item,
                    onDismiss = { viewModel.dismiss(item.key) },
                    onConfirm = { item.ruleId?.let(viewModel::confirmRecurring) },
                    onOpen = {
                        if (item.route == "record") onOpenRecord()
                    },
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page, vertical = 16.dp)) {
                JzOutlinedButton("恢复已忽略的提醒", onClick = viewModel::clearDismissed)
            }
        }
    }

    if (showTime) {
        TimeDialog(
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
            onDismiss = { showTime = false },
            onSave = { h, m ->
                viewModel.saveReminderTime(h, m)
                showTime = false
            },
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("操作未完成") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } },
        )
    }
}

private fun weekdayIndex(stored: Int): Int = if (stored == 0) 6 else (stored - 1).coerceIn(0, 6)

@Composable
private fun FlagLine(label: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    JzDivider()
}

@Composable
private fun ReminderCard(
    item: ReminderItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onOpen: () -> Unit,
) {
    val levelColor = when (item.level) {
        ReminderLevel.DANGER -> Jz.colors.expense
        ReminderLevel.WARN -> Jz.colors.accent
        ReminderLevel.INFO -> Jz.colors.income
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Jz.space.page, vertical = 10.dp)
            .background(Jz.colors.surface),
    ) {
        Box(Modifier.width(3.dp).height(74.dp).background(levelColor))
        Column(Modifier.weight(1f).padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(item.body, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink2)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (item.confirmable) {
                    Text(
                        "确认入账",
                        style = MaterialTheme.typography.labelLarge,
                        color = Jz.colors.accent,
                        modifier = Modifier.clickable(onClick = onConfirm),
                    )
                }
                if (item.route == "record") {
                    Text(
                        "去记一笔",
                        style = MaterialTheme.typography.labelLarge,
                        color = Jz.colors.accent,
                        modifier = Modifier.clickable(onClick = onOpen),
                    )
                }
                Text(
                    "忽略",
                    style = MaterialTheme.typography.labelLarge,
                    color = Jz.colors.ink3,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }
    }
}

@Composable
private fun TimeDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var h by remember { mutableStateOf(hour.toString()) }
    var m by remember { mutableStateOf(minute.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒时间") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = h,
                    onValueChange = { h = it.filter(Char::isDigit).take(2) },
                    label = { Text("小时") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = m,
                    onValueChange = { m = it.filter(Char::isDigit).take(2) },
                    label = { Text("分钟") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(h.toIntOrNull()?.coerceIn(0, 23) ?: 20, m.toIntOrNull()?.coerceIn(0, 59) ?: 0)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

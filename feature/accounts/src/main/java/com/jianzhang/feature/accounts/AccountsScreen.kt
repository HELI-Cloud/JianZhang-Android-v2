package com.jianzhang.feature.accounts

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.common.Money
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.AccountBalance
import com.jianzhang.core.model.AccountKind
import com.jianzhang.core.model.Transfer
import java.time.LocalDate

@Composable
fun AccountsScreen(
    onBack: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Account?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var transferOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Account?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                PageHeader(
                    "账户",
                    "资产、负债与资金流转",
                    trailing = {
                        Row {
                            IconButton(onClick = { transferOpen = true }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "转账", tint = Jz.colors.accent)
                            }
                            IconButton(onClick = { addOpen = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "新增账户", tint = Jz.colors.accent)
                            }
                        }
                    },
                )
                NetWorthPanel(state.netWorth.assetsCents, state.netWorth.liabilitiesCents, state.netWorth.totalCents)
                SectionTitle("账户", "${state.balances.size} 个")
            }
        }
        items(state.balances.size) { index ->
            val item = state.balances[index]
            AccountRow(
                item = item,
                onEdit = { editing = item.account },
                onArchive = { viewModel.archiveAccount(item.account, !item.account.archived) },
                onDelete = { deleting = item.account },
            )
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("本月转账", "不计入收支")
                if (state.transfers.isEmpty()) {
                    EmptyState("暂无转账", "把资金从一个账户挪到另一个账户。")
                } else {
                    state.transfers.forEach { transfer ->
                        TransferRow(transfer, state.balances, onDelete = { viewModel.deleteTransfer(transfer.id) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (addOpen || editing != null) {
        AccountDialog(
            account = editing,
            onDismiss = { addOpen = false; editing = null },
            onSave = { name, kind, initial, color ->
                viewModel.saveAccount(editing?.id, name, kind, initial, color, "wallet")
                addOpen = false
                editing = null
            },
        )
    }
    if (transferOpen) {
        TransferDialog(
            accounts = state.balances.map { it.account },
            onDismiss = { transferOpen = false },
            onSave = { from, to, amount, fee, note, date ->
                viewModel.saveTransfer(null, from, to, amount, fee, note, date)
                transferOpen = false
            },
        )
    }
    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除账户") },
            text = { Text("删除“${account.name}”后，关联流水与转账会转移到其他账户。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(account)
                    deleting = null
                }) { Text("删除", color = Jz.colors.expense) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
    error?.let {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("操作未完成") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } },
        )
    }
}

@Composable
private fun NetWorthPanel(assets: Long, liabilities: Long, total: Long) {
    Column(Modifier.fillMaxWidth().background(Jz.colors.surface).padding(16.dp)) {
        Text("净资产", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
        Text(
            "¥${Money.format(total)}",
            style = MaterialTheme.typography.displayMedium,
            color = Jz.colors.ink,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("资产 ¥${Money.format(assets)}", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.income)
            Text("负债 ¥${Money.format(liabilities)}", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.expense)
        }
    }
}

@Composable
private fun AccountRow(
    item: AccountBalance,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = Jz.space.page, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(8.dp).height(36.dp).clip(CircleShape)
                .background(runCatching { Color(android.graphics.Color.parseColor(item.account.color)) }.getOrElse { Jz.colors.accent }),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.account.name, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                if (item.account.archived) {
                    Spacer(Modifier.width(6.dp))
                    Text("已归档", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                }
            }
            Text(item.account.kind.label, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
        }
        Text(
            "¥${Money.format(item.balanceCents)}",
            style = MaterialTheme.typography.titleMedium,
            color = if (item.balanceCents < 0) Jz.colors.expense else Jz.colors.ink,
            fontFamily = FontFamily.Monospace,
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = Jz.colors.ink3)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(if (item.account.archived) "恢复" else "归档") }, onClick = { menu = false; onArchive() })
                DropdownMenuItem(
                    text = { Text("删除", color = Jz.colors.expense) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
    JzDivider()
}

@Composable
private fun TransferRow(transfer: Transfer, balances: List<AccountBalance>, onDelete: () -> Unit) {
    val from = balances.firstOrNull { it.account.id == transfer.fromAccountId }?.account?.name ?: "账户"
    val to = balances.firstOrNull { it.account.id == transfer.toAccountId }?.account?.name ?: "账户"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Jz.space.page, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$from → $to", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
            Text(
                "${transfer.date}  ${transfer.note.ifBlank { "转账" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = Jz.colors.ink3,
            )
        }
        Text(
            "¥${Money.format(transfer.amountCents)}",
            style = MaterialTheme.typography.bodyLarge,
            color = Jz.colors.ink2,
            fontFamily = FontFamily.Monospace,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除转账", tint = Jz.colors.ink3)
        }
    }
    JzDivider()
}

@Composable
private fun AccountDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (String, AccountKind, Long, String) -> Unit,
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var initial by remember { mutableStateOf(if (account == null) "" else Money.format(account.initialCents)) }
    var kind by remember { mutableStateOf(account?.kind ?: AccountKind.CASH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新增账户" else "编辑账户") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("账户名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = initial, onValueChange = { initial = it }, label = { Text("初始余额") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccountKind.entries.take(4).forEach {
                        Text(
                            it.label,
                            modifier = Modifier
                                .background(if (kind == it) Jz.colors.accent else Jz.colors.surface)
                                .clickable { kind = it }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            color = if (kind == it) Jz.colors.onAccent else Jz.colors.ink2,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name, kind, Money.parseToCents(initial) ?: 0L, account?.color ?: "#E9B44C")
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TransferDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Long, Long, String, LocalDate) -> Unit,
) {
    var from by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var to by remember { mutableStateOf(accounts.getOrNull(1)?.id) }
    var amount by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账户转账") },
        text = {
            Column {
                Text("转出", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    accounts.filter { !it.archived }.take(4).forEach {
                        Text(
                            it.name,
                            modifier = Modifier
                                .background(if (from == it.id) Jz.colors.accent else Jz.colors.surface)
                                .clickable { from = it.id }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            color = if (from == it.id) Jz.colors.onAccent else Jz.colors.ink2,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("转入", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    accounts.filter { !it.archived }.take(4).forEach {
                        Text(
                            it.name,
                            modifier = Modifier
                                .background(if (to == it.id) Jz.colors.accent else Jz.colors.surface)
                                .clickable { to = it.id }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            color = if (to == it.id) Jz.colors.onAccent else Jz.colors.ink2,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = fee, onValueChange = { fee = it }, label = { Text("手续费") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = from
                val t = to
                if (f != null && t != null) {
                    onSave(f, t, Money.parseToCents(amount) ?: 0L, Money.parseToCents(fee) ?: 0L, note, LocalDate.now())
                }
            }) { Text("确认转账") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


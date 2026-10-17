package com.jianzhang.feature.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.jianzhang.core.common.Dates
import com.jianzhang.core.common.Money
import com.jianzhang.core.designsystem.AmountText
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.Transfer
import com.jianzhang.core.model.TxnType
import com.jianzhang.core.model.TxnWithCategory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entries = viewModel.entries.collectAsLazyPagingItems()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var bulkCategory by remember { mutableStateOf(false) }
    var bulkTag by remember { mutableStateOf(false) }
    var bulkAccount by remember { mutableStateOf(false) }
    var deletingTransfer by remember { mutableStateOf<Transfer?>(null) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Jz.space.page)) {
            PageHeader(
                title = "流水",
                subtitle = if (state.totalCount == 0) "本月还没有记录" else "本月 ${state.totalCount} 笔 · 支出 ${Money.format(state.expense)}",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ChevronLeft,
                            contentDescription = "上个月",
                            tint = Jz.colors.ink3,
                            modifier = Modifier.size(22.dp).clickable(onClick = viewModel::previousMonth),
                        )
                        Text(
                            Dates.cnMonth(state.month),
                            style = MaterialTheme.typography.labelLarge,
                            color = Jz.colors.ink2,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = "下个月",
                            tint = if (state.month < java.time.YearMonth.now()) Jz.colors.ink3 else Jz.colors.ink.copy(alpha = 0.18f),
                            modifier = Modifier.size(22.dp).clickable(
                                enabled = state.month < java.time.YearMonth.now(),
                                onClick = viewModel::nextMonth,
                            ),
                        )
                    }
                },
            )
            TypeFilter(state.type, viewModel::setType)
            if (state.accounts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AccountFilter(
                    accounts = state.accounts,
                    selected = state.accountId,
                    onSelect = viewModel::setAccount,
                )
            }
            if (state.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                TagFilter(
                    tags = state.tags,
                    selected = state.tagId,
                    onSelect = viewModel::setTag,
                )
            }
            Spacer(Modifier.height(10.dp))
            SearchBox(state.query, viewModel::setQuery)
            Spacer(Modifier.height(8.dp))
        }

        if (selected.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Jz.colors.surface).padding(start = Jz.space.page, end = Jz.space.page, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已选 ${selected.size} 笔", color = Jz.colors.ink2, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BulkAction("改分类", Jz.colors.accent) { bulkCategory = true }
                    BulkAction("改账户", Jz.colors.accent) { bulkAccount = true }
                    BulkAction("加标签", Jz.colors.accent) { bulkTag = true }
                    BulkAction("删除", Jz.colors.expense) { scope.launch { viewModel.deleteSelected() } }
                    BulkAction("取消", Jz.colors.ink3) { viewModel.clearSelection() }
                }
            }
        }

        when {
            entries.itemCount == 0 && entries.loadState.refresh is LoadState.Loading && state.transfers.isEmpty() -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Jz.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            }
            entries.itemCount == 0 && entries.loadState.refresh is LoadState.Error && state.transfers.isEmpty() -> {
                Box(
                    Modifier.fillMaxWidth().weight(1f).clickable { entries.retry() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("流水加载失败，点击重试", color = Jz.colors.expense, style = MaterialTheme.typography.bodyMedium)
                }
            }
            entries.itemCount == 0 && entries.loadState.refresh is LoadState.NotLoading && state.transfers.isEmpty() -> {
                EmptyState("没有匹配的流水", "换个月份或筛选条件试试")
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                if (state.transfers.isNotEmpty() && state.type == null && state.accountId == null && state.tagId == null && state.query.isBlank()) {
                    item(key = "transfer-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = Jz.space.page, end = Jz.space.page, top = 16.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "本月转账",
                                style = MaterialTheme.typography.labelSmall,
                                color = Jz.colors.ink3,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${state.transfers.size} 笔", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                        }
                        JzDivider()
                    }
                    items(state.transfers, key = { "t-${it.id}" }) { transfer ->
                        TransferRow(
                            transfer = transfer,
                            accounts = state.accounts,
                            onDelete = { deletingTransfer = transfer },
                        )
                    }
                }
                items(count = entries.itemCount) { index ->
                    when (val entry = entries[index]) {
                        is LedgerEntry.DateHeader -> {
                            val date = entry.date
                            val sum = state.dailyExpense[Dates.iso(date)] ?: 0L
                            Row(
                                Modifier.fillMaxWidth().padding(start = Jz.space.page, end = Jz.space.page, top = 18.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    Dates.iso(date).substring(5).replace("-", " / "),
                                    color = Jz.colors.ink,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(Dates.weekday(date), color = Jz.colors.ink3, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (sum > 0) {
                                    Text("-${Money.format(sum)}", color = Jz.colors.expense, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                }
                            }
                            JzDivider()
                        }
                        is LedgerEntry.Row -> {
                            val row = entry.value
                            LedgerRow(
                                row = row,
                                selected = row.txn.id in selected,
                                onClick = { onEdit(row.txn.id) },
                                onLongClick = { viewModel.toggle(row.txn.id) },
                            )
                        }
                        null -> Unit
                    }
                }
                when (val append = entries.loadState.append) {
                    is LoadState.Loading -> item(key = "paging-loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Jz.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                    is LoadState.Error -> item(key = "paging-error") {
                        Box(
                            Modifier.fillMaxWidth().clickable { entries.retry() }.padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("加载更多失败，点击重试", color = Jz.colors.expense, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> Unit
                }
            }
        }
        }
    }

    if (bulkCategory) {
        ChoiceDialog(
            title = "批量改分类",
            options = state.categories
                .filter { state.type == null || it.type == state.type }
                .map { it.id to it.name },
            includeNone = true,
            noneLabel = "设为未分类",
            onDismiss = { bulkCategory = false },
            onPick = { id ->
                bulkCategory = false
                scope.launch { viewModel.setCategoryForSelected(id) }
            },
        )
    }
    if (bulkAccount) {
        ChoiceDialog(
            title = "批量改账户",
            options = state.accounts.map { it.id to it.name },
            includeNone = true,
            noneLabel = "设为无账户",
            onDismiss = { bulkAccount = false },
            onPick = { id ->
                bulkAccount = false
                scope.launch { viewModel.setAccountForSelected(id) }
            },
        )
    }
    if (bulkTag) {
        ChoiceDialog(
            title = "批量加标签",
            options = state.tags.map { it.id to it.name },
            includeNone = false,
            noneLabel = "",
            onDismiss = { bulkTag = false },
            onPick = { id ->
                bulkTag = false
                if (id != null) scope.launch { viewModel.addTagToSelected(id) }
            },
        )
    }
    deletingTransfer?.let { transfer ->
        AlertDialog(
            onDismissRequest = { deletingTransfer = null },
            title = { Text("删除转账") },
            text = { Text("转账记录会被删除，账户余额会随之恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.deleteTransfer(transfer.id) }
                    deletingTransfer = null
                }) { Text("删除", color = Jz.colors.expense) }
            },
            dismissButton = { TextButton(onClick = { deletingTransfer = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun BulkAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun AccountFilter(accounts: List<Account>, selected: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Chip("全部账户", selected == null) { onSelect(null) }
        }
        items(accounts, key = { it.id }) { account ->
            Chip(account.name, selected == account.id) { onSelect(account.id) }
        }
    }
}

@Composable
private fun TagFilter(tags: List<Tag>, selected: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Chip("全部标签", selected == null) { onSelect(null) }
        }
        items(tags, key = { it.id }) { tag ->
            Chip(tag.name, selected == tag.id) { onSelect(tag.id) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Jz.colors.onAccent else Jz.colors.ink2,
        modifier = Modifier
            .background(if (active) Jz.colors.accent else Jz.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<Long, String>>,
    includeNone: Boolean,
    noneLabel: String,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                if (includeNone) {
                    item { ChoiceItem(noneLabel) { onPick(null) } }
                }
                items(options, key = { it.first }) { option ->
                    ChoiceItem(option.second) { onPick(option.first) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChoiceItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = Jz.colors.ink,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
    JzDivider()
}

@Composable
private fun TransferRow(transfer: Transfer, accounts: List<Account>, onDelete: () -> Unit) {
    val from = accounts.firstOrNull { it.id == transfer.fromAccountId }?.name ?: "账户"
    val to = accounts.firstOrNull { it.id == transfer.toAccountId }?.name ?: "账户"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onDelete).padding(horizontal = Jz.space.page, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$from → $to", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
            Text(
                listOfNotNull(Dates.iso(transfer.date), transfer.note.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Jz.colors.ink3,
            )
        }
        Text(
            Money.format(transfer.amountCents),
            color = Jz.colors.ink2,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    JzDivider()
}

@Composable
private fun TypeFilter(type: TxnType?, onChange: (TxnType?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, Jz.colors.line).background(Jz.colors.track).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FilterCell("全部", type == null, Modifier.weight(1f)) { onChange(null) }
        FilterCell("支出", type == TxnType.EXPENSE, Modifier.weight(1f)) { onChange(TxnType.EXPENSE) }
        FilterCell("收入", type == TxnType.INCOME, Modifier.weight(1f)) { onChange(TxnType.INCOME) }
    }
}

@Composable
private fun FilterCell(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) Jz.colors.surface else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Jz.colors.ink else Jz.colors.ink3, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Jz.colors.surface).border(1.dp, Jz.colors.line).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = Jz.colors.ink3, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("搜索分类、账户、标签、备注", color = Jz.colors.ink3, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Jz.colors.ink),
                cursorBrush = SolidColor(Jz.colors.accent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "清除",
                tint = Jz.colors.ink3,
                modifier = Modifier.size(18.dp).clickable { onChange("") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerRow(
    row: TxnWithCategory,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Jz.colors.surface else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Jz.space.page, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeOf(row.txn.createdAt),
                color = Jz.colors.ink3,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(48.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    row.txn.note.ifBlank { row.categoryName },
                    color = Jz.colors.ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    listOfNotNull(row.categoryName, row.accountName).joinToString(" · "),
                    color = Jz.colors.ink3,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
            AmountText(row.txn.amountCents, row.txn.type)
        }
        Spacer(Modifier.height(8.dp))
        JzDivider()
    }
}

private fun timeOf(createdAt: String): String = runCatching {
    Instant.parse(createdAt).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
}.getOrDefault("")


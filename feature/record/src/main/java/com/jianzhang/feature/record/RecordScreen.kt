package com.jianzhang.feature.record

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.common.Dates
import com.jianzhang.core.common.Money
import com.jianzhang.core.designsystem.GlassPrimaryButton
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.TxnType
import java.time.LocalDate

@Composable
fun RecordScreen(
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var accountMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEvent.Saved -> onSaved(event.id)
                is RecordEvent.Failed -> Unit
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Jz.colors.bg),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "关闭",
                tint = Jz.colors.ink2,
                modifier = Modifier.size(24.dp).clickable(onClick = onClose),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                if (state.editing) "编辑记录" else "记一笔",
                style = MaterialTheme.typography.titleLarge,
                color = Jz.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (state.editing) "保存修改" else "保存",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.canSave) Jz.colors.accent else Jz.colors.ink3,
                modifier = Modifier.clickable(enabled = state.canSave) { viewModel.save() },
            )
        }
        JzDivider(hard = true)
        TypeSwitch(state.type, viewModel::setType)
        AmountPanel(state.amountText, state.type)
        if (state.error != null) {
            Text(
                state.error.orEmpty(),
                color = Jz.colors.expense,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            item {
                Text("分类", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                Spacer(Modifier.height(9.dp))
            }
            items(state.visibleCategories.chunked(4)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { category ->
                        CategoryCell(
                            category = category,
                            selected = category.id == state.categoryId,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.chooseCategory(category.id) },
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            item {
                Spacer(Modifier.height(4.dp))
                JzDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Row(
                            Modifier.clickable { accountMenu = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Wallet, contentDescription = null, tint = Jz.colors.ink3, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "未选择账户",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Jz.colors.ink2,
                            )
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = Jz.colors.ink3, modifier = Modifier.size(18.dp))
                        }
                        AccountMenu(
                            expanded = accountMenu,
                            accounts = state.accounts,
                            selectedId = state.accountId,
                            onDismiss = { accountMenu = false },
                            onChoose = { id -> viewModel.chooseAccount(id); accountMenu = false },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val dateLabel = if (state.date == LocalDate.now()) "今天" else Dates.cnDate(state.date)
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Jz.colors.accent,
                        modifier = Modifier.clickable {
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> viewModel.chooseDate(LocalDate.of(year, month + 1, day)) },
                                state.date.year,
                                state.date.monthValue - 1,
                                state.date.dayOfMonth,
                            ).show()
                        },
                    )
                }
                JzDivider()
                Spacer(Modifier.height(10.dp))
                NoteField(state.note, viewModel::setNote)
                if (state.tags.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("标签", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.tags, key = { it.id }) { tag ->
                            TagChip(tag = tag, selected = tag.id in state.selectedTagIds) {
                                viewModel.toggleTag(tag.id)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Keypad(
            onKey = viewModel::appendKey,
            onBackspace = viewModel::backspace,
            onClear = viewModel::clearAmount,
            onSave = viewModel::save,
            canSave = state.canSave,
            saving = state.saving,
        )
    }
}

@Composable
private fun TypeSwitch(type: TxnType, onType: (TxnType) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TypeItem("支出", type == TxnType.EXPENSE, Jz.colors.expense) { onType(TxnType.EXPENSE) }
        TypeItem("收入", type == TxnType.INCOME, Jz.colors.income) { onType(TxnType.INCOME) }
        Spacer(Modifier.weight(1f))
        Text(
            if (type == TxnType.EXPENSE) "OUT" else "IN",
            style = MaterialTheme.typography.labelSmall,
            color = Jz.colors.ink3,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TypeItem(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) color else Jz.colors.ink3,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(24.dp)
                .height(2.dp)
                .background(if (selected) color else Color.Transparent),
        )
    }
}

@Composable
private fun AmountPanel(amountText: String, type: TxnType) {
    val color = if (type == TxnType.EXPENSE) Jz.colors.expense else Jz.colors.income
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("金额", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("¥", color = color, fontSize = 23.sp, modifier = Modifier.padding(bottom = 5.dp, end = 5.dp))
            Text(
                if (amountText.isBlank()) "0.00" else amountText,
                color = if (amountText.isBlank()) Jz.colors.ink3 else Jz.colors.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 42.sp,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(2.dp).height(38.dp).background(color).padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            Money.format(Money.parseToCents(amountText) ?: 0, false) + " 元",
            style = MaterialTheme.typography.bodyMedium,
            color = Jz.colors.ink3,
            fontFamily = FontFamily.Monospace,
        )
    }
    JzDivider()
}

@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(if (selected) Jz.colors.surfaceRaised else Jz.colors.surface)
            .border(1.dp, if (selected) Jz.colors.accent.copy(alpha = 0.72f) else Jz.colors.line)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            iconFor(category.icon),
            contentDescription = null,
            tint = if (selected) Jz.colors.accent else Jz.colors.ink2,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Jz.colors.ink else Jz.colors.ink2,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountMenu(
    expanded: Boolean,
    accounts: List<Account>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onChoose: (Long?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("不指定账户") },
            onClick = { onChoose(null) },
            trailingIcon = { if (selectedId == null) Text("✓", color = Jz.colors.accent) },
        )
        accounts.forEach { account ->
            DropdownMenuItem(
                text = { Text(account.name) },
                onClick = { onChoose(account.id) },
                trailingIcon = { if (selectedId == account.id) Text("✓", color = Jz.colors.accent) },
            )
        }
    }
}

@Composable
private fun NoteField(note: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("备注", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3, modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f)) {
            if (note.isEmpty()) {
                Text("写点什么…（40字以内）", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
            }
            BasicTextField(
                value = note,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Jz.colors.ink),
                cursorBrush = SolidColor(Jz.colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("${note.length}/40", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
    }
}

@Composable
private fun TagChip(tag: Tag, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, if (selected) Jz.colors.accent else Jz.colors.line)
            .background(if (selected) Jz.colors.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tag.name, style = MaterialTheme.typography.bodyMedium, color = if (selected) Jz.colors.accent else Jz.colors.ink2)
    }
}

@Composable
private fun Keypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    saving: Boolean,
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Jz.colors.surface.copy(alpha = 0.96f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key == "⌫") {
                        KeypadKey(
                            label = key,
                            modifier = Modifier.weight(1f),
                            onClick = onBackspace,
                            onLongClick = onClear,
                        )
                    } else {
                        KeypadKey(label = key, modifier = Modifier.weight(1f), onClick = { onKey(key) })
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        GlassPrimaryButton(
            text = if (saving) "保存中…" else "记入",
            onClick = onSave,
            enabled = canSave,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .height(48.dp)
            .background(Jz.colors.surfaceRaised)
            .border(1.dp, Jz.colors.line)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label == "⌫") {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "删除", tint = Jz.colors.ink2, modifier = Modifier.size(20.dp))
        } else {
            Text(
                label,
                color = Jz.colors.ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun iconFor(name: String): ImageVector = when (name) {
    "utensils" -> Icons.Outlined.Restaurant
    "car" -> Icons.Outlined.DirectionsCar
    "shopping-bag" -> Icons.Outlined.ShoppingBag
    "home" -> Icons.Outlined.Home
    "gamepad-2" -> Icons.Outlined.SportsEsports
    "heart-pulse" -> Icons.Outlined.LocalHospital
    "graduation-cap" -> Icons.Outlined.School
    "gift" -> Icons.Outlined.CardGiftcard
    "briefcase" -> Icons.Outlined.Work
    "medal" -> Icons.Outlined.EmojiEvents
    "trending-up" -> Icons.AutoMirrored.Outlined.TrendingUp
    "laptop" -> Icons.Outlined.Laptop
    "wallet" -> Icons.Outlined.Wallet
    "bank" -> Icons.Outlined.AccountBalance
    else -> Icons.Outlined.MoreHoriz
}
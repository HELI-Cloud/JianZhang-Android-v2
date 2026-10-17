package com.jianzhang.feature.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.designsystem.EmptyState
import com.jianzhang.core.designsystem.GlassPrimaryButton
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzOutlinedButton
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.TagUsage

private val palette = listOf(
    "#E9B44C", "#4FD1A5", "#FF7A5A", "#7AA2FF",
    "#C084FC", "#F472B6", "#38BDF8", "#94A3B8",
)

private fun parseColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFFE9B44C))

@Composable
fun TagsScreen(
    onBack: () -> Unit = {},
    viewModel: PlanningViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TagUsage?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TagUsage?>(null) }
    val error by viewModel.error.collectAsStateWithLifecycle()

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
                    PageHeader("标签", "给流水加上你自己的维度", modifier = Modifier.weight(1f))
                }
                JzDivider()
            }
        }
        if (state.tags.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有标签",
                    body = "旅行、报销、某个人情往来，标签可以跨分类把流水串起来。",
                    modifier = Modifier.padding(horizontal = Jz.space.page),
                    action = { JzOutlinedButton("新建标签", onClick = { creating = true }) },
                )
            }
        } else {
            items(state.tags.size) { index ->
                val usage = state.tags[index]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Jz.space.page, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(parseColor(usage.tag.color)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(usage.tag.name, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                        Text(
                            "已用于 ${usage.usageCount} 笔流水",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jz.colors.ink3,
                        )
                    }
                    IconButton(onClick = { editing = usage }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Jz.colors.ink3)
                    }
                    IconButton(onClick = { deleting = usage }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Jz.colors.ink3)
                    }
                }
                JzDivider()
            }
        }
        item {
            Column(Modifier.padding(horizontal = Jz.space.page, vertical = 18.dp)) {
                GlassPrimaryButton("新建标签", onClick = { creating = true })
            }
        }
    }

    if (creating || editing != null) {
        TagEditorDialog(
            tag = editing?.tag,
            onDismiss = { creating = false; editing = null },
            onSave = { id, name, color ->
                viewModel.saveTag(id, name, color)
                creating = false
                editing = null
            },
        )
    }
    deleting?.let { usage ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除标签") },
            text = { Text("删除「${usage.tag.name}」不会删除流水，只解除标签关联。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(usage.tag.id)
                    deleting = null
                }) { Text("删除", color = Jz.colors.expense) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
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

@Composable
private fun TagEditorDialog(
    tag: Tag?,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(tag?.name ?: "") }
    var color by remember { mutableStateOf(tag?.color ?: palette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "新建标签" else "编辑标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("颜色", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { value ->
                        val selected = color.equals(value, ignoreCase = true)
                        Spacer(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(parseColor(value))
                                .then(
                                    if (selected) Modifier.border(2.dp, Jz.colors.ink, CircleShape) else Modifier,
                                )
                                .clickable { color = value },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(tag?.id, name.trim(), color) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

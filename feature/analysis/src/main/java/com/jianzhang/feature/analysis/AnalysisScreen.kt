package com.jianzhang.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianzhang.core.common.Dates
import com.jianzhang.core.designsystem.GlassPrimaryButton
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzOutlinedButton
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle
import com.jianzhang.core.model.AiFocus
import com.jianzhang.core.model.AiRange

@Composable
fun AnalysisScreen(
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 46.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Jz.colors.ink2)
                    }
                    PageHeader("AI 分析", "只发送汇总数据，不发送账户名", modifier = Modifier.weight(1f))
                }
                JzDivider()
            }
        }

        if (settings.aiApiKey.isBlank()) {
            item {
                Column(Modifier.padding(horizontal = Jz.space.page)) {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Jz.colors.surface)
                            .border(1.dp, Jz.colors.lineHard)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Jz.colors.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("尚未配置 AI", style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "填写接口地址、模型和 API Key 后即可分析。Key 加密保存在本机。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Jz.colors.ink3,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    JzOutlinedButton("打开设置", onOpenSettings, Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                SectionTitle("分析范围")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        modifier = Modifier.weight(1f),
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
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiRange.entries.forEach { range ->
                        Chip(range.label, state.range == range) { viewModel.setRange(range) }
                    }
                }

                SectionTitle("分析重点")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiFocus.entries.forEach { focus ->
                        Chip(focus.label, state.focus == focus) { viewModel.setFocus(focus) }
                    }
                }

                SectionTitle("补充问题（可选）")
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = viewModel::setPrompt,
                    placeholder = { Text("例如：这个月哪类支出最值得削减？") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                if (state.loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassPrimaryButton("取消", viewModel::cancel, Modifier.weight(1f))
                        JzOutlinedButton("分析中…", {}, Modifier.weight(1f))
                    }
                } else {
                    GlassPrimaryButton("开始分析", viewModel::analyze, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "数据仅在本机汇总后发送给你配置的 AI 服务；账户名不会发送。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jz.colors.ink3,
                )
            }
        }

        state.error?.let { message ->
            item {
                Column(Modifier.padding(horizontal = Jz.space.page)) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Jz.colors.surface)
                            .border(1.dp, Jz.colors.expense.copy(alpha = 0.4f))
                            .padding(14.dp),
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.expense)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = viewModel::clearError) { Text("关闭提示") }
                }
            }
        }

        if (state.result.isNotBlank()) {
            item {
                Column(Modifier.padding(horizontal = Jz.space.page)) {
                    SectionTitle(
                        title = "分析结果",
                        right = null,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Jz.colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${settings.aiModel} · ${state.result.length} 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = Jz.colors.ink3,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "复制结果",
                            tint = Jz.colors.ink2,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { clipboard.setText(AnnotatedString(state.result)) },
                        )
                    }
                    SelectionContainer {
                        Text(
                            state.result,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Jz.colors.ink,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Jz.colors.surface)
                                .border(1.dp, Jz.colors.line)
                                .padding(16.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    JzOutlinedButton("清空结果", viewModel::clearResult)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (active) Jz.colors.accent else Jz.colors.surface, RoundedCornerShape(0.dp))
            .border(1.dp, if (active) Jz.colors.accent else Jz.colors.line, RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Jz.colors.onAccent else Jz.colors.ink2,
        )
    }
}
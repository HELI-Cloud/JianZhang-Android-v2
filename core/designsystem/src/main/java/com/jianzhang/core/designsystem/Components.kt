package com.jianzhang.core.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianzhang.core.common.Money
import com.jianzhang.core.model.TxnType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun JzPage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(Jz.colors.bg)
            .padding(horizontal = Jz.space.page),
    ) { content() }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = Jz.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jz.colors.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(title: String, right: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = Jz.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        if (right != null) {
            Text(
                right,
                style = MaterialTheme.typography.bodyMedium,
                color = Jz.colors.ink3,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    JzDivider()
}

@Composable
fun JzDivider(modifier: Modifier = Modifier, hard: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (hard) Jz.colors.lineHard else Jz.colors.line),
    )
}

@Composable
fun AmountText(
    cents: Long,
    type: TxnType? = null,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    showSign: Boolean = true,
) {
    val color = when (type) {
        TxnType.INCOME -> Jz.colors.income
        TxnType.EXPENSE -> Jz.colors.expense
        null -> Jz.colors.ink
    }
    val prefix = when {
        !showSign || type == null -> ""
        cents > 0 && type == TxnType.INCOME -> "+"
        type == TxnType.EXPENSE -> "-"
        else -> ""
    }
    Text(
        text = prefix + Money.format(kotlin.math.abs(cents), false),
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = if (large) 40.sp else 15.sp,
        letterSpacing = if (large) (-0.6).sp else 0.sp,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun GaugeBar(
    progress: Float,
    modifier: Modifier = Modifier,
    ticks: Int = 20,
    over: Boolean = false,
    height: Dp = 16.dp,
) {
    val colors = Jz.colors
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        drawRect(colors.track)
        drawRect(
            color = if (over) colors.expense else colors.accent.copy(alpha = 0.92f),
            size = Size(w * progress.coerceIn(0f, 1f), h),
        )
        for (i in 1 until ticks) {
            val x = w * i / ticks
            drawLine(colors.bg.copy(alpha = 0.58f), Offset(x, 0f), Offset(x, h), 1.dp.toPx())
        }
        drawRect(colors.lineHard, style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun GaugeScale(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    Row(modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.sp,
                ),
                color = Jz.colors.ink2,
                textAlign = when {
                    labels.size == 1 -> TextAlign.Center
                    index == 0 -> TextAlign.Start
                    index == labels.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressDuration = if (pressed) 90 else 160
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(pressDuration),
        label = "glass-scale",
    )
    val translation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 0.dp,
        animationSpec = tween(pressDuration),
        label = "glass-y",
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = translation.toPx()
                alpha = if (enabled) 1f else 0.42f
            }
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .drawWithCache {
                val body = Brush.verticalGradient(
                    listOf(Color(0xFFFFDCA0), Color(0xFFEFC05F), Color(0xFFD5A038), Color(0xFFB07E26)),
                )
                val rim = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFE6B85C)))
                onDrawBehind {
                    drawRect(body)
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFFFF).copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.2f, -size.height * 0.1f),
                            radius = size.width * 0.8f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFF6DB).copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 1.15f),
                            radius = size.width * 0.7f,
                        ),
                    )
                    drawRoundRect(
                        brush = rim,
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color(0xFF2B1C05),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
fun GlassKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressDuration = if (pressed) 80 else 140
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(pressDuration),
        label = "key-scale",
    )
    val translation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 0.dp,
        animationSpec = tween(pressDuration),
        label = "key-y",
    )
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = translation.toPx()
                }
                .shadow(if (selected) 8.dp else 6.dp, shape, clip = false)
                .clip(shape)
                .drawWithCache {
                    val body = Brush.verticalGradient(
                        listOf(Color(0xFFFFE0AA), Color(0xFFEFC05F), Color(0xFFD5A038), Color(0xFFB07E26)),
                    )
                    onDrawBehind {
                        drawRect(body)
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.58f), Color.Transparent),
                                center = Offset(size.width * 0.2f, -size.height * 0.15f),
                                radius = size.width * 0.9f,
                            ),
                        )
                        drawRoundRect(
                            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), Color(0xFFFFE6B8).copy(alpha = 0.45f))),
                            cornerRadius = CornerRadius(15.dp.toPx()),
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "记一笔", tint = Color(0xFF2B1C05), modifier = Modifier.size(22.dp))
        }
    }
}

data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val MainTabs = listOf(
    BottomTab("overview", "刻度", Icons.Outlined.Speed),
    BottomTab("ledger", "流水", Icons.AutoMirrored.Outlined.List),
    BottomTab("stats", "统计", Icons.Outlined.BarChart),
    BottomTab("mine", "我的", Icons.Outlined.Person),
)

@Composable
fun GlassBottomBar(
    hazeState: HazeState,
    selectedRoute: String,
    onTab: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Jz.colors
    val style = HazeStyle(
        backgroundColor = Color.Transparent,
        tints = listOf(HazeTint(color = colors.glassMid, blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver)),
        blurRadius = 22.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(color = colors.glassBottom),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .hazeEffect(hazeState, style)
            .background(
                Brush.verticalGradient(
                    listOf(colors.glassTop, colors.glassMid, colors.glassBottom),
                ),
            )
            .border(1.dp, colors.lineHard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(63.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TabItem(MainTabs[0], selectedRoute, onTab)
            TabItem(MainTabs[1], selectedRoute, onTab)
            Box(Modifier.width(58.dp), contentAlignment = Alignment.Center) {
                GlassKey(onAdd, selected = false)
            }
            TabItem(MainTabs[2], selectedRoute, onTab)
            TabItem(MainTabs[3], selectedRoute, onTab)
        }
    }
}

@Composable
private fun TabItem(tab: BottomTab, selectedRoute: String, onTab: (String) -> Unit) {
    val selected = selectedRoute == tab.route
    Column(
        modifier = Modifier
            .width(62.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onTab(tab.route) },
            )
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = if (selected) Jz.colors.ink else Jz.colors.ink3,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            color = if (selected) Jz.colors.ink else Jz.colors.ink3,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .background(if (selected) Jz.colors.accent else Color.Transparent),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("—", color = Jz.colors.ink3, fontFamily = FontFamily.Monospace, fontSize = 28.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
        Spacer(Modifier.height(5.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun JzOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(BorderStroke(1.dp, Jz.colors.lineHard), RoundedCornerShape(0.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Jz.colors.ink2)
    }
}

@Composable
fun MetricRow(label: String, value: String, valueColor: Color = Jz.colors.ink) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink2, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}






package com.jianzhang.feature.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.designsystem.SectionTitle

@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val info = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val version = info?.versionName ?: "1.0.0"
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) info?.longVersionCode else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 44.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Jz.colors.ink2)
                    }
                    PageHeader("关于简账", "本地优先的个人账本", modifier = Modifier.weight(1f))
                }
                JzDivider()
            }
        }

        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(Jz.colors.accent, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "简",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Jz.colors.onAccent,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("简账", style = MaterialTheme.typography.headlineLarge, color = Jz.colors.ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "版本 $version" + (code?.let { " · build $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jz.colors.ink3,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "一个不要求注册、不上传账本、默认把数据留在设备里的记账工具。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Jz.colors.ink2,
                )

                SectionTitle("设计原则")
                Principle(
                    icon = { Icon(Icons.Outlined.Storage, contentDescription = null, tint = Jz.colors.accent) },
                    title = "本地优先",
                    body = "账本保存在应用私有目录，使用 SQLCipher 加密。没有账号体系，也没有云端同步。",
                )
                Principle(
                    icon = { Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Jz.colors.accent) },
                    title = "AI 是可选能力",
                    body = "只有在你主动点击分析时，汇总后的账本数据才会发送到你配置的接口。API Key 使用 Android Keystore 加密保存。",
                )
                Principle(
                    icon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = Jz.colors.accent) },
                    title = "应用锁独立于数据库",
                    body = "锁屏密码只用于解锁界面，不作为数据库口令。忘记密码只能清空数据后重置，这是本地应用的安全边界。",
                )

                SectionTitle("数据与隐私")
                InfoLine("账本数据库", "SQLCipher · 应用私有目录")
                InfoLine("加密备份", "v3 · PBKDF2 + AES-256-GCM")
                InfoLine("兼容导入", "支持 v3 加密备份与 v2 明文备份")
                InfoLine("CSV 导出", "UTF-8 带 BOM，可用 Excel 打开，不加密")
                InfoLine("网络请求", "仅 AI 分析与你配置的接口")
                Spacer(Modifier.height(8.dp))
                Text(
                    "加密备份需要口令，口令遗失后无法解密。明文备份不加密，把文件发给别人或放到公共目录，等同于交出账本内容。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jz.colors.expense,
                )

                SectionTitle("开源组件")
                LicenseLine("AndroidX / Jetpack Compose", "Apache License 2.0")
                LicenseLine("Kotlin & Coroutines", "Apache License 2.0")
                LicenseLine("Room", "Apache License 2.0")
                LicenseLine("Hilt / Dagger", "Apache License 2.0")
                LicenseLine("OkHttp", "Apache License 2.0")
                LicenseLine("Haze", "Apache License 2.0")
                LicenseLine("SQLCipher for Android", "BSD-style / Zetetic")
                Spacer(Modifier.height(10.dp))
                Text(
                    "完整许可文本随开源依赖分发，可在对应项目主页获取。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jz.colors.ink3,
                )

                SectionTitle("联系与反馈")
                Text(
                    "当前版本不包含账号、广告、统计 SDK 或崩溃上报。遇到问题时，请附上设备型号、系统版本与可复现步骤。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Jz.colors.ink2,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "LOCAL FIRST · NO ACCOUNT · NO TRACKING",
                    style = MaterialTheme.typography.labelSmall,
                    color = Jz.colors.ink3,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun Principle(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
        }
    }
    JzDivider()
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
    }
    JzDivider()
}

@Composable
private fun LicenseLine(name: String, license: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = Jz.colors.ink3, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink2, modifier = Modifier.weight(1f))
        Text(license, style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
    }
}

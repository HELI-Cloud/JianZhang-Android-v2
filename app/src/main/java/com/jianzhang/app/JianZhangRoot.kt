package com.jianzhang.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import com.jianzhang.core.data.BootstrapState
import com.jianzhang.core.designsystem.GlassBottomBar
import com.jianzhang.core.designsystem.Jz
import com.jianzhang.core.designsystem.JzDivider
import com.jianzhang.core.designsystem.JzTheme
import com.jianzhang.core.designsystem.PageHeader
import com.jianzhang.core.navigation.AboutRoute
import com.jianzhang.core.navigation.AccountsRoute
import com.jianzhang.core.navigation.AnalysisRoute
import com.jianzhang.core.navigation.BudgetRoute
import com.jianzhang.core.navigation.LedgerRoute
import com.jianzhang.core.navigation.LockRoute
import com.jianzhang.core.navigation.MineRoute
import com.jianzhang.core.navigation.OverviewRoute
import com.jianzhang.core.navigation.RecordRoute
import com.jianzhang.core.navigation.RecurringRoute
import com.jianzhang.core.navigation.ReminderRoute
import com.jianzhang.core.navigation.SettingsRoute
import com.jianzhang.core.navigation.StatsRoute
import com.jianzhang.core.navigation.TagsRoute
import com.jianzhang.feature.accounts.AccountsScreen
import com.jianzhang.feature.analysis.AnalysisScreen
import com.jianzhang.feature.ledger.LedgerScreen
import com.jianzhang.feature.lock.LockScreen
import com.jianzhang.feature.overview.OverviewScreen
import com.jianzhang.feature.planning.BudgetScreen
import com.jianzhang.feature.planning.RecurringScreen
import com.jianzhang.feature.planning.ReminderScreen
import com.jianzhang.feature.planning.TagsScreen
import com.jianzhang.feature.record.RecordScreen
import com.jianzhang.feature.settings.AboutScreen
import com.jianzhang.feature.settings.SettingsScreen
import com.jianzhang.feature.stats.StatsScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun JianZhangRoot(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val bootstrapState by appViewModel.bootstrapState.collectAsStateWithLifecycle()
    val lockUnlocked by appViewModel.lockUnlocked.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val appSettings = settings
    val context = LocalContext.current
    LaunchedEffect(appSettings?.lockEnabled, lockUnlocked) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        if (appSettings?.lockEnabled == true && !lockUnlocked) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val themeMode = appSettings?.themeMode ?: "dark"
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val fontScale = appSettings?.fontScale ?: 1f
    val density = LocalDensity.current
    val scaled = remember(density, fontScale) {
        Density(density.density, density.fontScale * fontScale.coerceIn(0.85f, 1.3f))
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        JzTheme(darkTheme = dark) {
            when (val state = bootstrapState) {
                BootstrapState.Loading -> StartupScreen()
                is BootstrapState.Failed -> StartupError(state.message, appViewModel::retry)
                is BootstrapState.Ready -> {
                    when {
                        appSettings == null -> StartupScreen()
                        appSettings.lockEnabled && !lockUnlocked -> LockScreen(gateOnly = true)
                        else -> JianZhangNav()
                    }
                }
            }
        }
    }
}

@Composable
private fun JianZhangNav() {
    val nav = rememberNavController()
    val hazeState = remember { HazeState() }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val mainRoute = when {
        destination?.hasRoute<OverviewRoute>() == true -> "overview"
        destination?.hasRoute<LedgerRoute>() == true -> "ledger"
        destination?.hasRoute<StatsRoute>() == true -> "stats"
        destination?.hasRoute<MineRoute>() == true -> "mine"
        else -> null
    }
    val request by NavigationRequests.target.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val target = request ?: return@LaunchedEffect
        val route: Any = when (target) {
            "budget" -> BudgetRoute
            "recurring" -> RecurringRoute
            "ledger" -> LedgerRoute
            "stats" -> StatsRoute
            "record" -> RecordRoute()
            else -> OverviewRoute
        }
        nav.navigate(route) { launchSingleTop = true }
        NavigationRequests.consume()
    }
    fun goMain(route: String) {
        val target: Any = when (route) {
            "overview" -> OverviewRoute
            "ledger" -> LedgerRoute
            "stats" -> StatsRoute
            else -> MineRoute
        }
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Box(Modifier.fillMaxSize().background(Jz.colors.bg)) {
        NavHost(
            navController = nav,
            startDestination = OverviewRoute,
            modifier = Modifier.fillMaxSize().statusBarsPadding().hazeSource(hazeState),
        ) {
            composable<OverviewRoute> {
                OverviewScreen(
                    onOpenRecord = { nav.navigate(RecordRoute()) },
                    onOpenBudget = { nav.navigate(BudgetRoute) },
                    onOpenPlanning = { nav.navigate(RecurringRoute) },
                    onEdit = { id -> nav.navigate(RecordRoute(id = id)) },
                )
            }
            composable<LedgerRoute> {
                LedgerScreen(onEdit = { id -> nav.navigate(RecordRoute(id = id)) })
            }
            composable<StatsRoute> { StatsScreen() }
            composable<MineRoute> { MineHome(nav::navigate) }
            composable<RecordRoute> {
                RecordScreen(
                    onClose = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable<BudgetRoute> { BudgetScreen(onBack = { nav.popBackStack() }) }
            composable<RecurringRoute> { RecurringScreen(onBack = { nav.popBackStack() }) }
            composable<ReminderRoute> { ReminderScreen(onBack = { nav.popBackStack() }, onOpenRecord = { nav.navigate(RecordRoute()) }) }
            composable<TagsRoute> { TagsScreen(onBack = { nav.popBackStack() }) }
            composable<AccountsRoute> { AccountsScreen(onBack = { nav.popBackStack() }) }
            composable<AnalysisRoute> {
                AnalysisScreen(
                    onBack = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenLock = { nav.navigate(LockRoute) },
                    onOpenAbout = { nav.navigate(AboutRoute) },
                )
            }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
            composable<LockRoute> { LockScreen(onBack = { nav.popBackStack() }) }
        }
        if (mainRoute != null) {
            GlassBottomBar(
                hazeState = hazeState,
                selectedRoute = mainRoute,
                onTab = ::goMain,
                onAdd = { nav.navigate(RecordRoute()) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}


private data class MineEntry(
    val title: String,
    val caption: String,
    val icon: ImageVector,
    val route: Any,
)

@Composable
private fun MineHome(onNavigate: (Any) -> Unit) {
    val entries = listOf(
        MineEntry("账户与转账", "余额、信用卡、账户归档", Icons.Outlined.AccountBalanceWallet, AccountsRoute),
        MineEntry("预算", "总额与分类额度", Icons.Outlined.Analytics, BudgetRoute),
        MineEntry("周期记账", "按日、周、月自动入账", Icons.Outlined.CalendarMonth, RecurringRoute),
        MineEntry("提醒", "每天固定时间提醒", Icons.Outlined.Notifications, ReminderRoute),
        MineEntry("标签", "给流水加上自己的维度", Icons.AutoMirrored.Outlined.Label, TagsRoute),
        MineEntry("AI 分析", "基于本地账本的消费洞察", Icons.Outlined.AutoAwesome, AnalysisRoute),
        MineEntry("应用锁", "本地密码保护", Icons.Outlined.Lock, LockRoute),
        MineEntry("关于简账", "版本、许可与隐私", Icons.Outlined.Info, AboutRoute),
        MineEntry("设置", "主题、字号、安全与备份", Icons.Outlined.Settings, SettingsRoute),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Jz.space.page)) {
                PageHeader("我的", "工具、数据与偏好")
                JzDivider()
            }
        }
        items(entries.size) { index ->
            val item = entries[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(item.route) }
                    .padding(horizontal = Jz.space.page, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = null, tint = Jz.colors.ink2)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = Jz.colors.ink)
                    Spacer(Modifier.height(3.dp))
                    Text(item.caption, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink3)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Jz.colors.ink3)
            }
            JzDivider()
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Jz.space.page, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("简账 1.0", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
                Text("LOCAL FIRST", style = MaterialTheme.typography.labelSmall, color = Jz.colors.ink3)
            }
        }
    }
}
@Composable
private fun StartupScreen() {
    Box(Modifier.fillMaxSize().background(Jz.colors.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("简账", style = MaterialTheme.typography.headlineLarge, color = Jz.colors.ink)
            Spacer(Modifier.height(12.dp))
            Text("正在打开本地账本", style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink2)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Jz.colors.accent, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun StartupError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Jz.colors.bg).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("账本未能打开", style = MaterialTheme.typography.headlineMedium, color = Jz.colors.ink)
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Jz.colors.ink2)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}



private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


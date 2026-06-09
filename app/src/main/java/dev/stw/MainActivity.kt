package dev.stw

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.stw.blocking.AccessibilityStatus
import dev.stw.blocking.DemoBlockPrefs
import dev.stw.blocking.DemoDebugState
import dev.stw.blocking.FloatingReminderService
import dev.stw.blocking.RestrictedGroup
import dev.stw.usage.LaunchableAppInfo
import dev.stw.usage.UsageAppInfo
import dev.stw.usage.UsageStatsRepository
import dev.stw.usage.formatDuration
import dev.stw.usage.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


private val NeonDarkColors = darkColorScheme(
    background = Color(0xFF05060A),
    surface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFF151B2E),
    primary = Color(0xFF7DD3FC),
    primaryContainer = Color(0xFF0F2A44),
    secondary = Color(0xFFC084FC),
    secondaryContainer = Color(0xFF2A1746),
    tertiary = Color(0xFF5EEAD4),
    tertiaryContainer = Color(0xFF123C3A),
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFEFF6FF),
    onSurfaceVariant = Color(0xFFC7D2FE),
    onPrimary = Color(0xFF06121F),
    onSecondary = Color(0xFF16051F),
    onTertiary = Color(0xFF031A17),
)

private val NeonBorderBrush = Brush.linearGradient(
    listOf(Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFFFACC15)),
)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    Build.VERSION.SDK_INT < 23 || powerManager.isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StopTheWorldDemoApp(
                onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                onOpenAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onOpenOverlaySettings = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                },
                onOpenBatterySettings = {
                    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    runCatching { startActivity(request) }.getOrElse { startActivity(details) }
                },
                onStartFloatingMonitor = {
                    val service = Intent(this, FloatingReminderService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                },
                onStopFloatingMonitor = {
                    stopService(Intent(this, FloatingReminderService::class.java))
                    DemoBlockPrefs.setFloatingRunning(this, false)
                },
            )
        }
    }
}

@Composable
fun StopTheWorldDemoApp(
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onStartFloatingMonitor: () -> Unit,
    onStopFloatingMonitor: () -> Unit,
) {
    MaterialTheme(colorScheme = NeonDarkColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DemoHome(
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onStartFloatingMonitor = onStartFloatingMonitor,
                onStopFloatingMonitor = onStopFloatingMonitor,
            )
        }
    }
}

@Composable
private fun DemoHome(
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onStartFloatingMonitor: () -> Unit,
    onStopFloatingMonitor: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { UsageStatsRepository(context.applicationContext) }
    var hasUsageAccess by remember { mutableStateOf(false) }
    var usageRows by remember { mutableStateOf<List<UsageAppInfo>>(emptyList()) }
    var launchableApps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var groups by remember { mutableStateOf(DemoBlockPrefs.groups(context)) }
    var selectedGroupId by remember { mutableStateOf(groups.firstOrNull()?.id ?: "default") }
    var newGroupName by remember { mutableStateOf("学习专注") }
    var restrictedPackage by remember { mutableStateOf(DemoBlockPrefs.restrictedPackage(context)) }
    var restrictedLabel by remember { mutableStateOf(DemoBlockPrefs.restrictedLabel(context)) }
    var debugState by remember { mutableStateOf(DemoBlockPrefs.debugState(context)) }
    var intentText by remember { mutableStateOf(DemoBlockPrefs.intents(context).joinToString("，")) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(AccessibilityStatus.isServiceEnabled(context)) }
    var hasFloatingRunning by remember { mutableStateOf(DemoBlockPrefs.isFloatingRunning(context)) }
    var ignoresBatteryOptimization by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showStatusDetails by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun refreshAll() {
        withContext(Dispatchers.IO) {
            hasUsageAccess = repository.hasUsageAccess()
            usageRows = repository.loadTodayUsage(limit = 12)
            launchableApps = repository.loadLaunchableApps().take(30)
            groups = DemoBlockPrefs.groups(context)
            if (groups.none { it.id == selectedGroupId }) selectedGroupId = groups.firstOrNull()?.id ?: "default"
            restrictedPackage = DemoBlockPrefs.restrictedPackage(context)
            restrictedLabel = DemoBlockPrefs.restrictedLabel(context)
            debugState = DemoBlockPrefs.debugState(context)
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibility = AccessibilityStatus.isServiceEnabled(context)
            hasFloatingRunning = DemoBlockPrefs.isFloatingRunning(context)
            ignoresBatteryOptimization = isIgnoringBatteryOptimizations(context)
            intentText = DemoBlockPrefs.intents(context).joinToString("，")
        }
    }

    LaunchedEffect(refreshTick) { refreshAll() }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refreshAll()
                delay(1_000)
            }
        }
    }

    val tabs = listOf("首页" to "✦", "分组" to "◈", "应用" to "⬢", "测试" to "⚡")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02030A), Color(0xFF070B18), Color(0xFF0C0615)),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xF20A0E1A),
                    tonalElevation = 10.dp,
                ) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(item.second, color = if (selectedTab == index) Color(0xFF67E8F9) else Color(0xFF94A3B8)) },
                            label = { Text(item.first) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("时停 Stop the World", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF8FAFC))
            when (selectedTab) {
                0 -> {
                    StatusCard(
                        hasUsageAccess = hasUsageAccess,
                        totalRestrictedApps = groups.sumOf { it.apps.size },
                        onOpenUsageAccess = onOpenUsageAccess,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onStartFloatingMonitor = onStartFloatingMonitor,
                        onStopFloatingMonitor = onStopFloatingMonitor,
                        hasOverlayPermission = hasOverlayPermission,
                        hasAccessibility = hasAccessibility,
                        hasFloatingRunning = hasFloatingRunning,
                        ignoresBatteryOptimization = ignoresBatteryOptimization,
                        showDetails = showStatusDetails,
                        onToggleDetails = { showStatusDetails = !showStatusDetails },
                        onRefresh = { refreshTick++ },
                    )
                    IntentConfigCard(
                        intentText = intentText,
                        onIntentTextChange = { intentText = it },
                        onSave = {
                            DemoBlockPrefs.setIntents(context, intentText.split('，', ',', '|', '\n'))
                            intentText = DemoBlockPrefs.intents(context).joinToString("，")
                        },
                    )
                }
                1 -> GroupManagementCard(
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    newGroupName = newGroupName,
                    onSelectedGroupChange = { selectedGroupId = it },
                    onNewGroupNameChange = { newGroupName = it },
                    onCreateGroup = {
                        val group = DemoBlockPrefs.addGroup(context, newGroupName)
                        groups = DemoBlockPrefs.groups(context)
                        selectedGroupId = group.id
                        newGroupName = ""
                    },
                    onDeleteGroup = { groupId ->
                        DemoBlockPrefs.deleteGroup(context, groupId)
                        groups = DemoBlockPrefs.groups(context)
                        selectedGroupId = groups.firstOrNull()?.id ?: "default"
                        restrictedPackage = DemoBlockPrefs.restrictedPackage(context)
                        restrictedLabel = DemoBlockPrefs.restrictedLabel(context)
                    },
                    onUpdateGroup = { groupId, name, limit, typed ->
                        DemoBlockPrefs.updateGroup(context, groupId, name, limit, typed)
                        groups = DemoBlockPrefs.groups(context)
                    },
                    onRemoveApp = { groupId, packageName ->
                        DemoBlockPrefs.removeAppFromGroup(context, groupId, packageName)
                        groups = DemoBlockPrefs.groups(context)
                        restrictedPackage = DemoBlockPrefs.restrictedPackage(context)
                        restrictedLabel = DemoBlockPrefs.restrictedLabel(context)
                    },
                )
                2 -> {
                    RestrictedAppPicker(
                        usageRows = usageRows,
                        launchableApps = launchableApps,
                        currentPackages = groups.flatMap { it.apps }.map { it.packageName }.toSet(),
                        selectedGroupName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "默认分组",
                        onSelect = { packageName, label ->
                            val target = groups.firstOrNull { it.id == selectedGroupId } ?: DemoBlockPrefs.addGroup(context, "默认分组")
                            DemoBlockPrefs.addAppToGroup(context, target.id, packageName, label)
                            groups = DemoBlockPrefs.groups(context)
                            selectedGroupId = target.id
                            restrictedPackage = DemoBlockPrefs.restrictedPackage(context)
                            restrictedLabel = DemoBlockPrefs.restrictedLabel(context)
                        },
                    )
                    AppPurposeConfigCard(
                        groups = groups,
                        selectedGroupId = selectedGroupId,
                        onSave = { packageName, options, typed ->
                            DemoBlockPrefs.updateAppPurpose(context, packageName, options.split('，', ',', '|', '\n'), typed)
                            groups = DemoBlockPrefs.groups(context)
                        },
                    )
                    UsageStatsCard(usageRows = usageRows)
                }
                3 -> {
                    TestAndDebugCard(
                        groups = groups,
                        debugState = debugState,
                        onOpenFirstRestrictedApp = {
                            val pkg = groups.flatMap { it.apps }.firstOrNull()?.packageName
                            val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launch)
                            }
                        },
                        onRefresh = { refreshTick++ },
                    )
                    RuleCard(
                        rule = AppRule(
                            packageName = restrictedPackage ?: "demo.app",
                            appLabel = restrictedLabel ?: "示例 App",
                            customMessage = "你现在是真的需要打开，还是只是习惯性点开？",
                        ),
                        state = AppRuntimeState(
                            packageName = restrictedPackage ?: "demo.app",
                            usedTodayMillis = usageRows.firstOrNull { it.packageName == restrictedPackage }?.usedMillis ?: 18 * 60_000L,
                            openCountToday = usageRows.firstOrNull { it.packageName == restrictedPackage }?.openCount ?: 3,
                        ),
                    )
                    FirstOpenInterventionCard(
                        rule = AppRule(
                            packageName = restrictedPackage ?: "demo.app",
                            appLabel = restrictedLabel ?: "示例 App",
                            delayBeforeOpenSeconds = 10,
                            customMessage = "真实流程：开启无障碍服务后，打开你选择的 App 会弹出类似页面。",
                        ),
                        decision = Decision.ShowDelay(10, 5, "真实流程：开启无障碍服务后，打开你选择的 App 会弹出类似页面。"),
                    )
                }
            }
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(
    hasUsageAccess: Boolean,
    totalRestrictedApps: Int,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onStartFloatingMonitor: () -> Unit,
    onStopFloatingMonitor: () -> Unit,
    hasOverlayPermission: Boolean,
    hasAccessibility: Boolean,
    hasFloatingRunning: Boolean,
    ignoresBatteryOptimization: Boolean,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xE6121730)),
        border = BorderStroke(1.dp, NeonBorderBrush),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("状态总览", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color(0xFFE0F2FE))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Usage", hasUsageAccess)
                StatusChip("悬浮窗", hasOverlayPermission)
                StatusChip("无障碍", hasAccessibility)
                StatusChip("极速监控", hasFloatingRunning)
                StatusChip("省电白名单", ignoresBatteryOptimization)
                StatusChip("目标App", totalRestrictedApps > 0)
            }
            Text("$totalRestrictedApps 个目标 App · 日常只保留监控开关", color = Color(0xFFCBD5E1))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartFloatingMonitor, modifier = Modifier.weight(1f)) { Text("启动兜底监控") }
                OutlinedButton(onClick = onStopFloatingMonitor, modifier = Modifier.weight(1f)) { Text("停止兜底") }
            }
            OutlinedButton(onClick = onToggleDetails, modifier = Modifier.fillMaxWidth()) { Text(if (showDetails) "收起状态监测" else "状态监测 / 权限设置") }
            if (showDetails) {
                Text("建议开启 Usage/悬浮窗/无障碍，并将省电设为不优化。", color = Color(0xFFCBD5E1))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onOpenUsageAccess, modifier = Modifier.weight(1f)) { Text("Usage") }
                    Button(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f)) { Text("悬浮窗") }
                    Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.weight(1f)) { Text("无障碍") }
                }
                OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) { Text("省电策略：设为无限制/不优化") }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("刷新状态") }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    val bg = if (ok) Color(0xFF123C3A) else Color(0xFF3A1420)
    val fg = if (ok) Color(0xFF6EE7B7) else Color(0xFFFDA4AF)
    ElevatedAssistChip(
        onClick = {},
        label = { Text("${if (ok) "✓" else "!"} $label") },
        colors = androidx.compose.material3.AssistChipDefaults.elevatedAssistChipColors(
            containerColor = bg,
            labelColor = fg,
        ),
    )
}

@Composable
private fun DebugCard(debugState: DemoDebugState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("触发调试", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("最近检测前台：${debugState.lastSeenPackage ?: "无"}")
            Text("检测时间：${if (debugState.lastSeenAt > 0) formatTime(debugState.lastSeenAt) else "无"}")
            Text("最近触发提醒：${debugState.lastTriggerPackage ?: "无"}")
            Text("触发时间：${if (debugState.lastTriggerAt > 0) formatTime(debugState.lastTriggerAt) else "无"}")
            Text("最近跳过原因：${debugState.lastSkipReason ?: "无"}")
            Text("极速监控运行：${if (debugState.floatingRunning) "是" else "否"}")
            Text("当前策略：无障碍负责即时事件，兜底监控负责 MIUI/OEM 延迟事件补偿；两路共用触发锁，只取包名/窗口身份，不读取文本。")
        }
    }
}

@Composable
private fun IntentConfigCard(
    intentText: String,
    onIntentTextChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111118))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("全局目的模板", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color(0xFF67E8F9))
            Text("自定义意图选项，用逗号分隔。弹窗会让你选择一个意图，继续后顶部小悬浮窗会显示当前意图。")
            OutlinedTextField(
                value = intentText,
                onValueChange = onIntentTextChange,
                label = { Text("意图列表") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存意图选项") }
        }
    }
}


@Composable
private fun AppPurposeConfigCard(
    groups: List<RestrictedGroup>,
    selectedGroupId: String,
    onSave: (String, String, Boolean?) -> Unit,
) {
    val apps = groups.firstOrNull { it.id == selectedGroupId }?.apps ?: groups.flatMap { it.apps }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("应用级目的配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("可针对某个应用覆盖目的选项；打开该应用时优先使用这里的设置。")
            if (apps.isEmpty()) {
                Text("当前分组暂无 App。先在上方添加目标 App。")
            }
            apps.forEach { app ->
                var options by remember(app.packageName, app.purposeOptions) { mutableStateOf(app.purposeOptions.joinToString("，")) }
                var typed by remember(app.packageName, app.requireTypedPurpose) { mutableStateOf(app.requireTypedPurpose == true) }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app.label, fontWeight = FontWeight.Bold)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = options,
                            onValueChange = { options = it },
                            label = { Text("该应用目的选项，空=使用全局目的") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("该应用要求手动输入目的")
                                Text("关闭则显示上面的目的选项；空选项时使用全局目的。", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = typed, onCheckedChange = { typed = it })
                        }
                        Button(onClick = { onSave(app.packageName, options, typed) }, modifier = Modifier.fillMaxWidth()) { Text("保存这个应用") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageStatsCard(usageRows: List<UsageAppInfo>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("今日使用统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (usageRows.isEmpty()) {
                Text("暂无数据。请确认 Usage Access 已授权，然后使用几个 App 后刷新。")
            } else {
                usageRows.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${index + 1}. ${row.appLabel}", fontWeight = FontWeight.SemiBold)
                        Text("${formatDuration(row.usedMillis)} · 打开 ${row.openCount} 次 · 最近 ${formatTime(row.lastTimeUsedMillis)}")
                        Text(row.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    if (index != usageRows.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}


@Composable
private fun GroupManagementCard(
    groups: List<RestrictedGroup>,
    selectedGroupId: String,
    newGroupName: String,
    onSelectedGroupChange: (String) -> Unit,
    onNewGroupNameChange: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onDeleteGroup: (String) -> Unit,
    onUpdateGroup: (String, String, Int, Boolean) -> Unit,
    onRemoveApp: (String, String) -> Unit,
) {
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()
    var detailPanel by remember(selectedGroup?.id) { mutableStateOf("settings") }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("分组管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("左侧像抽屉菜单一样只选分组；右侧二级菜单再管理设置/App，避免所有分组同时长展开。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = onNewGroupNameChange,
                    label = { Text("新分组名") },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onCreateGroup) { Text("添加") }
            }
            if (groups.isEmpty()) {
                Text("暂无分组。先添加一个分组，例如：学习、娱乐、社交。")
            } else {
                Text("分组抽屉", fontWeight = FontWeight.SemiBold)
            }

            groups.forEach { group ->
                GroupDrawerRow(
                    group = group,
                    selected = group.id == selectedGroup?.id,
                    onClick = {
                        onSelectedGroupChange(group.id)
                        detailPanel = "settings"
                    },
                )
            }

            selectedGroup?.let { group ->
                HorizontalDivider()
                Text("当前：${group.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${group.apps.size} 个 App · " +
                        if (group.dailyLimitMinutes > 0) "限时 ${group.dailyLimitMinutes} 分钟" else "不限时",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { detailPanel = "settings" },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (detailPanel == "settings") "✓ 设置" else "设置") }
                    OutlinedButton(
                        onClick = { detailPanel = "apps" },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (detailPanel == "apps") "✓ App" else "App") }
                }

                when (detailPanel) {
                    "apps" -> GroupAppsPanel(group = group, onRemoveApp = onRemoveApp)
                    else -> GroupSettingsPanel(
                        group = group,
                        onDeleteGroup = onDeleteGroup,
                        onUpdateGroup = onUpdateGroup,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDrawerRow(
    group: RestrictedGroup,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (selected) "› ${group.name}" else group.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${group.apps.size} 个 App · " +
                        if (group.dailyLimitMinutes > 0) "${group.dailyLimitMinutes} 分钟/天" else "不限时",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (selected) "已选中" else "进入", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GroupSettingsPanel(
    group: RestrictedGroup,
    onDeleteGroup: (String) -> Unit,
    onUpdateGroup: (String, String, Int, Boolean) -> Unit,
) {
    var editName by remember(group.id, group.name) { mutableStateOf(group.name) }
    var limitText by remember(group.id, group.dailyLimitMinutes) {
        mutableStateOf(if (group.dailyLimitMinutes > 0) group.dailyLimitMinutes.toString() else "")
    }
    var typedPurpose by remember(group.id, group.requireTypedPurpose) { mutableStateOf(group.requireTypedPurpose) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("二级菜单：分组设置", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("分组名") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it.filter { ch -> ch.isDigit() }.take(4) },
                label = { Text("每日使用超过多少分钟后提示超时；空/0=不限") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("自定义目的输入", fontWeight = FontWeight.SemiBold)
                    Text("开启后，这个分组内 App 弹窗不显示目的选项，而要求用户自己输入。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = typedPurpose, onCheckedChange = { typedPurpose = it })
            }
            Button(
                onClick = { onUpdateGroup(group.id, editName, limitText.toIntOrNull() ?: 0, typedPurpose) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存分组设置") }
            OutlinedButton(onClick = { onDeleteGroup(group.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("删除这个分组")
            }
        }
    }
}

@Composable
private fun GroupAppsPanel(
    group: RestrictedGroup,
    onRemoveApp: (String, String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("二级菜单：组内 App", fontWeight = FontWeight.SemiBold)
            if (group.apps.isEmpty()) {
                Text("这个分组还没有 App。保持选中当前分组后，到“应用”页添加。")
            } else {
                group.apps.forEachIndexed { index, app ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onRemoveApp(group.id, app.packageName) }) { Text("移除") }
                    }
                    if (index != group.apps.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TestAndDebugCard(
    groups: List<RestrictedGroup>,
    debugState: DemoDebugState,
    onOpenFirstRestrictedApp: () -> Unit,
    onRefresh: () -> Unit,
) {
    val totalApps = groups.sumOf { it.apps.size }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("测试与调试", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("这里是开发测试区，和上面的日常使用/分组配置分开。")
            Button(onClick = onOpenFirstRestrictedApp, enabled = totalApps > 0, modifier = Modifier.fillMaxWidth()) { Text("稳定测试：从时停打开第一个目标 App") }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("刷新调试信息") }
            Text("最近检测前台：${debugState.lastSeenPackage ?: "无"}")
            Text("检测时间：${if (debugState.lastSeenAt > 0) formatTime(debugState.lastSeenAt) else "无"}")
            Text("最近触发提醒：${debugState.lastTriggerPackage ?: "无"}")
            Text("触发时间：${if (debugState.lastTriggerAt > 0) formatTime(debugState.lastTriggerAt) else "无"}")
            Text("最近跳过原因：${debugState.lastSkipReason ?: "无"}")
            Text("极速监控运行：${if (debugState.floatingRunning) "是" else "否"}")
        }
    }
}

@Composable
private fun RestrictedAppPicker(
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackages: Set<String>,
    selectedGroupName: String,
    onSelect: (String, String) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("添加目标 App", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("当前添加到：$selectedGroupName。可从今日统计或可启动 App 中添加多个目标 App。")
            Text("今日常用 App", fontWeight = FontWeight.SemiBold)
            if (usageRows.isEmpty()) {
                Text("暂无 UsageStats 数据，下面显示可启动 App 列表。")
            }
            usageRows.take(6).forEach { row ->
                AppPickRow(
                    label = row.appLabel,
                    packageName = row.packageName,
                    selected = row.packageName in currentPackages,
                    subtitle = "${formatDuration(row.usedMillis)} · 打开 ${row.openCount} 次",
                    onClick = { onSelect(row.packageName, row.appLabel) },
                )
            }
            Text("可启动 App", fontWeight = FontWeight.SemiBold)
            launchableApps.take(10).forEach { app ->
                AppPickRow(
                    label = app.appLabel,
                    packageName = app.packageName,
                    selected = app.packageName in currentPackages,
                    subtitle = app.packageName,
                    onClick = { onSelect(app.packageName, app.appLabel) },
                )
            }
        }
    }
}

@Composable
private fun AppPickRow(
    label: String,
    packageName: String,
    selected: Boolean,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(if (selected) "✓ $label" else label, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        Text(packageName, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RuleCard(rule: AppRule, state: AppRuntimeState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前规则：${rule.appLabel}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("今日已使用：${state.usedTodayMillis / 60_000} / ${rule.dailyLimitMinutes} 分钟")
            Text("今日已打开：${state.openCountToday} / ${rule.maxOpenCountPerDay} 次")
            Text("打开前等待：10 秒")
            Text("短解锁时长：5 分钟")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstOpenInterventionCard(rule: AppRule, decision: Decision) {
    var selectedIntent by remember { mutableStateOf<String?>(null) }
    var countdown by remember(rule.packageName) { mutableIntStateOf(rule.delayBeforeOpenSeconds) }

    LaunchedEffect(rule.packageName) {
        countdown = rule.delayBeforeOpenSeconds
        while (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("提醒页预览：${rule.appLabel}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("先停一下", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            val message = when (decision) {
                is Decision.ShowDelay -> decision.message
                is Decision.LimitReached -> decision.reason
                is Decision.ForcedRest -> "现在处于强制休息中"
                Decision.Allow -> "当前允许使用"
            }
            Text("“$message”")

            Text("这次打开是为了：", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("查资料", "回复消息", "娱乐休息", "无聊", "逃避任务", "其他").forEach { intent ->
                    AssistChip(
                        onClick = { selectedIntent = intent },
                        label = { Text(if (selectedIntent == intent) "✓ $intent" else intent) },
                    )
                }
            }

            Text(if (countdown > 0) "还需等待 $countdown 秒" else "可以继续，但建议确认是否真的需要。")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("不打开了") }
                Button(onClick = {}, enabled = countdown == 0, modifier = Modifier.weight(1f)) { Text("继续 5 分钟") }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Demo 事件：app=${rule.packageName}, intent=${selectedIntent ?: "未选择"}, decision=${decision::class.simpleName}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

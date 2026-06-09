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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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


private val DayColors = lightColorScheme(
    background = Color(0xFFFAF9FE),
    surface = Color(0xFFF0F0F7),
    surfaceVariant = Color(0xFFE9EAF2),
    primary = Color(0xFF536AA3),
    primaryContainer = Color(0xFFE0E4F5),
    secondary = Color(0xFF6B7280),
    secondaryContainer = Color(0xFFE7E8EF),
    tertiary = Color(0xFF8B5CF6),
    tertiaryContainer = Color(0xFFEDE9FE),
    onBackground = Color(0xFF171821),
    onSurface = Color(0xFF171821),
    onSurfaceVariant = Color(0xFF5F6472),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
)

private val NeonBorderBrush = Brush.linearGradient(
    listOf(Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFFFACC15)),
)

private val DroidSpacesLightColors = lightColorScheme(
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFF6F2F7),
    surfaceVariant = Color(0xFFE7E1EA),
    surfaceContainer = Color(0xFFF0EDF2),
    surfaceContainerHigh = Color(0xFFEAE7EC),
    primary = Color(0xFF536AA3),
    primaryContainer = Color(0xFFE2E7FA),
    secondary = Color(0xFF6E7480),
    secondaryContainer = Color(0xFFE9ECF2),
    tertiary = Color(0xFF6B6477),
    tertiaryContainer = Color(0xFFECE7F2),
    outlineVariant = Color(0xFFC9C5CF),
    onBackground = Color(0xFF1B1B1F),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF5F5D66),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    Build.VERSION.SDK_INT < 23 || powerManager.isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(false)

private data class HomeSnapshot(
    val hasUsageAccess: Boolean,
    val usageRows: List<UsageAppInfo>,
    val launchableApps: List<LaunchableAppInfo>,
    val groups: List<RestrictedGroup>,
    val restrictedPackage: String?,
    val restrictedLabel: String?,
    val debugState: DemoDebugState,
    val hasOverlayPermission: Boolean,
    val hasAccessibility: Boolean,
    val hasFloatingRunning: Boolean,
    val ignoresBatteryOptimization: Boolean,
    val intentText: String,
)

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
    MaterialTheme(colorScheme = DroidSpacesLightColors) {
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
        val snapshot = withContext(Dispatchers.IO) {
            HomeSnapshot(
                hasUsageAccess = repository.hasUsageAccess(),
                usageRows = repository.loadTodayUsage(limit = 200),
                launchableApps = repository.loadLaunchableApps(),
                groups = DemoBlockPrefs.groups(context),
                restrictedPackage = DemoBlockPrefs.restrictedPackage(context),
                restrictedLabel = DemoBlockPrefs.restrictedLabel(context),
                debugState = DemoBlockPrefs.debugState(context),
                hasOverlayPermission = Settings.canDrawOverlays(context),
                hasAccessibility = AccessibilityStatus.isServiceEnabled(context),
                hasFloatingRunning = DemoBlockPrefs.isFloatingRunning(context),
                ignoresBatteryOptimization = isIgnoringBatteryOptimizations(context),
                intentText = DemoBlockPrefs.intents(context).joinToString("，"),
            )
        }
        hasUsageAccess = snapshot.hasUsageAccess
        usageRows = snapshot.usageRows
        launchableApps = snapshot.launchableApps
        groups = snapshot.groups
        if (groups.none { it.id == selectedGroupId }) selectedGroupId = groups.firstOrNull()?.id ?: "default"
        restrictedPackage = snapshot.restrictedPackage
        restrictedLabel = snapshot.restrictedLabel
        debugState = snapshot.debugState
        hasOverlayPermission = snapshot.hasOverlayPermission
        hasAccessibility = snapshot.hasAccessibility
        hasFloatingRunning = snapshot.hasFloatingRunning
        ignoresBatteryOptimization = snapshot.ignoresBatteryOptimization
        intentText = snapshot.intentText
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

    val createGroupAction = {
        val group = DemoBlockPrefs.addGroup(context, newGroupName.ifBlank { "新分组" })
        groups = DemoBlockPrefs.groups(context)
        selectedGroupId = group.id
        newGroupName = ""
    }

    val tabs = listOf("首页" to "⌂", "分组" to "▤", "统计" to "◈", "测试" to "⚙")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8F9FA), Color(0xFFF1F2F4), Color(0xFFEDEFF2)),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    shadowElevation = 3.dp,
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Text(item.second, color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)) },
                                label = { Text(item.first, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))) {
                        Text("时", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text("时停", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                        Text("Stop the World", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
                    onCreateGroup = createGroupAction,
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
                    usageRows = usageRows,
                    launchableApps = launchableApps,
                    currentPackages = groups.flatMap { it.apps }.map { it.packageName }.toSet(),
                    onAddAppToGroup = { groupId, packageName, label ->
                        DemoBlockPrefs.addAppToGroup(context, groupId, packageName, label)
                        groups = DemoBlockPrefs.groups(context)
                        selectedGroupId = groupId
                        restrictedPackage = DemoBlockPrefs.restrictedPackage(context)
                        restrictedLabel = DemoBlockPrefs.restrictedLabel(context)
                    },
                )
                2 -> {
                    UsageStatsOverviewCard(usageRows = usageRows, groups = groups)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("状态", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Usage", hasUsageAccess)
                StatusChip("悬浮窗", hasOverlayPermission)
                StatusChip("无障碍", hasAccessibility)
                StatusChip("极速监控", hasFloatingRunning)
                StatusChip("省电白名单", ignoresBatteryOptimization)
                StatusChip("目标App", totalRestrictedApps > 0)
            }
            Text("$totalRestrictedApps 个目标 App · 监控开关", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartFloatingMonitor, modifier = Modifier.weight(1f)) { Text("启动兜底监控") }
                OutlinedButton(onClick = onStopFloatingMonitor, modifier = Modifier.weight(1f)) { Text("停止兜底") }
            }
            OutlinedButton(onClick = onToggleDetails, modifier = Modifier.fillMaxWidth()) { Text(if (showDetails) "收起状态监测" else "状态监测 / 权限设置") }
            if (showDetails) {
                Text("建议开启 Usage/悬浮窗/无障碍，并将省电设为不优化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val bg = if (ok) Color(0xFFE4EFE9) else Color(0xFFF2E6E6)
    val fg = if (ok) Color(0xFF486052) else Color(0xFF7B5656)
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("触发调试", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("全局目的模板", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("自定义意图选项，用逗号分隔。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onSave: (String, String, Boolean?, Int?) -> Unit,
) {
    val apps = groups.firstOrNull { it.id == selectedGroupId }?.apps ?: groups.flatMap { it.apps }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("应用级目的配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("可针对某个应用覆盖目的选项；打开该应用时优先使用这里的设置。")
            if (apps.isEmpty()) {
                Text("当前分组暂无 App。先在上方添加目标 App。")
            }
            apps.forEach { app ->
                var options by remember(app.packageName, app.purposeOptions) { mutableStateOf(app.purposeOptions.joinToString("，")) }
                var typed by remember(app.packageName, app.requireTypedPurpose) { mutableStateOf(app.requireTypedPurpose == true) }
                var limitText by remember(app.packageName, app.dailyLimitMinutes) { mutableStateOf(if (app.dailyLimitMinutes > 0) app.dailyLimitMinutes.toString() else "") }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app.label, fontWeight = FontWeight.Bold)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = options,
                            onValueChange = { options = it },
                            label = { Text("该应用目的选项，空=使用全局目的") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = limitText,
                            onValueChange = { limitText = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("单应用每日限时（分钟，空/0=使用分组限时）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("该应用要求手动输入目的")
                                Text("关闭则显示上面的目的选项；空选项时使用全局目的。", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = typed, onCheckedChange = { typed = it })
                        }
                        Button(onClick = { onSave(app.packageName, options, typed, limitText.toIntOrNull()?.coerceAtLeast(0) ?: 0) }, modifier = Modifier.fillMaxWidth()) { Text("保存这个应用") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageStatsOverviewCard(
    usageRows: List<UsageAppInfo>,
    groups: List<RestrictedGroup>,
) {
    var mode by remember { mutableStateOf("app") }
    val usageByPackage = remember(usageRows) { usageRows.associateBy { it.packageName } }
    val groupRows = remember(usageRows, groups) {
        groups.map { group ->
            val rows = group.apps.mapNotNull { usageByPackage[it.packageName] }
            GroupUsageRow(
                group = group,
                usedMillis = rows.sumOf { it.usedMillis },
                openCount = rows.sumOf { it.openCount },
            )
        }.sortedByDescending { it.usedMillis }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("应用使用时间统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("按今天 0 点到现在的前台会话统计。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { mode = "app" }, label = { Text(if (mode == "app") "✓ 分应用" else "分应用") })
                AssistChip(onClick = { mode = "group" }, label = { Text(if (mode == "group") "✓ 分组" else "分组") })
            }
            if (mode == "group") {
                if (groupRows.isEmpty()) {
                    Text("暂无分组。", style = MaterialTheme.typography.bodySmall)
                }
                groupRows.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${index + 1}. ${row.group.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(formatDuration(row.usedMillis), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        val limitText = if (row.group.dailyLimitMinutes > 0) " · 限时 ${row.group.dailyLimitMinutes} 分钟/天" else " · 不限时"
                        Text("${row.group.apps.size} 个 App · 打开 ${row.openCount} 次$limitText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index != groupRows.lastIndex) HorizontalDivider()
                }
            } else {
                if (usageRows.isEmpty()) {
                    Text("暂无数据。请确认 Usage Access 已授权，然后使用几个 App 后刷新。", style = MaterialTheme.typography.bodySmall)
                }
                usageRows.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${index + 1}. ${row.appLabel}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDuration(row.usedMillis), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Text("打开 ${row.openCount} 次 · 最近 ${formatTime(row.lastTimeUsedMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (index != usageRows.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

private data class GroupUsageRow(
    val group: RestrictedGroup,
    val usedMillis: Long,
    val openCount: Int,
)

@Composable
private fun UsageStatsCard(usageRows: List<UsageAppInfo>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("今日使用统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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


@OptIn(ExperimentalFoundationApi::class)
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
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackages: Set<String>,
    onAddAppToGroup: (String, String, String) -> Unit,
) {
    var expandedGroupId by remember(groups, selectedGroupId) { mutableStateOf(selectedGroupId.takeIf { id -> groups.any { it.id == id } }) }
    var menuPanel by remember(expandedGroupId) { mutableStateOf("rename") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE0E2E6)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("分组", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("卡片列表展示；点按选择添加 App 的目标分组，长按卡片展开二级菜单。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = onNewGroupNameChange,
                    label = { Text("新分组名") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onCreateGroup) { Text("添加") }
            }
            if (groups.isEmpty()) {
                Text("暂无分组。右下角 + 添加分组，或在这里输入名称后添加。", style = MaterialTheme.typography.bodySmall)
            }
            groups.forEach { group ->
                GroupCardItem(
                    group = group,
                    selected = group.id == selectedGroupId,
                    expanded = group.id == expandedGroupId,
                    menuPanel = menuPanel,
                    onSelect = {
                        onSelectedGroupChange(group.id)
                    },
                    onLongPress = {
                        onSelectedGroupChange(group.id)
                        expandedGroupId = if (expandedGroupId == group.id) null else group.id
                        menuPanel = "rename"
                    },
                    onMenuPanelChange = { menuPanel = it },
                    onDeleteGroup = onDeleteGroup,
                    onUpdateGroup = onUpdateGroup,
                    onRemoveApp = onRemoveApp,
                    usageRows = usageRows,
                    launchableApps = launchableApps,
                    currentPackages = currentPackages,
                    onAddAppToGroup = onAddAppToGroup,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCardItem(
    group: RestrictedGroup,
    selected: Boolean,
    expanded: Boolean,
    menuPanel: String,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onMenuPanelChange: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onUpdateGroup: (String, String, Int, Boolean) -> Unit,
    onRemoveApp: (String, String) -> Unit,
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackages: Set<String>,
    onAddAppToGroup: (String, String, String) -> Unit,
) {
    val containerColor = when {
        expanded -> Color(0xFFE8EAEE)
        selected -> Color(0xFFE1E4E8)
        else -> Color(0xFFF0F1F3)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, if (selected || expanded) Color(0xFFD6DAE0) else Color(0xFFE4E6EA)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (selected) "● ${group.name}" else group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${group.apps.size} 个 App · ${if (group.dailyLimitMinutes > 0) "${group.dailyLimitMinutes} 分钟/天" else "不限时"} · ${if (group.requireTypedPurpose) "手写目的" else "选目的"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "收起" else "长按菜单", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (group.apps.isNotEmpty()) {
                Text(
                    group.apps.take(3).joinToString(" · ") { it.label } + if (group.apps.size > 3) " …" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                )
            }
            if (expanded) {
                GroupSecondMenu(
                    group = group,
                    menuPanel = menuPanel,
                    onMenuPanelChange = onMenuPanelChange,
                    onDeleteGroup = onDeleteGroup,
                    onUpdateGroup = onUpdateGroup,
                    onRemoveApp = onRemoveApp,
                    usageRows = usageRows,
                    launchableApps = launchableApps,
                    currentPackages = currentPackages,
                    onAddAppToGroup = onAddAppToGroup,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupSecondMenu(
    group: RestrictedGroup,
    menuPanel: String,
    onMenuPanelChange: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onUpdateGroup: (String, String, Int, Boolean) -> Unit,
    onRemoveApp: (String, String) -> Unit,
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackages: Set<String>,
    onAddAppToGroup: (String, String, String) -> Unit,
) {
    val items = listOf(
        "rename" to "改名",
        "limit" to "限时",
        "purpose" to "自定义目的",
        "delete" to "删除",
        "apps" to "App管理",
    )
    HorizontalDivider(color = Color(0xFFE4E6EA))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (key, label) ->
            AssistChip(
                onClick = { onMenuPanelChange(key) },
                label = { Text(if (menuPanel == key) "✓ $label" else label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
    when (menuPanel) {
        "limit" -> GroupLimitPanel(group = group, onUpdateGroup = onUpdateGroup)
        "purpose" -> GroupPurposePanel(group = group, onUpdateGroup = onUpdateGroup)
        "delete" -> GroupDeletePanel(group = group, onDeleteGroup = onDeleteGroup)
        "apps" -> GroupAppsPanel(
            group = group,
            usageRows = usageRows,
            launchableApps = launchableApps,
            currentPackages = currentPackages,
            onAddAppToGroup = onAddAppToGroup,
            onRemoveApp = onRemoveApp,
        )
        else -> GroupRenamePanel(group = group, onUpdateGroup = onUpdateGroup)
    }
}

@Composable
private fun GroupRenamePanel(
    group: RestrictedGroup,
    onUpdateGroup: (String, String, Int, Boolean) -> Unit,
) {
    var editName by remember(group.id, group.name) { mutableStateOf(group.name) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("分组名") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onUpdateGroup(group.id, editName, group.dailyLimitMinutes, group.requireTypedPurpose) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存改名", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun GroupLimitPanel(
    group: RestrictedGroup,
    onUpdateGroup: (String, String, Int, Boolean) -> Unit,
) {
    var limitText by remember(group.id, group.dailyLimitMinutes) {
        mutableStateOf(if (group.dailyLimitMinutes > 0) group.dailyLimitMinutes.toString() else "")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = limitText,
            onValueChange = { limitText = it.filter { ch -> ch.isDigit() }.take(4) },
            label = { Text("每日分钟，空/0=不限") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onUpdateGroup(group.id, group.name, limitText.toIntOrNull() ?: 0, group.requireTypedPurpose) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存限时", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun GroupPurposePanel(
    group: RestrictedGroup,
    onUpdateGroup: (String, String, Int, Boolean) -> Unit,
) {
    var typedPurpose by remember(group.id, group.requireTypedPurpose) { mutableStateOf(group.requireTypedPurpose) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text("自定义目的", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text("开启后组内 App 弹窗要求手动输入目的。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = typedPurpose, onCheckedChange = { typedPurpose = it })
        }
        Button(
            onClick = { onUpdateGroup(group.id, group.name, group.dailyLimitMinutes, typedPurpose) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存目的设置", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun GroupDeletePanel(
    group: RestrictedGroup,
    onDeleteGroup: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("删除“${group.name}”及其 App 关联？", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A5A5A))
        OutlinedButton(onClick = { onDeleteGroup(group.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("确认删除", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GroupAppsPanel(
    group: RestrictedGroup,
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackages: Set<String>,
    onAddAppToGroup: (String, String, String) -> Unit,
    onRemoveApp: (String, String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("组内 App", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Button(onClick = { showAddDialog = true }) { Text("添加 App", style = MaterialTheme.typography.labelSmall) }
        }
        if (group.apps.isEmpty()) {
            Text("这个分组还没有 App。点击添加 App。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            group.apps.forEachIndexed { index, app ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val limitText = if (app.dailyLimitMinutes > 0) " · 单应用 ${app.dailyLimitMinutes} 分钟/天" else ""
                        Text(app.packageName + limitText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { onRemoveApp(group.id, app.packageName) }) {
                        Text("移除", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (index != group.apps.lastIndex) HorizontalDivider(color = Color(0xFFE4E6EA))
            }
        }
    }
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加 App 到 ${group.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .height(460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RestrictedAppPicker(
                        usageRows = usageRows,
                        launchableApps = launchableApps,
                        currentPackages = currentPackages,
                        selectedGroupName = group.name,
                        onSelect = { packageName, label -> onAddAppToGroup(group.id, packageName, label) },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAddDialog = false }) { Text("完成") } },
        )
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("测试与调试", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestrictedAppPicker(
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackages: Set<String>,
    selectedGroupName: String,
    onSelect: (String, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("all") }
    var showAll by remember { mutableStateOf(false) }
    val normalizedQuery = query.trim().lowercase()
    val usagePackages = remember(usageRows) { usageRows.map { it.packageName }.toSet() }
    val commonApps = remember(usageRows, launchableApps) {
        val byPackage = launchableApps.associateBy { it.packageName }
        usageRows.map { row ->
            byPackage[row.packageName] ?: LaunchableAppInfo(row.packageName, row.appLabel)
        }.distinctBy { it.packageName }
    }
    val sourceApps = if (mode == "common") commonApps else launchableApps
    val filteredApps = remember(sourceApps, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            sourceApps
        } else {
            sourceApps.filter { app ->
                app.appLabel.lowercase().contains(normalizedQuery) || app.packageName.lowercase().contains(normalizedQuery)
            }
        }
    }
    val visibleApps = if (showAll || normalizedQuery.isNotBlank()) filteredApps else filteredApps.take(40)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("当前添加到：$selectedGroupName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; showAll = false },
            label = { Text("搜索应用 / 包名", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                AssistChip(onClick = { mode = "all"; showAll = false }, label = { Text(if (mode == "all") "✓ 全部" else "全部", style = MaterialTheme.typography.labelSmall) })
                AssistChip(onClick = { mode = "common"; showAll = false }, label = { Text(if (mode == "common") "✓ 常用" else "常用", style = MaterialTheme.typography.labelSmall) })
            }
            Text("${filteredApps.size} 个", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (mode == "common" && commonApps.isEmpty()) {
            Text("暂无常用数据，先确认 Usage Access 已授权。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleApps.forEach { app ->
            val usage = usageRows.firstOrNull { it.packageName == app.packageName }
            AppPickRow(
                label = app.appLabel,
                packageName = app.packageName,
                selected = app.packageName in currentPackages,
                subtitle = usage?.let { "${formatDuration(it.usedMillis)} · 打开 ${it.openCount} 次" } ?: if (app.packageName in usagePackages) "今日使用过" else "全部应用",
                onClick = { onSelect(app.packageName, app.appLabel) },
            )
        }
        if (filteredApps.size > visibleApps.size) {
            TextButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text("显示剩余 ${filteredApps.size - visibleApps.size} 个应用", style = MaterialTheme.typography.labelSmall)
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
            .padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(if (selected) "✓ $label" else label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RuleCard(rule: AppRule, state: AppRuntimeState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前规则：${rule.appLabel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("提醒页预览：${rule.appLabel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("先停一下", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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

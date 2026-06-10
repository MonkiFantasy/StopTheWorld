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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import dev.stw.blocking.AccessibilityStatus
import dev.stw.blocking.DemoBlockPrefs
import dev.stw.blocking.DemoDebugState
import dev.stw.blocking.FloatingReminderService
import dev.stw.blocking.RestrictedGroup
import dev.stw.usage.LaunchableAppInfo
import dev.stw.usage.UsageAppInfo
import dev.stw.usage.UsageStatsRepository
import dev.stw.usage.PurposeUsageSegment
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


private val CompactTypography = Typography(
    displayLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
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
    val purposeSegments: List<PurposeUsageSegment>,
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
    val dayBoundaryMinutes: Int,
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
    MaterialTheme(colorScheme = DroidSpacesLightColors, typography = CompactTypography) {
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
    var purposeSegments by remember { mutableStateOf<List<PurposeUsageSegment>>(emptyList()) }
    var launchableApps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var groups by remember { mutableStateOf(DemoBlockPrefs.groups(context)) }
    var selectedGroupId by remember { mutableStateOf(groups.firstOrNull()?.id ?: "default") }
    var newGroupName by remember { mutableStateOf("学习专注") }
    var restrictedPackage by remember { mutableStateOf(DemoBlockPrefs.restrictedPackage(context)) }
    var restrictedLabel by remember { mutableStateOf(DemoBlockPrefs.restrictedLabel(context)) }
    var debugState by remember { mutableStateOf(DemoBlockPrefs.debugState(context)) }
    var intentText by remember { mutableStateOf(DemoBlockPrefs.intents(context).joinToString("，")) }
    var dayBoundaryMinutes by remember { mutableIntStateOf(DemoBlockPrefs.dayBoundaryMinutes(context)) }
    var showDayBoundaryDialog by remember { mutableStateOf(false) }
    var dayBoundaryInput by remember { mutableStateOf(DemoBlockPrefs.formatDayBoundary(dayBoundaryMinutes)) }
    var dayBoundaryError by remember { mutableStateOf<String?>(null) }
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
                purposeSegments = repository.loadTargetPurposeSegments(DemoBlockPrefs.restrictedPackages(context), limit = 120),
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
                dayBoundaryMinutes = DemoBlockPrefs.dayBoundaryMinutes(context),
            )
        }
        hasUsageAccess = snapshot.hasUsageAccess
        usageRows = snapshot.usageRows
        purposeSegments = snapshot.purposeSegments
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
        dayBoundaryMinutes = snapshot.dayBoundaryMinutes
        if (!showDayBoundaryDialog) dayBoundaryInput = DemoBlockPrefs.formatDayBoundary(snapshot.dayBoundaryMinutes)
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

    if (showDayBoundaryDialog) {
        AlertDialog(
            onDismissRequest = { showDayBoundaryDialog = false },
            title = { Text("每日重置时间") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("设置“今日使用”和每日限时的分界线，例如 04:00 表示凌晨 4 点后算新一天。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = dayBoundaryInput,
                        onValueChange = {
                            dayBoundaryInput = it
                            dayBoundaryError = null
                        },
                        singleLine = true,
                        label = { Text("HH:mm / 小时") },
                        isError = dayBoundaryError != null,
                        supportingText = { Text(dayBoundaryError ?: "当前：${DemoBlockPrefs.formatDayBoundary(dayBoundaryMinutes)}") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = parseDayBoundaryInput(dayBoundaryInput)
                    if (parsed == null) {
                        dayBoundaryError = "请输入 0-23 或 HH:mm"
                    } else {
                        DemoBlockPrefs.setDayBoundaryMinutes(context, parsed)
                        dayBoundaryMinutes = parsed
                        dayBoundaryInput = DemoBlockPrefs.formatDayBoundary(parsed)
                        dayBoundaryError = null
                        showDayBoundaryDialog = false
                        refreshTick++
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDayBoundaryDialog = false }) { Text("取消") }
            },
        )
    }

    val createGroupAction = {
        val group = DemoBlockPrefs.addGroup(context, newGroupName.ifBlank { "新分组" })
        groups = DemoBlockPrefs.groups(context)
        selectedGroupId = group.id
        newGroupName = ""
    }

    val tabs = listOf(
        BottomTab("首页", "home"),
        BottomTab("分组", "layers"),
        BottomTab("统计", "grid"),
        BottomTab("测试", "settings"),
    )
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
                DroidStyleBottomBar(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                )
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
                        Text("时", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text("时停", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
                        dayBoundaryText = DemoBlockPrefs.formatDayBoundary(dayBoundaryMinutes),
                        onConfigureDayBoundary = {
                            dayBoundaryInput = DemoBlockPrefs.formatDayBoundary(dayBoundaryMinutes)
                            dayBoundaryError = null
                            showDayBoundaryDialog = true
                        },
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
                    onUpdateGroupPurpose = { groupId, options ->
                        DemoBlockPrefs.updateGroupPurposeOptions(context, groupId, options.split('，', ',', '|', '\n'))
                        groups = DemoBlockPrefs.groups(context)
                    },
                    onUpdateAppPurpose = { packageName, options, typed, limitMinutes ->
                        DemoBlockPrefs.updateAppPurpose(context, packageName, options.split('，', ',', '|', '\n'), typed, limitMinutes)
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
                    UsageStatsOverviewCard(usageRows = usageRows, groups = groups, purposeSegments = purposeSegments)
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

private data class BottomTab(val label: String, val icon: String)

@Composable
private fun DroidStyleBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        color = Color(0xFFF0EFF7),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalDivider(color = Color(0xFFE0DFE8))
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    DroidStyleNavItem(
                        tab = tab,
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DroidStyleNavItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = Color(0xFF4F6397)
    val inactiveColor = Color(0xFF7A7D86)
    val color = if (selected) activeColor else inactiveColor
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(if (selected) 122.dp else 78.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) Color(0xFFDCDCEB) else Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NavGlyph(tab.icon, color, Modifier.size(26.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
            )
        }
    }
}

@Composable
private fun NavGlyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.12f)
        when (kind) {
            "home" -> {
                val roof = Path().apply {
                    moveTo(w * 0.14f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.48f)
                    lineTo(w * 0.78f, h * 0.48f)
                    lineTo(w * 0.78f, h * 0.86f)
                    lineTo(w * 0.58f, h * 0.86f)
                    lineTo(w * 0.58f, h * 0.62f)
                    lineTo(w * 0.42f, h * 0.62f)
                    lineTo(w * 0.42f, h * 0.86f)
                    lineTo(w * 0.22f, h * 0.86f)
                    lineTo(w * 0.22f, h * 0.48f)
                    close()
                }
                drawPath(roof, color)
            }
            "layers" -> {
                val p1 = Path().apply { moveTo(w * 0.50f, h * 0.12f); lineTo(w * 0.86f, h * 0.32f); lineTo(w * 0.50f, h * 0.52f); lineTo(w * 0.14f, h * 0.32f); close() }
                val p2 = Path().apply { moveTo(w * 0.18f, h * 0.50f); lineTo(w * 0.50f, h * 0.68f); lineTo(w * 0.82f, h * 0.50f) }
                val p3 = Path().apply { moveTo(w * 0.18f, h * 0.68f); lineTo(w * 0.50f, h * 0.86f); lineTo(w * 0.82f, h * 0.68f) }
                drawPath(p1, color)
                drawPath(p2, color, style = stroke)
                drawPath(p3, color, style = stroke)
            }
            "grid" -> {
                val gap = w * 0.10f
                val cell = w * 0.28f
                listOf(0f, 1f).forEach { row ->
                    listOf(0f, 1f).forEach { col ->
                        drawRect(color, topLeft = Offset(w * 0.20f + col * (cell + gap), h * 0.20f + row * (cell + gap)), size = Size(cell, cell))
                    }
                }
            }
            else -> {
                drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.50f))
                repeat(8) { i ->
                    val angle = (Math.PI * 2.0 * i / 8.0).toFloat()
                    val sx = w * 0.50f + kotlin.math.cos(angle) * w * 0.26f
                    val sy = h * 0.50f + kotlin.math.sin(angle) * h * 0.26f
                    val ex = w * 0.50f + kotlin.math.cos(angle) * w * 0.40f
                    val ey = h * 0.50f + kotlin.math.sin(angle) * h * 0.40f
                    drawLine(color, Offset(sx, sy), Offset(ex, ey), strokeWidth = w * 0.10f)
                }
            }
        }
    }
}

private fun parseDayBoundaryInput(raw: String): Int? {
    val value = raw.trim()
    if (value.isBlank()) return null
    if (":" !in value) {
        val hour = value.toIntOrNull() ?: return null
        return if (hour in 0..23) hour * 60 else null
    }
    val parts = value.split(":", limit = 2)
    val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
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
    dayBoundaryText: String,
    onConfigureDayBoundary: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("状态", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Usage", hasUsageAccess, onClick = onOpenUsageAccess)
                StatusChip("悬浮窗", hasOverlayPermission, onClick = onOpenOverlaySettings)
                StatusChip("无障碍", hasAccessibility, onClick = onOpenAccessibilitySettings)
                StatusChip("极速监控", hasFloatingRunning, onClick = onStartFloatingMonitor)
                StatusChip("省电白名单", ignoresBatteryOptimization, onClick = onOpenBatterySettings)
                StatusChip("重置 $dayBoundaryText", true, onClick = onConfigureDayBoundary)
                StatusChip("目标App", totalRestrictedApps > 0)
            }
            Text("$totalRestrictedApps 个目标 App · 监控开关", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartFloatingMonitor, modifier = Modifier.weight(1f)) { Text("启动兜底监控", style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(onClick = onStopFloatingMonitor, modifier = Modifier.weight(1f)) { Text("停止兜底", style = MaterialTheme.typography.labelMedium) }
            }
            OutlinedButton(onClick = onToggleDetails, modifier = Modifier.fillMaxWidth()) { Text(if (showDetails) "收起状态监测" else "状态监测 / 权限设置", style = MaterialTheme.typography.labelMedium) }
            if (showDetails) {
                Text("点击上方状态标签可进入对应权限/设置；建议开启 Usage、悬浮窗、无障碍，并将省电设为不优化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("每日重置：$dayBoundaryText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onConfigureDayBoundary) { Text("修改", style = MaterialTheme.typography.labelMedium) }
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("刷新状态", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean, onClick: () -> Unit = {}) {
    val bg = if (ok) Color(0xFFE4EFE9) else Color(0xFFF2E6E6)
    val fg = if (ok) Color(0xFF486052) else Color(0xFF7B5656)
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text("${if (ok) "✓" else "!"} $label", style = MaterialTheme.typography.labelSmall) },
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
            Text("最近检测前台：${debugState.lastSeenPackage ?: "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("检测时间：${if (debugState.lastSeenAt > 0) formatTime(debugState.lastSeenAt) else "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("最近触发提醒：${debugState.lastTriggerPackage ?: "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("触发时间：${if (debugState.lastTriggerAt > 0) formatTime(debugState.lastTriggerAt) else "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("最近跳过原因：${debugState.lastSkipReason ?: "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("极速监控运行：${if (debugState.floatingRunning) "是" else "否"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                label = { Text("意图列表", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存意图选项", style = MaterialTheme.typography.labelMedium) }
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
            Text("可针对某个应用覆盖目的选项；打开该应用时优先使用这里的设置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (apps.isEmpty()) {
                Text("当前分组暂无 App。先在上方添加目标 App。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            apps.forEach { app ->
                var options by remember(app.packageName, app.purposeOptions) { mutableStateOf(app.purposeOptions.joinToString("，")) }
                var typed by remember(app.packageName, app.requireTypedPurpose) { mutableStateOf(app.requireTypedPurpose == true) }
                var limitText by remember(app.packageName, app.dailyLimitMinutes) { mutableStateOf(if (app.dailyLimitMinutes > 0) app.dailyLimitMinutes.toString() else "") }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
                                Text("该应用要求手动输入目的", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
    purposeSegments: List<PurposeUsageSegment>,
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
                    Text("按当前每日重置时间到现在的前台会话统计。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { mode = "app" }, label = { Text(if (mode == "app") "✓ 分应用" else "分应用") })
                AssistChip(onClick = { mode = "group" }, label = { Text(if (mode == "group") "✓ 分组" else "分组") })
                AssistChip(onClick = { mode = "purpose" }, label = { Text(if (mode == "purpose") "✓ 目的时段" else "目的时段") })
            }
            if (mode == "purpose") {
                PurposeSegmentsList(purposeSegments)
            } else if (mode == "group") {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposeSegmentsList(segments: List<PurposeUsageSegment>) {
    if (segments.isEmpty()) {
        Text("暂无目标 App 的目的时段。需要在提醒页选择/输入目的并继续打开后才会记录。", style = MaterialTheme.typography.bodySmall)
        return
    }
    val byApp = segments.groupBy { it.packageName }
    byApp.forEach { (_, appSegments) ->
        val first = appSegments.first()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(first.appLabel, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDuration(appSegments.sumOf { it.usedMillis }), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            val purposeTotals = appSegments.groupBy { it.purpose }.mapValues { entry -> entry.value.sumOf { it.usedMillis } }
                .toList().sortedByDescending { it.second }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                purposeTotals.forEach { (purpose, total) ->
                    AssistChip(onClick = {}, label = { Text("$purpose · ${formatDuration(total)}") })
                }
            }
            appSegments.take(6).forEach { segment ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${formatTime(segment.startMillis)}-${formatTime(segment.endMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(segment.purpose, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(" · ${formatDuration(segment.usedMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider()
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
    onUpdateGroupPurpose: (String, String) -> Unit,
    onUpdateAppPurpose: (String, String, Boolean?, Int?) -> Unit,
    onRemoveApp: (String, String) -> Unit,
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackages: Set<String>,
    onAddAppToGroup: (String, String, String) -> Unit,
) {
    var expandedGroupId by remember(groups, selectedGroupId) { mutableStateOf(selectedGroupId.takeIf { id -> groups.any { it.id == id } }) }
    var menuPanel by remember(expandedGroupId) { mutableStateOf("rename") }
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFFE0E2E6)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("分组", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("只显示分组卡片；点按选择，长按卡片展开二级菜单。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (groups.isEmpty()) {
                    Text("暂无分组。点击右下角添加分组。", style = MaterialTheme.typography.bodySmall)
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
                    onUpdateGroupPurpose = onUpdateGroupPurpose,
                    onUpdateAppPurpose = onUpdateAppPurpose,
                    onRemoveApp = onRemoveApp,
                    usageRows = usageRows,
                    launchableApps = launchableApps,
                    currentPackages = currentPackages,
                    onAddAppToGroup = onAddAppToGroup,
                )
                }
                Spacer(Modifier.height(52.dp))
            }
        }
        Surface(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp,
        ) {
            Text(
                "＋ 添加分组",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("添加分组", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = onNewGroupNameChange,
                    label = { Text("分组名") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateGroup()
                    showCreateDialog = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } },
        )
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
    onUpdateGroupPurpose: (String, String) -> Unit,
    onUpdateAppPurpose: (String, String, Boolean?, Int?) -> Unit,
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
                    onUpdateGroupPurpose = onUpdateGroupPurpose,
                    onUpdateAppPurpose = onUpdateAppPurpose,
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
    onUpdateGroupPurpose: (String, String) -> Unit,
    onUpdateAppPurpose: (String, String, Boolean?, Int?) -> Unit,
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
        "purpose" -> GroupPurposePanel(group = group, onUpdateGroup = onUpdateGroup, onUpdateGroupPurpose = onUpdateGroupPurpose)
        "delete" -> GroupDeletePanel(group = group, onDeleteGroup = onDeleteGroup)
        "apps" -> GroupAppsPanel(
            group = group,
            usageRows = usageRows,
            launchableApps = launchableApps,
            currentPackages = currentPackages,
            onAddAppToGroup = onAddAppToGroup,
            onUpdateAppPurpose = onUpdateAppPurpose,
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
    onUpdateGroupPurpose: (String, String) -> Unit,
) {
    var typedPurpose by remember(group.id, group.requireTypedPurpose) { mutableStateOf(group.requireTypedPurpose) }
    var purposeOptions by remember(group.id, group.purposeOptions) { mutableStateOf(group.purposeOptions.joinToString("，")) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = purposeOptions,
            onValueChange = { purposeOptions = it },
            label = { Text("分组目的模板，空=使用全局模板") },
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("目的模板优先级：单应用 ＞ 分组 ＞ 全局。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onClick = {
                onUpdateGroup(group.id, group.name, group.dailyLimitMinutes, typedPurpose)
                onUpdateGroupPurpose(group.id, purposeOptions)
            },
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
    onUpdateAppPurpose: (String, String, Boolean?, Int?) -> Unit,
    onRemoveApp: (String, String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<dev.stw.blocking.RestrictedAppEntry?>(null) }
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
                    OutlinedButton(onClick = { editingApp = app }) {
                        Text("设置", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { onRemoveApp(group.id, app.packageName) }) {
                        Text("移除", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (index != group.apps.lastIndex) HorizontalDivider(color = Color(0xFFE4E6EA))
            }
        }
    }
    editingApp?.let { app ->
        var options by remember(app.packageName, app.purposeOptions) { mutableStateOf(app.purposeOptions.joinToString("，")) }
        var typed by remember(app.packageName, app.requireTypedPurpose) { mutableStateOf(app.requireTypedPurpose == true) }
        var limitText by remember(app.packageName, app.dailyLimitMinutes) { mutableStateOf(if (app.dailyLimitMinutes > 0) app.dailyLimitMinutes.toString() else "") }
        AlertDialog(
            onDismissRequest = { editingApp = null },
            title = { Text("应用设置：${app.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = { Text("单应用目的模板，空=使用分组/全局") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("单应用每日限时（分钟）") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("要求手动输入目的", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("关闭则使用目的模板。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = typed, onCheckedChange = { typed = it })
                    }
                    Text("优先级：单应用 ＞ 分组 ＞ 全局。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateAppPurpose(app.packageName, options, typed, limitText.toIntOrNull()?.coerceAtLeast(0) ?: 0)
                    editingApp = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingApp = null }) { Text("取消") } },
        )
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
            Text("这里是开发测试区，和上面的日常使用/分组配置分开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenFirstRestrictedApp, enabled = totalApps > 0, modifier = Modifier.fillMaxWidth()) { Text("稳定测试：从时停打开第一个目标 App", style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("刷新调试信息") }
            Text("最近检测前台：${debugState.lastSeenPackage ?: "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("检测时间：${if (debugState.lastSeenAt > 0) formatTime(debugState.lastSeenAt) else "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("最近触发提醒：${debugState.lastTriggerPackage ?: "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("触发时间：${if (debugState.lastTriggerAt > 0) formatTime(debugState.lastTriggerAt) else "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("最近跳过原因：${debugState.lastSkipReason ?: "无"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("极速监控运行：${if (debugState.floatingRunning) "是" else "否"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("今日已使用：${state.usedTodayMillis / 60_000} / ${rule.dailyLimitMinutes} 分钟", style = MaterialTheme.typography.bodySmall)
            Text("今日已打开：${state.openCountToday} / ${rule.maxOpenCountPerDay} 次", style = MaterialTheme.typography.bodySmall)
            Text("打开前等待：10 秒", style = MaterialTheme.typography.bodySmall)
            Text("短解锁时长：5 分钟", style = MaterialTheme.typography.bodySmall)
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
            Text("先停一下", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            val message = when (decision) {
                is Decision.ShowDelay -> decision.message
                is Decision.LimitReached -> decision.reason
                is Decision.ForcedRest -> "现在处于强制休息中"
                Decision.Allow -> "当前允许使用"
            }
            Text("“$message”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("这次打开是为了：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("查资料", "回复消息", "娱乐休息", "无聊", "逃避任务", "其他").forEach { intent ->
                    AssistChip(
                        onClick = { selectedIntent = intent },
                        label = { Text(if (selectedIntent == intent) "✓ $intent" else intent) },
                    )
                }
            }

            Text(if (countdown > 0) "还需等待 $countdown 秒" else "可以继续，但建议确认是否真的需要。", style = MaterialTheme.typography.bodySmall)

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

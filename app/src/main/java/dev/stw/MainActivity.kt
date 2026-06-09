package dev.stw

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.stw.blocking.DemoBlockPrefs
import dev.stw.blocking.DemoDebugState
import dev.stw.blocking.FloatingReminderService
import dev.stw.usage.LaunchableAppInfo
import dev.stw.usage.UsageAppInfo
import dev.stw.usage.UsageStatsRepository
import dev.stw.usage.formatDuration
import dev.stw.usage.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
                onStartFloatingMonitor = {
                    val service = Intent(this, FloatingReminderService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                },
                onStopFloatingMonitor = {
                    stopService(Intent(this, FloatingReminderService::class.java))
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
    onStartFloatingMonitor: () -> Unit,
    onStopFloatingMonitor: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DemoHome(
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
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
    onStartFloatingMonitor: () -> Unit,
    onStopFloatingMonitor: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { UsageStatsRepository(context.applicationContext) }
    var hasUsageAccess by remember { mutableStateOf(false) }
    var usageRows by remember { mutableStateOf<List<UsageAppInfo>>(emptyList()) }
    var launchableApps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var restrictedPackage by remember { mutableStateOf(DemoBlockPrefs.restrictedPackage(context)) }
    var restrictedLabel by remember { mutableStateOf(DemoBlockPrefs.restrictedLabel(context)) }
    var debugState by remember { mutableStateOf(DemoBlockPrefs.debugState(context)) }
    var intentText by remember { mutableStateOf(DemoBlockPrefs.intents(context).joinToString("，")) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        withContext(Dispatchers.IO) {
            hasUsageAccess = repository.hasUsageAccess()
            usageRows = repository.loadTodayUsage(limit = 12)
            launchableApps = repository.loadLaunchableApps().take(30)
            restrictedPackage = DemoBlockPrefs.restrictedPackage(context)
            restrictedLabel = DemoBlockPrefs.restrictedLabel(context)
            debugState = DemoBlockPrefs.debugState(context)
            hasOverlayPermission = Settings.canDrawOverlays(context)
            intentText = DemoBlockPrefs.intents(context).joinToString("，")
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("时停 Stop the World", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("复杂 Demo：Usage Access 统计今日使用数据，并通过 AccessibilityService 在真实打开受限 App 时显示暂停提醒。")

        StatusCard(
            hasUsageAccess = hasUsageAccess,
            restrictedLabel = restrictedLabel,
            restrictedPackage = restrictedPackage,
            onOpenUsageAccess = onOpenUsageAccess,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onStartFloatingMonitor = onStartFloatingMonitor,
            onStopFloatingMonitor = onStopFloatingMonitor,
            hasOverlayPermission = hasOverlayPermission,
            onRefresh = { refreshTick++ },
        )

        DebugCard(debugState = debugState)

        IntentConfigCard(
            intentText = intentText,
            onIntentTextChange = { intentText = it },
            onSave = {
                DemoBlockPrefs.setIntents(context, intentText.split('，', ',', '|', '\n'))
                intentText = DemoBlockPrefs.intents(context).joinToString("，")
            },
        )

        UsageStatsCard(usageRows = usageRows)

        RestrictedAppPicker(
            usageRows = usageRows,
            launchableApps = launchableApps,
            currentPackage = restrictedPackage,
            onSelect = { packageName, label ->
                DemoBlockPrefs.setRestrictedApp(context, packageName, label)
                restrictedPackage = packageName
                restrictedLabel = label
            },
            onClear = {
                DemoBlockPrefs.clearRestrictedApp(context)
                restrictedPackage = null
                restrictedLabel = null
            },
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

@Composable
private fun StatusCard(
    hasUsageAccess: Boolean,
    restrictedLabel: String?,
    restrictedPackage: String?,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartFloatingMonitor: () -> Unit,
    onStopFloatingMonitor: () -> Unit,
    hasOverlayPermission: Boolean,
    onRefresh: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Demo 状态", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Usage Access：${if (hasUsageAccess) "已授权" else "未授权"}")
            Text("悬浮窗权限：${if (hasOverlayPermission) "已授权" else "未授权"}")
            Text("当前受限 App：${restrictedLabel ?: "未选择"}${restrictedPackage?.let { " ($it)" } ?: ""}")
            Text("推荐流程：Usage Access + 悬浮窗权限 → 选择受限 App → 启动极速监控 → 打开目标 App。无障碍服务保留为辅助。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onOpenUsageAccess, modifier = Modifier.weight(1f)) { Text("Usage") }
                Button(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f)) { Text("悬浮窗") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartFloatingMonitor, modifier = Modifier.weight(1f)) { Text("启动极速监控") }
                OutlinedButton(onClick = onStopFloatingMonitor, modifier = Modifier.weight(1f)) { Text("停止") }
            }
            OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) { Text("无障碍服务（辅助方案）") }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("刷新统计/规则") }
        }
    }
}

@Composable
private fun DebugCard(debugState: DemoDebugState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("无障碍调试", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("最近检测前台：${debugState.lastSeenPackage ?: "无"}")
            Text("检测时间：${if (debugState.lastSeenAt > 0) formatTime(debugState.lastSeenAt) else "无"}")
            Text("最近触发提醒：${debugState.lastTriggerPackage ?: "无"}")
            Text("触发时间：${if (debugState.lastTriggerAt > 0) formatTime(debugState.lastTriggerAt) else "无"}")
            Text("最近跳过原因：${debugState.lastSkipReason ?: "无"}")
            Text("极速逻辑：只监听 TYPE_WINDOW_STATE_CHANGED，notificationTimeout=0，命中 event.packageName 后立即显示 Accessibility Overlay；调试写入已节流。")
        }
    }
}

@Composable
private fun IntentConfigCard(
    intentText: String,
    onIntentTextChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("打开 App 是为了什么？", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
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
private fun RestrictedAppPicker(
    usageRows: List<UsageAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    currentPackage: String?,
    onSelect: (String, String) -> Unit,
    onClear: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("选择真实提醒 App", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("建议先从今日使用统计里选择一个 App。开启无障碍服务后，真实打开它会出现提醒页。")
            if (currentPackage != null) {
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("清除当前受限 App") }
            }
            Text("今日常用 App", fontWeight = FontWeight.SemiBold)
            if (usageRows.isEmpty()) {
                Text("暂无 UsageStats 数据，下面显示可启动 App 列表。")
            }
            usageRows.take(6).forEach { row ->
                AppPickRow(
                    label = row.appLabel,
                    packageName = row.packageName,
                    selected = row.packageName == currentPackage,
                    subtitle = "${formatDuration(row.usedMillis)} · 打开 ${row.openCount} 次",
                    onClick = { onSelect(row.packageName, row.appLabel) },
                )
            }
            Text("可启动 App", fontWeight = FontWeight.SemiBold)
            launchableApps.take(10).forEach { app ->
                AppPickRow(
                    label = app.appLabel,
                    packageName = app.packageName,
                    selected = app.packageName == currentPackage,
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

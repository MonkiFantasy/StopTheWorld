package dev.stw

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StopTheWorldDemoApp(
                onOpenUsageAccess = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
            )
        }
    }
}

@Composable
fun StopTheWorldDemoApp(onOpenUsageAccess: () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DemoHome(onOpenUsageAccess = onOpenUsageAccess)
        }
    }
}

@Composable
private fun DemoHome(onOpenUsageAccess: () -> Unit) {
    val demoRule = remember {
        AppRule(
            packageName = "tv.danmaku.bili",
            appLabel = "Bilibili",
            dailyLimitMinutes = 30,
            maxOpenCountPerDay = 5,
            delayBeforeOpenSeconds = 15,
            unlockSessionMinutes = 5,
            customMessage = "你说过今晚要早点睡。先确认这是有意打开。",
        )
    }
    val demoState = remember {
        AppRuntimeState(
            packageName = demoRule.packageName,
            usedTodayMillis = 18 * 60_000L,
            openCountToday = 3,
        )
    }
    val decision = remember { RuleEngine().decide(demoRule, demoState) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("时停 Stop the World", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("一个 ScreenZen 风格的 Android 屏幕时间管理 Demo。当前版本只演示核心产品逻辑，不做真实拦截。")

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MVP 0.1 目标", fontWeight = FontWeight.Bold)
                Text("1. 引导开启 Usage Access")
                Text("2. 读取使用统计")
                Text("3. 选择受限 App")
                Text("4. 保存规则")
                Text("5. 演示第一次打开受限 App 的暂停页")
            }
        }

        Button(onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth()) {
            Text("打开 Usage Access 设置")
        }

        RuleCard(rule = demoRule, state = demoState)
        FirstOpenInterventionCard(rule = demoRule, decision = decision)
    }
}

@Composable
private fun RuleCard(rule: AppRule, state: AppRuntimeState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("示例规则：${rule.appLabel}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("今日已使用：${state.usedTodayMillis / 60_000} / ${rule.dailyLimitMinutes} 分钟")
            Text("今日已打开：${state.openCountToday} / ${rule.maxOpenCountPerDay} 次")
            Text("打开前等待：${rule.delayBeforeOpenSeconds} 秒")
            Text("短解锁时长：${rule.unlockSessionMinutes} 分钟")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstOpenInterventionCard(rule: AppRule, decision: Decision) {
    var selectedIntent by remember { mutableStateOf<String?>(null) }
    var countdown by remember(rule.packageName) { mutableIntStateOf(rule.delayBeforeOpenSeconds) }

    LaunchedEffect(rule.packageName) {
        while (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("你正在打开 ${rule.appLabel}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text("不打开了")
                }
                Button(onClick = {}, enabled = countdown == 0, modifier = Modifier.weight(1f)) {
                    Text("继续 ${rule.unlockSessionMinutes} 分钟")
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Demo 事件：app=${rule.packageName}, intent=${selectedIntent ?: "未选择"}, decision=${decision::class.simpleName}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

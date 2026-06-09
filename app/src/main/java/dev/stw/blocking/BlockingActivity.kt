package dev.stw.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

class BlockingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: packageName
        val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 10).coerceAtLeast(0)
        val unlockMinutes = intent.getIntExtra(EXTRA_UNLOCK_MINUTES, 5).coerceAtLeast(1)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: "你现在打开它，是有明确目的，还是只是习惯？"

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BlockingScreen(
                        appLabel = appLabel,
                        packageName = packageName,
                        delaySeconds = delaySeconds,
                        unlockMinutes = unlockMinutes,
                        message = message,
                        onReturn = { returnHome() },
                        onContinue = {
                            DemoBlockPrefs.setUnlockUntil(this, packageName, System.currentTimeMillis() + unlockMinutes * 60_000L, null)
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun returnHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_APP_LABEL = "appLabel"
        const val EXTRA_DELAY_SECONDS = "delaySeconds"
        const val EXTRA_UNLOCK_MINUTES = "unlockMinutes"
        const val EXTRA_MESSAGE = "message"

        fun createIntent(
            context: Context,
            packageName: String,
            appLabel: String,
            delaySeconds: Int,
            unlockMinutes: Int,
            message: String,
        ): Intent = Intent(context, BlockingActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_APP_LABEL, appLabel)
            putExtra(EXTRA_DELAY_SECONDS, delaySeconds)
            putExtra(EXTRA_UNLOCK_MINUTES, unlockMinutes)
            putExtra(EXTRA_MESSAGE, message)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockingScreen(
    appLabel: String,
    packageName: String,
    delaySeconds: Int,
    unlockMinutes: Int,
    message: String,
    onReturn: () -> Unit,
    onContinue: () -> Unit,
) {
    var selectedIntent by remember { mutableStateOf<String?>(null) }
    var countdown by remember(packageName, delaySeconds) { mutableIntStateOf(delaySeconds) }

    BackHandler(onBack = onReturn)

    LaunchedEffect(packageName, delaySeconds) {
        while (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("你正在打开 $appLabel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("先停一下", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("“$message”")
                Text(
                    text = "时停只使用无障碍服务检测前台 App 是否命中你设置的受限列表；这个 Demo 不读取、不保存、不上传屏幕内容或输入内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("这次打开是为了：", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("查资料", "回复消息", "娱乐休息", "无聊", "逃避任务", "其他").forEach { intent ->
                        AssistChip(
                            onClick = { selectedIntent = intent },
                            label = { Text(if (selectedIntent == intent) "✓ $intent" else intent) },
                        )
                    }
                }

                Text(
                    text = if (countdown > 0) "还需等待 $countdown 秒" else "可以继续，但建议确认是否真的需要。",
                    fontWeight = FontWeight.SemiBold,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onReturn, modifier = Modifier.weight(1f)) {
                        Text("不打开了")
                    }
                    Button(onClick = onContinue, enabled = countdown == 0, modifier = Modifier.weight(1f)) {
                        Text("继续 $unlockMinutes 分钟")
                    }
                }
            }
        }
    }
}

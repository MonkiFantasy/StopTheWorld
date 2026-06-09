package dev.stw.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

data class UsageAppInfo(
    val packageName: String,
    val appLabel: String,
    val usedMillis: Long,
    val openCount: Int,
    val lastTimeUsedMillis: Long,
)

data class LaunchableAppInfo(
    val packageName: String,
    val appLabel: String,
)

class UsageStatsRepository(private val context: Context) {
    private val packageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun loadTodayUsage(limit: Int = 20): List<UsageAppInfo> {
        if (!hasUsageAccess()) return emptyList()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()

        val openCounts = mutableMapOf<String, Int>()
        val event = UsageEvents.Event()
        val events = usageStatsManager.queryEvents(start, end)
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue
                openCounts[pkg] = (openCounts[pkg] ?: 0) + 1
            }
        }

        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .groupBy { it.packageName }
            .mapNotNull { (pkg, rows) ->
                val used = rows.sumOf { it.totalTimeInForeground }
                if (used <= 0L || pkg == context.packageName) return@mapNotNull null
                UsageAppInfo(
                    packageName = pkg,
                    appLabel = labelFor(pkg),
                    usedMillis = used,
                    openCount = openCounts[pkg] ?: 0,
                    lastTimeUsedMillis = rows.maxOf { it.lastTimeUsed },
                )
            }
            .sortedByDescending { it.usedMillis }
            .take(limit)
    }

    fun loadLaunchableApps(): List<LaunchableAppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { resolveInfo ->
                LaunchableAppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    appLabel = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                        .ifBlank { resolveInfo.activityInfo.packageName },
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.appLabel.lowercase() }
    }

    private fun labelFor(packageName: String): String = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrElse { packageName }
}

fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

fun formatTime(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

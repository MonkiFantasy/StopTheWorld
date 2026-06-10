package dev.stw.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import java.text.DateFormat
import dev.stw.blocking.DemoBlockPrefs
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
        val start = DemoBlockPrefs.currentUsageWindowStartMillis(context)
        val end = System.currentTimeMillis()

        val openCounts = mutableMapOf<String, Int>()
        val totals = mutableMapOf<String, Long>()
        val activeSince = mutableMapOf<String, Long>()
        val lastSeen = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()
        val events = usageStatsManager.queryEvents(start, end)
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName?.takeIf { it != context.packageName } ?: continue
            val at = event.timeStamp.coerceIn(start, end)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    openCounts[pkg] = (openCounts[pkg] ?: 0) + 1
                    activeSince.putIfAbsent(pkg, at)
                    lastSeen[pkg] = at
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val since = activeSince.remove(pkg)
                    if (since != null && at > since) totals[pkg] = (totals[pkg] ?: 0L) + (at - since)
                    lastSeen[pkg] = at
                }
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    activeSince.toMap().forEach { (activePkg, since) ->
                        if (at > since) totals[activePkg] = (totals[activePkg] ?: 0L) + (at - since)
                        lastSeen[activePkg] = at
                    }
                    activeSince.clear()
                }
            }
        }
        activeSince.forEach { (pkg, since) ->
            if (end > since) totals[pkg] = (totals[pkg] ?: 0L) + (end - since)
            lastSeen[pkg] = end
        }

        return totals
            .filterValues { it > 0L }
            .map { (pkg, used) ->
                UsageAppInfo(
                    packageName = pkg,
                    appLabel = labelFor(pkg),
                    usedMillis = used,
                    openCount = openCounts[pkg] ?: 0,
                    lastTimeUsedMillis = lastSeen[pkg] ?: start,
                )
            }
            .sortedByDescending { it.usedMillis }
            .take(limit)
    }

    fun loadLaunchableApps(): List<LaunchableAppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherApps = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { resolveInfo ->
                LaunchableAppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    appLabel = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                        .ifBlank { resolveInfo.activityInfo.packageName },
                )
            }

        val installedApps = packageManager.getInstalledApplications(0)
            .filter { it.enabled }
            .map { appInfo ->
                LaunchableAppInfo(
                    packageName = appInfo.packageName,
                    appLabel = packageManager.getApplicationLabel(appInfo).toString()
                        .ifBlank { appInfo.packageName },
                )
            }

        return (launcherApps + installedApps)
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<LaunchableAppInfo> { it.appLabel.lowercase() }.thenBy { it.packageName })
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

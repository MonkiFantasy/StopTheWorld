package dev.stw.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.stw.MainActivity
import dev.stw.R
import dev.stw.blocking.DemoBlockPrefs
import dev.stw.usage.formatDuration

class UsageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId -> updateWidget(context, appWidgetManager, appWidgetId) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, UsageWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val total = todayTotalUsageMillis(context)
        val views = RemoteViews(context.packageName, R.layout.widget_usage).apply {
            setTextViewText(R.id.widget_title, "今日手机使用")
            setTextViewText(R.id.widget_duration, formatDuration(total))
            setTextViewText(R.id.widget_subtitle, "点击刷新 / 打开时停")
            val openIntent = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_root, openPending)
            val refreshIntent = Intent(context, UsageWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val refreshPending = PendingIntent.getBroadcast(context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_duration, refreshPending)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun todayTotalUsageMillis(context: Context): Long = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = DemoBlockPrefs.currentUsageWindowStartMillis(context)
        val end = System.currentTimeMillis()
        val totals = mutableMapOf<String, Long>()
        val activeSince = mutableMapOf<String, Long>()
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName?.takeIf { it != context.packageName } ?: continue
            val at = event.timeStamp.coerceIn(start, end)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> activeSince.putIfAbsent(pkg, at)
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val since = activeSince.remove(pkg)
                    if (since != null && at > since) totals[pkg] = (totals[pkg] ?: 0L) + (at - since)
                }
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    activeSince.toMap().forEach { (activePkg, since) ->
                        if (at > since) totals[activePkg] = (totals[activePkg] ?: 0L) + (at - since)
                    }
                    activeSince.clear()
                }
            }
        }
        activeSince.forEach { (pkg, since) -> if (end > since) totals[pkg] = (totals[pkg] ?: 0L) + (end - since) }
        totals.values.sum()
    }.getOrDefault(0L)

    companion object {
        private const val ACTION_REFRESH = "dev.stw.widget.REFRESH_USAGE_WIDGET"
    }
}

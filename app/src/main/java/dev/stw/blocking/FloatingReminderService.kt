package dev.stw.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class FloatingReminderService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var miniView: View? = null
    private var countdownRunnable: Runnable? = null
    private var pollRunnable: Runnable? = null
    private var overlayPackage: String? = null
    private var lastForegroundPackage: String? = null
    private var lastTriggerAt = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        DemoBlockPrefs.setFloatingRunning(this, true)
        DemoBlockPrefs.markSkip(this, "floating_service_created")
        startForeground(NOTIFICATION_ID, notification())
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPolling()
        hideOverlay()
        hideMini()
        DemoBlockPrefs.setFloatingRunning(this, false)
        super.onDestroy()
    }

    private fun startPolling() {
        if (pollRunnable != null) return
        pollRunnable = object : Runnable {
            override fun run() {
                checkForeground()
                handler.postDelayed(this, POLL_MS)
            }
        }
        handler.post(pollRunnable!!)
    }

    private fun stopPolling() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
    }

    private fun checkForeground() {
        // Keep polling as a safety net even when Accessibility is enabled. A shared trigger lock
        // below prevents double popups; this improves MIUI/OEM cases where accessibility events are
        // delayed or the active-window list is temporarily wrong.
        if (!Settings.canDrawOverlays(this)) {
            DemoBlockPrefs.markSkip(this, "overlay_permission_missing")
            return
        }
        val restrictedPackages = DemoBlockPrefs.restrictedPackages(this)
        if (restrictedPackages.isEmpty()) {
            DemoBlockPrefs.markSkip(this, "monitor_running_no_restricted")
            return
        }
        val fg = latestForegroundPackage() ?: run {
            DemoBlockPrefs.markSkip(this, "monitor_running_fg_null")
            return
        }
        if (fg != lastForegroundPackage) {
            lastForegroundPackage = fg
            DemoBlockPrefs.markSeen(this, fg)
        }
        if (fg == packageName) return
        if (fg !in restrictedPackages) return
        val now = System.currentTimeMillis()
        if (DemoBlockPrefs.unlockUntil(this, fg) > now) return
        if (overlayView != null && overlayPackage == fg) return
        if (now - lastTriggerAt < 700L) return
        if (!DemoBlockPrefs.canShowBlock(this, fg, now, "fallback_usage_poll")) return
        lastTriggerAt = now
        showOverlay(fg, DemoBlockPrefs.labelForPackage(this, fg) ?: fg, "fallback_usage_poll")
    }

    private fun latestForegroundPackage(): String? = runCatching {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 60_000L

        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var pkg: String? = null
        var ts = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if ((event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) && event.timeStamp >= ts) {
                pkg = event.packageName
                ts = event.timeStamp
            }
        }
        // UsageEvents can arrive late on MIUI. Do not re-trigger from an old foreground event
        // after the user has chosen "do not open" and gone Home.
        if (pkg != null && end - ts <= 2_500L) return@runCatching pkg
        if (pkg != null) {
            DemoBlockPrefs.markSkip(this, "fg_event_stale:${end - ts}ms:$pkg")
            return@runCatching null
        }

        // Fallback for devices/OEMs where queryEvents is delayed or sparse.
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }.onFailure {
        DemoBlockPrefs.markSkip(this, "fg_error:${it.javaClass.simpleName}")
    }.getOrNull()

    private fun showOverlay(packageName: String, appLabel: String, source: String) {
        hideOverlay()
        hideMini()
        overlayPackage = packageName
        val group = DemoBlockPrefs.groupForPackage(this, packageName)
        val handles = BlockOverlayUi.build(
            context = this,
            appLabel = appLabel,
            groupName = group?.name,
            limitSnapshot = DemoBlockPrefs.groupLimitSnapshot(this, packageName, group),
            requireTypedPurpose = DemoBlockPrefs.requireTypedPurposeForPackage(this, packageName),
            intents = DemoBlockPrefs.purposeOptionsForPackage(this, packageName),
            onCancel = {
                DemoBlockPrefs.suppressAfterCancel(this, packageName)
                hideOverlay()
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(home)
            },
            onContinue = { chosen ->
                DemoBlockPrefs.setUnlockUntil(this, packageName, System.currentTimeMillis() + 5 * 60_000L, chosen)
                hideOverlay()
                showMini(chosen ?: "有意使用")
            },
        )

        overlayView = handles.root
        runCatching {
            windowManager?.addView(handles.root, fullParams())
            countdownRunnable = BlockOverlayUi.startCountdown(handler, handles)
            DemoBlockPrefs.markBlockShown(this, packageName, System.currentTimeMillis(), source)
        }.onFailure {
            overlayView = null
            overlayPackage = null
            DemoBlockPrefs.markSkip(this, "float_overlay_error:${it.javaClass.simpleName}")
        }
    }

    private fun showMini(intentText: String) {
        hideMini()
        val mini = BlockOverlayUi.buildMini(this, intentText) { hideMini() }
        miniView = mini
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 40.dp
            title = "Stop the World mini reminder"
        }
        runCatching { windowManager?.addView(mini, params) }
        handler.postDelayed({ hideMini() }, 5 * 60_000L)
    }

    private fun hideOverlay() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
        overlayPackage = null
    }

    private fun hideMini() {
        miniView?.let { runCatching { windowManager?.removeView(it) } }
        miniView = null
    }

    private fun fullParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.CENTER; title = "Stop the World blocker" }

    private fun notification(): Notification {
        val channelId = "stw_monitor"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "时停监控", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId).setContentTitle("时停极速监控运行中").setContentText("用于检测受限 App 并显示提醒").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("时停极速监控运行中").setContentText("用于检测受限 App 并显示提醒").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val POLL_MS = 250L
        private const val NOTIFICATION_ID = 4301
        const val ACTION_STOP = "dev.stw.blocking.STOP_FLOATING_REMINDER"
    }
}

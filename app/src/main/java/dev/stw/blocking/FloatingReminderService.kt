package dev.stw.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

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
        if (!Settings.canDrawOverlays(this)) {
            DemoBlockPrefs.markSkip(this, "overlay_permission_missing")
            return
        }
        val restricted = DemoBlockPrefs.restrictedPackage(this) ?: return
        val fg = latestForegroundPackage() ?: return
        if (fg != lastForegroundPackage) {
            lastForegroundPackage = fg
            DemoBlockPrefs.markSeen(this, fg)
        }
        if (fg == packageName) return
        if (fg != restricted) return
        val now = System.currentTimeMillis()
        if (DemoBlockPrefs.unlockUntil(this, fg) > now) return
        if (overlayView != null && overlayPackage == fg) return
        if (now - lastTriggerAt < 1_000L) return
        lastTriggerAt = now
        DemoBlockPrefs.markBlocked(this, fg, now)
        showOverlay(fg, DemoBlockPrefs.restrictedLabel(this) ?: fg)
    }

    private fun latestForegroundPackage(): String? = runCatching {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 1_500L, end)
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
        pkg
    }.getOrNull()

    private fun showOverlay(packageName: String, appLabel: String) {
        hideOverlay()
        hideMini()
        overlayPackage = packageName
        val intents = DemoBlockPrefs.intents(this)
        var selectedIntent: String? = intents.firstOrNull()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20.dp, 20.dp, 20.dp, 20.dp)
            setBackgroundColor(0xCC0F172A.toInt())
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 20.dp, 22.dp, 20.dp)
            background = rounded(Color.WHITE, 24.dp)
            elevation = 10f
        }
        root.addView(card, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        card.addText("时停 · Stop the World", 12f, true, Color.rgb(37, 99, 235), 0)
        card.addText("先停一下", 30f, true, Color.rgb(15, 23, 42), 10.dp)
        card.addText("你正在打开 $appLabel", 17f, true, Color.rgb(51, 65, 85), 4.dp)
        card.addText("这次打开是为了什么？", 16f, false, Color.rgb(71, 85, 105), 8.dp)

        val chips = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        intents.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { item ->
                val chip = TextView(this).apply {
                    text = if (item == selectedIntent) "✓ $item" else item
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setTextColor(if (item == selectedIntent) Color.rgb(55, 48, 163) else Color.rgb(51, 65, 85))
                    setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                    background = rounded(if (item == selectedIntent) Color.rgb(224, 231, 255) else Color.rgb(241, 245, 249), 999.dp)
                    setOnClickListener {
                        selectedIntent = item
                        text = "✓ $item"
                        setTextColor(Color.rgb(55, 48, 163))
                        background = rounded(Color.rgb(224, 231, 255), 999.dp)
                    }
                }
                row.addView(chip, LinearLayout.LayoutParams(0, 42.dp, 1f).apply { setMargins(3.dp, 4.dp, 3.dp, 4.dp) })
            }
            chips.addView(row)
        }
        card.addView(chips)

        val countdownText = TextView(this).apply {
            text = "还需等待 3 秒"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(55, 48, 163))
            setPadding(0, 14.dp, 0, 12.dp)
        }
        card.addView(countdownText)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = button("不打开了", Color.rgb(239, 246, 255), Color.rgb(30, 64, 175)) {
            hideOverlay()
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
        }
        val cont = button("继续 5 分钟", Color.rgb(37, 99, 235), Color.WHITE) {
            val chosen = selectedIntent
            DemoBlockPrefs.setUnlockUntil(this, packageName, System.currentTimeMillis() + 5 * 60_000L, chosen)
            hideOverlay()
            showMini(chosen ?: "有意使用", appLabel)
        }.apply { isEnabled = false; alpha = 0.45f }
        row.addView(cancel, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { rightMargin = 6.dp })
        row.addView(cont, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { leftMargin = 6.dp })
        card.addView(row)
        card.addText("仅检测前台 App 包名，不读取屏幕内容。", 12f, false, Color.rgb(100, 116, 139), 10.dp)

        overlayView = root
        runCatching {
            windowManager?.addView(root, fullParams())
            startCountdown(countdownText, cont)
        }.onFailure { DemoBlockPrefs.markSkip(this, "float_overlay_error:${it.javaClass.simpleName}") }
    }

    private fun showMini(intentText: String, appLabel: String) {
        hideMini()
        val mini = TextView(this).apply {
            text = "时停：$intentText"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            background = rounded(0xDD2563EB.toInt(), 999.dp)
            setOnClickListener { hideMini() }
        }
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

    private fun startCountdown(textView: TextView, continueButton: Button) {
        var remaining = 3
        countdownRunnable = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    textView.text = "还需等待 $remaining 秒"
                    remaining -= 1
                    handler.postDelayed(this, 1_000L)
                } else {
                    textView.text = "可以继续，也可以选择不打开。"
                    continueButton.isEnabled = true
                    continueButton.alpha = 1f
                }
            }
        }
        handler.post(countdownRunnable!!)
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

    private fun button(text: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(fg)
        background = rounded(bg, 16.dp)
        setOnClickListener { onClick() }
    }

    private fun LinearLayout.addText(textValue: String, sizeSp: Float, bold: Boolean, color: Int, top: Int) {
        addView(TextView(this@FloatingReminderService).apply {
            text = textValue
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, top, 0, 2.dp)
        })
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val POLL_MS = 250L
        private const val NOTIFICATION_ID = 4301
        const val ACTION_STOP = "dev.stw.blocking.STOP_FLOATING_REMINDER"
    }
}

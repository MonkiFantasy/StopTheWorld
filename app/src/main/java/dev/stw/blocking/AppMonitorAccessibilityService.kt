package dev.stw.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AppMonitorAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayPackage: String? = null
    private var countdownRunnable: Runnable? = null
    private var lastEvaluatedPackage: String? = null
    private var lastEvaluatedAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        DemoBlockPrefs.markSkip(this, "service_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val eventType = event.eventType
        DemoBlockPrefs.markSeen(this, "event:$eventType:$packageName")

        // Fast path: TYPE_WINDOW_STATE_CHANGED is usually the app/activity switch event.
        // Do not query UsageStats here; it is too slow and can make low-end phones stutter.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            evaluatePackage(packageName)
            return
        }

        // Lightweight fallback for devices that only emit windows-changed around app switch.
        // Debounce aggressively and only use the event package; no root/usage polling.
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val now = System.currentTimeMillis()
            if (packageName == lastEvaluatedPackage && now - lastEvaluatedAt < 350L) return
            handler.postDelayed({ evaluatePackage(packageName) }, 80L)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun evaluatePackage(packageName: String) {
        val now = System.currentTimeMillis()
        lastEvaluatedPackage = packageName
        lastEvaluatedAt = now

        if (packageName == applicationContext.packageName) {
            DemoBlockPrefs.markSkip(this, "self_app_foreground")
            return
        }

        val restricted = DemoBlockPrefs.restrictedPackage(this)
        if (restricted == null) {
            DemoBlockPrefs.markSkip(this, "no_restricted_app")
            return
        }
        if (packageName != restricted) {
            DemoBlockPrefs.markSkip(this, "not_restricted: $packageName")
            return
        }

        val unlockUntil = DemoBlockPrefs.unlockUntil(this, packageName)
        if (unlockUntil > now) {
            DemoBlockPrefs.markSkip(this, "unlocked_until_$unlockUntil")
            return
        }
        if (overlayView != null && overlayPackage == packageName) {
            DemoBlockPrefs.markSkip(this, "overlay_already_showing")
            return
        }
        if (now - DemoBlockPrefs.lastBlockedAt(this, packageName) < 650L) {
            DemoBlockPrefs.markSkip(this, "trigger_debounce")
            return
        }

        DemoBlockPrefs.markBlocked(this, packageName, now)
        showOverlay(packageName, DemoBlockPrefs.restrictedLabel(this) ?: packageName)
    }

    private fun showOverlay(packageName: String, appLabel: String) {
        hideOverlay()
        overlayPackage = packageName

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
            elevation = 12f
        }
        root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        card.addText("时停 · Stop the World", 12f, true, Color.rgb(37, 99, 235), top = 0)
        card.addText("你正在打开 $appLabel", 18f, true, Color.rgb(17, 24, 39), top = 12.dp)
        card.addText("先停一下", 28f, true, Color.rgb(17, 24, 39), top = 0)
        card.addText("这是有意打开，还是习惯性点开？", 16f, false, Color.rgb(55, 65, 81), top = 6.dp)

        val countdownText = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(17, 24, 39))
            gravity = Gravity.CENTER
            text = "还需等待 5 秒"
            setPadding(0, 18.dp, 0, 14.dp)
        }
        card.addView(countdownText)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancelButton = Button(this).apply {
            text = "不打开了"
            setTextColor(Color.rgb(30, 64, 175))
            background = rounded(Color.rgb(239, 246, 255), 16.dp, strokeColor = Color.rgb(147, 197, 253), strokeWidth = 1.dp)
            setOnClickListener {
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        val continueButton = Button(this).apply {
            text = "继续 5 分钟"
            isEnabled = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(37, 99, 235), 16.dp)
            alpha = 0.45f
            setOnClickListener {
                DemoBlockPrefs.setUnlockUntil(
                    context = this@AppMonitorAccessibilityService,
                    packageName = packageName,
                    untilMillis = System.currentTimeMillis() + 5 * 60_000L,
                    intent = null,
                )
                hideOverlay()
            }
        }
        row.addView(cancelButton, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { rightMargin = 6.dp })
        row.addView(continueButton, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { leftMargin = 6.dp })
        card.addView(row)

        card.addText("隐私：仅检测前台 App 包名，不读取屏幕内容。", 12f, false, Color.rgb(100, 116, 139), top = 12.dp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        overlayView = root
        runCatching {
            windowManager?.addView(root, params)
            startCountdown(countdownText, continueButton)
        }.onFailure { error ->
            overlayView = null
            overlayPackage = null
            DemoBlockPrefs.markSkip(this, "overlay_error: ${error.javaClass.simpleName}")
            startActivity(
                BlockingActivity.createIntent(
                    context = this,
                    packageName = packageName,
                    appLabel = appLabel,
                    delaySeconds = 5,
                    unlockMinutes = 5,
                    message = "这是有意打开，还是习惯性点开？",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    private fun startCountdown(textView: TextView, continueButton: Button) {
        var remaining = 5
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
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlayView = null
        overlayPackage = null
    }

    private fun LinearLayout.addText(textValue: String, sizeSp: Float, bold: Boolean, color: Int, top: Int = 8.dp) {
        addView(
            TextView(this@AppMonitorAccessibilityService).apply {
                text = textValue
                textSize = sizeSp
                setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, top, 0, 2.dp)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}

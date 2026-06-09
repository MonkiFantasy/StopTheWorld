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

/**
 * Ultra-light demo blocker.
 *
 * Runtime performance rules:
 * - only handle TYPE_WINDOW_STATE_CHANGED;
 * - no UsageStats query inside accessibility callback;
 * - no rootInActiveWindow lookup;
 * - no repeated delayed foreground polling;
 * - no SharedPreferences debug write for every non-target event;
 * - minimal View hierarchy for overlay.
 */
class AppMonitorAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayPackage: String? = null
    private var countdownRunnable: Runnable? = null
    private var lastTargetTriggerAt = 0L
    private var lastDebugWriteAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        writeDebug("service_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (packageName == applicationContext.packageName) return

        val restricted = DemoBlockPrefs.restrictedPackage(this) ?: return
        if (packageName != restricted) return

        val now = System.currentTimeMillis()
        if (DemoBlockPrefs.unlockUntil(this, packageName) > now) return
        if (overlayView != null && overlayPackage == packageName) return
        if (now - lastTargetTriggerAt < 500L) return
        lastTargetTriggerAt = now

        // Only write debug on actual target trigger, not for every foreground event.
        DemoBlockPrefs.markSeen(this, packageName, now)
        DemoBlockPrefs.markBlocked(this, packageName, now)
        showOverlay(packageName, DemoBlockPrefs.restrictedLabel(this) ?: packageName)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
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
            elevation = 10f
        }
        root.addView(card, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        card.addText("时停 · Stop the World", 12f, true, Color.rgb(37, 99, 235), 0)
        card.addText("先停一下", 30f, true, Color.rgb(15, 23, 42), 10.dp)
        card.addText("你正在打开 $appLabel", 17f, true, Color.rgb(51, 65, 85), 4.dp)
        card.addText("这是有意打开，还是习惯性点开？", 16f, false, Color.rgb(71, 85, 105), 8.dp)

        val countdownText = TextView(this).apply {
            text = "还需等待 3 秒"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(55, 48, 163))
            setPadding(0, 18.dp, 0, 14.dp)
        }
        card.addView(countdownText)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = Button(this).apply {
            text = "不打开了"
            setTextColor(Color.rgb(30, 64, 175))
            background = rounded(Color.rgb(239, 246, 255), 16.dp, Color.rgb(147, 197, 253), 1.dp)
            setOnClickListener {
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        val cont = Button(this).apply {
            text = "继续 5 分钟"
            isEnabled = false
            alpha = 0.45f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(37, 99, 235), 16.dp)
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
        row.addView(cancel, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { rightMargin = 6.dp })
        row.addView(cont, LinearLayout.LayoutParams(0, 50.dp, 1f).apply { leftMargin = 6.dp })
        card.addView(row)

        val privacy = TextView(this).apply {
            text = "仅检测前台 App 包名，不读取屏幕内容。"
            textSize = 12f
            setTextColor(Color.rgb(100, 116, 139))
            gravity = Gravity.CENTER
            setPadding(0, 12.dp, 0, 0)
        }
        card.addView(privacy)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            title = "Stop the World"
        }

        overlayView = root
        runCatching {
            windowManager?.addView(root, params)
            startCountdown(countdownText, cont)
            writeDebug("overlay_added:$packageName")
        }.onFailure { error ->
            overlayView = null
            overlayPackage = null
            writeDebug("overlay_error:${error.javaClass.simpleName}")
            runCatching {
                startActivity(
                    BlockingActivity.createIntent(
                        context = this,
                        packageName = packageName,
                        appLabel = appLabel,
                        delaySeconds = 3,
                        unlockMinutes = 5,
                        message = "这是有意打开，还是习惯性点开？",
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }
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

    private fun writeDebug(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastDebugWriteAt > 1_000L) {
            lastDebugWriteAt = now
            DemoBlockPrefs.markSkip(this, reason)
        }
    }

    private fun LinearLayout.addText(textValue: String, sizeSp: Float, bold: Boolean, color: Int, top: Int) {
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

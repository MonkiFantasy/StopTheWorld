package dev.stw.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        DemoBlockPrefs.markSeen(this, packageName)

        if (packageName == applicationContext.packageName) {
            DemoBlockPrefs.markSkip(this, "self_app")
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

        val now = System.currentTimeMillis()
        if (DemoBlockPrefs.unlockUntil(this, packageName) > now) {
            DemoBlockPrefs.markSkip(this, "unlocked_until_${DemoBlockPrefs.unlockUntil(this, packageName)}")
            hideOverlay()
            return
        }
        if (overlayView != null && overlayPackage == packageName) {
            DemoBlockPrefs.markSkip(this, "overlay_already_showing")
            return
        }

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
            setPadding(36, 36, 36, 36)
            setBackgroundColor(0xEE111827.toInt())
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 32)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        card.addText("你正在打开 $appLabel", 20f, true, Color.rgb(17, 24, 39))
        card.addText("先停一下", 28f, true, Color.rgb(17, 24, 39))
        card.addText("你现在打开它，是有明确目的，还是只是习惯性点开？", 16f, false, Color.rgb(55, 65, 81))
        card.addText("隐私：只检测前台 App 包名，不读取屏幕文字、聊天内容、输入内容或密码。", 12f, false, Color.rgb(107, 114, 128))
        card.addText("这次打开是为了：查资料 / 回复消息 / 娱乐休息 / 无聊 / 逃避任务", 14f, false, Color.rgb(55, 65, 81))

        val countdownText = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(17, 24, 39))
            text = "还需等待 10 秒"
            setPadding(0, 20, 0, 20)
        }
        card.addView(countdownText)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancelButton = Button(this).apply {
            text = "不打开了"
            setOnClickListener {
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        val continueButton = Button(this).apply {
            text = "继续 5 分钟"
            isEnabled = false
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
        row.addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(continueButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayView = root
        runCatching {
            windowManager?.addView(root, params)
            startCountdown(countdownText, continueButton)
        }.onFailure { error ->
            overlayView = null
            overlayPackage = null
            DemoBlockPrefs.markSkip(this, "overlay_error: ${error.javaClass.simpleName}")
            // Fallback for devices that block overlays from services.
            startActivity(
                BlockingActivity.createIntent(
                    context = this,
                    packageName = packageName,
                    appLabel = appLabel,
                    delaySeconds = 10,
                    unlockMinutes = 5,
                    message = "你现在打开它，是有明确目的，还是只是习惯性点开？",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    private fun startCountdown(textView: TextView, continueButton: Button) {
        var remaining = 10
        countdownRunnable = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    textView.text = "还需等待 $remaining 秒"
                    remaining -= 1
                    handler.postDelayed(this, 1_000L)
                } else {
                    textView.text = "可以继续，但也可以选择不打开。"
                    continueButton.isEnabled = true
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun hideOverlay() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        overlayPackage = null
    }

    private fun LinearLayout.addText(textValue: String, sizeSp: Float, bold: Boolean, color: Int) {
        addView(
            TextView(this@AppMonitorAccessibilityService).apply {
                text = textValue
                textSize = sizeSp
                setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 8, 0, 8)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }
}

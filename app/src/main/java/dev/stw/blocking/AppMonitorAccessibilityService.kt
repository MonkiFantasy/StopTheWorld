package dev.stw.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
    private var pendingShowRunnable: Runnable? = null

    private val intents = listOf("查资料", "回复消息", "娱乐休息", "无聊", "逃避任务", "其他")
    private var selectedIntent: String? = null
    private var selectedChip: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = 0
        }
        DemoBlockPrefs.markSkip(this, "service_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        DemoBlockPrefs.markSeen(this, packageName)

        if (packageName == applicationContext.packageName) {
            DemoBlockPrefs.markSkip(this, "self_app")
            return
        }

        val restricted = DemoBlockPrefs.restrictedPackage(this)
        if (restricted == null) {
            DemoBlockPrefs.markSkip(this, "no_restricted_app")
            hideOverlay()
            return
        }
        if (packageName != restricted) {
            DemoBlockPrefs.markSkip(this, "not_restricted: $packageName")
            if (overlayView != null && overlayPackage != restricted) hideOverlay()
            return
        }

        val now = System.currentTimeMillis()
        val unlockUntil = DemoBlockPrefs.unlockUntil(this, packageName)
        if (unlockUntil > now) {
            DemoBlockPrefs.markSkip(this, "unlocked_until_$unlockUntil")
            hideOverlay()
            return
        }
        if (overlayView != null && overlayPackage == packageName) {
            DemoBlockPrefs.markSkip(this, "overlay_already_showing")
            return
        }

        // Let the target app finish its foreground transition, then attach the accessibility overlay.
        // This avoids falling back to a background Activity launch, which many Android/OEM builds delay
        // until our own app returns to foreground.
        pendingShowRunnable?.let { handler.removeCallbacks(it) }
        pendingShowRunnable = Runnable {
            DemoBlockPrefs.markBlocked(this, packageName, System.currentTimeMillis())
            showOverlay(packageName, DemoBlockPrefs.restrictedLabel(this) ?: packageName)
        }
        handler.postDelayed(pendingShowRunnable!!, 120L)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay(packageName: String, appLabel: String) {
        hideOverlay()
        overlayPackage = packageName
        selectedIntent = null
        selectedChip = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF20F172A.toInt(), 0xF21E1B4B.toInt()),
            )
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            background = roundRect(color = 0xFFFFFFFF.toInt(), radiusDp = 30)
            elevation = dp(10).toFloat()
        }
        root.addView(
            card,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            },
        )

        card.addPill("时停提醒", 0xFFEDE9FE.toInt(), 0xFF5B21B6.toInt())
        card.addSpace(12)
        card.addText("你正在打开", 14f, false, 0xFF64748B.toInt())
        card.addText(appLabel, 28f, true, 0xFF0F172A.toInt())
        card.addText("先停一下", 24f, true, 0xFF0F172A.toInt(), topDp = 12)
        card.addText("你现在打开它，是有明确目的，还是只是习惯性点开？", 16f, false, 0xFF334155.toInt(), topDp = 6)

        val privacy = TextView(this).apply {
            text = "隐私：只检测前台 App 包名，不读取屏幕文字、聊天内容、输入内容或密码。"
            textSize = 12f
            setTextColor(0xFF475569.toInt())
            setLineSpacing(0f, 1.08f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundRect(0xFFF8FAFC.toInt(), 16, strokeColor = 0xFFE2E8F0.toInt(), strokeDp = 1)
        }
        card.addView(privacy, matchWrap(top = 14))

        card.addText("这次打开是为了：", 15f, true, 0xFF0F172A.toInt(), topDp = 16)
        val chipGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        intents.chunked(3).forEach { rowIntents ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowIntents.forEach { label ->
                val chip = TextView(this).apply {
                    text = label
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF334155.toInt())
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = roundRect(0xFFF1F5F9.toInt(), 999, strokeColor = 0xFFE2E8F0.toInt(), strokeDp = 1)
                    setOnClickListener { selectIntentChip(this, label) }
                }
                row.addView(chip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(3)
                    rightMargin = dp(3)
                    topMargin = dp(8)
                })
            }
            chipGrid.addView(row)
        }
        card.addView(chipGrid, matchWrap())

        val countdownText = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF4338CA.toInt())
            gravity = Gravity.CENTER
            text = "还需等待 10 秒"
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundRect(0xFFEEF2FF.toInt(), 20)
        }
        card.addView(countdownText, matchWrap(top = 18))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancelButton = styledButton(
            text = "不打开了",
            backgroundColor = 0xFFFFFFFF.toInt(),
            textColor = 0xFF1E293B.toInt(),
            strokeColor = 0xFFCBD5E1.toInt(),
        ) {
            hideOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        val continueButton = styledButton(
            text = "继续 5 分钟",
            backgroundColor = 0xFF4F46E5.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
        ) {
            DemoBlockPrefs.setUnlockUntil(
                context = this@AppMonitorAccessibilityService,
                packageName = packageName,
                untilMillis = System.currentTimeMillis() + 5 * 60_000L,
                intent = selectedIntent,
            )
            hideOverlay()
        }.apply {
            isEnabled = false
            alpha = 0.45f
        }
        row.addView(cancelButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        row.addView(continueButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        card.addView(row, matchWrap(top = 16))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            title = "Stop the World reminder"
        }

        overlayView = root
        runCatching {
            windowManager?.addView(root, params)
            DemoBlockPrefs.markSkip(this, "overlay_added")
            startCountdown(countdownText, continueButton)
        }.onFailure { error ->
            overlayView = null
            overlayPackage = null
            DemoBlockPrefs.markSkip(this, "overlay_error: ${error.javaClass.simpleName}: ${error.message?.take(80)}")
            // Last-resort fallback only; do not rely on it for normal flow because background Activity
            // starts are often delayed until the user returns to this app.
            runCatching {
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
            }.onFailure { fallbackError ->
                DemoBlockPrefs.markSkip(this, "fallback_error: ${fallbackError.javaClass.simpleName}")
            }
        }
    }

    private fun selectIntentChip(chip: TextView, label: String) {
        selectedChip?.apply {
            text = text.toString().removePrefix("✓ ")
            setTextColor(0xFF334155.toInt())
            background = roundRect(0xFFF1F5F9.toInt(), 999, strokeColor = 0xFFE2E8F0.toInt(), strokeDp = 1)
        }
        selectedIntent = label
        selectedChip = chip
        chip.text = "✓ $label"
        chip.setTextColor(0xFF3730A3.toInt())
        chip.background = roundRect(0xFFE0E7FF.toInt(), 999, strokeColor = 0xFF818CF8.toInt(), strokeDp = 1)
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
                    continueButton.alpha = 1f
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun hideOverlay() {
        pendingShowRunnable?.let { handler.removeCallbacks(it) }
        pendingShowRunnable = null
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlayView = null
        overlayPackage = null
        selectedIntent = null
        selectedChip = null
    }

    private fun LinearLayout.addText(textValue: String, sizeSp: Float, bold: Boolean, color: Int, topDp: Int = 0) {
        addView(
            TextView(this@AppMonitorAccessibilityService).apply {
                text = textValue
                textSize = sizeSp
                setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setLineSpacing(0f, 1.06f)
            },
            matchWrap(top = topDp),
        )
    }

    private fun LinearLayout.addPill(textValue: String, backgroundColor: Int, textColor: Int) {
        val pill = TextView(this@AppMonitorAccessibilityService).apply {
            text = textValue
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundRect(backgroundColor, 999)
        }
        addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.addSpace(heightDp: Int) {
        addView(View(this@AppMonitorAccessibilityService), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun styledButton(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        strokeColor: Int? = null,
        onClick: View.OnClickListener,
    ): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(textColor)
        isAllCaps = false
        background = roundRect(backgroundColor, 18, strokeColor, 1)
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener(onClick)
    }

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun roundRect(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null && strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

package dev.stw.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Primary real-time trigger for the demo.
 *
 * Design goals:
 * - Accessibility is the main trigger because it is event-driven.
 * - We only use package/window identity; no text, no input, no screen content is read.
 * - FloatingReminderService is fallback only, so this service never suppresses itself because
 *   the fallback monitor is running.
 * - Stale events are ignored: self/system packages cannot trigger; repeated target events are
 *   de-bounced; unlocked sessions are respected.
 */
class AppMonitorAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var miniView: View? = null
    private var overlayPackage: String? = null
    private var countdownRunnable: Runnable? = null
    private var lastTargetTriggerAt = 0L
    private var lastSeenPackage: String? = null
    private var lastSeenWriteAt = 0L
    private var pendingCheck: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        DemoBlockPrefs.markSkip(this, "accessibility_primary_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val eventPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        val activePackage = activeWindowPackageName()
        val seenPackage = activePackage ?: eventPackage ?: return
        val now = System.currentTimeMillis()

        if (seenPackage != lastSeenPackage && now - lastSeenWriteAt > 250L) {
            lastSeenPackage = seenPackage
            lastSeenWriteAt = now
            DemoBlockPrefs.markSeen(this, seenPackage, now)
        }

        val restrictedPackages = DemoBlockPrefs.restrictedPackages(this)
        val restricted = eventPackage?.takeIf { it in restrictedPackages } ?: activePackage?.takeIf { it in restrictedPackages } ?: return

        // MIUI/Chromium-style apps can report the target package before the active-window list is
        // stable, while waiting for active-window verification can miss the instant-open moment.
        // Use a fast event path for the exact target package, then let the shared trigger lock
        // suppress duplicates from delayed verification/fallback polling.
        if (eventPackage == restricted && activePackage != applicationContext.packageName) {
            attemptBlock(restricted, "accessibility_event_fast:active=$activePackage")
            scheduleVerifiedBlockCheck(restricted, "event=${event.eventType},eventPkg=$eventPackage,active=$activePackage")
            return
        }

        scheduleVerifiedBlockCheck(restricted, "event=${event.eventType},eventPkg=$eventPackage,active=$activePackage")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        hideMini()
        DemoBlockPrefs.markSkip(this, "accessibility_primary_destroyed")
        super.onDestroy()
    }

    private fun scheduleVerifiedBlockCheck(restricted: String, reason: String) {
        pendingCheck?.let { handler.removeCallbacks(it) }
        pendingCheck = Runnable { verifyAndBlock(restricted, reason) }
        handler.postDelayed(pendingCheck!!, 120L)
    }

    private fun verifyAndBlock(restricted: String, reason: String) {
        pendingCheck = null
        val packageName = activeWindowPackageName()
        val now = System.currentTimeMillis()

        if (packageName != null && packageName != lastSeenPackage && now - lastSeenWriteAt > 250L) {
            lastSeenPackage = packageName
            lastSeenWriteAt = now
            DemoBlockPrefs.markSeen(this, packageName, now)
        }

        if (packageName == null) {
            DemoBlockPrefs.markSkip(this, "accessibility_active_null:$reason")
            return
        }
        if (packageName == applicationContext.packageName || packageName.isSystemTransientPackage()) {
            DemoBlockPrefs.markSkip(this, "accessibility_active_not_target:$packageName")
            return
        }
        if (packageName != restricted) {
            DemoBlockPrefs.markSkip(this, "accessibility_active_mismatch:$packageName")
            return
        }
        attemptBlock(packageName, "accessibility_verified")
    }

    private fun attemptBlock(packageName: String, source: String) {
        val now = System.currentTimeMillis()
        if (DemoBlockPrefs.unlockUntil(this, packageName) > now) {
            DemoBlockPrefs.markSkip(this, "accessibility_unlocked_until")
            return
        }
        if (overlayView != null && overlayPackage == packageName) return
        if (now - lastTargetTriggerAt < 700L) return
        if (!DemoBlockPrefs.canShowBlock(this, packageName, now, source)) return
        lastTargetTriggerAt = now
        showOverlay(packageName, DemoBlockPrefs.labelForPackage(this, packageName) ?: packageName, source)
    }

    private fun activeWindowPackageName(): String? {
        // We intentionally read only window package identity. We do not traverse child nodes,
        // read text, descriptions, input, or chat/content data.
        runCatching {
            windows
                .orEmpty()
                .asSequence()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { if (it.isActive) 2 else if (it.isFocused) 1 else 0 }
                .mapNotNull { it.root?.packageName?.toString()?.takeIf { pkg -> pkg.isNotBlank() } }
                .firstOrNull()
        }.getOrNull()?.let { return it }

        return runCatching { rootInActiveWindow?.packageName?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun showOverlay(packageName: String, appLabel: String, source: String) {
        hideOverlay()
        hideMini()
        overlayPackage = packageName
        val group = DemoBlockPrefs.groupForPackage(this, packageName)
        val limitSnapshot = DemoBlockPrefs.groupLimitSnapshot(this, packageName, group)
        val overLimit = limitSnapshot.overLimit
        val requireTypedPurpose = DemoBlockPrefs.requireTypedPurposeForPackage(this, packageName)
        val intents = DemoBlockPrefs.purposeOptionsForPackage(this, packageName)
        var selectedIntent: String? = intents.firstOrNull()
        var typedPurpose: EditText? = null

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
        root.addView(card, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        card.addText("时停 · Stop the World", 12f, true, if (overLimit) Color.rgb(185, 28, 28) else Color.rgb(37, 99, 235), 0)
        card.addText(if (overLimit) "已经超时" else "先停一下", 30f, true, Color.rgb(15, 23, 42), 10.dp)
        card.addText("你正在打开 $appLabel", 17f, true, Color.rgb(51, 65, 85), 4.dp)
        group?.let { card.addText("分组：${it.name}", 14f, true, Color.rgb(55, 48, 163), 4.dp) }
        if (overLimit) {
            card.addText(
                "当前应用今日已用 ${DemoBlockPrefs.compactDuration(limitSnapshot.appUsedMillis)}",
                14f,
                false,
                Color.rgb(71, 85, 105),
                8.dp,
            )
            card.addText(
                "分组限时 ${DemoBlockPrefs.compactDuration(limitSnapshot.limitMillis)} · 已超 ${DemoBlockPrefs.compactDuration(limitSnapshot.overMillis)}",
                16f,
                true,
                Color.rgb(185, 28, 28),
                4.dp,
            )
            card.addText(
                "建议先回到桌面，或确认这次继续打开的必要性。",
                14f,
                false,
                Color.rgb(100, 116, 139),
                4.dp,
            )
        }
        card.addText(if (requireTypedPurpose) "请输入这次打开的具体目的" else if (overLimit) "如果仍要打开，这次是为了什么？" else "这次打开是为了什么？", 16f, false, Color.rgb(71, 85, 105), 8.dp)

        if (requireTypedPurpose) {
            typedPurpose = EditText(this).apply {
                hint = "例如：查某个资料 / 回复某个人 / 完成一个任务"
                textSize = 14f
                setSingleLine(false)
                minLines = 2
                setPadding(12.dp, 8.dp, 12.dp, 8.dp)
                background = rounded(Color.rgb(241, 245, 249), 16.dp)
            }
            card.addView(typedPurpose)
        }

        val chipViews = mutableListOf<TextView>()
        val chipBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        intents.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 2.dp, 0, 2.dp); clipToPadding = false }
            rowItems.forEach { item ->
                val chip = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                    setOnClickListener {
                        selectedIntent = item
                        chipViews.forEach { view ->
                            val raw = view.tag as String
                            view.text = if (raw == selectedIntent) "✓ $raw" else raw
                            view.setTextColor(if (raw == selectedIntent) Color.rgb(55, 48, 163) else Color.rgb(51, 65, 85))
                            view.background = rounded(if (raw == selectedIntent) Color.rgb(224, 231, 255) else Color.rgb(241, 245, 249), 999.dp)
                        }
                    }
                    tag = item
                }
                chipViews += chip
                row.addView(chip, LinearLayout.LayoutParams(0, 42.dp, 1f).apply { setMargins(3.dp, 4.dp, 3.dp, 4.dp) })
            }
            chipBox.addView(row)
        }
        if (!requireTypedPurpose) card.addView(chipBox)
        chipViews.forEach { view ->
            val raw = view.tag as String
            view.text = if (raw == selectedIntent) "✓ $raw" else raw
            view.setTextColor(if (raw == selectedIntent) Color.rgb(55, 48, 163) else Color.rgb(51, 65, 85))
            view.background = rounded(if (raw == selectedIntent) Color.rgb(224, 231, 255) else Color.rgb(241, 245, 249), 999.dp)
        }

        val countdownText = TextView(this).apply {
            text = "还需等待 3 秒"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(55, 48, 163))
            setPadding(0, 14.dp, 0, 12.dp)
        }
        card.addView(countdownText)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 2.dp, 0, 2.dp); clipToPadding = false }
        val cancel = button("不打开了", Color.rgb(239, 246, 255), Color.rgb(30, 64, 175)) {
            DemoBlockPrefs.suppressAfterCancel(this, packageName)
            hideOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        val cont = button("继续 5 分钟", Color.rgb(37, 99, 235), Color.WHITE) {
            val chosen = if (requireTypedPurpose) typedPurpose?.text?.toString()?.trim()?.takeIf { it.isNotBlank() } else selectedIntent
            DemoBlockPrefs.setUnlockUntil(this, packageName, System.currentTimeMillis() + 5 * 60_000L, chosen)
            hideOverlay()
            showMini(chosen ?: "有意使用")
        }.apply { isEnabled = false; alpha = 0.45f }
        row.addView(cancel, LinearLayout.LayoutParams(0, 48.dp, 1f).apply { rightMargin = 6.dp })
        row.addView(cont, LinearLayout.LayoutParams(0, 48.dp, 1f).apply { leftMargin = 6.dp })
        card.addView(row)

        card.addText("仅检测前台 App 包名/窗口身份，不读取文字、输入或聊天内容。", 12f, false, Color.rgb(100, 116, 139), 10.dp)

        overlayView = root
        runCatching {
            windowManager?.addView(root, fullParams())
            startCountdown(countdownText, cont)
            DemoBlockPrefs.markBlockShown(this, packageName, System.currentTimeMillis(), source)
        }.onFailure { error ->
            overlayView = null
            overlayPackage = null
            DemoBlockPrefs.markSkip(this, "accessibility_overlay_error:${error.javaClass.simpleName}")
        }
    }

    private fun showMini(intentText: String) {
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
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 40.dp
            title = "Stop the World accessibility mini reminder"
        }
        runCatching { windowManager?.addView(mini, params) }
        handler.postDelayed({ hideMini() }, 5 * 60_000L)
    }

    private fun startCountdown(textView: TextView, continueButton: TextView) {
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
        pendingCheck?.let { handler.removeCallbacks(it) }
        pendingCheck = null
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
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.CENTER; title = "Stop the World accessibility blocker" }

    private fun button(text: String, bg: Int, fg: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
        minHeight = 0
        setPadding(10.dp, 0, 10.dp, 0)
        setTextColor(fg)
        background = rounded(bg, 18.dp)
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { if (isEnabled) onClick() }
    }

    private fun LinearLayout.addText(textValue: String, sizeSp: Float, bold: Boolean, color: Int, top: Int) {
        addView(TextView(this@AppMonitorAccessibilityService).apply {
            text = textValue
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, top, 0, 2.dp)
        })
    }

    private fun String.isSystemTransientPackage(): Boolean =
        this == "com.android.systemui" ||
            this.contains("launcher", ignoreCase = true) ||
            this.contains("inputmethod", ignoreCase = true)

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}

package dev.stw.blocking

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

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
    private var miniCollapsed = false
    private var miniText = "有意使用"
    private var miniX = 0
    private var miniY = 40

    override fun onServiceConnected() {
        super.onServiceConnected()
        // When the process/service is killed by the system or battery policy, we miss the screen-off
        // and screen-on events during that gap. Reusing the old persisted "screenOnSince" can produce
        // a fake 10+ hour continuous-usage session and show the global rest prompt immediately while
        // the user is still in Android Accessibility Settings. Treat a fresh service connection as a
        // new observed screen session instead of trusting stale state.
        DemoBlockPrefs.resetGlobalScreenSession(this, System.currentTimeMillis(), "accessibility_primary_connected_reset_session")
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

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive || isKeyguardLocked()) {
            hideOverlay()
            hideMini()
            DemoBlockPrefs.markScreenInteractive(this, false, now)
            DemoBlockPrefs.markSkip(this, "screen_locked_or_not_interactive")
            return
        }
        DemoBlockPrefs.markScreenInteractive(this, true, now)
        if (skipForPopupWhitelist(seenPackage)) return
        if (checkLateNight(seenPackage, now)) return
        if (checkGlobalBreak(seenPackage, now)) return

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
        DemoBlockPrefs.markScreenInteractive(this, false)
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
        if (skipForPopupWhitelist(packageName)) return
        if (packageName != restricted) {
            DemoBlockPrefs.markSkip(this, "accessibility_active_mismatch:$packageName")
            return
        }
        attemptBlock(packageName, "accessibility_verified")
    }

    private fun skipForPopupWhitelist(packageName: String): Boolean {
        if (!DemoBlockPrefs.isPopupWhitelisted(this, packageName)) return false
        hideOverlay()
        hideMini()
        DemoBlockPrefs.markSkip(this, "popup_whitelist:$packageName")
        return true
    }

    private fun checkGlobalBreak(packageName: String, now: Long): Boolean {
        DemoBlockPrefs.finishGlobalRestIfExpired(this, now)
        val settings = DemoBlockPrefs.globalBreakSettings(this)
        if (packageName.isIgnoredGlobalPackage()) return false
        val restUntil = settings.restUntil
        if (restUntil > now) {
            if (overlayView != null && overlayPackage == GLOBAL_BREAK_PACKAGE) return true
            if (!DemoBlockPrefs.acquireGlobalBreakOverlay(this, GLOBAL_OVERLAY_OWNER, now)) return true
            showGlobalRestOverlay(restUntil - now)
            return true
        }
        if (!settings.enabled) return false
        val since = settings.screenOnSince.takeIf { it > 0L } ?: DemoBlockPrefs.markScreenInteractive(this, true, now)
        val elapsed = now - since
        if (elapsed >= settings.limitMinutes * 60_000L) {
            if (overlayView != null && overlayPackage == GLOBAL_BREAK_PACKAGE) return true
            if (!DemoBlockPrefs.acquireGlobalBreakOverlay(this, GLOBAL_OVERLAY_OWNER, now)) return true
            showGlobalBreakPrompt(elapsed, settings.restOptionsMinutes)
            return true
        }
        return false
    }

    private fun checkLateNight(packageName: String, now: Long): Boolean {
        if (packageName.isIgnoredGlobalPackage()) return false
        if (overlayView != null && overlayPackage == LATE_NIGHT_PACKAGE) return true
        val window = DemoBlockPrefs.shouldShowLateNightPrompt(this, now) ?: return false
        if (!DemoBlockPrefs.acquireGlobalBreakOverlay(this, LATE_NIGHT_OVERLAY_OWNER, now)) return true
        showLateNightPrompt(window)
        return true
    }

    private fun attemptBlock(packageName: String, source: String) {
        val now = System.currentTimeMillis()
        if (skipForPopupWhitelist(packageName)) return
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

    private fun isKeyguardLocked(): Boolean =
        runCatching {
            val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguard.isKeyguardLocked || keyguard.isDeviceLocked
        }.getOrDefault(false)

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
            intents = DemoBlockPrefs.rankedPurposeOptionsForPackage(this, packageName),
            onCancel = {
                DemoBlockPrefs.suppressAfterCancel(this, packageName)
                val keepCurrentTask = isLikelyMultiAppWindow(packageName)
                hideOverlay()
                if (keepCurrentTask) {
                    DemoBlockPrefs.markSkip(this, "cancel_in_multi_window_keep_current_task:$packageName")
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            },
            onContinue = { chosen, addToPreset ->
                if (addToPreset && !chosen.isNullOrBlank()) DemoBlockPrefs.addPurposePresetForPackage(this, packageName, chosen)
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
        }.onFailure { error ->
            overlayView = null
            overlayPackage = null
            DemoBlockPrefs.markSkip(this, "accessibility_overlay_error:${error.javaClass.simpleName}")
        }
    }

    private fun isLikelyMultiAppWindow(targetPackage: String): Boolean =
        runCatching {
            val applicationPackages = windows
                .orEmpty()
                .asSequence()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root?.packageName?.toString()?.takeIf { pkg -> pkg.isNotBlank() } }
                .filterNot { it == applicationContext.packageName || it.isSystemTransientPackage() }
                .distinct()
                .toList()
            targetPackage in applicationPackages && applicationPackages.any { it != targetPackage }
        }.getOrDefault(false)

    private fun showLateNightPrompt(window: LateNightWindow) {
        hideOverlay()
        hideMini()
        overlayPackage = LATE_NIGHT_PACKAGE
        val settings = DemoBlockPrefs.lateNightSettings(this)
        val view = BlockOverlayUi.buildLateNightPrompt(
            context = this,
            thresholdText = DemoBlockPrefs.formatClockMinutes(settings.thresholdMinutes),
            wakeText = DemoBlockPrefs.formatClockMinutes(settings.wakeMinutes),
            onImportant = { task ->
                DemoBlockPrefs.recordLateNightImportantTask(this, task, window)
                hideOverlay()
                showMini("今晚重要：$task")
            },
            onSleep = {
                DemoBlockPrefs.startLateNightSleep(this, window)
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            },
            onSnoozeForTest = {
                DemoBlockPrefs.snoozeLateNightPromptForTest(this)
                hideOverlay()
            },
        )
        overlayView = view
        runCatching {
            windowManager?.addView(view, fullParams())
            DemoBlockPrefs.markLateNightPromptShown(this, System.currentTimeMillis(), "accessibility_late_night_prompt")
        }.onFailure { error ->
            overlayView = null
            overlayPackage = null
            DemoBlockPrefs.releaseGlobalBreakOverlay(LATE_NIGHT_OVERLAY_OWNER)
            DemoBlockPrefs.markSkip(this, "accessibility_late_night_overlay_error:${error.javaClass.simpleName}")
        }
    }

    private fun showGlobalBreakPrompt(screenOnMillis: Long, restOptionsMinutes: List<Int>) {
        hideOverlay()
        hideMini()
        overlayPackage = GLOBAL_BREAK_PACKAGE
        val view = BlockOverlayUi.buildGlobalBreakPrompt(
            context = this,
            screenOnMillis = screenOnMillis,
            restOptionsMinutes = restOptionsMinutes,
            onRest = { minutes, activity ->
                val now = System.currentTimeMillis()
                DemoBlockPrefs.setGlobalRestUntil(this, now + minutes * 60_000L, now, activity)
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            },
            onSkip = {
                DemoBlockPrefs.clearGlobalRestForTest(this)
                hideOverlay()
            },
        )
        overlayView = view
        runCatching { windowManager?.addView(view, fullParams()) }
            .onFailure { error ->
                overlayView = null
                overlayPackage = null
                DemoBlockPrefs.releaseGlobalBreakOverlay(GLOBAL_OVERLAY_OWNER)
                DemoBlockPrefs.markSkip(this, "accessibility_global_break_overlay_error:${error.javaClass.simpleName}")
            }
    }

    private fun showGlobalRestOverlay(remainingMillis: Long) {
        hideOverlay()
        hideMini()
        overlayPackage = GLOBAL_BREAK_PACKAGE
        val view = BlockOverlayUi.buildGlobalRestPrompt(
            context = this,
            remainingMillis = remainingMillis,
            restActivity = DemoBlockPrefs.globalBreakSettings(this).restActivity,
            onHome = {
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            },
            onSkip = {
                DemoBlockPrefs.clearGlobalRestForTest(this)
                hideOverlay()
            },
        )
        overlayView = view
        runCatching { windowManager?.addView(view, fullParams()) }
            .onFailure { error ->
                overlayView = null
                overlayPackage = null
                DemoBlockPrefs.releaseGlobalBreakOverlay(GLOBAL_OVERLAY_OWNER)
                DemoBlockPrefs.markSkip(this, "accessibility_global_rest_overlay_error:${error.javaClass.simpleName}")
            }
    }

    private fun showMini(intentText: String) {
        hideMini()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive || isKeyguardLocked()) return
        miniText = intentText
        val mini = BlockOverlayUi.buildMini(this, intentText, miniCollapsed) {
            miniCollapsed = !miniCollapsed
            showMini(miniText)
        }
        miniView = mini
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = miniX
            y = miniY
            title = "Stop the World accessibility mini reminder"
        }
        mini.setOnTouchListener(object : View.OnTouchListener {
            private var downRawX = 0f
            private var downRawY = 0f
            private var startX = 0
            private var startY = 0
            private var dragging = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downRawX).toInt()
                        val dy = (event.rawY - downRawY).toInt()
                        if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) dragging = true
                        if (dragging) {
                            params.x = (startX + dx).coerceAtLeast(0)
                            params.y = (startY + dy).coerceAtLeast(0)
                            miniX = params.x
                            miniY = params.y
                            runCatching { windowManager?.updateViewLayout(v, params) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            miniCollapsed = !miniCollapsed
                            showMini(miniText)
                        }
                        return true
                    }
                }
                return true
            }
        })
        runCatching { windowManager?.addView(mini, params) }
        handler.postDelayed({ hideMini() }, 5 * 60_000L)
    }

    private fun hideOverlay() {
        val hiddenPackage = overlayPackage
        pendingCheck?.let { handler.removeCallbacks(it) }
        pendingCheck = null
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
        overlayPackage = null
        if (hiddenPackage == GLOBAL_BREAK_PACKAGE) DemoBlockPrefs.releaseGlobalBreakOverlay(GLOBAL_OVERLAY_OWNER)
        if (hiddenPackage == LATE_NIGHT_PACKAGE) DemoBlockPrefs.releaseGlobalBreakOverlay(LATE_NIGHT_OVERLAY_OWNER)
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

    private fun String.isSystemTransientPackage(): Boolean =
        this == "com.android.systemui" ||
            this.contains("launcher", ignoreCase = true) ||
            this.contains("inputmethod", ignoreCase = true) ||
            this.contains("home", ignoreCase = true)

    private fun String.isIgnoredGlobalPackage(): Boolean =
        this == applicationContext.packageName ||
            this == "com.android.settings" ||
            this == "com.miui.securitycenter" ||
            this == "com.miui.powerkeeper" ||
            this == "com.android.permissioncontroller" ||
            this == "com.google.android.permissioncontroller" ||
            this == "com.android.packageinstaller" ||
            this == "com.miui.packageinstaller" ||
            contains("permissioncontroller", ignoreCase = true) ||
            contains("packageinstaller", ignoreCase = true) ||
            isSystemTransientPackage()

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val GLOBAL_BREAK_PACKAGE = "__global_screen_break__"
        private const val GLOBAL_OVERLAY_OWNER = "accessibility"
        private const val LATE_NIGHT_PACKAGE = "__late_night_prompt__"
        private const val LATE_NIGHT_OVERLAY_OWNER = "accessibility_late_night"
    }
}

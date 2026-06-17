package dev.stw.blocking

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
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

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            DemoBlockPrefs.markScreenInteractive(this, false, now)
            return
        }
        DemoBlockPrefs.markScreenInteractive(this, true, now)
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

    private fun checkGlobalBreak(packageName: String, now: Long): Boolean {
        DemoBlockPrefs.finishGlobalRestIfExpired(this, now)
        val settings = DemoBlockPrefs.globalBreakSettings(this)
        if (!settings.enabled || packageName.isIgnoredGlobalPackage()) return false
        val restUntil = settings.restUntil
        if (restUntil > now) {
            if (overlayView != null && overlayPackage == GLOBAL_BREAK_PACKAGE) return true
            if (!DemoBlockPrefs.canShowGlobalBreakOverlay(this, now)) return true
            showGlobalRestOverlay(restUntil - now)
            return true
        }
        val since = settings.screenOnSince.takeIf { it > 0L } ?: DemoBlockPrefs.markScreenInteractive(this, true, now)
        val elapsed = now - since
        if (elapsed >= settings.limitMinutes * 60_000L) {
            if (overlayView != null && overlayPackage == GLOBAL_BREAK_PACKAGE) return true
            if (!DemoBlockPrefs.canShowGlobalBreakOverlay(this, now)) return true
            showGlobalBreakPrompt(elapsed, settings.restOptionsMinutes)
            return true
        }
        return false
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
        val handles = BlockOverlayUi.build(
            context = this,
            appLabel = appLabel,
            groupName = group?.name,
            limitSnapshot = DemoBlockPrefs.groupLimitSnapshot(this, packageName, group),
            requireTypedPurpose = DemoBlockPrefs.requireTypedPurposeForPackage(this, packageName),
            intents = DemoBlockPrefs.rankedPurposeOptionsForPackage(this, packageName),
            onCancel = {
                DemoBlockPrefs.suppressAfterCancel(this, packageName)
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
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

    private fun showGlobalBreakPrompt(screenOnMillis: Long, restOptionsMinutes: List<Int>) {
        hideOverlay()
        hideMini()
        overlayPackage = GLOBAL_BREAK_PACKAGE
        val view = BlockOverlayUi.buildGlobalBreakPrompt(
            context = this,
            screenOnMillis = screenOnMillis,
            restOptionsMinutes = restOptionsMinutes,
            onRest = { minutes ->
                val now = System.currentTimeMillis()
                DemoBlockPrefs.setGlobalRestUntil(this, now + minutes * 60_000L, now)
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
                DemoBlockPrefs.markSkip(this, "accessibility_global_rest_overlay_error:${error.javaClass.simpleName}")
            }
    }

    private fun showMini(intentText: String) {
        hideMini()
        val mini = BlockOverlayUi.buildMini(this, intentText) { hideMini() }
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

    private fun String.isSystemTransientPackage(): Boolean =
        this == "com.android.systemui" ||
            this.contains("launcher", ignoreCase = true) ||
            this.contains("inputmethod", ignoreCase = true) ||
            this.contains("home", ignoreCase = true)

    private fun String.isIgnoredGlobalPackage(): Boolean =
        this == applicationContext.packageName || isSystemTransientPackage()

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val GLOBAL_BREAK_PACKAGE = "__global_screen_break__"
    }
}

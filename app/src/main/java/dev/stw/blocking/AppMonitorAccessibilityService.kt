package dev.stw.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppMonitorAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        val restricted = DemoBlockPrefs.restrictedPackage(this) ?: return
        if (packageName != restricted) return

        val now = System.currentTimeMillis()
        if (DemoBlockPrefs.unlockUntil(this, packageName) > now) return
        if (now - DemoBlockPrefs.lastBlockedAt(this, packageName) < 2_000L) return

        DemoBlockPrefs.markBlocked(this, packageName, now)
        val label = DemoBlockPrefs.restrictedLabel(this) ?: packageName
        val intent = Intent(this, BlockingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(BlockingActivity.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(BlockingActivity.EXTRA_APP_LABEL, label)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}

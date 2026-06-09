package dev.stw.blocking

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityStatus {
    fun isServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!accessibilityEnabled) return false

        val component = ComponentName(context, AppMonitorAccessibilityService::class.java)
        val expected = component.flattenToString()
        val expectedShort = component.flattenToShortString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (service in splitter) {
            if (service.equals(expected, ignoreCase = true) || service.equals(expectedShort, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}

package dev.stw.blocking

import android.content.Context

object DemoBlockPrefs {
    private const val FILE = "stop_the_world_demo"
    private const val KEY_RESTRICTED_PACKAGE = "restricted_package"
    private const val KEY_RESTRICTED_LABEL = "restricted_label"
    private const val KEY_UNLOCK_UNTIL_PREFIX = "unlock_until_"
    private const val KEY_LAST_BLOCK_AT_PREFIX = "last_block_at_"
    private const val KEY_LAST_INTENT_PREFIX = "last_intent_"

    fun setRestrictedApp(context: Context, packageName: String, label: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_RESTRICTED_PACKAGE, packageName)
            .putString(KEY_RESTRICTED_LABEL, label)
            .apply()
    }

    fun restrictedPackage(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_RESTRICTED_PACKAGE, null)

    fun restrictedLabel(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_RESTRICTED_LABEL, null)

    fun clearRestrictedApp(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(KEY_RESTRICTED_PACKAGE)
            .remove(KEY_RESTRICTED_LABEL)
            .apply()
    }

    fun setUnlockUntil(context: Context, packageName: String, untilMillis: Long, intent: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UNLOCK_UNTIL_PREFIX + packageName, untilMillis)
            .putString(KEY_LAST_INTENT_PREFIX + packageName, intent)
            .apply()
    }

    fun unlockUntil(context: Context, packageName: String): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_UNLOCK_UNTIL_PREFIX + packageName, 0L)

    fun lastIntent(context: Context, packageName: String): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LAST_INTENT_PREFIX + packageName, null)

    fun markBlocked(context: Context, packageName: String, atMillis: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_BLOCK_AT_PREFIX + packageName, atMillis)
            .apply()
    }

    fun lastBlockedAt(context: Context, packageName: String): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_LAST_BLOCK_AT_PREFIX + packageName, 0L)
}

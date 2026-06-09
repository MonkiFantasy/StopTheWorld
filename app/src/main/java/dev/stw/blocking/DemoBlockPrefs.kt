package dev.stw.blocking

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DemoBlockPrefs {
    private const val FILE = "stop_the_world_demo"
    private const val KEY_RESTRICTED_PACKAGE = "restricted_package"
    private const val KEY_RESTRICTED_LABEL = "restricted_label"
    private const val KEY_GROUPS = "restricted_groups"
    private const val KEY_UNLOCK_UNTIL_PREFIX = "unlock_until_"
    private const val KEY_LAST_BLOCK_AT_PREFIX = "last_block_at_"
    private const val KEY_LAST_INTENT_PREFIX = "last_intent_"
    private const val KEY_LAST_SEEN_PACKAGE = "last_seen_package"
    private const val KEY_LAST_SEEN_AT = "last_seen_at"
    private const val KEY_LAST_TRIGGER_PACKAGE = "last_trigger_package"
    private const val KEY_LAST_TRIGGER_AT = "last_trigger_at"
    private const val KEY_LAST_SKIP_REASON = "last_skip_reason"
    private const val KEY_SUPPRESS_UNTIL_PREFIX = "suppress_until_"
    private const val KEY_INTENTS = "custom_intents"
    private const val KEY_FLOATING_RUNNING = "floating_running"
    private const val DEFAULT_INTENTS = "查资料|回复消息|娱乐休息|无聊|逃避任务|其他"

    fun setRestrictedApp(context: Context, packageName: String, label: String) {
        val group = groups(context).firstOrNull() ?: RestrictedGroup(newGroupId(), "默认分组", emptyList())
        val updated = groups(context)
            .filterNot { it.id == group.id }
            .let { others ->
                val apps = (group.apps + RestrictedAppEntry(packageName, label)).distinctBy { it.packageName }
                listOf(group.copy(apps = apps)) + others
            }
        setGroups(context, updated)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_RESTRICTED_PACKAGE, packageName)
            .putString(KEY_RESTRICTED_LABEL, label)
            .apply()
    }

    fun restrictedPackage(context: Context): String? = restrictedApps(context).firstOrNull()?.packageName

    fun restrictedLabel(context: Context): String? = restrictedApps(context).firstOrNull()?.label

    fun restrictedApps(context: Context): List<RestrictedAppEntry> = groups(context).flatMap { it.apps }.distinctBy { it.packageName }

    fun restrictedPackages(context: Context): Set<String> = restrictedApps(context).map { it.packageName }.toSet()

    fun labelForPackage(context: Context, packageName: String): String? = restrictedApps(context).firstOrNull { it.packageName == packageName }?.label

    fun clearRestrictedApp(context: Context) {
        setGroups(context, emptyList())
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(KEY_RESTRICTED_PACKAGE)
            .remove(KEY_RESTRICTED_LABEL)
            .apply()
    }

    fun groups(context: Context): List<RestrictedGroup> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GROUPS, null)
        val parsed = raw?.let { parseGroups(it) }.orEmpty()
        if (parsed.isNotEmpty()) return parsed
        val legacyPkg = prefs.getString(KEY_RESTRICTED_PACKAGE, null)
        val legacyLabel = prefs.getString(KEY_RESTRICTED_LABEL, null)
        return if (legacyPkg != null) listOf(RestrictedGroup("default", "默认分组", listOf(RestrictedAppEntry(legacyPkg, legacyLabel ?: legacyPkg)))) else emptyList()
    }

    fun setGroups(context: Context, groups: List<RestrictedGroup>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_GROUPS, encodeGroups(groups))
            .apply()
    }

    fun addGroup(context: Context, name: String): RestrictedGroup {
        val cleaned = name.trim().ifBlank { "新分组" }
        val group = RestrictedGroup(newGroupId(), cleaned, emptyList())
        setGroups(context, groups(context) + group)
        return group
    }

    fun deleteGroup(context: Context, groupId: String) {
        setGroups(context, groups(context).filterNot { it.id == groupId })
    }

    fun addAppToGroup(context: Context, groupId: String, packageName: String, label: String) {
        val current = groups(context).ifEmpty { listOf(RestrictedGroup("default", "默认分组", emptyList())) }
        val targetId = current.firstOrNull { it.id == groupId }?.id ?: current.first().id
        setGroups(context, current.map { group ->
            if (group.id == targetId) group.copy(apps = (group.apps + RestrictedAppEntry(packageName, label)).distinctBy { it.packageName }) else group
        })
    }

    fun removeAppFromGroup(context: Context, groupId: String, packageName: String) {
        setGroups(context, groups(context).map { group ->
            if (group.id == groupId) group.copy(apps = group.apps.filterNot { it.packageName == packageName }) else group
        })
    }

    private fun parseGroups(raw: String): List<RestrictedGroup> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val appsArray = obj.optJSONArray("apps") ?: JSONArray()
                val apps = buildList {
                    for (j in 0 until appsArray.length()) {
                        val app = appsArray.getJSONObject(j)
                        val pkg = app.optString("packageName")
                        if (pkg.isNotBlank()) add(RestrictedAppEntry(pkg, app.optString("label", pkg)))
                    }
                }
                val id = obj.optString("id").ifBlank { newGroupId() }
                add(RestrictedGroup(id, obj.optString("name", "分组"), apps))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeGroups(groups: List<RestrictedGroup>): String = JSONArray().apply {
        groups.forEach { group ->
            put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("apps", JSONArray().apply {
                    group.apps.forEach { app ->
                        put(JSONObject().apply {
                            put("packageName", app.packageName)
                            put("label", app.label)
                        })
                    }
                })
            })
        }
    }.toString()

    private fun newGroupId(): String = "g_" + System.currentTimeMillis().toString(36)

    fun intents(context: Context): List<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_INTENTS, DEFAULT_INTENTS)
            .orEmpty()
            .split('|', ',', '，', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
            .ifEmpty { DEFAULT_INTENTS.split('|') }

    fun setIntents(context: Context, values: List<String>) {
        val cleaned = values.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_INTENTS, cleaned.ifEmpty { DEFAULT_INTENTS.split('|') }.joinToString("|"))
            .apply()
    }

    fun setFloatingRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FLOATING_RUNNING, running)
            .putString(KEY_LAST_SKIP_REASON, if (running) "floating_monitor_running" else "floating_monitor_stopped")
            .apply()
    }

    fun isFloatingRunning(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_FLOATING_RUNNING, false)

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

    fun suppressUntil(context: Context, packageName: String): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_SUPPRESS_UNTIL_PREFIX + packageName, 0L)

    fun suppressAfterCancel(context: Context, packageName: String, durationMillis: Long = 12_000L) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SUPPRESS_UNTIL_PREFIX + packageName, System.currentTimeMillis() + durationMillis)
            .putString(KEY_LAST_SKIP_REASON, "cancel_suppressed")
            .apply()
    }

    fun canShowBlock(
        context: Context,
        packageName: String,
        atMillis: Long = System.currentTimeMillis(),
        source: String = "triggered",
        cooldownMillis: Long = 3_500L,
    ): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val suppressedUntil = prefs.getLong(KEY_SUPPRESS_UNTIL_PREFIX + packageName, 0L)
        if (suppressedUntil > atMillis) {
            prefs.edit().putString(KEY_LAST_SKIP_REASON, "cancel_suppress_active:$source").apply()
            return false
        }
        val lastPackage = prefs.getString(KEY_LAST_TRIGGER_PACKAGE, null)
        val lastAt = prefs.getLong(KEY_LAST_TRIGGER_AT, 0L)
        if (lastPackage == packageName && atMillis - lastAt in 0 until cooldownMillis) {
            prefs.edit().putString(KEY_LAST_SKIP_REASON, "duplicate_suppressed:$source").apply()
            return false
        }
        return true
    }

    fun markBlockShown(
        context: Context,
        packageName: String,
        atMillis: Long = System.currentTimeMillis(),
        source: String = "triggered",
    ) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_BLOCK_AT_PREFIX + packageName, atMillis)
            .putString(KEY_LAST_TRIGGER_PACKAGE, packageName)
            .putLong(KEY_LAST_TRIGGER_AT, atMillis)
            .putString(KEY_LAST_SKIP_REASON, source)
            .apply()
    }

    fun markBlocked(context: Context, packageName: String, atMillis: Long = System.currentTimeMillis()) {
        markBlockShown(context, packageName, atMillis, "triggered")
    }

    fun lastBlockedAt(context: Context, packageName: String): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_LAST_BLOCK_AT_PREFIX + packageName, 0L)

    fun markSeen(context: Context, packageName: String, atMillis: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_SEEN_PACKAGE, packageName)
            .putLong(KEY_LAST_SEEN_AT, atMillis)
            .apply()
    }

    fun markSkip(context: Context, reason: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_SKIP_REASON, reason)
            .apply()
    }

    fun debugState(context: Context): DemoDebugState {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return DemoDebugState(
            lastSeenPackage = prefs.getString(KEY_LAST_SEEN_PACKAGE, null),
            lastSeenAt = prefs.getLong(KEY_LAST_SEEN_AT, 0L),
            lastTriggerPackage = prefs.getString(KEY_LAST_TRIGGER_PACKAGE, null),
            lastTriggerAt = prefs.getLong(KEY_LAST_TRIGGER_AT, 0L),
            lastSkipReason = prefs.getString(KEY_LAST_SKIP_REASON, null),
            floatingRunning = prefs.getBoolean(KEY_FLOATING_RUNNING, false),
        )
    }
}

data class DemoDebugState(
    val lastSeenPackage: String?,
    val lastSeenAt: Long,
    val lastTriggerPackage: String?,
    val lastTriggerAt: Long,
    val lastSkipReason: String?,
    val floatingRunning: Boolean,
)


data class RestrictedAppEntry(
    val packageName: String,
    val label: String,
)

data class RestrictedGroup(
    val id: String,
    val name: String,
    val apps: List<RestrictedAppEntry>,
)

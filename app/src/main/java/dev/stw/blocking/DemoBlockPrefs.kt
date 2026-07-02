package dev.stw.blocking

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

object DemoBlockPrefs {
    private const val FILE = "stop_the_world_demo"
    private const val KEY_RESTRICTED_PACKAGE = "restricted_package"
    private const val KEY_RESTRICTED_LABEL = "restricted_label"
    private const val KEY_GROUPS = "restricted_groups"
    private const val KEY_POPUP_WHITELIST = "popup_whitelist"
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
    private const val KEY_DAY_BOUNDARY_MINUTES = "day_boundary_minutes"
    private const val KEY_PURPOSE_RECORDS = "purpose_records"
    private const val KEY_GLOBAL_BREAK_ENABLED = "global_break_enabled"
    private const val KEY_GLOBAL_BREAK_LIMIT_MINUTES = "global_break_limit_minutes"
    private const val KEY_GLOBAL_BREAK_REST_OPTIONS = "global_break_rest_options"
    private const val KEY_GLOBAL_SCREEN_ON_SINCE = "global_screen_on_since"
    private const val KEY_GLOBAL_REST_UNTIL = "global_rest_until"
    private const val KEY_GLOBAL_REST_ACTIVITY = "global_rest_activity"
    private const val KEY_GLOBAL_LAST_OVERLAY_AT = "global_last_overlay_at"
    private const val KEY_LATE_NIGHT_ENABLED = "late_night_enabled"
    private const val KEY_LATE_NIGHT_THRESHOLD_MINUTES = "late_night_threshold_minutes"
    private const val KEY_LATE_NIGHT_WAKE_MINUTES = "late_night_wake_minutes"
    private const val KEY_LATE_NIGHT_HANDLED_WINDOW_START = "late_night_handled_window_start"
    private const val KEY_LATE_NIGHT_LAST_PROMPT_AT = "late_night_last_prompt_at"
    private const val KEY_LATE_NIGHT_RECORDS = "late_night_records"
    private const val KEY_OPERATION_LOGS = "operation_logs"
    private const val KEY_TIME_TODOS = "time_todos"
    private const val DEFAULT_INTENTS = "查资料|回复消息|娱乐休息|无聊|逃避任务|其他"
    private const val DEFAULT_REST_OPTIONS = "1|5|10|15"
    private var activeGlobalOverlayOwner: String? = null

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

    fun entryForPackage(context: Context, packageName: String): RestrictedAppEntry? = groups(context).flatMap { it.apps }.firstOrNull { it.packageName == packageName }

    fun purposeOptionsForPackage(context: Context, packageName: String): List<String> {
        val appOptions = entryForPackage(context, packageName)?.purposeOptions.orEmpty().filter { it.isNotBlank() }
        val groupOptions = groupForPackage(context, packageName)?.purposeOptions.orEmpty().filter { it.isNotBlank() }
        return appOptions.ifEmpty { groupOptions.ifEmpty { intents(context) } }
    }

    fun rankedPurposeOptionsForPackage(context: Context, packageName: String): List<String> {
        val base = purposeOptionsForPackage(context, packageName).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val counts = purposeRecords(context)
            .asSequence()
            .filter { it.packageName == packageName }
            .groupingBy { it.purpose.trim().lowercase() }
            .eachCount()
        return base.sortedWith(compareByDescending<String> { counts[it.lowercase()] ?: 0 }.thenBy { base.indexOf(it) })
    }

    fun addPurposePresetForPackage(context: Context, packageName: String, purpose: String) {
        val cleaned = purpose.trim().take(32)
        if (cleaned.isBlank()) return
        val current = purposeOptionsForPackage(context, packageName)
        if (current.any { it.equals(cleaned, ignoreCase = true) }) return
        val groupsNow = groups(context)
        val group = groupsNow.firstOrNull { g -> g.apps.any { it.packageName == packageName } }
        val app = group?.apps?.firstOrNull { it.packageName == packageName }
        when {
            app != null && app.purposeOptions.isNotEmpty() -> {
                setGroups(context, groupsNow.map { g ->
                    if (g.id == group.id) g.copy(apps = g.apps.map { entry ->
                        if (entry.packageName == packageName) entry.copy(purposeOptions = (listOf(cleaned) + entry.purposeOptions).distinct().take(8)) else entry
                    }) else g
                })
            }
            group != null && group.purposeOptions.isNotEmpty() -> {
                updateGroupPurposeOptions(context, group.id, (listOf(cleaned) + group.purposeOptions).distinct().take(8))
            }
            else -> setIntents(context, (listOf(cleaned) + intents(context)).distinct().take(8))
        }
    }

    fun requireTypedPurposeForPackage(context: Context, packageName: String): Boolean {
        val app = entryForPackage(context, packageName)
        return app?.requireTypedPurpose ?: (groupForPackage(context, packageName)?.requireTypedPurpose == true)
    }

    fun updateAppPurpose(context: Context, packageName: String, purposeOptions: List<String>, requireTypedPurpose: Boolean?, dailyLimitMinutes: Int? = null) {
        val cleaned = purposeOptions.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8)
        setGroups(context, groups(context).map { group ->
            group.copy(apps = group.apps.map { app ->
                if (app.packageName == packageName) app.copy(purposeOptions = cleaned, requireTypedPurpose = requireTypedPurpose, dailyLimitMinutes = dailyLimitMinutes?.coerceAtLeast(0) ?: app.dailyLimitMinutes) else app
            })
        })
    }

    fun groupForPackage(context: Context, packageName: String): RestrictedGroup? = groups(context).firstOrNull { group -> group.apps.any { it.packageName == packageName } }

    fun popupWhitelistApps(context: Context): List<PopupWhitelistEntry> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_POPUP_WHITELIST, null)
        return raw?.let { parsePopupWhitelist(it) }.orEmpty()
    }

    fun popupWhitelistPackages(context: Context): Set<String> = popupWhitelistApps(context).map { it.packageName }.toSet()

    fun isPopupWhitelisted(context: Context, packageName: String): Boolean =
        packageName in popupWhitelistPackages(context)

    fun addPopupWhitelistApp(context: Context, packageName: String, label: String) {
        val updated = (popupWhitelistApps(context) + PopupWhitelistEntry(packageName, label)).distinctBy { it.packageName }
        setPopupWhitelistApps(context, updated)
        appendLog(context, "info", "popup_whitelist_add pkg=$packageName label=$label")
    }

    fun removePopupWhitelistApp(context: Context, packageName: String) {
        setPopupWhitelistApps(context, popupWhitelistApps(context).filterNot { it.packageName == packageName })
        appendLog(context, "info", "popup_whitelist_remove pkg=$packageName")
    }

    private fun setPopupWhitelistApps(context: Context, apps: List<PopupWhitelistEntry>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_POPUP_WHITELIST, encodePopupWhitelist(apps))
            .apply()
    }

    private fun parsePopupWhitelist(raw: String): List<PopupWhitelistEntry> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName")
                if (pkg.isNotBlank()) add(PopupWhitelistEntry(pkg, obj.optString("label", pkg)))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodePopupWhitelist(apps: List<PopupWhitelistEntry>): String = JSONArray().apply {
        apps.forEach { app ->
            put(JSONObject().apply {
                put("packageName", app.packageName)
                put("label", app.label)
            })
        }
    }.toString()

    fun timeTodos(context: Context): List<TimeTodoEntry> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TIME_TODOS, null)
        return raw?.let { parseTimeTodos(it) }.orEmpty()
            .sortedWith(compareBy<TimeTodoEntry> { it.startMinutes }.thenBy { it.title })
    }

    fun addTimeTodo(context: Context, title: String, startMinutes: Int, endMinutes: Int): TimeTodoEntry {
        val cleanedTitle = title.trim().ifBlank { "未命名安排" }.take(40)
        val entry = TimeTodoEntry(
            id = "todo_" + System.currentTimeMillis().toString(36),
            title = cleanedTitle,
            startMinutes = startMinutes.coerceIn(0, 1435),
            endMinutes = endMinutes.coerceIn(5, 1440).coerceAtLeast(startMinutes + 5),
            done = false,
        )
        setTimeTodos(context, timeTodos(context) + entry)
        appendLog(context, "info", "time_todo_add ${formatClockMinutes(entry.startMinutes)}-${formatClockMinutes(entry.endMinutes % 1440)} $cleanedTitle")
        return entry
    }

    fun updateTimeTodo(context: Context, entry: TimeTodoEntry) {
        setTimeTodos(context, timeTodos(context).map { if (it.id == entry.id) entry.normalized() else it })
        appendLog(context, "debug", "time_todo_update id=${entry.id}")
    }

    fun toggleTimeTodo(context: Context, id: String) {
        setTimeTodos(context, timeTodos(context).map { if (it.id == id) it.copy(done = !it.done) else it })
        appendLog(context, "info", "time_todo_toggle id=$id")
    }

    fun deleteTimeTodo(context: Context, id: String) {
        setTimeTodos(context, timeTodos(context).filterNot { it.id == id })
        appendLog(context, "info", "time_todo_delete id=$id")
    }

    private fun setTimeTodos(context: Context, entries: List<TimeTodoEntry>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_TIME_TODOS, encodeTimeTodos(entries.take(80)))
            .apply()
    }

    private fun parseTimeTodos(raw: String): List<TimeTodoEntry> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id").ifBlank { "todo_$i" }
                val title = obj.optString("title")
                if (title.isNotBlank()) {
                    add(
                        TimeTodoEntry(
                            id = id,
                            title = title,
                            startMinutes = obj.optInt("startMinutes", 9 * 60),
                            endMinutes = obj.optInt("endMinutes", 10 * 60),
                            done = obj.optBoolean("done", false),
                        ).normalized(),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeTimeTodos(entries: List<TimeTodoEntry>): String = JSONArray().apply {
        entries.forEach { entry ->
            val normalized = entry.normalized()
            put(JSONObject().apply {
                put("id", normalized.id)
                put("title", normalized.title)
                put("startMinutes", normalized.startMinutes)
                put("endMinutes", normalized.endMinutes)
                put("done", normalized.done)
            })
        }
    }.toString()

    fun dayBoundaryMinutes(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_DAY_BOUNDARY_MINUTES, 0)
            .coerceIn(0, 23 * 60 + 59)

    fun setDayBoundaryMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DAY_BOUNDARY_MINUTES, minutes.coerceIn(0, 23 * 60 + 59))
            .apply()
    }

    fun formatDayBoundary(minutes: Int): String = "%02d:%02d".format((minutes.coerceIn(0, 1439) / 60), (minutes.coerceIn(0, 1439) % 60))

    fun currentUsageWindowStartMillis(context: Context, now: Long = System.currentTimeMillis()): Long {
        val boundary = dayBoundaryMinutes(context)
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        calendar.set(Calendar.HOUR_OF_DAY, boundary / 60)
        calendar.set(Calendar.MINUTE, boundary % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (now < calendar.timeInMillis) calendar.add(Calendar.DAY_OF_YEAR, -1)
        return calendar.timeInMillis
    }

    private fun usageTodayByPackage(context: Context, packages: Set<String>): Map<String, Long> = runCatching {
        if (packages.isEmpty()) return@runCatching emptyMap()
        val start = currentUsageWindowStartMillis(context)
        val now = System.currentTimeMillis()
        val totals = mutableMapOf<String, Long>()
        val activeSince = mutableMapOf<String, Long>()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, now)
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName?.takeIf { it in packages } ?: continue
            val at = event.timeStamp.coerceIn(start, now)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    activeSince.putIfAbsent(pkg, at)
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED,
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val since = activeSince.remove(pkg)
                    if (since != null && at > since) totals[pkg] = (totals[pkg] ?: 0L) + (at - since)
                }
                android.app.usage.UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    activeSince.toMap().forEach { (activePkg, since) ->
                        if (at > since) totals[activePkg] = (totals[activePkg] ?: 0L) + (at - since)
                    }
                    activeSince.clear()
                }
            }
        }
        activeSince.forEach { (pkg, since) ->
            if (now > since) totals[pkg] = (totals[pkg] ?: 0L) + (now - since)
        }
        totals
    }.getOrDefault(emptyMap())

    fun packageUsageTodayMillis(context: Context, packageName: String): Long =
        usageTodayByPackage(context, setOf(packageName))[packageName] ?: 0L

    fun groupUsageTodayMillis(context: Context, group: RestrictedGroup): Long =
        usageTodayByPackage(context, group.apps.map { it.packageName }.toSet()).values.sum()

    fun limitSnapshot(context: Context, packageName: String, group: RestrictedGroup?): GroupLimitSnapshot {
        val app = entryForPackage(context, packageName)
        val appUsed = packageUsageTodayMillis(context, packageName)
        val groupUsed = group?.let { groupUsageTodayMillis(context, it) } ?: appUsed
        val appLimit = (app?.dailyLimitMinutes ?: 0) * 60_000L
        val groupLimit = (group?.dailyLimitMinutes ?: 0) * 60_000L
        val useAppLimit = appLimit > 0L
        val effectiveUsed = if (useAppLimit) appUsed else groupUsed
        val effectiveLimit = if (useAppLimit) appLimit else groupLimit
        return GroupLimitSnapshot(
            appUsedMillis = appUsed,
            groupUsedMillis = groupUsed,
            limitMillis = effectiveLimit,
            overMillis = (effectiveUsed - effectiveLimit).coerceAtLeast(0L),
            source = if (useAppLimit) LimitSource.APP else LimitSource.GROUP,
        )
    }

    fun groupLimitSnapshot(context: Context, packageName: String, group: RestrictedGroup?): GroupLimitSnapshot = limitSnapshot(context, packageName, group)

    fun compactDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}小时${minutes}分钟"
            hours > 0L -> "${hours}小时"
            totalMinutes > 0L -> "${totalMinutes}分钟"
            else -> "不到1分钟"
        }
    }

    fun isGroupOverLimit(context: Context, group: RestrictedGroup): Boolean =
        group.dailyLimitMinutes > 0 && groupUsageTodayMillis(context, group) >= group.dailyLimitMinutes * 60_000L

    fun globalBreakSettings(context: Context): GlobalBreakSettings {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val options = prefs.getString(KEY_GLOBAL_BREAK_REST_OPTIONS, DEFAULT_REST_OPTIONS)
            .orEmpty()
            .split('|', ',', '，', '\n')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .take(6)
            .ifEmpty { DEFAULT_REST_OPTIONS.split('|').map { it.toInt() } }
        return GlobalBreakSettings(
            enabled = prefs.getBoolean(KEY_GLOBAL_BREAK_ENABLED, false),
            limitMinutes = prefs.getInt(KEY_GLOBAL_BREAK_LIMIT_MINUTES, 45).coerceAtLeast(1),
            restOptionsMinutes = options,
            screenOnSince = prefs.getLong(KEY_GLOBAL_SCREEN_ON_SINCE, 0L),
            restUntil = prefs.getLong(KEY_GLOBAL_REST_UNTIL, 0L),
            restActivity = prefs.getString(KEY_GLOBAL_REST_ACTIVITY, null),
        )
    }

    fun setGlobalBreakConfig(context: Context, enabled: Boolean, limitMinutes: Int, restOptionsMinutes: List<Int>) {
        val cleaned = restOptionsMinutes.filter { it > 0 }.distinct().take(6).ifEmpty { DEFAULT_REST_OPTIONS.split('|').map { it.toInt() } }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_GLOBAL_BREAK_ENABLED, enabled)
            .putInt(KEY_GLOBAL_BREAK_LIMIT_MINUTES, limitMinutes.coerceAtLeast(1))
            .putString(KEY_GLOBAL_BREAK_REST_OPTIONS, cleaned.joinToString("|"))
            .apply()
        appendLog(context, "info", "global_break_config enabled=$enabled limit=${limitMinutes.coerceAtLeast(1)} rest=${cleaned.joinToString(",")}")
    }

    fun markScreenInteractive(context: Context, interactive: Boolean, now: Long = System.currentTimeMillis()): Long {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!interactive) {
            prefs.edit().putLong(KEY_GLOBAL_SCREEN_ON_SINCE, 0L).apply()
            appendLog(context, "debug", "screen_interactive=false")
            return 0L
        }
        val existing = prefs.getLong(KEY_GLOBAL_SCREEN_ON_SINCE, 0L)
        if (existing > 0L) return existing
        prefs.edit().putLong(KEY_GLOBAL_SCREEN_ON_SINCE, now).apply()
        appendLog(context, "debug", "screen_interactive=true session_started")
        return now
    }

    fun resetGlobalScreenSession(context: Context, now: Long = System.currentTimeMillis(), reason: String = "global_screen_session_reset") {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_GLOBAL_SCREEN_ON_SINCE, now)
            .putString(KEY_LAST_SKIP_REASON, reason)
            .apply()
        appendLog(context, "info", reason)
    }

    fun globalScreenOnSince(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_GLOBAL_SCREEN_ON_SINCE, 0L)

    fun setGlobalRestUntil(context: Context, untilMillis: Long, now: Long = System.currentTimeMillis(), activity: String? = null) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_GLOBAL_REST_UNTIL, untilMillis)
            .putLong(KEY_GLOBAL_SCREEN_ON_SINCE, now)
            .putString(KEY_GLOBAL_REST_ACTIVITY, activity?.trim()?.takeIf { it.isNotBlank() })
            .putString(KEY_LAST_SKIP_REASON, "global_rest_until")
            .apply()
        appendLog(context, "notice", "global_rest_until until=${formatLogTime(untilMillis)} activity=${activity?.trim()?.takeIf { it.isNotBlank() } ?: "-"}")
    }

    fun clearGlobalRestForTest(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_GLOBAL_REST_UNTIL, 0L)
            .remove(KEY_GLOBAL_REST_ACTIVITY)
            .putLong(KEY_GLOBAL_SCREEN_ON_SINCE, now)
            .putString(KEY_LAST_SKIP_REASON, "global_rest_skipped_for_test")
            .apply()
        appendLog(context, "warn", "global_rest_skipped_for_test")
    }

    fun globalRestUntil(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_GLOBAL_REST_UNTIL, 0L)

    fun finishGlobalRestIfExpired(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val restUntil = prefs.getLong(KEY_GLOBAL_REST_UNTIL, 0L)
        if (restUntil in 1..now) {
            prefs.edit()
                .putLong(KEY_GLOBAL_REST_UNTIL, 0L)
                .remove(KEY_GLOBAL_REST_ACTIVITY)
                .putLong(KEY_GLOBAL_SCREEN_ON_SINCE, now)
                .putString(KEY_LAST_SKIP_REASON, "global_rest_finished")
                .apply()
            appendLog(context, "info", "global_rest_finished")
        }
    }

    @Synchronized
    fun acquireGlobalBreakOverlay(
        context: Context,
        owner: String,
        atMillis: Long = System.currentTimeMillis(),
        cooldownMillis: Long = 2_000L,
    ): Boolean {
        val activeOwner = activeGlobalOverlayOwner
        if (activeOwner != null && activeOwner != owner) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_SKIP_REASON, "global_overlay_active:$activeOwner")
                .apply()
            return false
        }
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val lastAt = prefs.getLong(KEY_GLOBAL_LAST_OVERLAY_AT, 0L)
        if (activeOwner == null && atMillis - lastAt in 0 until cooldownMillis) {
            prefs.edit().putString(KEY_LAST_SKIP_REASON, "global_overlay_cooldown").apply()
            return false
        }
        activeGlobalOverlayOwner = owner
        prefs.edit().putLong(KEY_GLOBAL_LAST_OVERLAY_AT, atMillis).apply()
        return true
    }

    @Synchronized
    fun releaseGlobalBreakOverlay(owner: String) {
        if (activeGlobalOverlayOwner == owner) activeGlobalOverlayOwner = null
    }

    fun lateNightSettings(context: Context): LateNightSettings {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return LateNightSettings(
            enabled = prefs.getBoolean(KEY_LATE_NIGHT_ENABLED, false),
            thresholdMinutes = prefs.getInt(KEY_LATE_NIGHT_THRESHOLD_MINUTES, 23 * 60).coerceIn(0, 1439),
            wakeMinutes = prefs.getInt(KEY_LATE_NIGHT_WAKE_MINUTES, 7 * 60).coerceIn(0, 1439),
            handledWindowStartMillis = prefs.getLong(KEY_LATE_NIGHT_HANDLED_WINDOW_START, 0L),
            records = lateNightRecords(context),
        )
    }

    fun setLateNightConfig(context: Context, enabled: Boolean, thresholdMinutes: Int, wakeMinutes: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LATE_NIGHT_ENABLED, enabled)
            .putInt(KEY_LATE_NIGHT_THRESHOLD_MINUTES, thresholdMinutes.coerceIn(0, 1439))
            .putInt(KEY_LATE_NIGHT_WAKE_MINUTES, wakeMinutes.coerceIn(0, 1439))
            .putString(KEY_LAST_SKIP_REASON, if (enabled) "late_night_enabled" else "late_night_disabled")
            .apply()
        appendLog(context, "info", "late_night_config enabled=$enabled threshold=${formatClockMinutes(thresholdMinutes)} wake=${formatClockMinutes(wakeMinutes)}")
    }

    fun currentLateNightWindow(context: Context, now: Long = System.currentTimeMillis()): LateNightWindow? {
        val settings = lateNightSettings(context)
        return currentLateNightWindow(settings.thresholdMinutes, settings.wakeMinutes, now)
    }

    fun shouldShowLateNightPrompt(context: Context, now: Long = System.currentTimeMillis()): LateNightWindow? {
        val settings = lateNightSettings(context)
        if (!settings.enabled) return null
        if (globalRestUntil(context) > now) return null
        val window = currentLateNightWindow(settings.thresholdMinutes, settings.wakeMinutes, now) ?: return null
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LATE_NIGHT_HANDLED_WINDOW_START, 0L) == window.startMillis) return null
        val lastPromptAt = prefs.getLong(KEY_LATE_NIGHT_LAST_PROMPT_AT, 0L)
        if (now - lastPromptAt in 0 until 5_000L) return null
        return window
    }

    fun markLateNightPromptShown(context: Context, atMillis: Long = System.currentTimeMillis(), source: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LATE_NIGHT_LAST_PROMPT_AT, atMillis)
            .putString(KEY_LAST_SKIP_REASON, source)
            .apply()
        appendLog(context, "notice", source)
    }

    fun recordLateNightImportantTask(
        context: Context,
        task: String,
        window: LateNightWindow,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val cleaned = task.trim().take(120)
        if (cleaned.isBlank()) return
        val updated = (lateNightRecords(context) + LateNightRecord(cleaned, atMillis, window.startMillis, window.wakeMillis))
            .sortedByDescending { it.createdMillis }
            .take(120)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LATE_NIGHT_RECORDS, encodeLateNightRecords(updated))
            .putLong(KEY_LATE_NIGHT_HANDLED_WINDOW_START, window.startMillis)
            .putLong(KEY_GLOBAL_SCREEN_ON_SINCE, atMillis)
            .putString(KEY_LAST_SKIP_REASON, "late_night_important_recorded")
            .apply()
        appendLog(context, "notice", "late_night_important_recorded task=$cleaned")
    }

    fun startLateNightSleep(context: Context, window: LateNightWindow, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LATE_NIGHT_HANDLED_WINDOW_START, window.startMillis)
            .apply()
        appendLog(context, "notice", "late_night_sleep_until ${formatLogTime(window.wakeMillis)}")
        setGlobalRestUntil(context, window.wakeMillis, now, "睡觉到 ${formatClockMinutes(lateNightSettings(context).wakeMinutes)}")
    }

    fun snoozeLateNightPromptForTest(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LATE_NIGHT_LAST_PROMPT_AT, now)
            .putString(KEY_LAST_SKIP_REASON, "late_night_snoozed_for_test")
            .apply()
        appendLog(context, "warn", "late_night_snoozed_for_test")
    }

    fun formatClockMinutes(minutes: Int): String = "%02d:%02d".format((minutes.coerceIn(0, 1439) / 60), (minutes.coerceIn(0, 1439) % 60))

    private fun currentLateNightWindow(thresholdMinutes: Int, wakeMinutes: Int, now: Long): LateNightWindow? {
        if (thresholdMinutes == wakeMinutes) return null
        val current = Calendar.getInstance().apply { timeInMillis = now }
        val nowMinutes = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE)

        fun clockOnDay(base: Calendar, minutes: Int): Long {
            val cal = base.clone() as Calendar
            cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
            cal.set(Calendar.MINUTE, minutes % 60)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        return if (thresholdMinutes > wakeMinutes) {
            when {
                nowMinutes >= thresholdMinutes -> {
                    val start = clockOnDay(current, thresholdMinutes)
                    val wake = (current.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.let { clockOnDay(it, wakeMinutes) }
                    LateNightWindow(start, wake)
                }
                nowMinutes < wakeMinutes -> {
                    val start = (current.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.let { clockOnDay(it, thresholdMinutes) }
                    val wake = clockOnDay(current, wakeMinutes)
                    LateNightWindow(start, wake)
                }
                else -> null
            }
        } else {
            if (nowMinutes in thresholdMinutes until wakeMinutes) {
                LateNightWindow(clockOnDay(current, thresholdMinutes), clockOnDay(current, wakeMinutes))
            } else {
                null
            }
        }
    }

    private fun lateNightRecords(context: Context): List<LateNightRecord> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LATE_NIGHT_RECORDS, null) ?: return emptyList()
        return parseLateNightRecords(raw)
    }

    private fun parseLateNightRecords(raw: String): List<LateNightRecord> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val task = obj.optString("task")
                if (task.isNotBlank()) {
                    add(
                        LateNightRecord(
                            task = task,
                            createdMillis = obj.optLong("createdMillis", 0L),
                            windowStartMillis = obj.optLong("windowStartMillis", 0L),
                            wakeMillis = obj.optLong("wakeMillis", 0L),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeLateNightRecords(records: List<LateNightRecord>): String = JSONArray().apply {
        records.forEach { record ->
            put(JSONObject().apply {
                put("task", record.task)
                put("createdMillis", record.createdMillis)
                put("windowStartMillis", record.windowStartMillis)
                put("wakeMillis", record.wakeMillis)
            })
        }
    }.toString()

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

    fun updateGroup(context: Context, groupId: String, name: String, dailyLimitMinutes: Int, requireTypedPurpose: Boolean) {
        setGroups(context, groups(context).map { group ->
            if (group.id == groupId) group.copy(
                name = name.trim().ifBlank { group.name },
                dailyLimitMinutes = dailyLimitMinutes.coerceAtLeast(0),
                requireTypedPurpose = requireTypedPurpose,
            ) else group
        })
    }


    fun updateGroupPurposeOptions(context: Context, groupId: String, purposeOptions: List<String>) {
        val cleaned = purposeOptions.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8)
        setGroups(context, groups(context).map { group ->
            if (group.id == groupId) group.copy(purposeOptions = cleaned) else group
        })
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
                        if (pkg.isNotBlank()) {
                            val purposeArray = app.optJSONArray("purposeOptions") ?: JSONArray()
                            val purposeOptions = buildList {
                                for (k in 0 until purposeArray.length()) purposeArray.optString(k).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                            val requireTyped = if (app.has("requireTypedPurpose")) app.optBoolean("requireTypedPurpose") else null
                            val dailyLimit = app.optInt("dailyLimitMinutes", 0).coerceAtLeast(0)
                            add(RestrictedAppEntry(pkg, app.optString("label", pkg), purposeOptions, requireTyped, dailyLimit))
                        }
                    }
                }
                val id = obj.optString("id").ifBlank { newGroupId() }
                val groupPurposeArray = obj.optJSONArray("purposeOptions") ?: JSONArray()
                val groupPurposeOptions = buildList {
                    for (k in 0 until groupPurposeArray.length()) groupPurposeArray.optString(k).takeIf { it.isNotBlank() }?.let { add(it) }
                }
                add(
                    RestrictedGroup(
                        id = id,
                        name = obj.optString("name", "分组"),
                        apps = apps,
                        dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 0).coerceAtLeast(0),
                        requireTypedPurpose = obj.optBoolean("requireTypedPurpose", false),
                        purposeOptions = groupPurposeOptions,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeGroups(groups: List<RestrictedGroup>): String = JSONArray().apply {
        groups.forEach { group ->
            put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("dailyLimitMinutes", group.dailyLimitMinutes)
                put("requireTypedPurpose", group.requireTypedPurpose)
                put("purposeOptions", JSONArray().apply { group.purposeOptions.forEach { put(it) } })
                put("apps", JSONArray().apply {
                    group.apps.forEach { app ->
                        put(JSONObject().apply {
                            put("packageName", app.packageName)
                            put("label", app.label)
                            put("purposeOptions", JSONArray().apply { app.purposeOptions.forEach { put(it) } })
                            app.requireTypedPurpose?.let { put("requireTypedPurpose", it) }
                            put("dailyLimitMinutes", app.dailyLimitMinutes)
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
        appendLog(context, "info", if (running) "floating_monitor_running" else "floating_monitor_stopped")
    }

    fun isFloatingRunning(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_FLOATING_RUNNING, false)

    fun setUnlockUntil(context: Context, packageName: String, untilMillis: Long, intent: String?) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UNLOCK_UNTIL_PREFIX + packageName, untilMillis)
            .putString(KEY_LAST_INTENT_PREFIX + packageName, intent)
            .apply()
        intent?.trim()?.takeIf { it.isNotBlank() }?.let { purpose ->
            addPurposeRecord(context, PurposeRecord(packageName, labelForPackage(context, packageName) ?: packageName, purpose, now, untilMillis))
        }
    }

    fun purposeRecordsForWindow(context: Context, startMillis: Long, endMillis: Long, packages: Set<String>): List<PurposeRecord> =
        purposeRecords(context).filter { record ->
            record.packageName in packages && record.untilMillis >= startMillis && record.startMillis <= endMillis
        }

    private fun addPurposeRecord(context: Context, record: PurposeRecord) {
        val windowStart = currentUsageWindowStartMillis(context)
        val updated = (purposeRecords(context).filter { it.untilMillis >= windowStart - 24 * 60 * 60_000L } + record)
            .sortedByDescending { it.startMillis }
            .take(500)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_PURPOSE_RECORDS, encodePurposeRecords(updated))
            .apply()
    }

    private fun purposeRecords(context: Context): List<PurposeRecord> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PURPOSE_RECORDS, null) ?: return emptyList()
        return parsePurposeRecords(raw)
    }

    private fun parsePurposeRecords(raw: String): List<PurposeRecord> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName")
                val purpose = obj.optString("purpose")
                if (pkg.isNotBlank() && purpose.isNotBlank()) {
                    add(PurposeRecord(
                        packageName = pkg,
                        label = obj.optString("label", pkg),
                        purpose = purpose,
                        startMillis = obj.optLong("startMillis", 0L),
                        untilMillis = obj.optLong("untilMillis", 0L),
                    ))
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodePurposeRecords(records: List<PurposeRecord>): String = JSONArray().apply {
        records.forEach { record ->
            put(JSONObject().apply {
                put("packageName", record.packageName)
                put("label", record.label)
                put("purpose", record.purpose)
                put("startMillis", record.startMillis)
                put("untilMillis", record.untilMillis)
            })
        }
    }.toString()

    fun unlockUntil(context: Context, packageName: String): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_UNLOCK_UNTIL_PREFIX + packageName, 0L)

    fun lastIntent(context: Context, packageName: String): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LAST_INTENT_PREFIX + packageName, null)

    fun suppressUntil(context: Context, packageName: String): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_SUPPRESS_UNTIL_PREFIX + packageName, 0L)

    fun suppressAfterCancel(context: Context, packageName: String, durationMillis: Long = 1_500L) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SUPPRESS_UNTIL_PREFIX + packageName, System.currentTimeMillis() + durationMillis)
            .remove(KEY_LAST_TRIGGER_PACKAGE)
            .remove(KEY_LAST_TRIGGER_AT)
            .putString(KEY_LAST_SKIP_REASON, "cancel_suppressed")
            .apply()
        appendLog(context, "debug", "cancel_suppressed pkg=$packageName duration=${durationMillis}ms")
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
        appendLog(context, "notice", "block_shown pkg=$packageName source=$source")
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
        appendLog(context, "debug", "seen_foreground pkg=$packageName")
    }

    fun markSkip(context: Context, reason: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_SKIP_REASON, reason)
            .apply()
        appendLog(context, "debug", "skip reason=$reason")
    }

    fun operationLogs(context: Context): List<OperationLogEntry> =
        parseOperationLogs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_OPERATION_LOGS, null).orEmpty())

    fun clearOperationLogs(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(KEY_OPERATION_LOGS)
            .putString(KEY_LAST_SKIP_REASON, "operation_logs_cleared")
            .apply()
    }

    private fun appendLog(context: Context, level: String, message: String, atMillis: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val current = parseOperationLogs(prefs.getString(KEY_OPERATION_LOGS, null).orEmpty())
        val cleanedMessage = message.replace('\n', ' ').take(180)
        val latest = current.firstOrNull()
        if (latest != null && latest.level == level && latest.message == cleanedMessage && atMillis - latest.atMillis in 0..10_000L) return
        val updated = (listOf(OperationLogEntry(atMillis, level, cleanedMessage)) + current).take(160)
        prefs.edit().putString(KEY_OPERATION_LOGS, encodeOperationLogs(updated)).apply()
    }

    private fun parseOperationLogs(raw: String): List<OperationLogEntry> = runCatching {
        if (raw.isBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val message = obj.optString("message")
                if (message.isNotBlank()) {
                    add(
                        OperationLogEntry(
                            atMillis = obj.optLong("atMillis", 0L),
                            level = obj.optString("level", "info"),
                            message = message,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeOperationLogs(logs: List<OperationLogEntry>): String = JSONArray().apply {
        logs.forEach { entry ->
            put(JSONObject().apply {
                put("atMillis", entry.atMillis)
                put("level", entry.level)
                put("message", entry.message)
            })
        }
    }.toString()

    private fun formatLogTime(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d:%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND),
        )
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

data class GlobalBreakSettings(
    val enabled: Boolean,
    val limitMinutes: Int,
    val restOptionsMinutes: List<Int>,
    val screenOnSince: Long,
    val restUntil: Long,
    val restActivity: String?,
)

data class LateNightSettings(
    val enabled: Boolean,
    val thresholdMinutes: Int,
    val wakeMinutes: Int,
    val handledWindowStartMillis: Long,
    val records: List<LateNightRecord>,
)

data class LateNightWindow(
    val startMillis: Long,
    val wakeMillis: Long,
)

data class LateNightRecord(
    val task: String,
    val createdMillis: Long,
    val windowStartMillis: Long,
    val wakeMillis: Long,
)

data class PurposeRecord(
    val packageName: String,
    val label: String,
    val purpose: String,
    val startMillis: Long,
    val untilMillis: Long,
)

data class DemoDebugState(
    val lastSeenPackage: String?,
    val lastSeenAt: Long,
    val lastTriggerPackage: String?,
    val lastTriggerAt: Long,
    val lastSkipReason: String?,
    val floatingRunning: Boolean,
)

data class OperationLogEntry(
    val atMillis: Long,
    val level: String,
    val message: String,
)


enum class LimitSource { APP, GROUP }

data class GroupLimitSnapshot(
    val appUsedMillis: Long,
    val groupUsedMillis: Long,
    val limitMillis: Long,
    val overMillis: Long,
    val source: LimitSource,
) {
    val usedMillisForLimit: Long get() = if (source == LimitSource.APP) appUsedMillis else groupUsedMillis
    val overLimit: Boolean get() = limitMillis > 0 && usedMillisForLimit >= limitMillis
}

data class RestrictedAppEntry(
    val packageName: String,
    val label: String,
    val purposeOptions: List<String> = emptyList(),
    val requireTypedPurpose: Boolean? = null,
    val dailyLimitMinutes: Int = 0,
)

data class PopupWhitelistEntry(
    val packageName: String,
    val label: String,
)

data class TimeTodoEntry(
    val id: String,
    val title: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val done: Boolean,
) {
    fun normalized(): TimeTodoEntry {
        val start = startMinutes.coerceIn(0, 1435)
        val end = endMinutes.coerceIn(5, 1440).coerceAtLeast(start + 5).coerceAtMost(1440)
        return copy(startMinutes = start, endMinutes = end)
    }
}

data class RestrictedGroup(
    val id: String,
    val name: String,
    val apps: List<RestrictedAppEntry>,
    val dailyLimitMinutes: Int = 0,
    val requireTypedPurpose: Boolean = false,
    val purposeOptions: List<String> = emptyList(),
)

package dev.stw

import java.time.Instant

data class AppRule(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val dailyLimitMinutes: Int? = 30,
    val maxOpenCountPerDay: Int? = 5,
    val delayBeforeOpenSeconds: Int = 15,
    val unlockSessionMinutes: Int = 5,
    val forcedRestMinutes: Int = 5,
    val customMessage: String? = null,
)

data class AppRuntimeState(
    val packageName: String,
    val usedTodayMillis: Long = 0,
    val openCountToday: Int = 0,
    val currentUnlockExpireAtEpochMillis: Long? = null,
    val forcedRestUntilEpochMillis: Long? = null,
)

sealed interface Decision {
    data object Allow : Decision
    data class ShowDelay(val seconds: Int, val unlockMinutes: Int, val message: String) : Decision
    data class LimitReached(val reason: String) : Decision
    data class ForcedRest(val untilEpochMillis: Long) : Decision
}

class RuleEngine {
    fun decide(
        rule: AppRule?,
        state: AppRuntimeState?,
        nowEpochMillis: Long = Instant.now().toEpochMilli(),
    ): Decision {
        if (rule == null || !rule.enabled) return Decision.Allow
        val runtime = state ?: AppRuntimeState(rule.packageName)

        val restUntil = runtime.forcedRestUntilEpochMillis
        if (restUntil != null && nowEpochMillis < restUntil) {
            return Decision.ForcedRest(restUntil)
        }

        val dailyLimitMillis = rule.dailyLimitMinutes?.times(60_000L)
        if (dailyLimitMillis != null && runtime.usedTodayMillis >= dailyLimitMillis) {
            return Decision.LimitReached("今日使用时长已达到 ${rule.dailyLimitMinutes} 分钟")
        }

        val openLimit = rule.maxOpenCountPerDay
        if (openLimit != null && runtime.openCountToday >= openLimit) {
            return Decision.LimitReached("今日打开次数已达到 $openLimit 次")
        }

        val unlockExpireAt = runtime.currentUnlockExpireAtEpochMillis
        if (unlockExpireAt != null && nowEpochMillis < unlockExpireAt) {
            return Decision.Allow
        }

        return Decision.ShowDelay(
            seconds = rule.delayBeforeOpenSeconds,
            unlockMinutes = rule.unlockSessionMinutes,
            message = rule.customMessage ?: "你现在打开它，是有明确目的，还是只是习惯？",
        )
    }
}

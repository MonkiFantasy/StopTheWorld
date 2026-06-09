package dev.stw

import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val rule = AppRule(packageName = "demo.app", appLabel = "Demo")

    @Test
    fun noRuleAllows() {
        assertTrue(RuleEngine().decide(null, null) is Decision.Allow)
    }

    @Test
    fun limitedAppWithoutUnlockShowsDelay() {
        assertTrue(RuleEngine().decide(rule, AppRuntimeState("demo.app")) is Decision.ShowDelay)
    }

    @Test
    fun dailyLimitBlocks() {
        val state = AppRuntimeState("demo.app", usedTodayMillis = 30 * 60_000L)
        assertTrue(RuleEngine().decide(rule, state) is Decision.LimitReached)
    }

    @Test
    fun activeUnlockAllows() {
        val state = AppRuntimeState("demo.app", currentUnlockExpireAtEpochMillis = 2_000L)
        assertTrue(RuleEngine().decide(rule, state, nowEpochMillis = 1_000L) is Decision.Allow)
    }
}

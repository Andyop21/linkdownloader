package io.github.andyop21.linkdownloader.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUpdateRuleTest {

    private val day = EngineUpdateRule.INTERVAL_MILLIS
    private val lastCheck = 1_800_000_000_000L

    @Test
    fun `due when no check has succeeded yet`() {
        assertTrue(EngineUpdateRule.isDue(null, lastCheck))
    }

    @Test
    fun `not due within a day of the last check`() {
        assertFalse(EngineUpdateRule.isDue(lastCheck, lastCheck))
        assertFalse(EngineUpdateRule.isDue(lastCheck, lastCheck + 1))
        assertFalse(EngineUpdateRule.isDue(lastCheck, lastCheck + day - 1))
    }

    @Test
    fun `due a day or more after the last check`() {
        assertTrue(EngineUpdateRule.isDue(lastCheck, lastCheck + day))
        assertTrue(EngineUpdateRule.isDue(lastCheck, lastCheck + 30 * day))
    }

    @Test
    fun `due when the clock reads earlier than the last check`() {
        assertTrue(EngineUpdateRule.isDue(lastCheck, lastCheck - 1))
    }
}

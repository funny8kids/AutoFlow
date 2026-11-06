package com.autoflow.app

import com.autoflow.app.platform.ClickerTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连点器计时规则（纯 JVM，不依赖 Android）。
 */
class ClickerTimingTest {

    @Test
    fun intervalIsClampedToSafeRange() {
        assertEquals(ClickerTiming.MIN_INTERVAL_MS, ClickerTiming.sanitizeInterval(0L))
        assertEquals(ClickerTiming.MIN_INTERVAL_MS, ClickerTiming.sanitizeInterval(-100L))
        assertEquals(ClickerTiming.MAX_INTERVAL_MS, ClickerTiming.sanitizeInterval(999_999L))
        assertEquals(200L, ClickerTiming.sanitizeInterval(200L))
    }

    @Test
    fun tapDurationIsClampedToSafeRange() {
        assertEquals(ClickerTiming.MIN_TAP_MS, ClickerTiming.sanitizeTapDuration(0L))
        assertEquals(ClickerTiming.MAX_TAP_MS, ClickerTiming.sanitizeTapDuration(99_999L))
        assertEquals(60L, ClickerTiming.sanitizeTapDuration(60L))
    }

    @Test
    fun waitSubtractsGestureTime() {
        assertEquals(140L, ClickerTiming.waitBeforeNext(200L, 60L))
        assertEquals(0L, ClickerTiming.waitBeforeNext(50L, 60L))
    }

    @Test
    fun countLimitIsRespected() {
        assertTrue(ClickerTiming.shouldContinue(0, 0))
        assertTrue(ClickerTiming.shouldContinue(999, 0))
        assertTrue(ClickerTiming.shouldContinue(4, 5))
        assertFalse(ClickerTiming.shouldContinue(5, 5))
    }

    @Test
    fun durationLimitIsRespected() {
        assertTrue(ClickerTiming.withinDuration(10_000L, 0L))
        assertTrue(ClickerTiming.withinDuration(999L, 1000L))
        assertFalse(ClickerTiming.withinDuration(1000L, 1000L))
    }

    @Test
    fun progressStaysInRange() {
        assertEquals(0f, ClickerTiming.progress(3, 0), 0.0001f)
        assertEquals(0.5f, ClickerTiming.progress(5, 10), 0.0001f)
        assertEquals(1f, ClickerTiming.progress(12, 10), 0.0001f)
    }
}

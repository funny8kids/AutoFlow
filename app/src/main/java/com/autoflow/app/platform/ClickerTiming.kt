package com.autoflow.app.platform

/**
 * 连点器计时规则。
 *
 * 纯逻辑、不依赖任何 Android 类型，因此可以用普通 JVM 单测覆盖
 * （见 app/src/test/java/com/autoflow/app/ClickerTimingTest.kt）。
 */
object ClickerTiming {
    /** 两次点击的最小间隔，防止把系统手势队列打爆。 */
    const val MIN_INTERVAL_MS = 50L

    /** 两次点击的间隔上限。 */
    const val MAX_INTERVAL_MS = 60_000L

    /** 单次点击按住的时长下限（系统手势下限）。 */
    const val MIN_TAP_MS = 30L

    /** 单次点击按住的时长上限。 */
    const val MAX_TAP_MS = 5_000L

    /** 点击间隔落在合理区间。 */
    fun sanitizeInterval(intervalMs: Long): Long = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

    /** 单次点击时长落在合理区间。 */
    fun sanitizeTapDuration(durationMs: Long): Long = durationMs.coerceIn(MIN_TAP_MS, MAX_TAP_MS)

    /**
     * 两次点击之间的等待时间。
     *
     * 一次点击手势本身要占用 [tapDurationMs]，所以周期里剩下的才是等待，
     * 这样实际点击频率才等于设定的间隔。
     */
    fun waitBeforeNext(intervalMs: Long, tapDurationMs: Long): Long =
        (sanitizeInterval(intervalMs) - sanitizeTapDuration(tapDurationMs)).coerceAtLeast(0L)

    /** 次数上限：[maxCount] 小于等于 0 表示不限次数。 */
    fun shouldContinue(done: Int, maxCount: Int): Boolean = maxCount <= 0 || done < maxCount

    /** 总时长上限：[durationMs] 小于等于 0 表示不限时长。 */
    fun withinDuration(elapsedMs: Long, durationMs: Long): Boolean =
        durationMs <= 0L || elapsedMs < durationMs

    /** 进度（0f~1f），供界面显示；不限次数时返回 0。 */
    fun progress(done: Int, maxCount: Int): Float =
        if (maxCount <= 0) 0f else (done.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
}

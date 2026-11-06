package com.autoflow.app.platform

import android.content.Context

/**
 * 连点器配置持久化（悬浮球服务与设置页面共享同一份配置）。
 */
object ClickerPrefs {
    private const val FILE = "autoflow_clicker"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_DURATION = "duration_ms"
    private const val KEY_MAX_COUNT = "max_count"
    private const val KEY_TAP = "tap_ms"
    private const val KEY_PASS_THROUGH = "pass_through"
    private const val KEY_BALL_X = "ball_x"
    private const val KEY_BALL_Y = "ball_y"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(context: Context, config: ClickerConfig) {
        prefs(context).edit()
            .putFloat(KEY_X, config.x)
            .putFloat(KEY_Y, config.y)
            .putLong(KEY_INTERVAL, config.intervalMs)
            .putLong(KEY_DURATION, config.durationMs)
            .putInt(KEY_MAX_COUNT, config.maxCount)
            .putLong(KEY_TAP, config.tapDurationMs)
            .apply()
    }

    fun load(context: Context): ClickerConfig {
        val p = prefs(context)
        return ClickerConfig(
            x = p.getFloat(KEY_X, 540f),
            y = p.getFloat(KEY_Y, 1200f),
            intervalMs = p.getLong(KEY_INTERVAL, 200L),
            durationMs = p.getLong(KEY_DURATION, 0L),
            maxCount = p.getInt(KEY_MAX_COUNT, 0),
            tapDurationMs = p.getLong(KEY_TAP, 60L),
        )
    }

    /** 是否完全穿透：悬浮球自身也不再拦截触摸。 */
    fun passThrough(context: Context): Boolean = prefs(context).getBoolean(KEY_PASS_THROUGH, false)

    fun setPassThrough(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PASS_THROUGH, value).apply()
    }

    /** 悬浮球上次的位置；没有记录时返回 null。 */
    fun ballPosition(context: Context): Pair<Int, Int>? {
        val p = prefs(context)
        if (!p.contains(KEY_BALL_X)) return null
        return p.getInt(KEY_BALL_X, 0) to p.getInt(KEY_BALL_Y, 0)
    }

    fun saveBallPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt(KEY_BALL_X, x).putInt(KEY_BALL_Y, y).apply()
    }
}

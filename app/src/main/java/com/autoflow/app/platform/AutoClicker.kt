package com.autoflow.app.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 连点器参数。 */
data class ClickerConfig(
    val x: Float = 540f,
    val y: Float = 1200f,
    /** 两次点击的间隔（毫秒）。 */
    val intervalMs: Long = 200L,
    /** 总时长（毫秒），0 表示不限。 */
    val durationMs: Long = 0L,
    /** 总次数，0 表示不限。 */
    val maxCount: Int = 0,
    /** 单次点击按住时长（毫秒）。 */
    val tapDurationMs: Long = 60L,
)

/** 连点器运行状态。 */
enum class ClickerPhase { IDLE, RUNNING, STOPPED }

/**
 * 连点引擎：按设定坐标与间隔连续点击。
 *
 * 手势注入依赖无障碍服务（[AutoAccessibilityService.tapAsync]），
 * 因此必须先在系统设置里开启 AutoFlow 的无障碍服务；
 * 手势逐个串行下发，避免系统拒绝并发手势导致漏点。
 */
object AutoClicker {

    private val _config = MutableStateFlow(ClickerConfig())
    val config: StateFlow<ClickerConfig> = _config.asStateFlow()

    private val _phase = MutableStateFlow(ClickerPhase.IDLE)
    val phase: StateFlow<ClickerPhase> = _phase.asStateFlow()

    private val _taps = MutableStateFlow(0)
    val taps: StateFlow<Int> = _taps.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    private var job: Job? = null

    fun update(config: ClickerConfig) {
        _config.value = config
    }

    fun isRunning(): Boolean = _phase.value == ClickerPhase.RUNNING

    /** 开始连点；重复调用无效。 */
    fun start(scope: CoroutineScope) {
        if (isRunning()) return
        _phase.value = ClickerPhase.RUNNING
        _taps.value = 0
        _error.value = null
        job = scope.launch {
            val startedAt = System.currentTimeMillis()
            var done = 0
            while (true) {
                val cfg = _config.value
                if (!ClickerTiming.withinDuration(System.currentTimeMillis() - startedAt, cfg.durationMs)) break
                if (!ClickerTiming.shouldContinue(done, cfg.maxCount)) break
                val ok = withTimeoutOrNull(GESTURE_TIMEOUT_MS) { tapOnce(cfg) } ?: false
                if (!ok) {
                    _error.value = "点击未执行：请确认已开启 AutoFlow 无障碍服务"
                    break
                }
                done++
                _taps.value = done
                delay(ClickerTiming.waitBeforeNext(cfg.intervalMs, cfg.tapDurationMs))
            }
            _phase.value = ClickerPhase.STOPPED
        }
    }

    /** 停止连点。 */
    fun stop() {
        job?.cancel()
        job = null
        if (_phase.value == ClickerPhase.RUNNING) _phase.value = ClickerPhase.STOPPED
    }

    private suspend fun tapOnce(cfg: ClickerConfig): Boolean {
        val service = AutoAccessibilityService.service() ?: return false
        val result = CompletableDeferred<Boolean>()
        val submitted = service.tapAsync(cfg.x, cfg.y, cfg.tapDurationMs) { ok -> result.complete(ok) }
        if (!submitted) {
            result.complete(false)
            return false
        }
        return result.await()
    }

    /** 单次手势的最长等待；超时视为失败，避免无限挂起。 */
    private const val GESTURE_TIMEOUT_MS = 5_000L
}

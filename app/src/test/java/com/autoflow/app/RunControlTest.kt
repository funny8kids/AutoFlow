package com.autoflow.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.app.core.ActionSpec
import com.autoflow.app.core.Macro
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 运行控制：删除宏 / 禁用宏 / 手动停止，都必须让正在执行的循环停下来。
 *
 * 回归背景：用户创建“灯光闪烁”（loop）后删除宏，循环仍在继续。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = AutoFlowApp::class)
class RunControlTest {

    private fun app() = ApplicationProvider.getApplicationContext<AutoFlowApp>()

    private fun blinkingMacro(name: String) = Macro(
        name = name,
        actions = listOf(
            ActionSpec("loop", mapOf("times" to "30")),
            ActionSpec("delay", mapOf("seconds" to "1")),
            ActionSpec("incrementVar", mapOf("name" to "n", "delta" to "1")),
            ActionSpec("endloop"),
        ),
    )

    private suspend fun awaitStopped(timeoutMs: Long = 5_000) {
        withTimeout(timeoutMs) {
            while (app().engine.runningIds.value.isNotEmpty()) delay(25)
        }
    }

    /** 高负载下启动可能慢：轮询等待宏进入运行状态。 */
    private suspend fun awaitRunning(id: String, timeoutMs: Long = 8_000) {
        withTimeout(timeoutMs) {
            while (!app().engine.isRunning(id)) delay(25)
        }
    }

    @Test
    fun deletingMacroStopsRunningLoop() = runBlocking {
        val macro = blinkingMacro("灯光闪烁-删除")
        app().store.upsert(macro)
        app().engine.run(macro)
        awaitRunning(macro.id)

        app().store.delete(macro)   // 删除宏 -> 必须停止执行
        awaitStopped()
        assertFalse(app().engine.isRunning(macro.id))
    }

    @Test
    fun disablingMacroStopsRunningLoop() = runBlocking {
        val macro = blinkingMacro("灯光闪烁-禁用")
        app().store.upsert(macro)
        app().engine.run(macro)
        awaitRunning(macro.id)
        app().store.upsert(macro.copy(enabled = false))
        awaitStopped()
        assertFalse(app().engine.isRunning(macro.id))
    }

    @Test
    fun cancelAllStopsEverything() = runBlocking {
        val a = blinkingMacro("A")
        val b = blinkingMacro("B")
        app().engine.run(a)
        app().engine.run(b)
        awaitRunning(a.id)
        awaitRunning(b.id)
        app().engine.cancelAll()
        awaitStopped()
        assertTrue(app().engine.runningIds.value.isEmpty())
    }
}

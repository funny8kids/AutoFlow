package com.autoflow.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.app.core.ActionSpec
import com.autoflow.app.core.Macro
import com.autoflow.app.core.RunContext
import com.autoflow.app.core.Variable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 引擎控制流测试：if / loop / 变量。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = AutoFlowApp::class)
class EngineTest {

    private fun engine() = ApplicationProvider.getApplicationContext<AutoFlowApp>().engine

    @Test
    fun ifAndLoopWork() = runBlocking {
        val macro = Macro(
            name = "控制流",
            variables = listOf(Variable("x", value = "0")),
            actions = listOf(
                ActionSpec("setVariable", mapOf("name" to "x", "value" to "5", "scope" to "local")),
                ActionSpec("if", mapOf("op" to "equals", "name" to "x", "value" to "5")),
                ActionSpec("setVariable", mapOf("name" to "y", "value" to "hit", "scope" to "local")),
                ActionSpec("endif"),
                ActionSpec("loop", mapOf("times" to "3")),
                ActionSpec("incrementVar", mapOf("name" to "n", "delta" to "1")),
                ActionSpec("endloop"),
            ),
        )
        val ctx = RunContext(macro, "manual", "test", globals = mutableMapOf())
        engine().execute(macro, ctx)
        assertEquals("5", ctx.locals["x"])
        assertEquals("hit", ctx.locals["y"])
        assertEquals("3", ctx.locals["n"])
    }

    @Test
    fun ifFalseSkipsBody() = runBlocking {
        val macro = Macro(
            name = "if false",
            actions = listOf(
                ActionSpec("setVariable", mapOf("name" to "x", "value" to "a")),
                ActionSpec("if", mapOf("op" to "equals", "name" to "x", "value" to "b")),
                ActionSpec("setVariable", mapOf("name" to "y", "value" to "hit")),
                ActionSpec("endif"),
                ActionSpec("setVariable", mapOf("name" to "z", "value" to "done")),
            ),
        )
        val ctx = RunContext(macro, "manual", "test", globals = mutableMapOf())
        engine().execute(macro, ctx)
        assertEquals(null, ctx.locals["y"])
        assertEquals("done", ctx.locals["z"])
    }

    @Test
    fun breakLoopExitsImmediately() = runBlocking {
        val macro = Macro(
            name = "break",
            actions = listOf(
                ActionSpec("loop", mapOf("times" to "5")),
                ActionSpec("incrementVar", mapOf("name" to "n", "delta" to "1")),
                ActionSpec("breakLoop"),
                ActionSpec("incrementVar", mapOf("name" to "never", "delta" to "1")),
                ActionSpec("endloop"),
                ActionSpec("setVariable", mapOf("name" to "after", "value" to "yes")),
            ),
        )
        val ctx = RunContext(macro, "manual", "test", globals = mutableMapOf())
        engine().execute(macro, ctx)
        assertEquals("1", ctx.locals["n"])
        assertEquals(null, ctx.locals["never"])
        assertEquals("yes", ctx.locals["after"])
    }
}

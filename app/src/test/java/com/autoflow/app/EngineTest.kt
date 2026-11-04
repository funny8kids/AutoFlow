package com.autoflow.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.app.core.ActionSpec
import com.autoflow.app.core.ConstraintSpec
import com.autoflow.app.core.Macro
import com.autoflow.app.core.RunContext
import com.autoflow.app.core.Variable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** 约束 OR 模式：任一满足即通过（对齐 MacroDroid constraintAndOrSpinner）。 */
    @Test
    fun constraintOrModePassesWhenOneSatisfies() = runBlocking {
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val wrongMonth = month % 12 + 1
        val macro = Macro(
            name = "or-约束",
            constraintMode = "or",
            constraints = listOf(
                ConstraintSpec("monthEquals", mapOf("month" to wrongMonth.toString())),  // 恒为 false
                ConstraintSpec("monthEquals", mapOf("month" to month.toString())),       // 恒为 true
            ),
        )
        val ctx = RunContext(macro, "manual", "test", globals = mutableMapOf())
        assertTrue(engine().constraintsPass(macro, ctx))
    }

    /** 约束 AND 模式：一个不满足即整体不通过。 */
    @Test
    fun constraintAndModeFailsWhenOneFails() = runBlocking {
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val wrongMonth = month % 12 + 1
        val macro = Macro(
            name = "and-约束",
            constraintMode = "and",
            constraints = listOf(
                ConstraintSpec("monthEquals", mapOf("month" to wrongMonth.toString())),
                ConstraintSpec("monthEquals", mapOf("month" to month.toString())),
            ),
        )
        val ctx = RunContext(macro, "manual", "test", globals = mutableMapOf())
        assertFalse(engine().constraintsPass(macro, ctx))
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
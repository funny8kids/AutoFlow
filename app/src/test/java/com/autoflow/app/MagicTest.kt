package com.autoflow.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.app.core.Macro
import com.autoflow.app.core.RunContext
import com.autoflow.app.platform.ActionExecutors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 魔法文本替换。
 *
 * 回归背景：早期实现用 Regex("\\{lv=([^}]+)}")，在 Android 的 ICU 正则引擎上
 * 会因“多余转义”抛 PatternSyntaxException，导致 ActionExecutors 静态初始化失败、
 * App 启动必崩；而 JVM 正则允许该写法，单元测试无法发现。
 * 现在改为手写解析，此测试保证行为正确且不依赖正则方言。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = AutoFlowApp::class)
class MagicTest {

    private fun ctx(): RunContext {
        val ctx = RunContext(Macro(name = "magic"), "manual", "test", globals = mutableMapOf())
        ctx.globals["g"] = "G"
        ctx.locals["l"] = "L"
        return ctx
    }

    @Test
    fun replacesLocalAndGlobal() {
        assertEquals("L/G", ActionExecutors.magic("{lv=l}/{v=g}", ctx()))
    }

    @Test
    fun globalFallsBackToLocal() {
        assertEquals("L", ActionExecutors.magic("{v=l}", ctx()))
    }

    @Test
    fun missingVariableBecomesEmpty() {
        assertEquals("[]", ActionExecutors.magic("[{lv=nope}]", ctx()))
    }

    @Test
    fun plainTextUntouched() {
        assertEquals("hello {not a var} world", ActionExecutors.magic("hello {not a var} world", ctx()))
        assertEquals("no braces", ActionExecutors.magic("no braces", ctx()))
    }

    @Test
    fun unclosedBraceIsKept() {
        assertEquals("open {lv=x", ActionExecutors.magic("open {lv=x", ctx()))
    }
}

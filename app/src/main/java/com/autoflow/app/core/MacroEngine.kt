package com.autoflow.app.core

import com.autoflow.app.AutoFlowApp
import com.autoflow.app.platform.ActionExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 宏执行引擎：顺序执行动作列表，支持 if/else/endif、loop/endloop、breakLoop / stopMacro，
 * 以及 callBlock 调用可复用动作块（最多嵌套 5 层）。
 */
class MacroEngine(private val app: AutoFlowApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val executors = ActionExecutors(app)

    fun run(macro: Macro, triggerLabel: String = "手动运行") {
        scope.launch {
            execute(macro, RunContext(macro, "manual", triggerLabel, globals = app.globals.snapshot().toMutableMap()))
        }
    }

    fun runWith(macro: Macro, triggerType: String, triggerLabel: String) {
        scope.launch {
            execute(macro, RunContext(macro, triggerType, triggerLabel, globals = app.globals.snapshot().toMutableMap()))
        }
    }

    suspend fun execute(macro: Macro, ctx: RunContext) = withContext(Dispatchers.Default) {
        if (!macro.enabled) return@withContext
        if (macro.actions.isEmpty()) {
            app.logs.warn(macro.name, "宏没有动作，跳过")
            return@withContext
        }
        if (!constraintsPass(macro, ctx)) {
            app.logs.warn(macro.name, "条件不满足，跳过执行")
            return@withContext
        }

        app.logs.info(macro.name, "▶ 开始执行（${ctx.triggerLabel}）")
        try {
            runActions(macro.actions, macro, ctx, 0)
        } catch (t: Throwable) {
            app.logs.error(macro.name, "执行异常: ${t.message}")
        }
        app.logs.info(macro.name, "■ 执行结束")
    }

    private suspend fun runActions(actions: List<ActionSpec>, macro: Macro, ctx: RunContext, depth: Int) {
        val loopCounters = HashMap<Int, Int>()
        var i = 0
        while (i < actions.size) {
            if (ctx.cancelled) {
                app.logs.warn(macro.name, "已被停止")
                return
            }
            val action = actions[i]
            when (action.type) {
                "if" -> {
                    if (!evalCondition(action, ctx)) {
                        i = skipToElseOrEndIf(actions, i)
                        if (i < 0) return
                    }
                }
                "else" -> {
                    val endIf = findMatching(actions, i, "else", "endif")
                    i = if (endIf < 0) return else endIf
                }
                "loop" -> {
                    val times = action.params["times"]?.toIntOrNull() ?: 1
                    val remaining = loopCounters[i] ?: times
                    if (remaining <= 0) {
                        loopCounters.remove(i)
                        i = findMatching(actions, i, "loop", "endloop")
                        if (i < 0) return
                    } else {
                        loopCounters[i] = remaining - 1
                    }
                }
                "endloop" -> {
                    val start = findLoopStart(actions, i)
                    if (start < 0) return
                    i = start - 1
                }
                "breakLoop" -> {
                    val end = findLoopEnd(actions, i)
                    if (end < 0) return
                    i = end
                }
                "stopMacro" -> {
                    ctx.cancelled = true
                    return
                }
                "comment" -> Unit
                "callBlock" -> callBlock(action, macro, ctx, depth)
                else -> executors.run(macro, action, ctx)
            }
            i++
        }
    }

    /** 调用可复用动作块：共享调用方的局部变量表，参数通过 JSON 注入。 */
    private suspend fun callBlock(action: ActionSpec, macro: Macro, ctx: RunContext, depth: Int) {
        val name = action.params["block"].orEmpty()
        if (name.isBlank()) {
            app.logs.error(macro.name, "调用动作块失败：没有填写块名称")
            return
        }
        val block = app.store.macros.value.firstOrNull { it.isBlock && it.name == name }
        if (block == null) {
            app.logs.error(macro.name, "动作块不存在：$name")
            return
        }
        if (depth >= 5) {
            app.logs.error(macro.name, "动作块嵌套超过 5 层，已中止：$name")
            return
        }
        val argsJson = action.params["args"].orEmpty().ifBlank { "{}" }
        runCatching {
            Json.parseToJsonElement(argsJson).jsonObject.forEach { (k, v) ->
                ctx.locals[k] = v.jsonPrimitive.content
            }
        }.onFailure { app.logs.warn(macro.name, "动作块参数不是合法 JSON：${it.message}") }

        app.logs.info(macro.name, "→ 进入动作块「$name」")
        runActions(block.actions, block, ctx, depth + 1)
        app.logs.info(macro.name, "← 退出动作块「$name」")
    }

    private suspend fun constraintsPass(macro: Macro, ctx: RunContext): Boolean =
        macro.constraints.all { c -> executors.checkConstraint(c, ctx) }

    private fun evalCondition(action: ActionSpec, ctx: RunContext): Boolean {
        val name = action.params["name"].orEmpty()
        val op = action.params["op"] ?: "notEmpty"
        val expected = ActionExecutors.magic(action.params["value"].orEmpty(), ctx)
        val actual = ctx.vars[name] ?: return op == "empty"
        return when (op) {
            "empty" -> actual.isEmpty()
            "notEmpty" -> actual.isNotEmpty()
            "equals" -> actual == expected
            "greater" -> (actual.toDoubleOrNull() ?: 0.0) > (expected.toDoubleOrNull() ?: 0.0)
            "less" -> (actual.toDoubleOrNull() ?: 0.0) < (expected.toDoubleOrNull() ?: 0.0)
            else -> false
        }
    }

    private fun skipToElseOrEndIf(actions: List<ActionSpec>, ifIndex: Int): Int {
        var depth = 0
        for (i in ifIndex until actions.size) {
            when (actions[i].type) {
                "if" -> depth++
                "endif" -> {
                    depth--
                    if (depth == 0) return i
                }
                "else" -> if (depth == 1) return i
            }
        }
        return -1
    }

    private fun findMatching(actions: List<ActionSpec>, start: Int, open: String, close: String): Int {
        var depth = 0
        for (i in start until actions.size) {
            when (actions[i].type) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun findLoopEnd(actions: List<ActionSpec>, from: Int): Int {
        var depth = 0
        for (i in from until actions.size) {
            when (actions[i].type) {
                "loop" -> depth++
                "endloop" -> {
                    if (depth == 0) return i
                    depth--
                }
            }
        }
        return -1
    }

    private fun findLoopStart(actions: List<ActionSpec>, end: Int): Int {
        var depth = 0
        for (i in end - 1 downTo 0) {
            when (actions[i].type) {
                "endloop" -> depth++
                "loop" -> {
                    if (depth == 0) return i
                    depth--
                }
            }
        }
        return -1
    }
}

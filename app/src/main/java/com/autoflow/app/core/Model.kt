package com.autoflow.app.core

import kotlinx.serialization.Serializable
import java.util.UUID

/** 一个自动化宏：触发器 + 条件 + 动作。 */
@Serializable
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "未命名宏",
    val description: String = "",
    val enabled: Boolean = true,
    val accent: Int = 0,
    val category: String = "",
    val isBlock: Boolean = false,
    val triggers: List<TriggerSpec> = emptyList(),
    val constraints: List<ConstraintSpec> = emptyList(),
    val actions: List<ActionSpec> = emptyList(),
    val variables: List<Variable> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
) {
    val hasTrigger: Boolean get() = triggers.isNotEmpty()
}

@Serializable
data class TriggerSpec(val type: String, val params: Map<String, String> = emptyMap())

@Serializable
data class ConstraintSpec(val type: String, val params: Map<String, String> = emptyMap())

@Serializable
data class ActionSpec(val type: String, val params: Map<String, String> = emptyMap())

@Serializable
enum class VarType { BOOLEAN, INTEGER, DECIMAL, STRING, DICTIONARY, ARRAY }

@Serializable
data class Variable(
    val name: String,
    val type: VarType = VarType.STRING,
    val value: String = "",
)

/** 一次宏执行的上下文：局部变量 + 全局变量快照。 */
class RunContext(
    val macro: Macro,
    val triggerType: String = "manual",
    val triggerLabel: String = "手动运行",
    val locals: MutableMap<String, String> = macro.variables.associate { it.name to it.value }.toMutableMap(),
    val globals: MutableMap<String, String> = mutableMapOf(),
) {
    var cancelled: Boolean = false

    /** 兼容旧代码：默认读写局部变量。 */
    val vars: MutableMap<String, String> get() = locals

    fun resolve(name: String): String = globals[name] ?: locals[name] ?: ""
}

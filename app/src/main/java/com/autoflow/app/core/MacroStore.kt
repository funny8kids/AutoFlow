package com.autoflow.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 一宏一 JSON 文件，写入走「临时文件 -> 原子 rename」协议，防止掉电损坏。
 */
class MacroStore(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dir: File by lazy {
        File(context.filesDir, "macros").apply { mkdirs() }
    }

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    /** 宏被删除时回调（用于停止仍在运行的执行）。 */
    var onMacroRemoved: ((Macro) -> Unit)? = null

    /** 宏被禁用时回调（用于停止仍在运行的执行）。 */
    var onMacroDisabled: ((Macro) -> Unit)? = null

    suspend fun load() = withContext(Dispatchers.IO) {
        val list = dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<Macro>(f.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.modifiedAt }
            ?: emptyList()
        _macros.value = list
    }

    suspend fun upsert(macro: Macro) {
        val updated = macro.copy(modifiedAt = System.currentTimeMillis())
        _macros.value = _macros.value.filterNot { it.id == updated.id } + updated
        write(updated)
        if (!updated.enabled) onMacroDisabled?.invoke(updated)
    }

    suspend fun delete(macro: Macro) {
        onMacroRemoved?.invoke(macro)
        _macros.value = _macros.value.filterNot { it.id == macro.id }
        withContext(Dispatchers.IO) {
            File(dir, "${macro.id}.json").delete()
            File(dir, "${macro.id}.json.new").delete()
            File(dir, "${macro.id}.json.backup").delete()
        }
    }

    private suspend fun write(macro: Macro) = withContext(Dispatchers.IO) {
        val target = File(dir, "${macro.id}.json")
        val tmp = File(dir, "${macro.id}.json.new")
        val backup = File(dir, "${macro.id}.json.backup")
        tmp.writeText(json.encodeToString(Macro.serializer(), macro))
        if (target.exists()) target.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun byId(id: String): Macro? = _macros.value.firstOrNull { it.id == id }
}

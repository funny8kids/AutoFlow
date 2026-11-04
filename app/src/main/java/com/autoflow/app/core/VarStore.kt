package com.autoflow.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 全局变量仓库：跨宏共享，持久化到 files/global_vars.json（原子写）。
 */
class GlobalVarStore(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "global_vars.json")

    private val _vars = MutableStateFlow<List<Variable>>(emptyList())
    val vars: StateFlow<List<Variable>> = _vars.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val list = runCatching {
            if (!file.exists()) emptyList() else
                json.decodeFromString(ListSerializer(Variable.serializer()), file.readText())
        }.getOrDefault(emptyList())
        _vars.value = list
    }

    suspend fun upsert(variable: Variable) {
        _vars.value = _vars.value.filterNot { it.name == variable.name } + variable
        persist()
    }

    suspend fun delete(name: String) {
        _vars.value = _vars.value.filterNot { it.name == name }
        persist()
    }

    /** 引擎执行前取一份快照，避免运行中读磁盘。 */
    fun snapshot(): Map<String, String> = _vars.value.associate { it.name to it.value }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val text = json.encodeToString(ListSerializer(Variable.serializer()), _vars.value)
        val tmp = File(file.parentFile, "global_vars.json.new")
        tmp.writeText(text)
        if (file.exists()) file.copyTo(File(file.parentFile, "global_vars.json.bak"), overwrite = true)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

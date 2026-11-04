package com.autoflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoflow.app.AutoFlowApp
import com.autoflow.app.core.LogEntry
import com.autoflow.app.core.Backup
import com.autoflow.app.core.Macro
import com.autoflow.app.core.Variable
import com.autoflow.app.core.ai.AiSettings
import com.autoflow.app.core.ai.DeepSeekClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Success(val macro: Macro, val sourcePrompt: String) : AiState
    data class Templates(val macros: List<Macro>, val sourcePrompt: String) : AiState
    data class Error(val message: String) : AiState
}

class AppViewModel : ViewModel() {
    private val app = AutoFlowApp.app()
    private val aiSettings = AiSettings(app)
    private val deepSeek = DeepSeekClient(aiSettings)

    val macros: StateFlow<List<Macro>> = app.store.macros
    val runningIds: StateFlow<Set<String>> = app.engine.runningIds

    fun stop(macro: Macro) = app.engine.cancel(macro.id)
    fun stopAll() = app.engine.cancelAll()
    val logs: StateFlow<List<LogEntry>> = app.logs.logs
    val globalVars: StateFlow<List<Variable>> = app.globals.vars

    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()

    private val _backupMsg = MutableStateFlow<String?>(null)
    val backupMsg: StateFlow<String?> = _backupMsg.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    // ---- AI 配置 ----
    val aiConfigured: Boolean get() = aiSettings.configured
    val aiBaseUrl: String get() = aiSettings.baseUrl
    val aiModel: String get() = aiSettings.model
    val aiApiKey: String get() = aiSettings.apiKey

    fun saveAiConfig(key: String, baseUrl: String, model: String) {
        aiSettings.apiKey = key
        aiSettings.baseUrl = baseUrl
        aiSettings.model = model
        _aiState.value = AiState.Idle
    }

    fun fetchModels(key: String, baseUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            aiSettings.apiKey = key
            aiSettings.baseUrl = baseUrl
            _modelsLoading.value = true
            deepSeek.listModels()
                .onSuccess { list ->
                    _models.value = list
                    app.logs.info("AI", "拉取到 ${list.size} 个模型")
                }
                .onFailure { app.logs.error("AI", "拉取模型失败：${it.message}") }
            _modelsLoading.value = false
        }
    }

    fun generateMacro(prompt: String) {
        if (prompt.isBlank()) {
            _aiState.value = AiState.Error("先描述一下你想要什么自动化")
            return
        }
        _aiState.value = AiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            deepSeek.generateMacro(prompt)
                .onSuccess { m ->
                    app.logs.info("AI", "生成宏「${m.name}」：${m.triggers.size} 触发器 / ${m.actions.size} 动作")
                    _aiState.value = AiState.Success(m, prompt)
                }
                .onFailure { e ->
                    app.logs.error("AI", "生成失败：${e.message}")
                    _aiState.value = AiState.Error(e.message ?: "生成失败")
                }
        }
    }

    fun generateTemplates(prompt: String) {
        if (prompt.isBlank()) {
            _aiState.value = AiState.Error("先描述一下模板主题，例如：通勤、睡眠、办公")
            return
        }
        _aiState.value = AiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            deepSeek.generateTemplates(prompt)
                .onSuccess { list ->
                    app.logs.info("AI", "生成模板包：${list.size} 个宏")
                    _aiState.value = AiState.Templates(list, prompt)
                }
                .onFailure { e ->
                    app.logs.error("AI", "生成模板失败：${e.message}")
                    _aiState.value = AiState.Error(e.message ?: "生成模板失败")
                }
        }
    }

    fun saveAll(macros: List<Macro>) = viewModelScope.launch(Dispatchers.IO) {
        macros.forEach { app.store.upsert(it) }
        app.triggers.refresh()
        com.autoflow.app.platform.QuickWidgetProvider.refresh(app)
        _aiState.value = AiState.Idle
    }

    fun resetAiState() { _aiState.value = AiState.Idle }

    // ---- 宏操作 ----
    fun run(macro: Macro) = app.engine.run(macro)

    fun save(macro: Macro, reloadTriggers: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {
        app.store.upsert(macro)
        if (reloadTriggers) app.triggers.refresh()
        com.autoflow.app.platform.QuickWidgetProvider.refresh(app)
    }

    fun delete(macro: Macro) = viewModelScope.launch(Dispatchers.IO) {
        app.store.delete(macro)
        app.triggers.refresh()
        com.autoflow.app.platform.QuickWidgetProvider.refresh(app)
    }

    fun clearLogs() = app.logs.clear()

    // ---- 备份 ----
    fun exportText(password: String): String =
        Backup.export(macros.value, globalVars.value, password)

    fun importBackup(raw: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = Backup.import(raw, password)
                file.macros.forEach { app.store.upsert(it) }
                file.globalVars.forEach { app.globals.upsert(it) }
                app.triggers.refresh()
                file.macros.size to file.globalVars.size
            }.onSuccess { (m, g) ->
                _backupMsg.value = "导入成功：${m} 个宏、${g} 个全局变量"
                app.logs.info("备份", "导入 ${m} 宏 / ${g} 变量")
            }.onFailure { e ->
                _backupMsg.value = "导入失败：${e.message}"
                app.logs.error("备份", "导入失败：${e.message}")
            }
        }
    }

    fun setBackupMessage(msg: String?) { _backupMsg.value = msg }

    fun saveGlobalVar(v: Variable) = viewModelScope.launch(Dispatchers.IO) { app.globals.upsert(v) }

    fun deleteGlobalVar(name: String) = viewModelScope.launch(Dispatchers.IO) { app.globals.delete(name) }

    fun newMacro(): Macro = Macro(name = "未命名宏", accent = (0..5).random())

    fun newBlock(): Macro = Macro(name = "未命名动作块", isBlock = true, accent = (0..5).random())
}
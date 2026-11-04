package com.autoflow.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val time: Long,
    val macroName: String,
    val message: String,
    val level: Level,
) {
    enum class Level { INFO, WARN, ERROR }
}

class LogRepository(private val context: Context) {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val file: File by lazy { File(context.filesDir, "autoflow.log") }
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun info(macro: String, message: String) = add(macro, message, LogEntry.Level.INFO)
    fun warn(macro: String, message: String) = add(macro, message, LogEntry.Level.WARN)
    fun error(macro: String, message: String) = add(macro, message, LogEntry.Level.ERROR)

    private fun add(macro: String, message: String, level: LogEntry.Level) {
        val entry = LogEntry(System.currentTimeMillis(), macro, message, level)
        _logs.value = (listOf(entry) + _logs.value).take(300)
        runCatching {
            file.appendText("${fmt.format(Date(entry.time))} [${level.name}] $macro: $message\n")
        }
    }

    fun clear() {
        _logs.value = emptyList()
        runCatching { file.delete() }
    }
}

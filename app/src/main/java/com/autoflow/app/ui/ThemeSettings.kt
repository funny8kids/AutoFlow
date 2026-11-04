package com.autoflow.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 主题设置：深色模式（system/on/off）与 AMOLED 纯黑，写入 SharedPreferences。 */
object ThemeSettings {
    var darkMode by mutableStateOf("system")
        private set
    var amoled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("autoflow_runtime", Context.MODE_PRIVATE)
        darkMode = p.getString("dark_mode", "system") ?: "system"
        amoled = p.getBoolean("amoled", false)
    }

    fun setDarkMode(context: Context, value: String) {
        darkMode = value
        context.getSharedPreferences("autoflow_runtime", Context.MODE_PRIVATE)
            .edit().putString("dark_mode", value).apply()
    }

    fun setAmoled(context: Context, value: Boolean) {
        amoled = value
        context.getSharedPreferences("autoflow_runtime", Context.MODE_PRIVATE)
            .edit().putBoolean("amoled", value).apply()
    }
}

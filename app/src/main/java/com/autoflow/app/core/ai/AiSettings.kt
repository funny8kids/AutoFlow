package com.autoflow.app.core.ai

import android.content.Context

/**
 * DeepSeek 连接配置。
 * 模型名不写死：用户可在设置页从 /models 接口拉取后选择。
 */
class AiSettings(context: Context) {

    private val prefs = context.getSharedPreferences("autoflow_ai", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().ifBlank { DEFAULT_BASE_URL }).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    val configured: Boolean get() = apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
    }
}

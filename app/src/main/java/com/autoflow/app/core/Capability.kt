package com.autoflow.app.core

import android.content.Context
import android.provider.Settings

/**
 * 能力分级：复刻 MacroDroid 的「公开 API < 无障碍 < Shizuku < Root」模型。
 * 动作声明自己最低需要哪一级，运行时探测并降级提示。
 */
enum class Capability(val label: String, val level: Int) {
    PUBLIC_API("公开 API", 0),
    NOTIFICATION_LISTENER("通知使用权", 1),
    ACCESSIBILITY("无障碍", 2),
    SHIZUKU("Shizuku (ADB)", 3),
    ROOT("Root", 4),
    ;

    companion object {
        fun byName(name: String?): Capability =
            entries.firstOrNull { it.name == name } ?: PUBLIC_API
    }
}

data class CapabilityState(
    val accessibility: Boolean = false,
    val notificationListener: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val root: Boolean = false,
) {
    fun available(capability: Capability): Boolean = when (capability) {
        Capability.PUBLIC_API -> true
        Capability.NOTIFICATION_LISTENER -> notificationListener
        Capability.ACCESSIBILITY -> accessibility
        Capability.SHIZUKU -> shizukuInstalled
        Capability.ROOT -> root
    }

    fun bestLabel(): String = when {
        root -> "Root"
        shizukuInstalled -> "Shizuku"
        accessibility -> "无障碍"
        else -> "公开 API"
    }
}

object CapabilityDetector {

    fun notificationListenerEnabled(context: Context): Boolean = runCatching {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val expected = context.packageName + "/" + "com.autoflow.app.platform.AutoNotificationListener"
        flat.split(':').any { it.equals(expected, ignoreCase = true) }
    }.getOrDefault(false)

    fun shizukuInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    fun isAccessibilityEnabled(context: Context, expectedComponent: String): Boolean = runCatching {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        flat.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }.getOrDefault(false)
}

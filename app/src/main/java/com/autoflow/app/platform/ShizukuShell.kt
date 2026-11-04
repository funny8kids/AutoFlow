package com.autoflow.app.platform

import android.content.Context
import com.autoflow.app.AutoFlowApp

/**
 * Shizuku 探测（仅检查是否安装）。
 *
 * 说明：Shizuku 13.x 的 api 包不再对外暴露命令执行接口，且其 ContentProvider
 * 会在 Application 之前初始化，带来启动崩溃风险，因此本项目不引入该 SDK。
 * 如需 ADB 权限执行，请使用 Root 通道，或在后续版本接入 Shizuku UserService。
 */
object ShizukuShell {

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    fun hasPermission(): Boolean = false

    fun available(): Boolean = runCatching { isInstalled(AutoFlowApp.app()) }.getOrDefault(false)

    /** 预留：后续接入 UserService 时实现授权流程。 */
    fun requestPermission() = Unit
}

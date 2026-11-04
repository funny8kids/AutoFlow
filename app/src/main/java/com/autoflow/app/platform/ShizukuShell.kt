package com.autoflow.app.platform

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku（ADB 权限）探测与授权。
 *
 * 说明：Shizuku 13.x 的 api 包不再对外暴露命令执行接口（newProcess 已非 public），
 * 因此这里只负责「是否可用 + 申请授权」；实际命令执行仍走 Root 或普通 shell。
 */
object ShizukuShell {

    const val PERMISSION_REQUEST_CODE = 4101

    @Volatile
    private var lastGranted: Boolean? = null

    private val resultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        lastGranted = grantResult == PackageManager.PERMISSION_GRANTED
    }

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(lastGranted == true)

    fun available(): Boolean = hasPermission()

    /** 注册监听 + 发起授权请求（不需要 Activity 回调）。 */
    fun requestPermission() {
        runCatching {
            if (!isBinderAlive()) return
            Shizuku.addRequestPermissionResultListener(resultListener)
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }
}

package com.autoflow.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shell 通道：Root 优先，否则普通 shell。
 */
object ShellRunner {

    enum class Tier { ROOT, SHELL }

    data class Result(val output: String, val exitCode: Int, val tier: Tier)

    @Volatile
    private var rootCached: Boolean? = null

    suspend fun isRootAvailable(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val cached = rootCached
        if (cached != null && !force) return@withContext cached
        val result = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()
            out.contains("uid=0") || err.contains("uid=0")
        }.getOrDefault(false)
        rootCached = result
        result
    }

    suspend fun run(command: String): Result = withContext(Dispatchers.IO) {
        val asRoot = isRootAvailable()
        runCatching {
            val process = if (asRoot) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            val out = process.inputStream.bufferedReader().readText().trim()
            val err = process.errorStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            Result(out.ifBlank { err }, code, if (asRoot) Tier.ROOT else Tier.SHELL)
        }.getOrElse {
            Result(it.message ?: "执行失败", -1, if (asRoot) Tier.ROOT else Tier.SHELL)
        }
    }
}

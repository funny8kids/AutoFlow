package com.autoflow.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.autoflow.app.core.GlobalVarStore
import com.autoflow.app.core.LogRepository
import com.autoflow.app.core.MacroEngine
import com.autoflow.app.core.MacroStore
import com.autoflow.app.platform.TriggerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoFlowApp : Application() {

    lateinit var store: MacroStore
    lateinit var globals: GlobalVarStore
    lateinit var logs: LogRepository
    lateinit var engine: MacroEngine
    lateinit var triggers: TriggerManager

    /** 启动过程中捕获到的致命错误（用于展示而不是白屏崩溃）。 */
    @Volatile
    var startupError: String? = null
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        runCatching {
            store = MacroStore(this)
            globals = GlobalVarStore(this)
            logs = LogRepository(this)
            engine = MacroEngine(this)
            triggers = TriggerManager(this, store, engine)
        }.onFailure {
            startupError = it.stackTraceToString()
            // 兜底：即使子系统初始化失败，也要保证字段可用
            if (::store.isInitialized.not()) store = runCatching { MacroStore(this) }.getOrThrow()
        }
        createChannel()
        if (startupError == null) {
            appScope.launch {
                runCatching { store.load() }
                runCatching { globals.load() }
                runCatching { triggers.refresh() }
                runCatching { com.autoflow.app.platform.QuickWidgetProvider.refresh(this@AutoFlowApp) }
            }
        }
    }

    /** 未捕获异常：写内部与外部文件，并标记下次进入安全模式。 */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val text = buildString {
                    append("=== ")
                    append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
                    append(" ===\n")
                    append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append(" / Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                    append(throwable.stackTraceToString())
                }
                runCatching { java.io.File(filesDir, "crash.log").writeText(text) }
                runCatching {
                    getExternalFilesDir(null)?.let { dir ->
                        dir.mkdirs()
                        java.io.File(dir, "crash.log").writeText(text)
                    }
                }
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_CRASH, text)
                    .putBoolean(KEY_CRASHED, true)
                    .apply()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "AutoFlow 通知", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { triggers.onLowMemory() }
    }

    companion object {
        const val CHANNEL_ID = "autoflow_default"
        const val PREFS = "autoflow_runtime"
        const val KEY_LAST_CRASH = "last_crash"
        const val KEY_CRASHED = "crashed_last_launch"

        @Volatile
        private var instance: AutoFlowApp? = null

        fun app(): AutoFlowApp = instance!!
    }
}

package com.autoflow.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        store = MacroStore(this)
        globals = GlobalVarStore(this)
        logs = LogRepository(this)
        engine = MacroEngine(this)
        triggers = TriggerManager(this, store, engine)
        createChannel()
        appScope.launch {
            store.load()
            globals.load()
            triggers.refresh()
            com.autoflow.app.platform.QuickWidgetProvider.refresh(this@AutoFlowApp)
        }
    }

    /** 把未捕获异常写到 files/crash.log，便于排查启动崩溃。 */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = java.io.File(filesDir, "crash.log")
                file.appendText(
                    "=== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ===\n" +
                        android.util.Log.getStackTraceString(throwable) + "\n"
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { triggers.onLowMemory() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AutoFlow 通知", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "autoflow_default"

        @Volatile
        private var instance: AutoFlowApp? = null

        fun app(): AutoFlowApp = instance!!
    }
}

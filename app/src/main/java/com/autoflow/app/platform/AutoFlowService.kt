package com.autoflow.app.platform

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import com.autoflow.app.AutoFlowApp

/**
 * 前台服务：保活进程 + 轮询前台应用（应用切换触发器）。
 */
class AutoFlowService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastForeground: String? = null
    private var lastMusic = false

    private val poller = object : Runnable {
        override fun run() {
            runCatching { pollForeground() }
            runCatching { pollMusic() }
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        handler.post(poller)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { startForeground(NOTIF_ID, buildNotification()) }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "AutoFlow 后台服务", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun hasUsageAccess(): Boolean = runCatching {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun pollForeground() {
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        val watched = app.store.macros.value.any { m ->
            m.enabled && m.triggers.any { it.type == "appForeground" }
        }
        if (!watched || !hasUsageAccess()) return

        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 6000, now) ?: return
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val resumed = if (Build.VERSION.SDK_INT >= 29) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (resumed) latest = event.packageName
        }
        val pkg = latest ?: return
        if (pkg != lastForeground) {
            lastForeground = pkg
            app.triggers.onAppForeground(pkg)
        }
    }

    private fun pollMusic() {
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        val watched = app.store.macros.value.any { m ->
            m.enabled && m.triggers.any { it.type == "musicActive" }
        }
        if (!watched) return
        val am = getSystemService(android.media.AudioManager::class.java) ?: return
        val playing = am.isMusicActive
        if (playing && !lastMusic) app.triggers.onMusicActive()
        lastMusic = playing
    }

    private fun buildNotification(): Notification {
        val enabled = runCatching {
            AutoFlowApp.app().store.macros.value.count { it.enabled && !it.isBlock }
        }.getOrDefault(0)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_SERVICE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("AutoFlow 正在后台运行")
            .setContentText("$enabled 个宏已启用，触发器保持监听")
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_SERVICE = "autoflow_service"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, AutoFlowService::class.java))
            }
        }
    }
}

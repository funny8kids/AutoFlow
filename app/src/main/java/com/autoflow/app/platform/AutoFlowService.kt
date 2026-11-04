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
            runCatching { pollCalendar() }
            runCatching { pollDataUsed() }
            runCatching { pollWeather() }
            runCatching { pollAirQuality() }
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (!safeStartForeground()) {
            stopSelf()
            return
        }
        handler.post(poller)
    }

    /** 前台服务启动在 Android 12+ 可能被系统拒绝，失败时安静退出而不是崩溃。 */
    private fun safeStartForeground(): Boolean = try {
        startForeground(NOTIF_ID, buildNotification())
        true
    } catch (t: Throwable) {
        runCatching {
            android.util.Log.w("AutoFlow", "startForeground failed: ${t.message}")
        }
        false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!safeStartForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
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

    private var calendarLastPoll = 0L
    private var dataLastPoll = 0L
    private var weatherLastPoll = 0L
    private var lastWeatherTemp: Double? = null
    private var lastWeatherKey = ""
    private var airLastPoll = 0L

    private fun pollCalendar() {
        val now = System.currentTimeMillis()
        if (now - calendarLastPoll < 30_000) return
        calendarLastPoll = now
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        val watched = app.store.macros.value.any { m ->
            m.enabled && m.triggers.any { it.type == "calendarEvent" }
        }
        if (!watched) return
        if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        runCatching {
            val end = now + 5 * 60_000L
            val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(end.toString())
                .build()
            val projection = arrayOf(
                android.provider.CalendarContract.Instances.EVENT_ID,
                android.provider.CalendarContract.Instances.BEGIN,
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    app.triggers.onCalendarEvent(cursor.getLong(0), cursor.getLong(1))
                }
            }
        }
    }

    /** 流量累计（自开机，TrafficStats），5 分钟一次。 */
    private fun pollDataUsed() {
        val now = System.currentTimeMillis()
        if (now - dataLastPoll < 5 * 60_000L) return
        dataLastPoll = now
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        val watched = app.store.macros.value.any { m ->
            m.enabled && m.triggers.any { it.type == "dataUsed" }
        }
        if (!watched) return
        val rx = android.net.TrafficStats.getTotalRxBytes()
        val tx = android.net.TrafficStats.getTotalTxBytes()
        val mb = ((rx + tx) / 1024L / 1024L).toInt()
        app.triggers.onDataUsed(mb)
    }

    /** 天气轮询（Open-Meteo 免费 API），15 分钟一次；坐标取第一个宏的参数。 */
    private fun pollWeather() {
        val now = System.currentTimeMillis()
        if (now - weatherLastPoll < 15 * 60_000L) return
        weatherLastPoll = now
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        val macro = app.store.macros.value.firstOrNull { m ->
            m.enabled && m.triggers.any { it.type == "weatherChange" }
        } ?: return
        val t = macro.triggers.first { it.type == "weatherChange" }
        val lat = t.params["lat"]?.toDoubleOrNull()
        val lon = t.params["lon"]?.toDoubleOrNull()
        if (lat == null || lon == null) return
        val key = "$lat,$lon"
        runCatching {
            val url = java.net.URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,precipitation,uv_index",
            )
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val text = conn.inputStream.use { it.readBytes().decodeToString() }
            conn.disconnect()
            val current = org.json.JSONObject(text).getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val precip = current.optDouble("precipitation", 0.0)
            val uv = current.optDouble("uv_index", 0.0)
            val previous = if (key == lastWeatherKey) lastWeatherTemp else null
            if (previous != null) {
                app.triggers.onWeatherChange(temp, previous)
            }
            app.triggers.onWeatherPrecip(precip)
            app.triggers.onUvIndex(uv)
            lastWeatherTemp = temp
            lastWeatherKey = key
        }.onFailure { app.logs.warn("AutoFlowService", "天气拉取失败: ${it.message}") }
    }

    /** 空气质量轮询（Open-Meteo air-quality），30 分钟一次。 */
    private fun pollAirQuality() {
        val now = System.currentTimeMillis()
        if (now - airLastPoll < 30 * 60_000L) return
        airLastPoll = now
        val app = runCatching { AutoFlowApp.app() }.getOrNull() ?: return
        val macro = app.store.macros.value.firstOrNull { m ->
            m.enabled && m.triggers.any { it.type == "airQuality" }
        } ?: return
        val t = macro.triggers.first { it.type == "airQuality" }
        val lat = t.params["lat"]?.toDoubleOrNull()
        val lon = t.params["lon"]?.toDoubleOrNull()
        if (lat == null || lon == null) return
        runCatching {
            val conn = java.net.URL(
                "https://api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi",
            ).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val text = conn.inputStream.use { it.readBytes().decodeToString() }
            conn.disconnect()
            val aqi = org.json.JSONObject(text).getJSONObject("current").getInt("us_aqi")
            app.triggers.onAirQuality(aqi)
        }.onFailure { app.logs.warn("AutoFlowService", "空气质量拉取失败: ${it.message}") }
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
        if (playing != lastMusic) app.triggers.onMediaPlaybackChanged(playing)
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
            // Android 13+ 未授予通知权限时不启动前台服务，避免无通知导致的异常
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, AutoFlowService::class.java))
            }
        }
    }
}

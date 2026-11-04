package com.autoflow.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.autoflow.app.AutoFlowApp
import com.autoflow.app.core.Macro
import com.autoflow.app.core.MacroEngine
import com.autoflow.app.core.MacroStore
import com.autoflow.app.core.TriggerSpec
import java.util.Calendar
import kotlin.math.sqrt

/**
 * 触发器管理：把宏里声明的触发器翻译成系统监听（闹钟 / 广播 / 传感器 / 通知服务）。
 */
class TriggerManager(
    private val app: AutoFlowApp,
    private val store: MacroStore,
    private val engine: MacroEngine,
) {
    private var batteryReceiver: BroadcastReceiver? = null
    private var connectivityReceiver: BroadcastReceiver? = null
    private var mediaReceiver: BroadcastReceiver? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var btReceiver: BroadcastReceiver? = null
    private var tickReceiver: BroadcastReceiver? = null
    private var wifiScanReceiver: BroadcastReceiver? = null
    private var genericReceiver: BroadcastReceiver? = null
    private var genericActions: Set<String> = emptySet()
    private var btProfileReceiver: BroadcastReceiver? = null
    private var smsObserver: android.database.ContentObserver? = null
    private var brightnessObserver: android.database.ContentObserver? = null
    private var volumeObserver: android.database.ContentObserver? = null
    private var ringerReceiver: BroadcastReceiver? = null
    private var lastHour = -1
    private var lastSsid: String? = null
    private var lastVpn = false
    private var lastForegroundPkg: String? = null
    private var lastBrightness = -1
    private var lastVolume = -1
    private val sensorManager by lazy { app.getSystemService(SensorManager::class.java) }
    private val sensors = HashMap<Int, SensorEventListener>()
    private var lastShakeAt = 0L
    private var proximityNear = false
    private var lightAbove: Boolean? = null

    fun refresh() {
        val macros = store.macros.value.filter { !it.isBlock }
        scheduleAlarms(macros)
        ensureBattery(macros)
        ensureConnectivity(macros)
        ensureExtraReceivers(macros)
        ensureMoreReceivers(macros)
        ensureGenericBroadcast(macros)
        ensureSensors(macros)
        app.logs.info("触发器", "已刷新：${macros.count { it.enabled }} 个启用宏")
    }

    // ---------------- 通用分发 ----------------

    private fun dispatch(type: String, label: String, match: (TriggerSpec) -> Boolean = { true }) {
        store.macros.value.filter { it.enabled && !it.isBlock }.forEach { macro ->
            if (macro.triggers.any { it.type == type && match(it) }) {
                engine.runWith(macro, type, label)
            }
        }
    }

    // ---------------- 闹钟 ----------------

    private fun scheduleAlarms(macros: List<Macro>) {
        val am = app.getSystemService(AlarmManager::class.java)
        macros.filter { it.enabled }.forEach { macro ->
            macro.triggers.forEach { t ->
                when (t.type) {
                    "interval" -> {
                        val minutes = t.params["minutes"]?.toLongOrNull() ?: 15L
                        runCatching {
                            am.setRepeating(
                                AlarmManager.RTC_WAKEUP,
                                System.currentTimeMillis() + minutes * 60_000,
                                minutes * 60_000,
                                pending(macro.id, "interval"),
                            )
                        }
                    }
                    "timeOfDay" -> {
                        val hour = t.params["hour"]?.toIntOrNull() ?: 8
                        val minute = t.params["minute"]?.toIntOrNull() ?: 0
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        runCatching {
                            am.setRepeating(
                                AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                                AlarmManager.INTERVAL_DAY, pending(macro.id, "timeOfDay"),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun pending(macroId: String, kind: String): PendingIntent {
        val intent = Intent(app, SystemEventReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_MACRO_ID, macroId)
            putExtra(EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            app, (macroId + kind).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---------------- 电量 / 温度 ----------------

    private fun ensureBattery(macros: List<Macro>) {
        val need = macros.any { m -> m.enabled && m.triggers.any { it.type == "battery" || it.type == "batteryTemp" } }
        if (need && batteryReceiver == null) {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)

                    if (level >= 0 && scale > 0) {
                        val pct = level * 100 / scale
                        if (pct >= 100) dispatch("batteryFull", "电量已充满")
                        dispatch("battery", "电量 $pct%") { t ->
                            val threshold = t.params["level"]?.toIntOrNull() ?: 20
                            if ((t.params["direction"] ?: "below") == "below") pct <= threshold else pct >= threshold
                        }
                    }
                    when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                        BatteryManager.BATTERY_PLUGGED_AC -> dispatch("chargingAc", "接通交流电")
                        BatteryManager.BATTERY_PLUGGED_USB -> dispatch("chargingUsb", "接通 USB 电")
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> dispatch("chargingWireless", "无线充电")
                    }
                    val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    if (voltage > 0) {
                        dispatch("batteryVoltage", "电压 $voltage mV") { t ->
                            voltage <= (t.params["mv"]?.toIntOrNull() ?: 3600)
                        }
                    }
                    val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                    if (health > 0 && health != BatteryManager.BATTERY_HEALTH_GOOD) {
                        dispatch("batteryHealth", "电池健康异常（$health）")
                    }
                    if (tempTenths != Int.MIN_VALUE) {
                        val celsius = tempTenths / 10.0
                        dispatch("batteryTemp", "电池 $celsius°C") { t ->
                            val threshold = t.params["celsius"]?.toDoubleOrNull() ?: 40.0
                            if ((t.params["direction"] ?: "above") == "above") celsius >= threshold else celsius <= threshold
                        }
                    }
                }
            }
            register(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } else if (!need) {
            batteryReceiver?.let { r -> runCatching { app.unregisterReceiver(r) } }
            batteryReceiver = null
        }
    }

    // ---------------- 网络 / WiFi SSID ----------------

    private fun ensureConnectivity(macros: List<Macro>) {
        val need = macros.any { m -> m.enabled && m.triggers.any { it.type == "connectivity" || it.type == "wifiSsid" } }
        if (need && connectivityReceiver == null) {
            connectivityReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = handleNetwork()
            }
            register(connectivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        } else if (!need) {
            connectivityReceiver?.let { r -> runCatching { app.unregisterReceiver(r) } }
            connectivityReceiver = null
        }
    }

    private fun handleNetwork() {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val wifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true

        dispatch("connectivity", if (online) "网络可用" else "网络断开") { t ->
            (t.params["state"] ?: "online") == if (online) "online" else "offline"
        }
        if (cellular) dispatch("mobileDataConnected", "移动数据已连接")
        else dispatch("mobileDataDisconnected", "移动数据断开")
        if (!wifi) dispatch("wifiLost", "WiFi 已断开")

        val vpn = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        if (vpn != lastVpn) {
            lastVpn = vpn
            if (vpn) dispatch("vpnConnected", "VPN 已连接") else dispatch("vpnDisconnected", "VPN 已断开")
        }

        val ssid = currentSsid()
        if (ssid != null) {
            if (ssid != lastSsid) {
                lastSsid = ssid
                dispatch("wifiSsidChanged", "WiFi 切换到 $ssid")
            }
            dispatch("wifiSsid", "WiFi $ssid") { t ->
                t.params["ssid"].orEmpty().equals(ssid, ignoreCase = true)
            }
        } else {
            lastSsid = null
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? = runCatching {
        val wm = app.applicationContext.getSystemService(WifiManager::class.java)
        wm.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()

    private fun networkAvailable(): Boolean = runCatching {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    // ---------------- 媒体键 / USB ----------------

    private fun ensureExtraReceivers(macros: List<Macro>) {
        val needMedia = macros.any { m -> m.enabled && m.triggers.any { it.type == "mediaButton" } }
        if (needMedia && mediaReceiver == null) {
            mediaReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    dispatch("mediaButton", "媒体按键")
                }
            }
            register(mediaReceiver, IntentFilter(Intent.ACTION_MEDIA_BUTTON))
        } else if (!needMedia) {
            mediaReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            mediaReceiver = null
        }

        val needUsb = macros.any { m -> m.enabled && m.triggers.any { it.type == "usbConnected" } }
        if (needUsb && usbReceiver == null) {
            usbReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val connected = intent.getBooleanExtra("connected", false)
                    dispatch("usbConnected", if (connected) "USB 已连接" else "USB 已断开") { t ->
                        (t.params["state"] ?: "on") == if (connected) "on" else "off"
                    }
                }
            }
            register(usbReceiver, IntentFilter("android.hardware.usb.action.USB_STATE"))
        } else if (!needUsb) {
            usbReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            usbReceiver = null
        }
    }

    /** 通用广播触发器：按宏里填写的 Action 动态注册一个接收器。 */
    private fun ensureGenericBroadcast(macros: List<Macro>) {
        val wanted = macros.filter { it.enabled }
            .flatMap { m ->
                m.triggers.filter { it.type == "broadcast" }
                    .mapNotNull { it.params["action"]?.trim()?.takeIf { a -> a.isNotEmpty() } }
            }
            .toSet()

        if (wanted == genericActions && genericReceiver != null) return

        genericReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        genericReceiver = null
        genericActions = wanted
        if (wanted.isEmpty()) return

        val filter = IntentFilter()
        wanted.forEach { filter.addAction(it) }
        genericReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                dispatch("broadcast", "广播 $action") { t -> t.params["action"] == action }
            }
        }
        register(genericReceiver, filter)
    }

    private fun ensureMoreReceivers(macros: List<Macro>) {
        val needBt = macros.any { m ->
            m.enabled && m.triggers.any { it.type == "bluetoothConnected" || it.type == "bluetoothDisconnected" }
        }
        if (needBt && btReceiver == null) {
            btReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED ->
                            dispatch("bluetoothConnected", "蓝牙已连接")
                        android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                            dispatch("bluetoothDisconnected", "蓝牙已断开")
                    }
                }
            }
            register(
                btReceiver,
                IntentFilter().apply {
                    addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                },
            )
        } else if (!needBt) {
            btReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            btReceiver = null
        }

        val needRinger = macros.any { m -> m.enabled && m.triggers.any { it.type == "ringerModeChanged" } }
        if (needRinger && ringerReceiver == null) {
            ringerReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val mode = app.getSystemService(android.media.AudioManager::class.java).ringerMode
                    val key = when (mode) {
                        android.media.AudioManager.RINGER_MODE_SILENT -> "silent"
                        android.media.AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                        else -> "normal"
                    }
                    dispatch("ringerModeChanged", "铃声模式: $key") { t ->
                        (t.params["mode"] ?: "normal") == key
                    }
                }
            }
            register(ringerReceiver, IntentFilter(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION))
        } else if (!needRinger) {
            ringerReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            ringerReceiver = null
        }

        val needTick = macros.any { m -> m.enabled && m.triggers.any { it.type == "timeTick" } }
        if (needTick && tickReceiver == null) {
            tickReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    dispatch("timeTick", "每分钟")
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    if (hour != lastHour) {
                        lastHour = hour
                        dispatch("hourChanged", "整点 $hour 点")
                    }
                }
            }
            register(tickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        } else if (!needTick) {
            tickReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            tickReceiver = null
        }

        val needScan = macros.any { m -> m.enabled && m.triggers.any { it.type == "wifiScanResults" } }
        if (needScan && wifiScanReceiver == null) {
            wifiScanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) =
                    dispatch("wifiScanResults", "WiFi 扫描完成")
            }
            register(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        } else if (!needScan) {
            wifiScanReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            wifiScanReceiver = null
        }

        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        val needA2dp = macros.any { m -> m.enabled && m.triggers.any { it.type == "bluetoothA2dp" } }
        if (needA2dp && btProfileReceiver == null) {
            btProfileReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1)
                    if (state == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                        dispatch("bluetoothA2dp", "蓝牙音频已连接")
                    }
                }
            }
            register(
                btProfileReceiver,
                IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED),
            )
        } else if (!needA2dp) {
            btProfileReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            btProfileReceiver = null
        }

        val needSmsSent = macros.any { m -> m.enabled && m.triggers.any { it.type == "smsSent" } }
        if (needSmsSent && smsObserver == null) {
            smsObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    dispatch("smsSent", "短信已发送")
                }
            }
            runCatching {
                app.contentResolver.registerContentObserver(
                    android.net.Uri.parse("content://sms/sent"),
                    true,
                    smsObserver!!,
                )
            }
        } else if (!needSmsSent) {
            smsObserver?.let { runCatching { app.contentResolver.unregisterContentObserver(it) } }
            smsObserver = null
        }
        val needBrightness = macros.any { m -> m.enabled && m.triggers.any { it.type == "brightnessChanged" } }
        if (needBrightness && brightnessObserver == null) {
            brightnessObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    val value = runCatching {
                        android.provider.Settings.System.getInt(
                            app.contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS,
                            -1,
                        )
                    }.getOrDefault(-1)
                    if (value != lastBrightness && value >= 0) {
                        lastBrightness = value
                        dispatch("brightnessChanged", "亮度 $value")
                        dispatch("brightnessAt", "亮度 $value") { t ->
                            value >= (t.params["level"]?.toIntOrNull() ?: 200)
                        }
                    }
                }
            }
            runCatching {
                app.contentResolver.registerContentObserver(
                    android.provider.Settings.System.getUriFor(
                        android.provider.Settings.System.SCREEN_BRIGHTNESS
                    ),
                    true,
                    brightnessObserver!!,
                )
            }
        } else if (!needBrightness) {
            brightnessObserver?.let { runCatching { app.contentResolver.unregisterContentObserver(it) } }
            brightnessObserver = null
        }

        val needVolume = macros.any { m -> m.enabled && m.triggers.any { it.type == "volumeChanged" } }
        if (needVolume && volumeObserver == null) {
            volumeObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    val am = app.getSystemService(android.media.AudioManager::class.java)
                    val value = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    if (value != lastVolume) {
                        lastVolume = value
                        dispatch("volumeChanged", "音量 $value")
                        dispatch("volumeAt", "音量 $value") { t ->
                            value >= (t.params["level"]?.toIntOrNull() ?: 10)
                        }
                    }
                }
            }
            runCatching {
                app.contentResolver.registerContentObserver(
                    android.provider.Settings.System.CONTENT_URI,
                    true,
                    volumeObserver!!,
                )
            }
        } else if (!needVolume) {
            volumeObserver?.let { runCatching { app.contentResolver.unregisterContentObserver(it) } }
            volumeObserver = null
        }
    }

    // ---------------- 传感器 ----------------

    private fun ensureSensors(macros: List<Macro>) {
        val needStepDetect = macros.any { m -> m.enabled && m.triggers.any { it.type == "stepDetected" } }
        if (needStepDetect) {
            registerSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR) {
                dispatch("stepDetected", "走了一步")
            }
        } else unregisterSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR)

        val needStepCount = macros.any { m -> m.enabled && m.triggers.any { it.type == "stepCounter" } }
        if (needStepCount) {
            registerSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) { event ->
                val total = event.values[0].toInt()
                dispatch("stepCounter", "步数 $total") { t ->
                    total >= (t.params["steps"]?.toIntOrNull() ?: 5000)
                }
            }
        } else unregisterSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)

        val needPressure = macros.any { m -> m.enabled && m.triggers.any { it.type == "pressureChange" } }
        if (needPressure) {
            registerSensor(android.hardware.Sensor.TYPE_PRESSURE) { event ->
                val hpa = event.values[0]
                dispatch("pressureChange", "气压 ${hpa.toInt()} hPa") { t ->
                    val threshold = t.params["hpa"]?.toFloatOrNull() ?: 1013f
                    if ((t.params["direction"] ?: "above") == "above") hpa >= threshold else hpa <= threshold
                }
            }
        } else unregisterSensor(android.hardware.Sensor.TYPE_PRESSURE)

        val needAmbient = macros.any { m -> m.enabled && m.triggers.any { it.type == "ambientTemp" } }
        if (needAmbient) {
            registerSensor(android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE) { event ->
                val celsius = event.values[0]
                dispatch("ambientTemp", "环境 ${celsius.toInt()} ℃") { t ->
                    val threshold = t.params["celsius"]?.toFloatOrNull() ?: 30f
                    if ((t.params["direction"] ?: "above") == "above") celsius >= threshold else celsius <= threshold
                }
            }
        } else unregisterSensor(android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE)

        val needShake = macros.any { m -> m.enabled && m.triggers.any { it.type == "shake" } }
        val needProximity = macros.any { m -> m.enabled && m.triggers.any { it.type == "proximity" } }
        val needLight = macros.any { m -> m.enabled && m.triggers.any { it.type == "lightLevel" } }

        if (needShake) registerSensor(Sensor.TYPE_ACCELEROMETER) { event ->
            val gx = event.values[0] / SensorManager.GRAVITY_EARTH
            val gy = event.values[1] / SensorManager.GRAVITY_EARTH
            val gz = event.values[2] / SensorManager.GRAVITY_EARTH
            val g = sqrt((gx * gx + gy * gy + gz * gz).toDouble())
            val now = System.currentTimeMillis()
            if (g > 2.4 && now - lastShakeAt > 1200) {
                lastShakeAt = now
                dispatch("shake", "摇晃设备")
            }
        } else unregisterSensor(Sensor.TYPE_ACCELEROMETER)

        if (needProximity) registerSensor(Sensor.TYPE_PROXIMITY) { event ->
            val max = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.maximumRange ?: 5f
            val near = event.values[0] < max
            if (near != proximityNear) {
                proximityNear = near
                dispatch("proximity", if (near) "接近" else "离开") { t ->
                    (t.params["state"] ?: "near") == if (near) "near" else "far"
                }
            }
        } else unregisterSensor(Sensor.TYPE_PROXIMITY)

        if (needLight) registerSensor(Sensor.TYPE_LIGHT) { event ->
            val lux = event.values[0]
            val above = lux >= 500f
            if (lightAbove == null || above != lightAbove) {
                lightAbove = above
                dispatch("lightLevel", "光线 ${lux.toInt()} lux") { t ->
                    val threshold = t.params["lux"]?.toFloatOrNull() ?: 500f
                    val dir = t.params["direction"] ?: "above"
                    if (dir == "above") lux >= threshold else lux <= threshold
                }
            }
        } else unregisterSensor(Sensor.TYPE_LIGHT)
    }

    private fun registerSensor(type: Int, onEvent: (SensorEvent) -> Unit) {
        if (sensors.containsKey(type)) return
        val manager = sensorManager ?: return
        val sensor = manager.getDefaultSensor(type) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onEvent(event)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensors[type] = listener
    }

    private fun unregisterSensor(type: Int) {
        sensors.remove(type)?.let { runCatching { sensorManager?.unregisterListener(it) } }
    }

    private fun register(receiver: BroadcastReceiver?, filter: IntentFilter) {
        if (receiver == null) return
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
    }

    // ---------------- 系统事件入口 ----------------

    fun onSystemEvent(intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_ALARM -> {
                val id = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
                val kind = intent.getStringExtra(EXTRA_KIND) ?: "interval"
                store.macros.value.firstOrNull { it.id == id && it.enabled && !it.isBlock }?.let {
                    engine.runWith(it, kind, if (kind == "interval") "定时循环" else "每日定时")
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                dispatch("boot", "开机完成")
                AutoFlowService.start(app)
            }
            Intent.ACTION_SHUTDOWN -> dispatch("shutdown", "关机")
            Intent.ACTION_SCREEN_ON -> {
                dispatch("screen", "屏幕亮起") { it.params["state"] != "off" }
                val km = app.getSystemService(android.app.KeyguardManager::class.java)
                if (km?.isKeyguardLocked != true) dispatch("screenOnUnlocked", "亮屏并解锁")
            }
            Intent.ACTION_SCREEN_OFF -> {
                dispatch("screen", "屏幕熄灭") { it.params["state"] == "off" }
                val km = app.getSystemService(android.app.KeyguardManager::class.java)
                if (km?.isKeyguardLocked == true) dispatch("keyguardLocked", "屏幕已锁定")
            }
            Intent.ACTION_USER_UNLOCKED -> dispatch("userUnlocked", "用户解锁完成")
            Intent.ACTION_BATTERY_OKAY -> dispatch("batteryOkay", "电量恢复正常")
            Intent.ACTION_WALLPAPER_CHANGED -> dispatch("wallpaperChanged", "壁纸更换")
            "android.intent.action.SIM_STATE_CHANGED" -> dispatch("simStateChanged", "SIM 卡状态变化")
            "android.intent.action.PACKAGE_SUSPENDED" -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                dispatch("packageSuspended", "应用被暂停: $pkg") { t ->
                    val want = t.params["package"].orEmpty()
                    want.isBlank() || want == pkg
                }
            }
            "android.intent.action.PACKAGE_UNSUSPENDED" -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                dispatch("packageUnsuspended", "应用已恢复: $pkg") { t ->
                    val want = t.params["package"].orEmpty()
                    want.isBlank() || want == pkg
                }
            }
            android.app.UiModeManager.ACTION_ENTER_CAR_MODE -> dispatch("carModeEnter", "进入车载模式")
            android.app.UiModeManager.ACTION_EXIT_CAR_MODE -> dispatch("carModeExit", "退出车载模式")
            android.app.UiModeManager.ACTION_ENTER_DESK_MODE -> dispatch("deskModeEnter", "进入桌面模式")
            android.app.UiModeManager.ACTION_EXIT_DESK_MODE -> dispatch("deskModeExit", "退出桌面模式")
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                val o = app.resources.configuration.orientation
                val landscape = o == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                dispatch("rotationChange", if (landscape) "横屏" else "竖屏")
                dispatch("screenOrientation", if (landscape) "横屏" else "竖屏") { t ->
                    (t.params["orientation"] ?: "portrait") == if (landscape) "landscape" else "portrait"
                }
            }
            Intent.ACTION_DEVICE_STORAGE_OK -> dispatch("storageOk", "存储已恢复")
            Intent.ACTION_DATE_CHANGED -> dispatch("dayChanged", "日期已变化")
            Intent.ACTION_PACKAGE_DATA_CLEARED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                dispatch("packageDataCleared", "数据被清除: $pkg") { t ->
                    val want = t.params["package"].orEmpty()
                    want.isBlank() || want == pkg
                }
            }
            Intent.ACTION_MEDIA_MOUNTED -> dispatch("mediaMounted", "外部存储已挂载")
            Intent.ACTION_MEDIA_UNMOUNTED -> dispatch("mediaRemoved", "外部存储已移除")
            "android.nfc.action.NDEF_DISCOVERED" -> dispatch("nfcTag", "扫描到 NFC 标签")
            Intent.ACTION_DEVICE_STORAGE_LOW -> dispatch("storageLow", "存储空间不足")
            "android.os.action.DEVICE_IDLE_MODE_CHANGED" -> {
                val idle = app.getSystemService(android.os.PowerManager::class.java)?.isDeviceIdleMode == true
                dispatch("deviceIdle", if (idle) "进入低电耗" else "退出低电耗") { t ->
                    (t.params["state"] ?: "on") == if (idle) "on" else "off"
                }
            }
            Intent.ACTION_INPUT_METHOD_CHANGED -> dispatch("inputMethodChanged", "输入法切换")
            Intent.ACTION_MANAGED_PROFILE_ADDED -> dispatch("managedProfileAdded", "添加工作资料")
            Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE -> dispatch("externalAppsAvailable", "外部存储可用")
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                dispatch("packageFullyRemoved", "彻底卸载 $pkg") { t ->
                    val want = t.params["package"].orEmpty()
                    want.isBlank() || want == pkg
                }
            }
            Intent.ACTION_USER_PRESENT -> dispatch("userPresent", "解锁设备")
            Intent.ACTION_BATTERY_LOW -> dispatch("lowBattery", "低电量")
            "android.os.action.POWER_SAVE_MODE_CHANGED" -> {
                val on = app.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
                dispatch("powerSave", if (on) "省电模式开启" else "省电模式关闭") { t ->
                    (t.params["state"] ?: "on") == if (on) "on" else "off"
                }
            }
            Intent.ACTION_LOCALE_CHANGED -> dispatch("localeChange", "语言已切换")
            Intent.ACTION_TIMEZONE_CHANGED -> dispatch("timezoneChange", "时区已改变")
            Intent.ACTION_TIME_CHANGED -> dispatch("timeChange", "系统时间被修改")
            Intent.ACTION_MY_PACKAGE_REPLACED -> dispatch("packageReplaced", "应用已更新")
            Intent.ACTION_DOCK_EVENT -> {
                val state = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1)
                val docked = state > 0
                dispatch("dock", if (docked) "插入底座" else "拔出底座") { t ->
                    (t.params["state"] ?: "on") == if (docked) "on" else "off"
                }
            }
            Intent.ACTION_DREAMING_STARTED -> dispatch("dreamStart", "屏保开始")
            Intent.ACTION_DREAMING_STOPPED -> dispatch("dreamStop", "屏保结束")
            "android.nfc.action.ADAPTER_STATE_CHANGED" -> {
                val state = intent.getIntExtra("android.nfc.extra.ADAPTER_STATE", -1)
                val on = state == 3
                dispatch("nfcState", if (on) "NFC 打开" else "NFC 关闭") { t ->
                    (t.params["state"] ?: "on") == if (on) "on" else "off"
                }
            }
            Intent.ACTION_POWER_CONNECTED -> dispatch("powerConnected", "插入电源")
            Intent.ACTION_POWER_DISCONNECTED -> dispatch("powerDisconnected", "断开电源")
            Intent.ACTION_HEADSET_PLUG -> {
                val plugged = intent.getIntExtra("state", 0) == 1
                dispatch("headset", if (plugged) "插入耳机" else "拔出耳机") { t ->
                    (t.params["state"] ?: "on") == if (plugged) "on" else "off"
                }
            }
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                dispatch("airplane", if (on) "飞行模式开启" else "飞行模式关闭") { t ->
                    (t.params["state"] ?: "on") == if (on) "on" else "off"
                }
            }
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)
                val on = state == WifiManager.WIFI_STATE_ENABLED
                dispatch("wifi", if (on) "WiFi 开启" else "WiFi 关闭") { t ->
                    (t.params["state"] ?: "on") == if (on) "on" else "off"
                }
                if (on) handleNetwork()
            }
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                val type = if (action == Intent.ACTION_PACKAGE_ADDED) "packageAdded" else "packageRemoved"
                dispatch(type, if (type == "packageAdded") "安装 $pkg" else "卸载 $pkg") { t ->
                    val want = t.params["package"].orEmpty()
                    want.isBlank() || want == pkg
                }
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val key = when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> "ringing"
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> "offhook"
                    else -> "idle"
                }
                dispatch("callState", "通话: $key") { t -> (t.params["state"] ?: "ringing") == key }
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val body = runCatching {
                    Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString("") { it.messageBody.orEmpty() }
                }.getOrDefault("")
                dispatch("smsReceived", "收到短信") { t ->
                    val contains = t.params["contains"].orEmpty()
                    contains.isBlank() || body.contains(contains, ignoreCase = true)
                }
            }
        }
    }

    fun onAppForeground(packageName: String) {
        val previous = lastForegroundPkg
        lastForegroundPkg = packageName
        if (previous != null && previous != packageName) {
            dispatch("appLeftForeground", "离开 $previous") { t ->
                val want = t.params["package"].orEmpty()
                want.isBlank() || want == previous
            }
        }
        dispatch("appForeground", "切换到 $packageName") { t ->
            val want = t.params["package"].orEmpty()
            want.isBlank() || want == packageName
        }
    }

    fun onMediaPlaybackChanged(playing: Boolean) {
        dispatch("mediaPlaybackChanged", if (playing) "开始播放" else "停止播放") { t ->
            (t.params["state"] ?: "playing") == if (playing) "playing" else "stopped"
        }
    }

    private val firedCalendar = mutableSetOf<String>()

    fun onCalendarEvent(eventId: Long, begin: Long) {
        val key = "$eventId@$begin"
        if (!firedCalendar.add(key)) return
        if (firedCalendar.size > 200) firedCalendar.clear()
        val minutesUntil = ((begin - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
        dispatch("calendarEvent", "日历事件 $minutesUntil 分钟后") { t ->
            minutesUntil <= (t.params["minutes"]?.toIntOrNull() ?: 0)
        }
    }

    fun onNotificationRemoved(packageName: String) {
        dispatch("notificationRemoved", "通知被移除: $packageName") { t ->
            val want = t.params["package"].orEmpty()
            want.isBlank() || want == packageName
        }
    }

    fun onNotificationText(packageName: String, text: String) {
        dispatch("notificationText", "通知含文字: $packageName") { t ->
            val want = t.params["text"].orEmpty()
            want.isBlank() || text.contains(want, ignoreCase = true)
        }
    }

    fun onLowMemory() {
        dispatch("lowMemory", "系统内存不足")
    }

    fun onWifiSignal(rssi: Int) {
        dispatch("wifiSignal", "WiFi 信号 $rssi dBm") { t ->
            rssi <= (t.params["rssi"]?.toIntOrNull() ?: -75)
        }
    }

    fun onMusicActive() {
        dispatch("musicActive", "检测到音频播放")
    }

    fun onNotificationPosted(packageName: String) {
        dispatch("notification", "通知: $packageName") { t ->
            val want = t.params["package"].orEmpty()
            want.isBlank() || want == packageName
        }
    }

    companion object {
        const val ACTION_ALARM = "com.autoflow.app.ALARM"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_KIND = "kind"
    }
}

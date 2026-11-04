package com.autoflow.app.platform

import android.app.Notification
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.widget.Toast
import com.autoflow.app.AutoFlowApp
import com.autoflow.app.core.ActionSpec
import com.autoflow.app.core.ConstraintSpec
import com.autoflow.app.core.RunContext
import com.autoflow.app.core.VarType
import com.autoflow.app.core.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActionExecutors(private val app: AutoFlowApp) {

    private var torchOn = false
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var tts: TextToSpeech? = null

    init {
        runCatching { tts = TextToSpeech(app) { } }
    }

    suspend fun run(macro: com.autoflow.app.core.Macro, action: ActionSpec, ctx: RunContext) {
        val p = action.params
        when (action.type) {
            "toast" -> toast(magic(p["text"].orEmpty(), ctx))
            "notification" -> notify(macro.name, magic(p["title"].orEmpty(), ctx), magic(p["text"].orEmpty(), ctx))
            "vibrate" -> vibrate(p["ms"]?.toLongOrNull() ?: 300L)
            "beep" -> beep(p["ms"]?.toIntOrNull() ?: 200)
            "torch" -> torch(p["state"] ?: "toggle")
            "screenWake" -> screenWake(p["seconds"]?.toLongOrNull() ?: 5L)
            "volume" -> setVolume(p["level"]?.toIntOrNull() ?: 8)
            "mediaControl" -> mediaControl(p["action"] ?: "playPause")
            "playSound" -> playSound()
            "speak" -> speak(magic(p["text"].orEmpty(), ctx))
            "wifiSet" -> setWifi(p["on"] == "true")
            "http" -> http(macro.name, magic(p["url"].orEmpty(), ctx), p["method"] ?: "GET", p["body"].orEmpty(), p["toVar"].orEmpty(), ctx)
            "openUrl" -> openUrl(magic(p["url"].orEmpty(), ctx))
            "openSettings" -> openSettings()
            "setVariable" -> {
                val name = p["name"].orEmpty()
                val value = magic(p["value"].orEmpty(), ctx)
                val scope = p["scope"] ?: "local"
                if (name.isNotEmpty()) {
                    if (scope == "global") {
                        ctx.globals[name] = value
                        app.globals.upsert(Variable(name, VarType.STRING, value))
                    } else {
                        ctx.locals[name] = value
                    }
                }
                app.logs.info(macro.name, "变量 $name = $value（$scope）")
            }
            "clearVar" -> ctx.locals[p["name"].orEmpty()] = ""
            "dictSet" -> {
                val name = p["name"].orEmpty()
                val obj = runCatching {
                    Json.parseToJsonElement(ctx.locals[name].orEmpty()).jsonObject
                }.getOrNull() ?: kotlinx.serialization.json.JsonObject(emptyMap())
                val updated = kotlinx.serialization.json.JsonObject(
                    obj.toMutableMap().apply {
                        put(p["key"].orEmpty(), kotlinx.serialization.json.JsonPrimitive(magic(p["value"].orEmpty(), ctx)))
                    }
                )
                ctx.locals[name] = updated.toString()
            }
            "dictGet" -> {
                val name = p["name"].orEmpty()
                val key = p["key"].orEmpty()
                val value = runCatching {
                    Json.parseToJsonElement(ctx.locals[name].orEmpty()).jsonObject[key]?.jsonPrimitive?.content.orEmpty()
                }.getOrDefault("")
                ctx.locals[p["toVar"].orEmpty()] = value
            }
            "arrayPush" -> {
                val name = p["name"].orEmpty()
                val arr = runCatching {
                    Json.parseToJsonElement(ctx.locals[name].orEmpty()).jsonArray
                }.getOrNull() ?: kotlinx.serialization.json.JsonArray(emptyList())
                val updated = kotlinx.serialization.json.JsonArray(
                    arr + kotlinx.serialization.json.JsonPrimitive(magic(p["value"].orEmpty(), ctx))
                )
                ctx.locals[name] = updated.toString()
            }
            "arrayGet" -> {
                val name = p["name"].orEmpty()
                val index = p["index"]?.toIntOrNull() ?: 0
                val value = runCatching {
                    Json.parseToJsonElement(ctx.locals[name].orEmpty()).jsonArray.getOrNull(index)?.jsonPrimitive?.content.orEmpty()
                }.getOrDefault("")
                ctx.locals[p["toVar"].orEmpty()] = value
            }
            "arraySize" -> {
                val name = p["name"].orEmpty()
                val size = runCatching {
                    Json.parseToJsonElement(ctx.locals[name].orEmpty()).jsonArray.size
                }.getOrDefault(0)
                ctx.locals[p["toVar"].orEmpty()] = size.toString()
            }
            "incrementVar" -> {
                val name = p["name"].orEmpty()
                val delta = p["delta"]?.toDoubleOrNull() ?: 1.0
                val cur = ctx.vars[name]?.toDoubleOrNull() ?: 0.0
                ctx.vars[name] = fmt(cur + delta)
            }
            "randomNumber" -> {
                val min = p["min"]?.toIntOrNull() ?: 1
                val max = p["max"]?.toIntOrNull() ?: 100
                val v = if (max > min) (min..max).random() else min
                ctx.vars[p["name"].orEmpty()] = v.toString()
            }
            "timestamp" -> {
                val pattern = p["pattern"].orEmpty().ifBlank { "yyyy-MM-dd HH:mm:ss" }
                ctx.vars[p["name"].orEmpty()] = runCatching {
                    SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
                }.getOrDefault(System.currentTimeMillis().toString())
            }
            "batteryToVar" -> ctx.vars[p["name"].orEmpty()] = batteryLevel().toString()
            "deviceInfo" -> ctx.vars[p["name"].orEmpty()] = when (p["field"] ?: "model") {
                "brand" -> Build.BRAND
                "sdk" -> Build.VERSION.SDK_INT.toString()
                "android" -> Build.VERSION.RELEASE
                else -> Build.MODEL
            }
            "stringOps" -> {
                val name = p["name"].orEmpty()
                val cur = ctx.vars[name].orEmpty()
                ctx.vars[name] = when (p["op"] ?: "upper") {
                    "lower" -> cur.lowercase()
                    "trim" -> cur.trim()
                    "reverse" -> cur.reversed()
                    else -> cur.uppercase()
                }
            }
            "regexExtract" -> {
                val src = magic(p["source"].orEmpty(), ctx)
                val group = p["group"]?.toIntOrNull() ?: 1
                val result = runCatching {
                    val m = Regex(p["pattern"].orEmpty()).find(src)
                    m?.groupValues?.getOrNull(group) ?: m?.value.orEmpty()
                }.getOrDefault("")
                ctx.vars[p["name"].orEmpty()] = result
            }
            "jsonExtract" -> {
                val src = magic(p["json"].orEmpty(), ctx)
                val key = p["key"].orEmpty()
                val result = runCatching {
                    Json.parseToJsonElement(src).jsonObject[key]?.jsonPrimitive?.content.orEmpty()
                }.getOrDefault("")
                ctx.vars[p["name"].orEmpty()] = result
            }
            "writeFile" -> writeFile(p["file"].orEmpty(), magic(p["content"].orEmpty(), ctx), append = false)
            "appendFile" -> writeFile(p["file"].orEmpty(), magic(p["content"].orEmpty(), ctx), append = true)
            "readFileToVar" -> {
                val f = File(app.filesDir, p["file"].orEmpty())
                ctx.vars[p["name"].orEmpty()] = runCatching { f.readText() }.getOrDefault("")
            }
            "deleteFile" -> runCatching { File(app.filesDir, p["file"].orEmpty()).delete() }
            "launchApp" -> launchApp(macro.name, p["package"].orEmpty())
            // ---- UI 交互（无障碍） ----
            "uiTap" -> a11y(macro.name, "点击坐标") { s ->
                s.tap(p["x"]?.toFloatOrNull() ?: 0f, p["y"]?.toFloatOrNull() ?: 0f)
            }
            "uiTapText" -> a11y(macro.name, "点击文字") { s ->
                s.tapText(magic(p["text"].orEmpty(), ctx))
            }
            "uiSwipe" -> a11y(macro.name, "滑动") { s ->
                s.swipe(
                    p["x1"]?.toFloatOrNull() ?: 0f,
                    p["y1"]?.toFloatOrNull() ?: 0f,
                    p["x2"]?.toFloatOrNull() ?: 0f,
                    p["y2"]?.toFloatOrNull() ?: 0f,
                    p["ms"]?.toLongOrNull() ?: 300L,
                )
            }
            "uiInput" -> a11y(macro.name, "输入文字") { s ->
                s.inputText(magic(p["text"].orEmpty(), ctx))
            }
            "uiBack" -> a11y(macro.name, "返回") { s ->
                s.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            "uiHome" -> a11y(macro.name, "回到桌面") { s ->
                s.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            }
            "uiRecents" -> a11y(macro.name, "最近任务") { s ->
                s.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            }
            "uiReadScreen" -> {
                val s = AutoAccessibilityService.service()
                if (s == null) {
                    app.logs.error(macro.name, "无障碍服务未开启，无法读取屏幕")
                } else {
                    val text = s.readScreen()
                    ctx.locals[p["toVar"].orEmpty().ifBlank { "screen" }] = text
                    app.logs.info(macro.name, "读取屏幕 ${text.length} 字")
                }
            }
            "delay" -> delay(((p["seconds"]?.toLongOrNull() ?: 1L) * 1000L).coerceIn(0L, 600_000L))
            "logMessage" -> app.logs.info(macro.name, magic(p["text"].orEmpty(), ctx))
            "comment", "if", "else", "endif", "loop", "endloop", "breakLoop", "stopMacro" -> Unit
            // ---- 文本处理 ----
            "stringConcat" -> {
                ctx.locals[p["toVar"].orEmpty()] =
                    magic(p["a"].orEmpty(), ctx) + magic(p["b"].orEmpty(), ctx)
            }
            "stringReplace" -> {
                ctx.locals[p["toVar"].orEmpty()] =
                    magic(p["source"].orEmpty(), ctx).replace(p["from"].orEmpty(), p["to"].orEmpty())
            }
            "stringSubstring" -> {
                val s = magic(p["source"].orEmpty(), ctx)
                val start = (p["start"]?.toIntOrNull() ?: 0).coerceIn(0, s.length)
                val len = (p["length"]?.toIntOrNull() ?: 10).coerceAtLeast(0)
                ctx.locals[p["toVar"].orEmpty()] = s.substring(start, (start + len).coerceAtMost(s.length))
            }
            "urlEncode" -> {
                ctx.locals[p["toVar"].orEmpty()] =
                    java.net.URLEncoder.encode(magic(p["source"].orEmpty(), ctx), "UTF-8")
            }
            "base64Encode" -> {
                ctx.locals[p["toVar"].orEmpty()] =
                    android.util.Base64.encodeToString(
                        magic(p["source"].orEmpty(), ctx).toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
            }
            "base64Decode" -> {
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    String(
                        android.util.Base64.decode(p["source"].orEmpty(), android.util.Base64.NO_WRAP),
                        Charsets.UTF_8,
                    )
                }.getOrDefault("")
            }
            "randomString" -> {
                val len = (p["length"]?.toIntOrNull() ?: 8).coerceIn(1, 256)
                val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                ctx.locals[p["toVar"].orEmpty()] = (1..len).map { chars.random() }.joinToString("")
            }
            // ---- 系统集成 ----
            "setRingerMode" -> runCatching {
                val am = app.getSystemService(AudioManager::class.java)
                am.ringerMode = when (p["mode"]) {
                    "silent" -> AudioManager.RINGER_MODE_SILENT
                    "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
            }
            "sendBroadcast" -> {
                val action = p["action"].orEmpty()
                if (action.isNotBlank()) {
                    val intent = Intent(action)
                    runCatching {
                        Json.parseToJsonElement(p["extras"].orEmpty().ifBlank { "{}" }).jsonObject.forEach { (k, v) ->
                            intent.putExtra(k, v.jsonPrimitive.content)
                        }
                    }
                    app.sendBroadcast(intent)
                }
            }
            "shareText" -> runCatching {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, magic(p["text"].orEmpty(), ctx))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "openAppInfo" -> runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:" + p["package"].orEmpty())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            "openWifiSettings" -> runCatching {
                app.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "setScreenTimeout" -> shellPrivileged(
                macro.name,
                "settings put system screen_off_timeout ${((p["seconds"]?.toIntOrNull() ?: 60) * 1000)}",
            )
            // ---- UI 扩展 ----
            "uiLongPress" -> a11y(macro.name, "长按") { s ->
                s.tap(
                    p["x"]?.toFloatOrNull() ?: 0f,
                    p["y"]?.toFloatOrNull() ?: 0f,
                    p["ms"]?.toLongOrNull() ?: 800L,
                )
            }
            "uiScroll" -> a11y(macro.name, "滚动") { s ->
                s.scroll(up = (p["direction"] ?: "up") == "up")
            }
            // ---- 剪贴板 / 文件 ----
            "clipboardSet" -> runCatching {
                val cm = app.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("AutoFlow", magic(p["text"].orEmpty(), ctx)))
            }
            "clipboardGet" -> {
                val text = runCatching {
                    val cm = app.getSystemService(android.content.ClipboardManager::class.java)
                    cm.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString().orEmpty()
                }.getOrDefault("")
                ctx.locals[p["toVar"].orEmpty()] = text
            }
            "fileList" -> {
                val dir = File(app.filesDir, p["dir"].orEmpty())
                ctx.locals[p["toVar"].orEmpty()] = dir.list()?.joinToString("|").orEmpty()
            }
            "fileExists" -> ctx.locals[p["toVar"].orEmpty()] =
                File(app.filesDir, p["file"].orEmpty()).exists().toString()
            "fileSize" -> ctx.locals[p["toVar"].orEmpty()] =
                File(app.filesDir, p["file"].orEmpty()).length().toString()
            "zipFile" -> withContext(Dispatchers.IO) {
                runCatching {
                    val src = File(app.filesDir, p["file"].orEmpty())
                    val out = File(app.filesDir, p["zip"].orEmpty().ifBlank { "archive.zip" })
                    java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zos ->
                        zos.putNextEntry(java.util.zip.ZipEntry(src.name))
                        src.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }.onFailure { app.logs.error(macro.name, "压缩失败: ${it.message}") }
            }
            "unzipFile" -> withContext(Dispatchers.IO) {
                runCatching {
                    val zip = File(app.filesDir, p["zip"].orEmpty().ifBlank { "archive.zip" })
                    java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val target = File(app.filesDir, entry.name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zis.copyTo(it) }
                            entry = zis.nextEntry
                        }
                    }
                }.onFailure { app.logs.error(macro.name, "解压失败: ${it.message}") }
            }
            // ---- 设备信息 ----
            "getIpAddress" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                java.net.NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { nic -> nic.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    ?.hostAddress.orEmpty()
            }.getOrDefault("")
            "getWifiSsid" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                @Suppress("DEPRECATION")
                val wm = app.applicationContext.getSystemService(WifiManager::class.java)
                wm.connectionInfo?.ssid?.trim('"').orEmpty()
            }.getOrDefault("")
            "getNetworkType" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                when {
                    caps == null -> "none"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "other"
                }
            }.getOrDefault("none")
            "getUptime" -> ctx.locals[p["toVar"].orEmpty()] =
                (android.os.SystemClock.elapsedRealtime() / 1000).toString()
            "getStorageFree" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                val stat = android.os.StatFs(app.filesDir.path)
                (stat.availableBytes / 1024 / 1024).toString()
            }.getOrDefault("0")
            // ---- 系统闹钟 ----
            "setAlarm" -> runCatching {
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_HOUR, p["hour"]?.toIntOrNull() ?: 7)
                    putExtra(android.provider.AlarmClock.EXTRA_MINUTES, p["minute"]?.toIntOrNull() ?: 0)
                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, p["message"].orEmpty())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            }
            "setTimer" -> runCatching {
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_LENGTH, p["seconds"]?.toIntOrNull() ?: 300)
                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, p["message"].orEmpty())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            }
            // ---- 通知扩展 ----
            "notificationBigText" -> runCatching {
                val nm = app.getSystemService(NotificationManager::class.java)
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    Notification.Builder(app, AutoFlowApp.CHANNEL_ID) else Notification.Builder(app)
                val n = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(magic(p["title"].orEmpty(), ctx).ifBlank { macro.name })
                    .setContentText(magic(p["text"].orEmpty(), ctx))
                    .setStyle(
                        Notification.BigTextStyle().bigText(
                            magic(p["big"].orEmpty(), ctx).ifBlank { magic(p["text"].orEmpty(), ctx) }
                        )
                    )
                    .setAutoCancel(true).build()
                nm.notify(System.currentTimeMillis().toInt(), n)
            }
            "cancelNotification" -> runCatching {
                val nm = app.getSystemService(NotificationManager::class.java)
                nm.cancel(p["id"]?.toIntOrNull() ?: 0)
            }
            "vibratePattern" -> {
                val pattern = p["pattern"].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toLongArray()
                if (pattern.isNotEmpty()) {
                    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
                    else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
                    runCatching { v?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
                }
            }
            "ttsStop" -> runCatching { tts?.stop() }
            // ---- 文件扩展 ----
            "fileMove" -> withContext(Dispatchers.IO) {
                runCatching {
                    val from = File(app.filesDir, p["from"].orEmpty())
                    val to = File(app.filesDir, p["to"].orEmpty())
                    if (!from.renameTo(to)) {
                        from.copyTo(to, overwrite = true)
                        from.delete()
                    }
                }.onFailure { app.logs.error(macro.name, "移动失败: ${it.message}") }
            }
            "fileCopy" -> withContext(Dispatchers.IO) {
                runCatching {
                    File(app.filesDir, p["from"].orEmpty())
                        .copyTo(File(app.filesDir, p["to"].orEmpty()), overwrite = true)
                }.onFailure { app.logs.error(macro.name, "复制失败: ${it.message}") }
            }
            "fileRename" -> withContext(Dispatchers.IO) {
                runCatching {
                    File(app.filesDir, p["from"].orEmpty()).renameTo(File(app.filesDir, p["to"].orEmpty()))
                }.onFailure { app.logs.error(macro.name, "重命名失败: ${it.message}") }
            }
            "hashFile" -> withContext(Dispatchers.IO) {
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    val digest = java.security.MessageDigest.getInstance(p["algo"] ?: "MD5")
                    File(app.filesDir, p["file"].orEmpty()).inputStream().use { stream ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = stream.read(buf)
                            if (n <= 0) break
                            digest.update(buf, 0, n)
                        }
                    }
                    digest.digest().joinToString("") { b -> "%02x".format(b) }
                }.getOrDefault("")
            }
            "hashText" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                val digest = java.security.MessageDigest.getInstance(p["algo"] ?: "MD5")
                digest.digest(magic(p["text"].orEmpty(), ctx).toByteArray()).joinToString("") { b -> "%02x".format(b) }
            }.getOrDefault("")
            "uuid" -> ctx.locals[p["toVar"].orEmpty()] = java.util.UUID.randomUUID().toString()
            "stringSplit" -> {
                val src = magic(p["source"].orEmpty(), ctx)
                val sep = p["sep"].orEmpty().ifBlank { "," }
                val arr = if (src.isEmpty()) emptyList() else src.split(sep)
                ctx.locals[p["toVar"].orEmpty()] =
                    kotlinx.serialization.json.JsonArray(arr.map { kotlinx.serialization.json.JsonPrimitive(it) }).toString()
            }
            "arrayJoin" -> {
                val sep = p["sep"].orEmpty().ifBlank { "," }
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    Json.parseToJsonElement(ctx.locals[p["name"].orEmpty()].orEmpty()).jsonArray
                        .joinToString(sep) { it.jsonPrimitive.content }
                }.getOrDefault("")
            }
            "arrayRemove" -> {
                val target = magic(p["value"].orEmpty(), ctx)
                ctx.locals[p["name"].orEmpty()] = runCatching {
                    val arr = Json.parseToJsonElement(ctx.locals[p["name"].orEmpty()].orEmpty()).jsonArray
                    kotlinx.serialization.json.JsonArray(arr.filter { it.jsonPrimitive.content != target }).toString()
                }.getOrDefault("[]")
            }
            "dictRemove" -> {
                val key = p["key"].orEmpty()
                ctx.locals[p["name"].orEmpty()] = runCatching {
                    val obj = Json.parseToJsonElement(ctx.locals[p["name"].orEmpty()].orEmpty()).jsonObject
                    kotlinx.serialization.json.JsonObject(obj.filterKeys { it != key }).toString()
                }.getOrDefault("{}")
            }
            "dictKeys" -> {
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    Json.parseToJsonElement(ctx.locals[p["name"].orEmpty()].orEmpty()).jsonObject.keys.joinToString("|")
                }.getOrDefault("")
            }
            "numberRound" -> {
                val name = p["name"].orEmpty()
                val digits = (p["digits"]?.toIntOrNull() ?: 0).coerceIn(0, 6)
                val raw = ctx.locals[name]?.toDoubleOrNull()
                if (raw != null) {
                    ctx.locals[name] = String.format(java.util.Locale.US, "%.${digits}f", raw)
                }
            }
            "jsonBuild" -> {
                val obj = kotlinx.serialization.json.JsonObject(
                    mapOf(p["key"].orEmpty() to kotlinx.serialization.json.JsonPrimitive(magic(p["value"].orEmpty(), ctx)))
                )
                ctx.locals[p["toVar"].orEmpty()] = obj.toString()
            }
            "systemInfo" -> ctx.locals[p["toVar"].orEmpty()] =
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            "currentLocale" -> ctx.locals[p["toVar"].orEmpty()] = java.util.Locale.getDefault().toString()
            "currentTimezone" -> ctx.locals[p["toVar"].orEmpty()] = java.util.TimeZone.getDefault().id
            "memFree" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                val am = app.getSystemService(android.app.ActivityManager::class.java)
                val info = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                (info.availMem / 1024 / 1024).toString()
            }.getOrDefault("0")
            // ---- 音量 / 屏幕 ----
            "volumeAdjust" -> runCatching {
                val am = app.getSystemService(AudioManager::class.java)
                when (p["action"] ?: "up") {
                    "down" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                    "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                    else -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                }
            }
            "screenRotationLock" -> {
                val cmd = when (p["mode"] ?: "auto") {
                    "portrait" -> "settings put system accelerometer_rotation 0; settings put system user_rotation 0"
                    "landscape" -> "settings put system accelerometer_rotation 0; settings put system user_rotation 1"
                    else -> "settings put system accelerometer_rotation 1"
                }
                shellPrivileged(macro.name, cmd)
            }
            // ---- 设置页跳转 ----
            "openDeveloperSettings" -> openSettingsPage(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            "openBatterySettings" -> openSettingsPage(Intent.ACTION_POWER_USAGE_SUMMARY)
            "openAccessibilitySettings" -> openSettingsPage(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "openNotificationSettings" -> openSettingsPage("android.settings.NOTIFICATION_SETTINGS")
            "openAppNotifications" -> runCatching {
                app.startActivity(
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, p["package"].orEmpty())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            // ---- 应用 / 通信 ----
            "appUninstall" -> runCatching {
                app.startActivity(
                    Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:" + p["package"].orEmpty())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            "dialNumber" -> runCatching {
                app.startActivity(
                    Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:" + p["number"].orEmpty())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            "smsSend" -> runCatching {
                val sms = android.telephony.SmsManager.getDefault()
                val number = p["number"].orEmpty()
                val text = magic(p["text"].orEmpty(), ctx)
                sms.sendTextMessage(number, null, text, null, null)
                app.logs.info(macro.name, "短信已发送: $number")
            }.onFailure { app.logs.error(macro.name, "短信发送失败: ${it.message}") }
            "takePhoto" -> runCatching {
                app.startActivity(
                    Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            // ---- 网络 / 文件 ----
            "httpDownload" -> withContext(Dispatchers.IO) {
                runCatching {
                    val url = URL(magic(p["url"].orEmpty(), ctx))
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                    }
                    val file = File(app.filesDir, p["file"].orEmpty().ifBlank { "download.bin" })
                    conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                    conn.disconnect()
                    app.logs.info(macro.name, "已下载到 ${file.name}（${file.length()} 字节）")
                }.onFailure { app.logs.error(macro.name, "下载失败: ${it.message}") }
            }
            "dirCreate" -> withContext(Dispatchers.IO) {
                runCatching { File(app.filesDir, p["dir"].orEmpty()).mkdirs() }
            }
            "dirDelete" -> withContext(Dispatchers.IO) {
                runCatching { File(app.filesDir, p["dir"].orEmpty()).deleteRecursively() }
            }
            "fileListRecursive" -> withContext(Dispatchers.IO) {
                runCatching {
                    val root = File(app.filesDir, p["dir"].orEmpty())
                    ctx.locals[p["toVar"].orEmpty()] = root.walkTopDown()
                        .filter { it.isFile }
                        .map { it.relativeTo(root).path }
                        .take(500)
                        .joinToString("|")
                }
            }
            // ---- 文本 / 数据 ----
            "regexReplace" -> {
                val src = magic(p["source"].orEmpty(), ctx)
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    Regex(p["pattern"].orEmpty()).replace(src, p["to"].orEmpty())
                }.getOrDefault(src)
            }
            "textPad" -> {
                val src = magic(p["source"].orEmpty(), ctx)
                val len = (p["length"]?.toIntOrNull() ?: 8).coerceIn(1, 512)
                val pad = p["pad"].orEmpty().ifBlank { "0" }
                ctx.locals[p["toVar"].orEmpty()] = src.padStart(len, pad.first())
            }
            "jsonGetPath" -> {
                val src = magic(p["json"].orEmpty(), ctx)
                val path = p["path"].orEmpty().split('.').filter { it.isNotBlank() }
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    var el: kotlinx.serialization.json.JsonElement = Json.parseToJsonElement(src)
                    for (key in path) {
                        el = el.jsonObject[key] ?: return@runCatching ""
                    }
                    el.jsonPrimitive.content
                }.getOrDefault("")
            }
            "dateAdd" -> {
                val name = p["name"].orEmpty()
                val base = ctx.locals[name]?.toLongOrNull() ?: System.currentTimeMillis()
                val days = p["days"]?.toLongOrNull() ?: 1L
                ctx.locals[name] = (base + days * 86_400_000L).toString()
            }
            "dateDiff" -> {
                val from = magic(p["from"].orEmpty(), ctx).toLongOrNull() ?: 0L
                val to = magic(p["to"].orEmpty(), ctx).toLongOrNull() ?: 0L
                ctx.locals[p["toVar"].orEmpty()] = ((to - from) / 86_400_000L).toString()
            }
            "clipboardClear" -> runCatching {
                val cm = app.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            "batteryInfo" -> {
                val intent = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temp = (intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
                val volt = intent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                val health = intent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 0) ?: 0
                ctx.locals[p["toVar"].orEmpty()] = "temp=${temp}℃, voltage=${volt}mV, health=$health"
            }
            // ---- 图片 / 音频 ----
            "imageInfo" -> withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(app.filesDir, p["file"].orEmpty())
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.path, opts)
                    ctx.locals[p["toVar"].orEmpty()] = "${opts.outWidth}x${opts.outHeight}, ${file.length()} bytes"
                }
            }
            "imageScale" -> withContext(Dispatchers.IO) {
                runCatching {
                    val src = android.graphics.BitmapFactory.decodeFile(File(app.filesDir, p["file"].orEmpty()).path)
                    val targetW = (p["width"]?.toIntOrNull() ?: 720).coerceIn(16, 4096)
                    val ratio = targetW.toFloat() / src.width
                    val scaled = android.graphics.Bitmap.createScaledBitmap(
                        src, targetW, (src.height * ratio).toInt().coerceAtLeast(1), true,
                    )
                    File(app.filesDir, p["to"].orEmpty().ifBlank { "scaled.jpg" }).outputStream().use {
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
                    }
                    scaled.recycle(); src.recycle()
                }.onFailure { app.logs.error(macro.name, "缩放失败: ${it.message}") }
            }
            "imageRotate" -> withContext(Dispatchers.IO) {
                runCatching {
                    val src = android.graphics.BitmapFactory.decodeFile(File(app.filesDir, p["file"].orEmpty()).path)
                    val matrix = android.graphics.Matrix().apply { postRotate((p["degrees"]?.toFloatOrNull() ?: 90f)) }
                    val rotated = android.graphics.Bitmap.createBitmap(
                        src, 0, 0, src.width, src.height, matrix, true,
                    )
                    File(app.filesDir, p["to"].orEmpty().ifBlank { "rotated.jpg" }).outputStream().use {
                        rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
                    }
                    rotated.recycle(); src.recycle()
                }.onFailure { app.logs.error(macro.name, "旋转失败: ${it.message}") }
            }
            "audioPlayFile" -> runCatching {
                audioStopInternal()
                val file = File(app.filesDir, p["file"].orEmpty())
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(file.path)
                    prepare()
                    start()
                }
            }.onFailure { app.logs.error(macro.name, "播放失败: ${it.message}") }
            "audioStop" -> audioStopInternal()
            // ---- 通知 / 应用 ----
            "cancelAllNotifications" -> runCatching {
                app.getSystemService(NotificationManager::class.java).cancelAll()
            }
            "getAppVersion" -> {
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    @Suppress("DEPRECATION")
                    val info = app.packageManager.getPackageInfo(p["package"].orEmpty(), 0)
                    info.versionName.orEmpty()
                }.getOrDefault("")
            }
            "isAppRunning" -> {
                ctx.locals[p["toVar"].orEmpty()] = runCatching {
                    val am = app.getSystemService(android.app.ActivityManager::class.java)
                    am.runningAppProcesses?.any { it.processName == p["package"].orEmpty() } == true
                }.getOrDefault(false).toString()
            }
            "getScreenSize" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                val dm = app.resources.displayMetrics
                "${dm.widthPixels}x${dm.heightPixels}"
            }.getOrDefault("")
            "getSimOperator" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                val tm = app.getSystemService(android.telephony.TelephonyManager::class.java)
                val name = tm.simOperatorName.ifBlank { tm.networkOperatorName }
                "$name (${tm.simOperator})"
            }.getOrDefault("")
            "openAppStore" -> runCatching {
                val pkg = p["package"].orEmpty()
                app.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                runCatching {
                    app.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + p["package"].orEmpty()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            "shareFile" -> runCatching {
                val file = File(app.filesDir, p["file"].orEmpty())
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    app, app.packageName + ".fileprovider", file,
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(Intent.createChooser(send, "分享文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { app.logs.error(macro.name, "分享失败: ${it.message}") }
            "wakeLock" -> runCatching {
                @Suppress("DEPRECATION")
                val lock = app.getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "autoflow:manual")
                lock.acquire(((p["seconds"]?.toLongOrNull() ?: 60L).coerceIn(1, 3600)) * 1000)
            }
            "getBatteryHealthText" -> {
                val intent = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val health = intent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1) ?: -1
                ctx.locals[p["toVar"].orEmpty()] = when (health) {
                    android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                    android.os.BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                    android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
                    else -> "unknown"
                }
            }
            "getStorageTotal" -> ctx.locals[p["toVar"].orEmpty()] = runCatching {
                val stat = android.os.StatFs(app.filesDir.path)
                (stat.totalBytes / 1024 / 1024).toString()
            }.getOrDefault("0")
            "shell" -> shell(macro.name, magic(p["cmd"].orEmpty(), ctx))
            "shellRoot" -> shellPrivileged(macro.name, magic(p["cmd"].orEmpty(), ctx))
            "airplaneMode" -> {
                val on = p["on"] != "false"
                shellPrivileged(
                    macro.name,
                    "settings put global airplane_mode_on ${if (on) 1 else 0}; " +
                        "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on",
                )
            }
            "mobileData" -> shellPrivileged(
                macro.name,
                "svc data ${if (p["on"] != "false") "enable" else "disable"}",
            )
            "rebootDevice" -> shellPrivileged(
                macro.name,
                when (p["mode"]) {
                    "recovery" -> "svc power reboot recovery"
                    "bootloader" -> "svc power reboot bootloader"
                    else -> "svc power reboot"
                },
            )
            "setBrightness" -> shellPrivileged(
                macro.name,
                "settings put system screen_brightness ${(p["value"]?.toIntOrNull() ?: 128).coerceIn(0, 255)}",
            )
        }
    }

    suspend fun checkConstraint(c: ConstraintSpec, ctx: RunContext?): Boolean = when (c.type) {
        "timeWindow" -> {
            val from = c.params["from"]?.toIntOrNull() ?: 0
            val to = c.params["to"]?.toIntOrNull() ?: 24
            val now = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (from <= to) now in from until to else now >= from || now < to
        }
        "dayOfWeek" -> {
            val dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            val weekend = dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY
            when (c.params["mode"] ?: "weekday") {
                "weekend" -> weekend
                "everyday" -> true
                else -> !weekend
            }
        }
        "batteryAbove" -> batteryLevel() >= (c.params["level"]?.toIntOrNull() ?: 20)
        "batteryBelow" -> batteryLevel() <= (c.params["level"]?.toIntOrNull() ?: 80)
        "charging" -> charging()
        "powerSave" -> app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        "screenOn" -> app.getSystemService(PowerManager::class.java)?.isInteractive == true
        "unlocked" -> app.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked != true
        "wifiConnected" -> wifiConnected()
        "wifiDisabled" -> !wifiConnected()
        "networkAvailable" -> networkAvailable()
        "headphones" -> headphones()
        "darkMode" -> (app.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        "appInstalled" -> runCatching {
            app.packageManager.getPackageInfo(c.params["package"].orEmpty(), 0)
            true
        }.getOrDefault(false)
        "varEquals" -> ctx?.vars?.get(c.params["name"].orEmpty()) == c.params["value"].orEmpty()
        "accessibilityOn" -> AutoAccessibilityService.isConnected() || AutoAccessibilityService.isEnabled(app)
        "screenOff" -> app.getSystemService(PowerManager::class.java)?.isInteractive != true
        "screenLocked" -> app.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        "ringerSilent" -> app.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_SILENT
        "ringerVibrate" -> app.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_VIBRATE
        "wifiSsidEquals" -> currentSsid()?.equals(c.params["ssid"].orEmpty(), true) == true
        "batteryTempAbove" -> batteryTempC() >= (c.params["celsius"]?.toDoubleOrNull() ?: 40.0)
        "batteryTempBelow" -> batteryTempC() <= (c.params["celsius"]?.toDoubleOrNull() ?: 30.0)
        "deviceIdleMode" -> app.getSystemService(PowerManager::class.java)?.isDeviceIdleMode == true
        "foregroundApp" -> currentForeground() == c.params["package"].orEmpty()
        "foregroundAppNot" -> currentForeground() != c.params["package"].orEmpty()
        "bluetoothOn" -> runCatching {
            val bm = app.getSystemService(android.bluetooth.BluetoothManager::class.java)
            bm?.adapter?.isEnabled == true
        }.getOrDefault(false)
        "wifiEnabled" -> runCatching {
            @Suppress("DEPRECATION")
            app.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled
        }.getOrDefault(false)
        "airplaneMode" -> android.provider.Settings.Global.getInt(
            app.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0,
        ) == 1
        "ringerNormal" -> app.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL
        "screenPortrait" -> app.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        "screenLandscape" -> app.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        "storageFreeAbove" -> runCatching {
            val stat = android.os.StatFs(app.filesDir.path)
            stat.availableBytes / 1024 / 1024 >= (c.params["mb"]?.toLongOrNull() ?: 512L)
        }.getOrDefault(false)
        "memoryFreeAbove" -> runCatching {
            val am = app.getSystemService(android.app.ActivityManager::class.java)
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.availMem / 1024 / 1024 >= (c.params["mb"]?.toLongOrNull() ?: 256L)
        }.getOrDefault(false)
        "appNotInstalled" -> runCatching {
            app.packageManager.getPackageInfo(c.params["package"].orEmpty(), 0)
            false
        }.getOrDefault(true)
        "notificationAccess" -> com.autoflow.app.core.CapabilityDetector.notificationListenerEnabled(app)
        "rootAvailable" -> ShellRunner.isRootAvailable()
        "shizukuAvailable" -> ShizukuShell.hasPermission()
        "batteryFull" -> batteryLevel() >= 100
        "chargingAc" -> chargingType() == android.os.BatteryManager.BATTERY_PLUGGED_AC
        "chargingUsb" -> chargingType() == android.os.BatteryManager.BATTERY_PLUGGED_USB
        "chargingWireless" -> chargingType() == android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS
        "ringerNotSilent" -> app.getSystemService(AudioManager::class.java)?.ringerMode != AudioManager.RINGER_MODE_SILENT
        "volumeAbove" -> mediaVolume() > (c.params["level"]?.toIntOrNull() ?: 8)
        "volumeBelow" -> mediaVolume() < (c.params["level"]?.toIntOrNull() ?: 4)
        "brightnessAbove" -> screenBrightness() > (c.params["level"]?.toIntOrNull() ?: 150)
        "brightnessBelow" -> screenBrightness() < (c.params["level"]?.toIntOrNull() ?: 100)
        "mobileNetwork" -> runCatching {
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        }.getOrDefault(false)
        "dayOfMonth" -> java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH) ==
            (c.params["day"]?.toIntOrNull() ?: 1)
        "monthEquals" -> java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 ==
            (c.params["month"]?.toIntOrNull() ?: 1)
        "carMode" -> app.getSystemService(android.app.UiModeManager::class.java)
            ?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_CAR
        "deskMode" -> app.getSystemService(android.app.UiModeManager::class.java)
            ?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_DESK
        "nfcEnabled" -> runCatching {
            android.nfc.NfcAdapter.getDefaultAdapter(app)?.isEnabled == true
        }.getOrDefault(false)
        "locationEnabled" -> runCatching {
            android.provider.Settings.Secure.getInt(
                app.contentResolver, android.provider.Settings.Secure.LOCATION_MODE, 0,
            ) != 0
        }.getOrDefault(false)
        "volumeMuted" -> mediaVolume() == 0
        "batteryPluggedAny" -> chargingType() != 0
        "mobileDataEnabled" -> runCatching {
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        }.getOrDefault(false)
        "powerSaveOff" -> app.getSystemService(PowerManager::class.java)?.isPowerSaveMode != true
        else -> true
    }

    // ---------------- 基础能力 ----------------

    private fun audioStopInternal() {
        runCatching { mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() } }
        mediaPlayer = null
    }

    private fun toast(text: String) = main { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }

    /** 统一处理无障碍动作：服务没开就记日志而不是静默失败。 */
    private fun a11y(macroName: String, label: String, block: (AutoAccessibilityService) -> Boolean) {
        val service = AutoAccessibilityService.service()
        if (service == null) {
            app.logs.error(macroName, "无障碍服务未开启，无法执行「$label」。请到 设置 → 无障碍 里打开 AutoFlow")
            return
        }
        val ok = runCatching { block(service) }.getOrDefault(false)
        app.logs.info(macroName, "UI $label -> ${if (ok) "完成" else "失败"}")
    }

    private fun notify(tag: String, title: String, text: String) {
        val nm = app.getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(app, AutoFlowApp.CHANNEL_ID)
        else Notification.Builder(app)
        val n = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.ifBlank { tag }).setContentText(text).setAutoCancel(true).build()
        runCatching { nm.notify(System.currentTimeMillis().toInt(), n) }
    }

    private fun vibrate(ms: Long) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        runCatching { v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun beep(ms: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, ms.coerceIn(50, 2000))
            android.os.Handler(app.mainLooper).postDelayed({ runCatching { tg.release() } }, (ms + 300).toLong())
        }
    }

    private fun torch(state: String) {
        runCatching {
            val cm = app.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull { cam ->
                cm.getCameraCharacteristics(cam).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            val target = when (state) {
                "on" -> true
                "off" -> false
                else -> !torchOn
            }
            cm.setTorchMode(id, target)
            torchOn = target
        }
    }

    private fun screenWake(seconds: Long) {
        runCatching {
            @Suppress("DEPRECATION")
            val lock = app.getSystemService(PowerManager::class.java).newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "autoflow:wake"
            )
            lock.acquire(seconds.coerceIn(1, 300) * 1000)
        }
    }

    private fun setVolume(level: Int) {
        val am = app.getSystemService(AudioManager::class.java)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0) }
    }

    private fun mediaControl(action: String) {
        runCatching {
            val am = app.getSystemService(AudioManager::class.java)
            val code = when (action) {
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun playSound() {
        runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.play()
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "autoflow") }
    }

    @Suppress("DEPRECATION")
    private fun setWifi(on: Boolean) {
        runCatching { app.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled = on }
    }

    private suspend fun http(tag: String, url: String, method: String, body: String, toVar: String, ctx: RunContext) =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8000
                    readTimeout = 8000
                    if (method == "POST") {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                }
                if (method == "POST") conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val text = runCatching {
                    (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
                }.getOrDefault("")
                conn.disconnect()
                if (toVar.isNotBlank()) ctx.vars[toVar] = text
                app.logs.info(tag, "HTTP $method $url -> $code${if (toVar.isNotBlank()) " (存到 $toVar)" else ""}")
            }.onFailure { app.logs.error(tag, "HTTP 失败: ${it.message}") }
        }

    private fun openUrl(url: String) {
        runCatching {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openSettingsPage(action: String) {
        runCatching {
            app.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openSettings() {
        runCatching {
            app.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun writeFile(name: String, content: String, append: Boolean) {
        runCatching {
            val f = File(app.filesDir, name)
            if (append) f.appendText(content) else f.writeText(content)
        }
    }

    private fun launchApp(tag: String, pkg: String) {
        runCatching {
            val intent = app.packageManager.getLaunchIntentForPackage(pkg)
                ?: throw IllegalStateException("未找到应用 $pkg")
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            app.logs.info(tag, "已启动 $pkg")
        }.onFailure { app.logs.error(tag, "启动失败: ${it.message}") }
    }

    /** 特权命令：优先 root，没有 root 就退化为普通 shell 并提示。 */
    private suspend fun shellPrivileged(macroName: String, cmd: String) {
        if (cmd.isBlank()) return
        val root = ShellRunner.isRootAvailable()
        val result = ShellRunner.run(cmd)
        val tier = if (result.tier == ShellRunner.Tier.ROOT) "root" else "shell"
        when {
            result.exitCode == 0 -> app.logs.info(
                macroName,
                "[$tier] ${cmd.take(80)} → ${result.output.take(120).ifBlank { "ok" }}",
            )
            else -> {
                val hint = if (!root) "（该操作可能需要在 Root 环境下才能生效）" else ""
                app.logs.warn(
                    macroName,
                    "[$tier] ${cmd.take(80)} 失败(${result.exitCode})：${result.output.take(120)}$hint",
                )
            }
        }
    }

    private suspend fun shell(tag: String, cmd: String) = withContext(Dispatchers.IO) {
        runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = proc.inputStream.bufferedReader().readText().trim()
            val err = proc.errorStream.bufferedReader().readText().trim()
            proc.waitFor()
            app.logs.info(tag, "shell: ${out.ifBlank { err }.ifBlank { "ok" }.take(300)}")
        }.onFailure { app.logs.error(tag, "shell 失败: ${it.message}") }
    }

    private fun chargingType(): Int {
        val intent = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    }

    private fun mediaVolume(): Int = runCatching {
        app.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC)
    }.getOrDefault(0)

    private fun screenBrightness(): Int = runCatching {
        android.provider.Settings.System.getInt(
            app.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 0,
        )
    }.getOrDefault(0)

    private fun batteryTempC(): Double {
        val intent = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return if (tenths == Int.MIN_VALUE) 0.0 else tenths / 10.0
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? = runCatching {
        val wm = app.applicationContext.getSystemService(WifiManager::class.java)
        wm.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()

    private fun currentForeground(): String? = runCatching {
        val usm = app.getSystemService(android.app.usage.UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000, now) ?: return null
        val event = android.app.usage.UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val resumed = if (Build.VERSION.SDK_INT >= 29) {
                event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (resumed) latest = event.packageName
        }
        latest
    }.getOrNull()

    private fun batteryLevel(): Int {
        val bm = app.getSystemService(android.os.BatteryManager::class.java)
        val i = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (i in 0..100) return i
        val intent = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) level * 100 / scale else 100
    }

    private fun charging(): Boolean = app.getSystemService(android.os.BatteryManager::class.java)?.isCharging == true

    private fun wifiConnected(): Boolean = runCatching {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    private fun networkAvailable(): Boolean = runCatching {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    private fun headphones(): Boolean = runCatching {
        val am = app.getSystemService(AudioManager::class.java)
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    }.getOrDefault(false)

    private fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun main(block: (Context) -> Unit) {
        android.os.Handler(app.mainLooper).post { block(app) }
    }

    companion object {
        private val MAGIC_LOCAL = Regex("\\{lv=([^}]+)}")
        private val MAGIC_GLOBAL = Regex("\\{v=([^}]+)}")

        /**
         * {lv=name} -> 局部变量；{v=name} -> 全局优先，找不到再回落到局部（兼容旧宏）。
         */
        fun magic(text: String, ctx: RunContext): String {
            var s = MAGIC_LOCAL.replace(text) { ctx.locals[it.groupValues[1]] ?: "" }
            s = MAGIC_GLOBAL.replace(s) { m ->
                val n = m.groupValues[1]
                ctx.globals[n] ?: ctx.locals[n] ?: ""
            }
            return s
        }
    }
}

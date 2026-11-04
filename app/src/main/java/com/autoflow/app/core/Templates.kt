package com.autoflow.app.core

/**
 * 内置模板：首次启动就能看到可用内容，一键安装成宏。
 * 全部使用注册表里真实存在的触发器/动作类型，保证可执行。
 */
data class TemplateInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val emoji: String,
    val build: () -> Macro,
)

object Templates {

    private fun macro(
        name: String,
        description: String,
        category: String,
        triggers: List<TriggerSpec>,
        actions: List<ActionSpec>,
        constraints: List<ConstraintSpec> = emptyList(),
    ): Macro = Macro(
        name = name,
        description = description,
        category = category,
        triggers = triggers,
        actions = actions,
        constraints = constraints,
        enabled = true,
    )

    val all: List<TemplateInfo> = listOf(

        TemplateInfo("torch_blink", "手电筒闪烁", "手动触发，闪光灯闪 5 次", "灯光", "✳") {
            macro(
                name = "手电筒闪烁",
                description = "手动运行：闪 5 次",
                category = "灯光",
                triggers = listOf(TriggerSpec("manual", emptyMap())),
                actions = listOf(
                    ActionSpec("loop", mapOf("times" to "5")),
                    ActionSpec("torch", mapOf("state" to "on")),
                    ActionSpec("delay", mapOf("seconds" to "0.4")),
                    ActionSpec("torch", mapOf("state" to "off")),
                    ActionSpec("delay", mapOf("seconds" to "0.4")),
                    ActionSpec("endloop"),
                ),
            )
        },

        TemplateInfo("silent_at_night", "睡前静音", "每天 23:00 切静音并提示", "睡眠", "☾") {
            macro(
                name = "睡前静音",
                description = "每天 23:00 静音",
                category = "睡眠",
                triggers = listOf(TriggerSpec("timeOfDay", mapOf("hour" to "23", "minute" to "0"))),
                actions = listOf(
                    ActionSpec("setRingerMode", mapOf("mode" to "silent")),
                    ActionSpec("notification", mapOf("title" to "晚安", "text" to "已切换到静音模式")),
                ),
            )
        },

        TemplateInfo("office_wifi_silent", "到公司静音", "连上公司 WiFi 自动静音", "通勤", "◷") {
            macro(
                name = "到公司静音",
                description = "连上指定 WiFi 后静音",
                category = "通勤",
                triggers = listOf(TriggerSpec("wifiSsid", mapOf("ssid" to "公司WiFi"))),
                actions = listOf(
                    ActionSpec("setRingerMode", mapOf("mode" to "vibrate")),
                    ActionSpec("toast", mapOf("text" to "已到公司，已切振动")),
                ),
            )
        },

        TemplateInfo("low_battery_alert", "低电提醒", "电量低时提醒充电", "省电", "⚡") {
            macro(
                name = "低电提醒",
                description = "低电量广播触发",
                category = "省电",
                triggers = listOf(TriggerSpec("lowBattery", emptyMap())),
                actions = listOf(
                    ActionSpec("vibrate", mapOf("ms" to "500")),
                    ActionSpec("notification", mapOf("title" to "电量不足", "text" to "记得充电")),
                ),
            )
        },

        TemplateInfo("charging_greet", "充电问候", "插上电源时语音播报电量", "电源", "🔌") {
            macro(
                name = "充电问候",
                description = "接通电源触发",
                category = "电源",
                triggers = listOf(TriggerSpec("powerConnected", emptyMap())),
                actions = listOf(
                    ActionSpec("batteryToVar", mapOf("name" to "battery")),
                    ActionSpec("speak", mapOf("text" to "开始充电，当前电量 {lv=battery} 百分比")),
                ),
            )
        },

        TemplateInfo("hourly_chime", "整点报时", "每到整点报一次时间", "时间", "◷") {
            macro(
                name = "整点报时",
                description = "整点触发",
                category = "时间",
                triggers = listOf(TriggerSpec("hourChanged", emptyMap())),
                actions = listOf(
                    ActionSpec("timestamp", mapOf("name" to "now", "pattern" to "HH:mm")),
                    ActionSpec("toast", mapOf("text" to "现在是 {lv=now}")),
                ),
            )
        },

        TemplateInfo("wifi_lost_alert", "WiFi 断开提醒", "断开 WiFi 时振动提示", "网络", "⌁") {
            macro(
                name = "WiFi 断开提醒",
                description = "WiFi 断开触发",
                category = "网络",
                triggers = listOf(TriggerSpec("wifiLost", emptyMap())),
                actions = listOf(
                    ActionSpec("vibrate", mapOf("ms" to "200")),
                    ActionSpec("toast", mapOf("text" to "WiFi 已断开")),
                ),
            )
        },

        TemplateInfo("storage_alert", "存储不足提醒", "存储空间不足时通知", "系统", "▤") {
            macro(
                name = "存储不足提醒",
                description = "存储不足触发",
                category = "系统",
                triggers = listOf(TriggerSpec("storageLow", emptyMap())),
                actions = listOf(
                    ActionSpec("notification", mapOf("title" to "存储空间不足", "text" to "建议清理缓存")),
                ),
            )
        },

        TemplateInfo("headset_pause", "耳机插入暂停", "插入耳机时暂停音乐", "设备", "♪") {
            macro(
                name = "耳机插入暂停",
                description = "耳机插拔触发",
                category = "设备",
                triggers = listOf(TriggerSpec("headset", emptyMap())),
                actions = listOf(ActionSpec("mediaControl", mapOf("action" to "playPause"))),
            )
        },

        TemplateInfo("shake_torch", "摇一摇开灯", "摇晃手机切换手电筒", "传感器", "≈") {
            macro(
                name = "摇一摇开灯",
                description = "摇晃设备触发",
                category = "传感器",
                triggers = listOf(TriggerSpec("shake", emptyMap())),
                actions = listOf(
                    ActionSpec("torch", mapOf("state" to "toggle")),
                    ActionSpec("toast", mapOf("text" to "手电筒已切换")),
                ),
            )
        },

        TemplateInfo("screen_on_brightness", "亮屏解锁提醒", "解锁时提示今日待办", "效率", "☀") {
            macro(
                name = "解锁提醒",
                description = "亮屏并解锁触发",
                category = "效率",
                triggers = listOf(TriggerSpec("screenOnUnlocked", emptyMap())),
                actions = listOf(
                    ActionSpec("timestamp", mapOf("name" to "now", "pattern" to "HH:mm")),
                    ActionSpec("toast", mapOf("text" to "早上好，现在是 {lv=now}")),
                ),
            )
        },

        TemplateInfo("daily_log", "每日记录时间", "每天 23:59 把时间写入文件", "效率", "▤") {
            macro(
                name = "每日记录时间",
                description = "每天 23:59 触发",
                category = "效率",
                triggers = listOf(TriggerSpec("timeOfDay", mapOf("hour" to "23", "minute" to "59"))),
                actions = listOf(
                    ActionSpec("timestamp", mapOf("name" to "ts", "pattern" to "yyyy-MM-dd HH:mm:ss")),
                    ActionSpec("appendFile", mapOf("file" to "daily.log", "text" to "{lv=ts}")),
                ),
            )
        },
    )

    fun byCategory(): Map<String, List<TemplateInfo>> = all.groupBy { it.category }

    fun install(info: TemplateInfo): Macro = info.build()
}

package com.autoflow.app.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * UI 交互无障碍服务：点击、滑动、输入、读屏、全局操作。
 * 需要用户在「设置 → 无障碍」里手动开启。
 */
class AutoAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ---------------- 基础能力 ----------------

    fun tap(x: Float, y: Float, durationMs: Long = 60L): Boolean = runCatching {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(30L, 5000L)))
                .build(),
            null, handler,
        )
    }.getOrDefault(false)

    /**
     * 异步点击：手势真正完成（或被取消）时回调。
     *
     * 连点器靠它把每次点击串起来，避免系统拒绝并发手势导致漏点，
     * 也让「点击周期」算得准：等待时间 = 间隔 - 本次手势耗时。
     */
    fun tapAsync(x: Float, y: Float, durationMs: Long = 60L, onResult: (Boolean) -> Unit): Boolean =
        runCatching {
            val path = Path().apply { moveTo(x, y) }
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(30L, 5000L)),
                    )
                    .build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) = onResult(true)

                    override fun onCancelled(gestureDescription: GestureDescription?) = onResult(false)
                },
                handler,
            )
        }.getOrDefault(false)

    /** 滚动一屏。 */
    fun scroll(up: Boolean): Boolean {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val fromY = dm.heightPixels * 0.72f
        val toY = dm.heightPixels * 0.28f
        return if (up) swipe(cx, fromY, cx, toY, 300L) else swipe(cx, toY, cx, fromY, 300L)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean = runCatching {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50L, 5000L))
                )
                .build(),
            null, handler,
        )
    }.getOrDefault(false)

    fun tapText(text: String): Boolean {
        if (text.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val node = findNode(root, text) ?: return false
        return clickNode(node)
    }

    fun inputText(text: String): Boolean = runCatching {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }.getOrDefault(false)

    fun readScreen(): String = runCatching {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb, 0)
        sb.toString().trim()
    }.getOrDefault("")

    fun global(action: Int): Boolean = runCatching { performGlobalAction(action) }.getOrDefault(false)

    // ---------------- 节点查找 ----------------

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return tap(rect.exactCenterX(), rect.exactCenterY())
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && guard < 3000) {
            guard++
            val node = queue.removeFirst()
            val label = node.text?.toString() ?: node.contentDescription?.toString()
            if (!label.isNullOrBlank() && label.contains(text, ignoreCase = true)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 40 || sb.length > 4000) return
        val label = node.text?.toString() ?: node.contentDescription?.toString()
        if (!label.isNullOrBlank()) sb.append(label).append(NEWLINE)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb, depth + 1) }
        }
    }

    companion object {
        @Volatile
        private var instance: AutoAccessibilityService? = null

        private val NEWLINE: Char = 10.toChar()

        fun service(): AutoAccessibilityService? = instance

        fun isConnected(): Boolean = instance != null

        /** 从系统设置里读取本服务是否已被勾选。 */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expected = context.packageName + "/" + AutoAccessibilityService::class.java.name
            return flat.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}

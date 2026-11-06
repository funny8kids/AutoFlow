package com.autoflow.app.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 连点器悬浮球：前台服务 + 系统悬浮窗。
 *
 * - 拖动：移动悬浮球位置（位置会记住）
 * - 单击：开始 / 停止连点
 * - 长按：打开控制面板
 * - 触摸穿透：窗口使用 FLAG_NOT_TOUCH_MODAL，悬浮球以外的区域直接透传给下层应用；
 *   开启「完全穿透」后连悬浮球自身也不再拦截触摸。
 *
 * 点击本身由无障碍服务（AutoAccessibilityService）注入手势实现，
 * 所以使用前必须开启 AutoFlow 的无障碍服务。
 */
class AutoFlowOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var windowManager: WindowManager
    private var ballView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var dragging = false
    private var longPressed = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var passThrough = false

    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (longPressed) {
                    longPressed = false
                    return true
                }
                toggleClicker()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                longPressed = true
                togglePanel()
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        passThrough = ClickerPrefs.passThrough(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!safeStartForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> {
                showBall()
                AutoClicker.update(ClickerPrefs.load(this))
                if (!AutoClicker.isRunning()) AutoClicker.start(scope)
                return START_STICKY
            }
            ACTION_TOGGLE -> {
                showBall()
                toggleClicker()
                return START_STICKY
            }
            ACTION_HIDE -> {
                AutoClicker.stop()
                removeViews()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                showBall()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        AutoClicker.stop()
        removeViews()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- 悬浮球 ----------------

    private fun showBall() {
        if (ballView != null) return
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.w("AutoFlow", "悬浮窗权限未授予，无法显示悬浮球")
            stopSelf()
            return
        }
        val size = dp(BALL_SIZE_DP)
        val view = TextView(this).apply {
            text = ballText()
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC3B82F6.toInt())
                setStroke(dp(2), 0xFF33E1C4.toInt())
            }
        }
        val saved = ClickerPrefs.ballPosition(this)
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            windowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = saved?.first ?: dp(24)
            y = saved?.second ?: dp(320)
        }
        view.setOnTouchListener { _, event -> onBallTouch(event) }
        runCatching {
            windowManager.addView(view, params)
            ballView = view
            ballParams = params
        }.onFailure {
            android.util.Log.w("AutoFlow", "悬浮球添加失败: ${it.message}")
            stopSelf()
        }
    }

    private fun onBallTouch(event: MotionEvent): Boolean {
        val view = ballView ?: return false
        val params = ballParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                gestureDetector.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (kotlin.math.abs(dx) > TOUCH_SLOP || kotlin.math.abs(dy) > TOUCH_SLOP)) {
                    dragging = true
                }
                if (dragging) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    ClickerPrefs.saveBallPosition(this, params.x, params.y)
                } else {
                    gestureDetector.onTouchEvent(event)
                }
                dragging = false
                return true
            }
        }
        return false
    }

    // ---------------- 控制面板 ----------------

    private fun togglePanel() {
        if (panelView != null) {
            removePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (!Settings.canDrawOverlays(this)) return
        if (panelView != null) return
        val pad = dp(12)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(0xF21B2140.toInt())
                setStroke(dp(1), 0xFF3C4A7A.toInt())
            }
        }
        val status = TextView(this).apply {
            text = statusText()
            textSize = 13f
            setTextColor(0xFFE7EAF6.toInt())
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(status)
        root.addView(panelButton("开始 / 停止") { toggleClicker() })
        root.addView(panelButton(if (passThrough) "完全穿透：已开" else "完全穿透：已关") { togglePassThrough() })
        root.addView(panelButton("关闭面板") { removePanel() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(200)
        }
        runCatching {
            windowManager.addView(root, params)
            panelView = root
            panelParams = params
        }
    }

    private fun panelButton(text: String, onClick: () -> Unit): View = Button(this).apply {
        this.text = text
        textSize = 13f
        setOnClickListener { onClick() }
    }

    private fun removePanel() {
        val view = panelView ?: return
        panelView = null
        panelParams = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun togglePassThrough() {
        passThrough = !passThrough
        ClickerPrefs.setPassThrough(this, passThrough)
        val view = ballView
        val params = ballParams
        if (view != null && params != null) {
            params.flags = windowFlags()
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        removePanel()
    }

    // ---------------- 连点控制 ----------------

    private fun toggleClicker() {
        if (AutoClicker.isRunning()) {
            AutoClicker.stop()
        } else {
            AutoClicker.update(ClickerPrefs.load(this))
            if (!AutoClicker.isRunning()) AutoClicker.start(scope)
        }
        updateBallText()
    }

    private fun ballText(): String = if (AutoClicker.isRunning()) "停" else "点"

    private fun updateBallText() {
        (ballView as? TextView)?.text = ballText()
    }

    private fun statusText(): String {
        val cfg = ClickerPrefs.load(this)
        val phase = when (AutoClicker.phase.value) {
            ClickerPhase.RUNNING -> "运行中"
            ClickerPhase.STOPPED -> "已停止"
            ClickerPhase.IDLE -> "空闲"
        }
        return "连点器 $phase · 已点 ${AutoClicker.taps.value} 次\n" +
            "间隔 ${cfg.intervalMs} ms · 坐标 ${cfg.x.toInt()}, ${cfg.y.toInt()}"
    }

    // ---------------- 窗口与通知 ----------------

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** FLAG_NOT_TOUCH_MODAL 让悬浮球以外的触摸透传给下层应用。 */
    private fun windowFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (passThrough) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    private fun removeViews() {
        removePanel()
        val view = ballView
        val params = ballParams
        ballView = null
        ballParams = null
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
        if (params != null) {
            ClickerPrefs.saveBallPosition(this, params.x, params.y)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_OVERLAY, "AutoFlow 连点器", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("AutoFlow 连点器运行中")
            .setContentText("悬浮球可拖动，单击开始或停止，长按打开控制面板")
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    /** 前台服务启动在 Android 12+ 可能被系统拒绝，失败时安静退出而不是崩溃。 */
    private fun safeStartForeground(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        true
    } catch (t: Throwable) {
        runCatching {
            android.util.Log.w("AutoFlow", "startForeground failed: ${t.message}")
        }
        false
    }

    companion object {
        private const val CHANNEL_OVERLAY = "autoflow_overlay"
        private const val NOTIF_ID = 1002
        private const val BALL_SIZE_DP = 56
        private const val TOUCH_SLOP = 12f

        const val ACTION_START = "com.autoflow.app.action.START_CLICKER"
        const val ACTION_TOGGLE = "com.autoflow.app.action.TOGGLE_CLICKER"
        const val ACTION_HIDE = "com.autoflow.app.action.HIDE_CLICKER"

        /** 是否已获得悬浮窗权限。 */
        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** 显示悬浮球（不自动开始连点）。 */
        fun show(context: Context) {
            val intent = Intent(context, AutoFlowOverlayService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        /** 显示悬浮球并开始连点。 */
        fun startClicker(context: Context) {
            val intent = Intent(context, AutoFlowOverlayService::class.java).apply { action = ACTION_START }
            runCatching { context.startForegroundService(intent) }
        }

        /** 隐藏悬浮球并停止连点。 */
        fun hide(context: Context) {
            val intent = Intent(context, AutoFlowOverlayService::class.java).apply { action = ACTION_HIDE }
            runCatching { context.startForegroundService(intent) }
        }
    }
}

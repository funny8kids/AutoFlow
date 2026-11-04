package com.autoflow.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.autoflow.app.ui.AppRoot
import com.autoflow.app.ui.AutoFlowTheme
import com.autoflow.app.ui.CrashScreen

class MainActivity : ComponentActivity() {

    /** 上次启动崩溃过 → 本次进入安全模式（不启动前台服务）。 */
    private var safeMode = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前安装，接管系统启动页
        val splash = installSplashScreen()
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        splash.setOnExitAnimationListener { provider ->
            val view = provider.view
            val anim = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.06f),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.06f),
                )
                duration = 320
                interpolator = DecelerateInterpolator()
            }
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    provider.remove()
                }
            })
            anim.start()
        }

        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(AutoFlowApp.PREFS, Context.MODE_PRIVATE)
        safeMode = prefs.getBoolean(AutoFlowApp.KEY_CRASHED, false)
        // 标记“本次启动进行中”，正常渲染后清除；若崩溃则保留，下次进入安全模式
        prefs.edit().putBoolean(AutoFlowApp.KEY_CRASHED, false).apply()

        enableEdgeToEdge()
        requestRuntimePermissions()

        // 数据就绪或超时后放行，避免启动页一闪而过
        lifecycleScope.launch {
            delay(420)
            keepSplash = false
        }

        val app = runCatching { AutoFlowApp.app() }.getOrNull()
        val fatal = app?.startupError

        setContent {
            AutoFlowTheme {
                if (fatal != null) {
                    CrashScreen(
                        title = "启动失败",
                        detail = fatal,
                        onRetry = { recreate() },
                        onContinue = null,
                    )
                } else {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val startupFailed = runCatching { AutoFlowApp.app().startupError != null }.getOrDefault(true)
        if (!safeMode && !startupFailed) {
            com.autoflow.app.platform.AutoFlowService.start(this)
        }
    }

    private fun requestRuntimePermissions() {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        wanted += Manifest.permission.RECEIVE_SMS
        wanted += Manifest.permission.READ_SMS
        wanted += Manifest.permission.READ_CALENDAR
        wanted += Manifest.permission.READ_PHONE_STATE
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            runCatching { permissionLauncher.launch(missing.toTypedArray()) }
        }
    }
}
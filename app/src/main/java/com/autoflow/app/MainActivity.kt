package com.autoflow.app

import android.Manifest
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

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // 应用已在前台，此时启动前台服务不会被系统拒绝
        com.autoflow.app.platform.AutoFlowService.start(this)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()
        setContent {
            AutoFlowTheme {
                AppRoot()
            }
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
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}

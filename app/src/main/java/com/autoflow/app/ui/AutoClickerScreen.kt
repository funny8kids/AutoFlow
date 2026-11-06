package com.autoflow.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.platform.AutoAccessibilityService
import com.autoflow.app.platform.AutoClicker
import com.autoflow.app.platform.AutoFlowOverlayService
import com.autoflow.app.platform.ClickerConfig
import com.autoflow.app.platform.ClickerPhase
import com.autoflow.app.platform.ClickerPrefs

/**
 * 连点器设置页：权限检查 + 参数 + 悬浮球控制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics

    var xText by remember { mutableStateOf("540") }
    var yText by remember { mutableStateOf("1200") }
    var intervalText by remember { mutableStateOf("200") }
    var durationText by remember { mutableStateOf("0") }
    var countText by remember { mutableStateOf("0") }
    var tapText by remember { mutableStateOf("60") }

    var overlayGranted by remember { mutableStateOf(false) }
    var a11yGranted by remember { mutableStateOf(false) }

    val phase by AutoClicker.phase.collectAsState()
    val taps by AutoClicker.taps.collectAsState()
    val error by AutoClicker.error.collectAsState()

    fun refreshPermissions() {
        overlayGranted = AutoFlowOverlayService.canDraw(context)
        a11yGranted = AutoAccessibilityService.isEnabled(context)
    }

    fun commit() {
        val current = ClickerPrefs.load(context)
        val config = ClickerConfig(
            x = xText.toFloatOrNull() ?: current.x,
            y = yText.toFloatOrNull() ?: current.y,
            intervalMs = intervalText.toLongOrNull() ?: current.intervalMs,
            durationMs = durationText.toLongOrNull() ?: current.durationMs,
            maxCount = countText.toIntOrNull() ?: current.maxCount,
            tapDurationMs = tapText.toLongOrNull() ?: current.tapDurationMs,
        )
        ClickerPrefs.save(context, config)
        AutoClicker.update(config)
    }

    LaunchedEffect(Unit) {
        val saved = ClickerPrefs.load(context)
        xText = saved.x.toInt().toString()
        yText = saved.y.toInt().toString()
        intervalText = saved.intervalMs.toString()
        durationText = saved.durationMs.toString()
        countText = saved.maxCount.toString()
        tapText = saved.tapDurationMs.toString()
        refreshPermissions()
    }

    val phaseText = when (phase) {
        ClickerPhase.RUNNING -> "运行中"
        ClickerPhase.STOPPED -> "已停止"
        ClickerPhase.IDLE -> "空闲"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("连点器", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("无障碍服务", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (a11yGranted) "已开启，可以执行点击手势" else "未开启，无法点击屏幕",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                refreshPermissions()
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            },
                        ) { Text(if (a11yGranted) "查看" else "去开启") }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("悬浮窗权限", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (overlayGranted) "已授予，可以显示悬浮球" else "未授予，悬浮球无法显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                refreshPermissions()
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                            },
                        ) { Text(if (overlayGranted) "查看" else "去授予") }
                    }
                }
            }

            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("点击参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "屏幕分辨率 ${metrics.widthPixels} x ${metrics.heightPixels}，坐标按屏幕像素填写",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = xText,
                            onValueChange = { xText = it; commit() },
                            label = { Text("X 坐标") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = yText,
                            onValueChange = { yText = it; commit() },
                            label = { Text("Y 坐标") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it; commit() },
                            label = { Text("间隔 ms") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = tapText,
                            onValueChange = { tapText = it; commit() },
                            label = { Text("按压 ms") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = countText,
                            onValueChange = { countText = it; commit() },
                            label = { Text("次数 0=不限") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it; commit() },
                            label = { Text("时长 ms 0=不限") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("运行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "状态 $phaseText · 已点 $taps 次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (error != null) {
                        Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = {
                            commit()
                            if (AutoClicker.isRunning()) {
                                AutoClicker.stop()
                            } else {
                                AutoFlowOverlayService.startClicker(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (AutoClicker.isRunning()) "停止连点" else "开始连点") }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { AutoFlowOverlayService.show(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text("显示悬浮球") }
                        OutlinedButton(
                            onClick = { AutoFlowOverlayService.hide(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text("隐藏悬浮球") }
                    }
                    Text(
                        "悬浮球：拖动移动，单击开始或停止，长按打开控制面板；悬浮球以外的触摸直接穿透给下层应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

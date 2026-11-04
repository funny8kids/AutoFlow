package com.autoflow.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import com.autoflow.app.core.CapabilityDetector
import com.autoflow.app.core.CapabilityState
import com.autoflow.app.platform.AutoAccessibilityService
import com.autoflow.app.platform.ShizukuShell
import com.autoflow.app.platform.ShellRunner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.autoflow.app.core.ai.AiSettings

@Composable
private fun CapabilityRow(title: String, subtitle: String, available: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatChip(if (available) "可用" else "不可用", if (available) Brand.Secondary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialKey: String,
    initialBaseUrl: String,
    initialModel: String,
    models: List<String>,
    modelsLoading: Boolean,
    onFetchModels: (String, String) -> Unit,
    onSave: (String, String, String) -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
) {
    var key by remember { mutableStateOf(initialKey) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var model by remember { mutableStateOf(initialModel) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AI 设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(key, baseUrl, model); saved = true }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" 保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val context = LocalContext.current
            val a11yOn = remember {
                AutoAccessibilityService.isConnected() || AutoAccessibilityService.isEnabled(context)
            }
            var caps by remember { mutableStateOf(CapabilityState()) }
            var shizukuGranted by remember { mutableStateOf(ShizukuShell.hasPermission()) }
            LaunchedEffect(Unit) {
                caps = CapabilityState(
                    accessibility = a11yOn,
                    notificationListener = CapabilityDetector.notificationListenerEnabled(context),
                    shizukuInstalled = CapabilityDetector.shizukuInstalled(context),
                    root = ShellRunner.isRootAvailable(),
                )
                shizukuGranted = ShizukuShell.hasPermission()
            }
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "无障碍服务",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        StatChip(if (a11yOn) "已开启" else "未开启", if (a11yOn) Brand.Secondary else Brand.Danger)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "「点击坐标 / 点击文字 / 滑动 / 输入文字 / 读取屏幕」这些 UI 交互动作需要它。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(if (a11yOn) "管理无障碍" else "去开启") }
                }
            }

            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("接 DeepSeek 官方 API", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Base URL 已预填。模型名不写死：填好 Key 后点「拉取可用模型」，从接口返回的列表里选一个即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "能力分级",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        StatChip(caps.bestLabel(), Brand.Primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    CapabilityRow("公开 API", "始终可用", true)
                    CapabilityRow("通知使用权", "收到通知触发器", caps.notificationListener)
                    CapabilityRow("无障碍", "UI 交互动作", caps.accessibility)
                    CapabilityRow(
                        "Shizuku",
                        if (caps.shizukuInstalled) "已安装，可申请 ADB 权限" else "未安装 Shizuku App",
                        shizukuGranted,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("使用情况访问（应用切换触发器）") }
                    }
                    if (caps.shizukuInstalled && !shizukuGranted) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = {
                                    ShizukuShell.requestPermission()
                                },
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("申请 Shizuku 权限") }
                        }
                    }
                    CapabilityRow("Root", "特权命令、飞行模式、重启", caps.root)
                }
            }

            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "把所有宏和全局变量导出成文件，支持 AES-GCM 加密。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenBackup, shape = RoundedCornerShape(14.dp)) {
                        Text("打开备份页")
                    }
                }
            }

            OutlinedTextField(
                value = key,
                onValueChange = { key = it; saved = false },
                label = { Text("DeepSeek API Key") },
                placeholder = { Text("sk-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                label = { Text("API Base URL（预填）") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onFetchModels(key, baseUrl) },
                    enabled = !modelsLoading && key.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (modelsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("拉取可用模型")
                }
                Spacer(Modifier.size(10.dp))
                if (models.isNotEmpty()) {
                    StatChip("${models.size} 个", Brand.Secondary)
                }
            }

            if (models.isNotEmpty()) {
                Text("选择一个模型", style = MaterialTheme.typography.labelSmall)
                models.forEach { m ->
                    FilterChip(
                        selected = model == m,
                        onClick = { model = m; saved = false },
                        label = { Text(m) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    label = { Text("模型名（也可手动填写）") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                "默认地址：${AiSettings.DEFAULT_BASE_URL}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (saved) {
                StatChip("已保存", Brand.Secondary)
            }
        }
    }
}

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.core.Macro
import com.autoflow.app.core.Registry

private val presets = listOf(
    "每天 8 点提醒我喝水，并弹一个通知",
    "电量低于 20% 时自动开启省电模式并提示我",
    "连上家里的 WiFi 就把媒体音量调到 5",
    "开机后等 30 秒，发一条通知告诉我设备已就绪",
    "摇晃手机时振动一下并打印当前时间",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    state: AiState,
    configured: Boolean,
    onGenerate: (String) -> Unit,
    onGenerateTemplates: (String) -> Unit,
    onSaveAll: (List<Macro>) -> Unit,
    onOpenSettings: () -> Unit,
    onSave: (Macro) -> Unit,
    onEdit: (Macro) -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var templateMode by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AI 生成宏", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "AI 设置")
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
            if (!configured) {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Brand.Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("还没有配置 DeepSeek API Key", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "地址和模型已经预填好，只需填入你的 API Key 即可使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onOpenSettings, shape = RoundedCornerShape(14.dp)) {
                            Text("去配置")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = !templateMode,
                    onClick = { templateMode = false; onReset() },
                    label = { Text("单个宏") },
                )
                androidx.compose.material3.FilterChip(
                    selected = templateMode,
                    onClick = { templateMode = true; onReset() },
                    label = { Text("模板包（3-5 个）") },
                )
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("用一句话描述你的自动化") },
                placeholder = { Text("例如：每天晚上 11 点把屏幕亮度调到最低并静音") },
                minLines = 3,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { p ->
                    AssistChip(
                        onClick = { prompt = p },
                        label = { Text(p.take(12) + "…", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Button(
                onClick = { if (templateMode) onGenerateTemplates(prompt) else onGenerate(prompt) },
                enabled = state !is AiState.Loading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (templateMode) "用 DeepSeek 生成模板包" else "用 DeepSeek 生成",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            when (state) {
                is AiState.Loading -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("DeepSeek 正在思考…", style = MaterialTheme.typography.bodySmall)
                }
                is AiState.Error -> Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Brand.Danger.copy(alpha = 0.12f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("生成失败", style = MaterialTheme.typography.titleMedium, color = Brand.Danger)
                        Spacer(Modifier.height(4.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onReset) { Text("知道了") }
                    }
                }
                is AiState.Success -> {
                    val m = state.macro
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Brand.Secondary)
                                Spacer(Modifier.width(8.dp))
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (m.description.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("${m.triggers.size} 触发器", Brand.Primary)
                                StatChip("${m.actions.size} 动作", Brand.Secondary)
                                if (m.constraints.isNotEmpty()) StatChip("${m.constraints.size} 条件", Brand.Tertiary)
                            }
                            Spacer(Modifier.height(12.dp))
                            m.triggers.forEach { t ->
                                Text("▸ ${Registry.trigger(t.type)?.label ?: t.type}", style = MaterialTheme.typography.bodySmall)
                            }
                            m.actions.take(8).forEachIndexed { i, a ->
                                Text(
                                    "${i + 1}. ${Registry.action(a.type)?.label ?: a.type}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (m.actions.size > 8) {
                                Text("… 共 ${m.actions.size} 个动作", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onSave(m) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f),
                                ) { Text("保存宏") }
                                TextButton(
                                    onClick = { onEdit(m) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("保存并编辑") }
                            }
                        }
                    }
                }
                is AiState.Templates -> {
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Brand.Secondary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "模板包：${state.macros.size} 个宏",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            state.macros.forEachIndexed { i, m ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${i + 1}. ${m.name}", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            m.description.ifBlank { "无描述" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { onSave(m) }) { Text("安装") }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onSaveAll(state.macros) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("全部安装（${state.macros.size} 个）") }
                        }
                    }
                }
                AiState.Idle -> Unit
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "AI 生成的内容会先经过校验：未知的动作类型会被丢弃，参数会按注册表补齐默认值。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

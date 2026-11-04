package com.autoflow.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoflow.app.core.Macro

/** 聊天流里的一条消息。 */
private sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Bot(val text: String) : ChatItem
    data class MacroCard(val macro: Macro, val index: Int) : ChatItem
    data class MacroGroup(val macros: List<Macro>) : ChatItem
    data class Error(val text: String) : ChatItem
}

/**
 * AI 助手：对话式界面。
 * 用户用一句话描述需求，助手返回可安装的宏卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    state: AiState,
    configured: Boolean,
    onGenerate: (String) -> Unit,
    onGenerateTemplates: (String) -> Unit,
    onSaveAll: (List<Macro>) -> Unit,
    onSave: (Macro) -> Unit,
    onEdit: (Macro) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var templateMode by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatItem>() }
    val listState = rememberLazyListState()

    // 把外部生成状态转成聊天消息
    LaunchedEffect(state) {
        when (val s = state) {
            is AiState.Success -> messages.add(ChatItem.MacroCard(s.macro, messages.size))
            is AiState.Templates -> messages.add(ChatItem.MacroGroup(s.macros))
            is AiState.Error -> messages.add(ChatItem.Error(s.message))
            else -> Unit
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || !configured) return
        messages.add(ChatItem.User(text))
        input = ""
        if (templateMode) onGenerateTemplates(text) else onGenerate(text)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Brand.Secondary)
                            Spacer(Modifier.width(6.dp))
                            Text("AI 助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (templateMode) "模板包模式 · 一次生成多个宏" else "描述你要的自动化，我来生成宏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "清空对话")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "AI 设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                if (messages.isEmpty() && configured) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(onClick = { input = "每天 23:00 静音并提示晚安" }, label = { Text("23:00 静音") })
                        AssistChip(onClick = { input = "连上公司 WiFi 自动静音" }, label = { Text("到公司静音") })
                        AssistChip(onClick = { input = "电量低于 20% 提醒我" }, label = { Text("低电提醒") })
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Card(
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(
                                onClick = { templateMode = !templateMode },
                                label = { Text(if (templateMode) "模板包 ✓" else "模板包") },
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = {
                                    Text(if (templateMode) "如：一套通勤自动化" else "如：每天早上 8 点播报天气")
                                },
                                maxLines = 3,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { send() },
                                enabled = configured && input.isNotBlank(),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (configured && input.isNotBlank()) Brand.Primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!configured) {
                            TextButton(onClick = onOpenSettings) { Text("先配置 DeepSeek API Key") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 10.dp, 16.dp, 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(84.dp)
                                .background(
                                    Brush.linearGradient(listOf(Brand.Primary, Brand.Secondary)),
                                    RoundedCornerShape(28.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("用一句话创建自动化", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "描述清楚「什么时候、做什么」即可\n例如：每天晚上 11 点把铃声调成静音",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(messages.size) { index ->
                when (val item = messages[index]) {
                    is ChatItem.User -> UserBubble(item.text)
                    is ChatItem.Bot -> BotBubble(item.text)
                    is ChatItem.Error -> ErrorBubble(item.text)
                    is ChatItem.MacroCard -> MacroResultCard(
                        macro = item.macro,
                        onSave = { onSave(item.macro) },
                        onEdit = { onEdit(item.macro) },
                    )
                    is ChatItem.MacroGroup -> MacroGroupCard(
                        macros = item.macros,
                        onSaveAll = { onSaveAll(item.macros) },
                        onSaveOne = onSave,
                        onEditOne = onEdit,
                    )
                }
            }
            if (state is AiState.Loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (templateMode) "正在规划模板包…" else "正在生成宏…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .background(Brand.Primary, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BotBubble(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorBubble(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MacroResultCard(macro: Macro, onSave: () -> Unit, onEdit: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Brand.Primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Brand.Secondary)
                Spacer(Modifier.width(8.dp))
                Text(macro.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (macro.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    macro.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${macro.triggers.size} 触发器 · ${macro.actions.size} 动作" +
                        if (macro.constraints.isEmpty()) "" else " · ${macro.constraints.size} 约束",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text("安装") }
                TextButton(onClick = onEdit) { Text("查看/编辑") }
            }
        }
    }
}

@Composable
private fun MacroGroupCard(
    macros: List<Macro>,
    onSaveAll: () -> Unit,
    onSaveOne: (Macro) -> Unit,
    onEditOne: (Macro) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Brand.Secondary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("生成 ${macros.size} 个宏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            macros.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${m.triggers.size} 触发器 · ${m.actions.size} 动作",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onEditOne(m) }) { Text("编辑") }
                    TextButton(onClick = { onSaveOne(m) }) { Text("安装") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSaveAll, modifier = Modifier.fillMaxWidth()) { Text("全部安装") }
        }
    }
}

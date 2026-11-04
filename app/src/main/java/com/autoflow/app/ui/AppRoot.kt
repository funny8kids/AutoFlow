package com.autoflow.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoflow.app.core.LogEntry
import com.autoflow.app.core.Macro
import com.autoflow.app.core.Registry

private sealed interface Route {
    data object List : Route
    data class Edit(val macro: Macro) : Route
    data object Logs : Route
    data object Ai : Route
    data object Globals : Route
    data object Backup : Route
    data object Quick : Route
    data object Blocks : Route
    data object Settings : Route
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val allMacros by vm.macros.collectAsStateWithLifecycle()
    val macros = remember(allMacros) { allMacros.filter { !it.isBlock } }
    val blocks = remember(allMacros) { allMacros.filter { it.isBlock } }
    val logs by vm.logs.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val modelsLoading by vm.modelsLoading.collectAsStateWithLifecycle()
    val globalVars by vm.globalVars.collectAsStateWithLifecycle()
    val backupMsg by vm.backupMsg.collectAsStateWithLifecycle()
    val runningIds by vm.runningIds.collectAsStateWithLifecycle()
    var route by remember { mutableStateOf<Route>(Route.List) }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(tween(360)) + scaleIn(tween(360), initialScale = 0.985f),
    ) {
    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "route",
    ) { screen ->
        when (screen) {
            is Route.List -> MacroListScreen(
                macros = macros,
                onOpen = { route = Route.Edit(it) },
                onCreate = { route = Route.Edit(vm.newMacro()) },
                onRun = { vm.run(it) },
                onStop = { vm.stop(it) },
                onStopAll = { vm.stopAll() },
                runningIds = runningIds,
                onToggle = { m, on -> vm.save(m.copy(enabled = on)) },
                onDelete = { vm.delete(it) },
                onLogs = { route = Route.Logs },
                onAi = { vm.resetAiState(); route = Route.Ai },
                onGlobals = { route = Route.Globals },
                onQuick = { route = Route.Quick },
                onBlocks = { route = Route.Blocks },
                blockCount = blocks.size,
                onSettings = { route = Route.Settings },
            )
            is Route.Edit -> MacroEditorScreen(
                initial = screen.macro,
                onBack = { route = Route.List },
                onSave = {
                    vm.save(it)
                    route = Route.List
                },
            )
            is Route.Logs -> LogsScreen(
                logs = logs,
                onBack = { route = Route.List },
                onClear = { vm.clearLogs() },
            )
            is Route.Ai -> AiScreen(
                state = aiState,
                configured = vm.aiConfigured,
                onGenerate = { vm.generateMacro(it) },
                onGenerateTemplates = { vm.generateTemplates(it) },
                onSaveAll = { list -> vm.saveAll(list); route = Route.List },
                onOpenSettings = { route = Route.Settings },
                onSave = { vm.save(it); route = Route.List },
                onEdit = { vm.save(it); route = Route.Edit(it) },
                onBack = { route = Route.List },
                onReset = { vm.resetAiState() },
            )
            is Route.Globals -> GlobalVarsScreen(
                vars = globalVars,
                onSave = { vm.saveGlobalVar(it) },
                onDelete = { vm.deleteGlobalVar(it) },
                onBack = { route = Route.List },
            )
            is Route.Blocks -> BlocksScreen(
                blocks = blocks,
                onCreate = { route = Route.Edit(vm.newBlock()) },
                onOpen = { route = Route.Edit(it) },
                onBack = { route = Route.List },
            )
            is Route.Quick -> QuickPanelScreen(
                macros = macros,
                onRun = { vm.run(it) },
                onBack = { route = Route.List },
            )
            is Route.Backup -> BackupScreen(
                lastMessage = backupMsg,
                onExportText = { pw -> vm.exportText(pw) },
                onImport = { text, pw -> vm.importBackup(text, pw) },
                onMessage = { vm.setBackupMessage(it) },
                onBack = { route = Route.List },
            )
            is Route.Settings -> SettingsScreen(
                initialKey = vm.aiApiKey,
                initialBaseUrl = vm.aiBaseUrl,
                initialModel = vm.aiModel,
                models = models,
                modelsLoading = modelsLoading,
                onFetchModels = { k, u -> vm.fetchModels(k, u) },
                onSave = { k, b, m -> vm.saveAiConfig(k, b, m) },
                onOpenBackup = { route = Route.Backup },
                onBack = { route = Route.List },
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroListScreen(
    macros: List<Macro>,
    onOpen: (Macro) -> Unit,
    onCreate: () -> Unit,
    onRun: (Macro) -> Unit,
    onStop: (Macro) -> Unit,
    onStopAll: () -> Unit,
    runningIds: Set<String>,
    onToggle: (Macro, Boolean) -> Unit,
    onDelete: (Macro) -> Unit,
    onLogs: () -> Unit,
    onAi: () -> Unit,
    onGlobals: () -> Unit,
    onQuick: () -> Unit,
    onBlocks: () -> Unit,
    blockCount: Int,
    onSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AutoFlow",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "让手机自己动起来",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (runningIds.isNotEmpty()) {
                        IconButton(onClick = onStopAll) {
                            Icon(Icons.Default.Close, contentDescription = "停止全部运行", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onAi) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI 生成", tint = Brand.Secondary)
                    }
                    IconButton(onClick = onGlobals) {
                        Icon(Icons.Default.Code, contentDescription = "全局变量")
                    }
                    IconButton(onClick = onLogs) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "运行日志")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建宏") },
            )
        },
    ) { padding ->
        if (macros.isEmpty()) {
            EmptyState(Modifier.padding(padding), onAi)
        } else {
            var query by remember { mutableStateOf("") }
            var category by remember { mutableStateOf("全部") }
            val categories = remember(macros) {
                listOf("全部") + macros.map { it.category.ifBlank { "未分类" } }.distinct()
            }
            val filtered = remember(macros, query, category) {
                macros.filter { m ->
                    val cat = m.category.ifBlank { "未分类" }
                    (category == "全部" || cat == category) &&
                        (query.isBlank() || m.name.contains(query, true) || m.description.contains(query, true))
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.linearGradient(listOf(Brand.Primary, Brand.Secondary)))
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                "AutoFlow",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${macros.size} 个宏 · ${macros.count { it.enabled }} 个启用" +
                                    (if (runningIds.isNotEmpty()) " · ${runningIds.size} 运行中" else "") +
                                    " · $blockCount 个动作块",
                                color = Color.White.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { onQuick() },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Default.Bolt, Brand.Primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("快捷面板", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "一键运行常用宏，也可添加到桌面小组件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Brand.Primary)
                        }
                    }
                }
                item {
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().clickable { onBlocks() },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Default.Repeat, Brand.Secondary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("动作块", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "可复用的子流程，在宏里用「调用动作块」执行",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "$blockCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = Brand.Secondary,
                            )
                        }
                    }
                }
                item {
                    Column {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("搜索宏…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { c ->
                                FilterChip(
                                    selected = category == c,
                                    onClick = { category = c },
                                    label = { Text(c) },
                                )
                            }
                        }
                    }
                }
                items(filtered, key = { it.id }) { macro ->
                    MacroCard(
                        macro = macro,
                        running = macro.id in runningIds,
                        onOpen = onOpen,
                        onRun = onRun,
                        onStop = onStop,
                        onToggle = onToggle,
                        onDelete = onDelete,
                    )
                }
                if (filtered.isEmpty()) {
                    item { EmptyHint("没有匹配的宏") }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAi: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(
                        Brush.linearGradient(listOf(Brand.Primary, Brand.Secondary)),
                        RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("还没有宏", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "点右下角「新建宏」，或让 AI 帮你写一个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.clickable { onAi() },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Brand.Secondary)
                    Spacer(Modifier.width(10.dp))
                    Text("用一句话让 DeepSeek 生成宏", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MacroCard(
    macro: Macro,
    running: Boolean,
    onOpen: (Macro) -> Unit,
    onRun: (Macro) -> Unit,
    onStop: (Macro) -> Unit,
    onToggle: (Macro, Boolean) -> Unit,
    onDelete: (Macro) -> Unit,
) {
    val accent = Brand.accents[macro.accent.coerceIn(0, Brand.accents.lastIndex)]
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().clickable { onOpen(macro) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Bolt, accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        macro.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (macro.description.isBlank()) "无描述" else macro.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = macro.enabled, onCheckedChange = { onToggle(macro, it) })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) StatChip("运行中", MaterialTheme.colorScheme.error)
                StatChip("${macro.triggers.size} 触发器", accent)
                StatChip("${macro.actions.size} 动作", Brand.Secondary)
                if (macro.constraints.isNotEmpty()) StatChip("${macro.constraints.size} 条件", Brand.Tertiary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    macro.triggers.firstOrNull()?.let { Registry.trigger(it.type)?.label } ?: "未设置触发器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (running) {
                    IconButton(onClick = { onStop(macro) }) {
                        Icon(Icons.Default.Close, contentDescription = "停止运行", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(onClick = { onRun(macro) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "运行", tint = Brand.Secondary)
                    }
                }
                IconButton(onClick = { onDelete(macro) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreen(logs: List<LogEntry>, onBack: () -> Unit, onClear: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("运行日志", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Bolt, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            EmptyState(Modifier.padding(padding), onAi = {})
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs) { log ->
                    val color = when (log.level) {
                        LogEntry.Level.INFO -> Brand.Secondary
                        LogEntry.Level.WARN -> Brand.Tertiary
                        LogEntry.Level.ERROR -> Brand.Danger
                    }
                    Card(
                        shape = SmallShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            AccentDot(color)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(log.macroName, style = MaterialTheme.typography.labelSmall, color = color)
                                Text(log.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
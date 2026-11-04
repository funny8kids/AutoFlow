package com.autoflow.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.autoflow.app.core.ActionSpec
import com.autoflow.app.core.ConstraintSpec
import com.autoflow.app.core.ItemSpec
import com.autoflow.app.core.Macro
import com.autoflow.app.core.Registry
import com.autoflow.app.core.TriggerSpec
import com.autoflow.app.core.Variable

private enum class SectionKind { TRIGGER, CONSTRAINT, ACTION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    initial: Macro,
    onBack: () -> Unit,
    onSave: (Macro) -> Unit,
    isRunning: Boolean = false,
    onTestRun: () -> Unit = {},
    onStopTest: () -> Unit = {},
) {
    var draft by remember { mutableStateOf(initial) }
    var catalogFor by remember { mutableStateOf<SectionKind?>(null) }
    val prefs = remember { com.autoflow.app.AutoFlowApp.app().getSharedPreferences("autoflow_runtime", android.content.Context.MODE_PRIVATE) }
    var recentTypes by remember {
        mutableStateOf(prefs.getString("recent_items", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList())
    }
    var editing by remember { mutableStateOf<Triple<SectionKind, Int, ItemSpec>?>(null) }
    val actionDepths = remember(draft.actions) {
        val list = mutableListOf<Int>()
        var d = 0
        draft.actions.forEach { a ->
            when (a.type) {
                "loop", "if" -> { list.add(d); d += 1 }
                "endloop", "endif" -> { d = (d - 1).coerceAtLeast(0); list.add(d) }
                else -> list.add(d)
            }
        }
        list
    }
    var showVarDialog by remember { mutableStateOf(false) }
    var editingVarIndex by remember { mutableStateOf(-1) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial.isBlock) "编辑动作块" else "编辑宏",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { if (isRunning) onStopTest() else onTestRun() }) {
                        Icon(
                            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "停止运行" else "测试运行",
                            tint = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = { onSave(draft) }) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isRunning) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "该宏正在运行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onStopTest) {
                                Text("停止", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("宏名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it) },
                    label = { Text("描述（可选）") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draft.category,
                    onValueChange = { draft = draft.copy(category = it) },
                    label = { Text("分类（可选）") },
                    placeholder = { Text("例如：家居 / 通勤 / 工作") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SectionCard(
                    title = "变量",
                    subtitle = "宏内局部变量；文本中用 {lv=名} 引用，全局用 {v=名}",
                    accent = Brand.Primary,
                    trailing = { AddButton { showVarDialog = true } },
                ) {
                    if (draft.variables.isEmpty()) {
                        EmptyHint("还没有声明变量")
                    } else {
                        draft.variables.forEachIndexed { index, v ->
                            VariableRow(
                                variable = v,
                                onEdit = { editingVarIndex = index },
                                onRemove = {
                                    draft = draft.copy(variables = draft.variables.filterIndexed { i, _ -> i != index })
                                },
                            )
                        }
                    }
                }
            }

            if (!draft.isBlock) {
            item {
                SectionCard(
                    title = "触发器 · ${draft.triggers.size}",
                    subtitle = "什么时候启动这个宏（多个为「或」）",
                    accent = Brand.Primary,
                    trailing = { AddButton { catalogFor = SectionKind.TRIGGER } },
                ) {
                    if (draft.triggers.isEmpty()) {
                        EmptyHint("还没有触发器，点 + 添加")
                    } else {
                        draft.triggers.forEachIndexed { index, trigger ->
                            val spec = Registry.trigger(trigger.type)
                            ItemRow(
                                spec = spec,
                                params = trigger.params,
                                accent = Brand.Primary,
                                onEdit = { spec?.let { editing = Triple(SectionKind.TRIGGER, index, it) } },
                                onRemove = { draft = draft.copy(triggers = draft.triggers.filterIndexed { i, _ -> i != index }) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "条件 · ${draft.constraints.size}",
                    subtitle = if (draft.constraintMode == "or") "任一满足即执行（「或」）" else "全部满足才执行（「与」）",
                    accent = Brand.Tertiary,
                    trailing = { AddButton { catalogFor = SectionKind.CONSTRAINT } },
                ) {
                    if (draft.constraints.size >= 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = draft.constraintMode != "or",
                                onClick = { draft = draft.copy(constraintMode = "and") },
                                label = { Text("全部满足（与）") },
                            )
                            FilterChip(
                                selected = draft.constraintMode == "or",
                                onClick = { draft = draft.copy(constraintMode = "or") },
                                label = { Text("任一满足（或）") },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (draft.constraints.isEmpty()) {
                        EmptyHint("没有条件限制")
                    } else {
                        draft.constraints.forEachIndexed { index, c ->
                            ItemRow(
                                spec = Registry.constraint(c.type),
                                params = c.params,
                                accent = Brand.Tertiary,
                                onEdit = {
                                    Registry.constraint(c.type)?.let { editing = Triple(SectionKind.CONSTRAINT, index, it) }
                                },
                                onRemove = { draft = draft.copy(constraints = draft.constraints.filterIndexed { i, _ -> i != index }) },
                            )
                        }
                    }
                }
            }

            }

            item {
                SectionCard(
                    title = "动作 · ${draft.actions.size}",
                    subtitle = "按顺序执行，共 ${draft.actions.size} 个",
                    accent = Brand.Secondary,
                    trailing = { AddButton { catalogFor = SectionKind.ACTION } },
                ) {
                    if (draft.actions.isEmpty()) {
                        EmptyHint("还没有动作，点 + 添加")
                    } else {
                        draft.actions.forEachIndexed { index, a ->
                            Box(Modifier.padding(start = ((actionDepths.getOrNull(index) ?: 0) * 14).dp)) {
                                ItemRow(
                                    spec = Registry.action(a.type),
                                    params = a.params,
                                    accent = Brand.Secondary,
                                    index = index + 1,
                                    onEdit = { Registry.action(a.type)?.let { editing = Triple(SectionKind.ACTION, index, it) } },
                                    onRemove = { draft = draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index }) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    catalogFor?.let { kind ->
        val specs = when (kind) {
            SectionKind.TRIGGER -> Registry.triggers
            SectionKind.CONSTRAINT -> Registry.constraints
            SectionKind.ACTION -> Registry.actions
        }
        CatalogSheet(
            title = when (kind) {
                SectionKind.TRIGGER -> "选择触发器"
                SectionKind.CONSTRAINT -> "选择条件"
                SectionKind.ACTION -> "选择动作"
            },
            items = specs,
            recent = recentTypes.mapNotNull { t -> specs.firstOrNull { it.type == t } },
            onPick = { spec ->
                recentTypes = (listOf(spec.type) + recentTypes.filter { it != spec.type }).take(6)
                prefs.edit().putString("recent_items", recentTypes.joinToString("|")).apply()
                catalogFor = null
                when (kind) {
                    SectionKind.TRIGGER -> draft = draft.copy(triggers = draft.triggers + TriggerSpec(spec.type, spec.defaults()))
                    SectionKind.CONSTRAINT -> draft = draft.copy(constraints = draft.constraints + ConstraintSpec(spec.type, spec.defaults()))
                    SectionKind.ACTION -> draft = draft.copy(actions = draft.actions + ActionSpec(spec.type, spec.defaults()))
                }
            },
            onDismiss = { catalogFor = null },
        )
    }

    if (showVarDialog) {
        VariableDialog(
            initial = null,
            onConfirm = { v ->
                draft = draft.copy(variables = draft.variables + v)
                showVarDialog = false
            },
            onDismiss = { showVarDialog = false },
        )
    }

    if (editingVarIndex >= 0) {
        VariableDialog(
            initial = draft.variables.getOrNull(editingVarIndex),
            onConfirm = { v ->
                draft = draft.copy(
                    variables = draft.variables.mapIndexed { i, old -> if (i == editingVarIndex) v else old }
                )
                editingVarIndex = -1
            },
            onDismiss = { editingVarIndex = -1 },
        )
    }

    editing?.let { (kind, index, spec) ->
        val current = when (kind) {
            SectionKind.TRIGGER -> draft.triggers.getOrNull(index)?.params
            SectionKind.CONSTRAINT -> draft.constraints.getOrNull(index)?.params
            SectionKind.ACTION -> draft.actions.getOrNull(index)?.params
        } ?: emptyMap()
        ParamEditorDialog(
            spec = spec,
            initial = current,
            onConfirm = { params ->
                draft = when (kind) {
                    SectionKind.TRIGGER -> draft.copy(
                        triggers = draft.triggers.mapIndexed { i, t -> if (i == index) t.copy(params = params) else t }
                    )
                    SectionKind.CONSTRAINT -> draft.copy(
                        constraints = draft.constraints.mapIndexed { i, c -> if (i == index) c.copy(params = params) else c }
                    )
                    SectionKind.ACTION -> draft.copy(
                        actions = draft.actions.mapIndexed { i, a -> if (i == index) a.copy(params = params) else a }
                    )
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ItemRow(
    spec: ItemSpec?,
    params: Map<String, String>,
    accent: androidx.compose.ui.graphics.Color,
    index: Int? = null,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(spec?.icon ?: Icons.Default.Add, accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (index?.let { "$it. " } ?: "") + (spec?.label ?: "未知项目"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                spec?.summary(params) ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        IconButton(onClick = onEdit) {
            Text("编辑", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

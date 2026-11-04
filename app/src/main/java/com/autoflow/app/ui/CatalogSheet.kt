package com.autoflow.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.core.ItemSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogSheet(
    title: String,
    items: List<ItemSpec>,
    onPick: (ItemSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    val categories = remember(items) { listOf("全部") + items.map { it.category }.distinct() }
    val filtered = remember(query, category, items) {
        items.filter { spec ->
            (category == "全部" || spec.category == category) &&
                (query.isBlank() || spec.label.contains(query, true) || spec.description.contains(query, true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索…") },
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered) { spec ->
                    Card(
                        shape = SmallShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        onClick = { onPick(spec) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(spec.icon, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(spec.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    spec.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParamEditorDialog(
    spec: ItemSpec,
    initial: Map<String, String>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember { mutableMapOf<String, String>().apply { putAll(initial) } }
    var tick by remember { mutableStateOf(0) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = CardShape,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(spec.icon, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(spec.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                if (spec.params.isEmpty()) {
                    Text("这个项目没有参数", style = MaterialTheme.typography.bodySmall)
                }
                spec.params.forEach { p ->
                    val current = values[p.key] ?: p.default
                    Spacer(Modifier.height(8.dp))
                    when (p.kind) {
                        com.autoflow.app.core.ParamKind.CHOICE -> {
                            Text(p.label, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(p.choices) { choice ->
                                    FilterChip(
                                        selected = current == choice.value,
                                        onClick = {
                                            values[p.key] = choice.value
                                            tick++
                                        },
                                        label = { Text(choice.label) },
                                    )
                                }
                            }
                        }
                        com.autoflow.app.core.ParamKind.BOOLEAN -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                androidx.compose.material3.Switch(
                                    checked = current == "true",
                                    onCheckedChange = {
                                        values[p.key] = it.toString()
                                        tick++
                                    },
                                )
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = current,
                                onValueChange = {
                                    values[p.key] = it
                                    tick++
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                label = { Text(p.label) },
                                placeholder = if (p.placeholder.isBlank()) null else {
                                    { Text(p.placeholder) }
                                },
                            )
                        }
                    }
                }
                @Suppress("UNUSED_EXPRESSION")
                tick
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                spec.params.forEach { p -> if (values[p.key].isNullOrBlank()) values[p.key] = p.default }
                onConfirm(values.toMap())
            }) { Text("完成", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

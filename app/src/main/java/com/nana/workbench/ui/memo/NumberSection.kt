package com.nana.workbench.ui.memo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.memo.NumberViewModel
import com.nana.workbench.ui.common.EmptyState
import com.nana.workbench.ui.common.MiniTrendChart
import com.nana.workbench.ui.common.UndoSnackbarHandler

@Composable
fun NumberSection(viewModel: NumberViewModel = viewModel()) {
    val tables by viewModel.tables.collectAsState()
    val selected by viewModel.selectedTable.collectAsState()
    val records by viewModel.records.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val justDeleted by viewModel.justDeleted.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddRecord by remember { mutableStateOf(false) }
    var showAddTable by remember { mutableStateOf(false) }

    UndoSnackbarHandler(
        deletedKey = justDeleted,
        snackbarHostState = snackbarHostState,
        message = "已删除记录",
        onUndo = viewModel::undoDelete,
        onDismiss = viewModel::clearJustDeleted,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (tables.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddRecord = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = "记一笔") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tables.forEach { table ->
                    FilterChip(
                        selected = table.id == selected?.id,
                        onClick = { viewModel.select(table.id) },
                        label = { Text(table.name) },
                    )
                }
                AssistChip(onClick = { showAddTable = true }, label = { Text("+ 新建表格") })
            }

            if (tables.isEmpty()) {
                EmptyState(
                    message = "挑一个想跟踪的数字，比如体重或每天花多少钱，记几次就能看见趋势。",
                    actionLabel = "新建表格",
                    onAction = { showAddTable = true },
                )
            } else {
                val decimals = selected?.decimals ?: 1
                val unit = selected?.unit.orEmpty()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = selected?.name.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stats.latest == null) {
                            Text(
                                text = "还没记过。点右下角 + 记一笔。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "${formatValue(stats.latest!!.value, decimals)} $unit",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = deltaText(stats.delta, decimals, unit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            if (stats.hasTrend) {
                                MiniTrendChart(points = stats.trend, modifier = Modifier.padding(top = 8.dp))
                                Text(
                                    text = "近 30 天 · 共 ${stats.totalCount} 笔",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(records, key = { it.id }) { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = record.date,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${formatValue(record.value, decimals)} $unit",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            IconButton(onClick = { viewModel.deleteRecord(record) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除这条")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRecord) {
        AddRecordDialog(
            tableName = selected?.name.orEmpty(),
            unit = selected?.unit.orEmpty(),
            decimals = selected?.decimals ?: 1,
            onDismiss = { showAddRecord = false },
            onSave = { value, note ->
                viewModel.addRecord(value, note)
                showAddRecord = false
            },
        )
    }

    if (showAddTable) {
        AddTableDialog(
            onDismiss = { showAddTable = false },
            onSave = { name, unit, decimals ->
                viewModel.addTable(name, unit, decimals)
                showAddTable = false
            },
        )
    }
}

private fun formatValue(value: Double, decimals: Int): String =
    "%.${decimals.coerceIn(0, 3)}f".format(value)

private fun deltaText(delta: Double?, decimals: Int, unit: String): String = when {
    delta == null -> "第一次记录"
    kotlin.math.abs(delta) < 1e-9 -> "和上次一样"
    delta > 0 -> "比上次 ↑ ${formatValue(delta, decimals)} $unit"
    else -> "比上次 ↓ ${formatValue(-delta, decimals)} $unit"
}

@Composable
private fun AddRecordDialog(
    tableName: String,
    unit: String,
    decimals: Int,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔 · $tableName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("数值（$unit）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可留空）") },
                    singleLine = true,
                )
                Text(
                    text = "保留 $decimals 位小数；同一天再记会覆盖当天那条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = text.trim().toDoubleOrNull()
                    if (value != null) onSave(value, note.trim())
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AddTableDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var decimals by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建表格") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("表名（例如 喝水）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("单位（例如 杯）") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2).forEach { digits ->
                        FilterChip(
                            selected = decimals == digits,
                            onClick = { decimals = digits },
                            label = { Text("$digits 位小数") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, unit, decimals) }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

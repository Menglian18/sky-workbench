package io.github.menglian18.skyworkbench.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.menglian18.skyworkbench.tools.ToolsViewModel
import io.github.menglian18.skyworkbench.tools.WheelSet

@Composable
fun ToolsSection(viewModel: ToolsViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddSet by remember { mutableStateOf(false) }
    var editingRandom by remember { mutableStateOf(false) }

    // 这两份数据在最后的弹窗里也要用，所以声明在函数开头
    val sets by viewModel.wheelSets.collectAsState()
    val randomList by viewModel.randomList.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 骰子 ──────────────────────────────────────────────────────────────
        val diceSides by viewModel.diceSides.collectAsState()
        val diceResult by viewModel.diceResult.collectAsState()
        ToolCard(title = "骰子", hint = "") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 20, 100).forEach { sides ->
                    FilterChip(
                        selected = diceSides == sides,
                        onClick = { viewModel.setDiceSides(sides) },
                        label = { Text("$sides 面") },
                    )
                }
            }
            diceResult?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = {
                    viewModel.rollDice()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            ) { Text("掷骰子") }
        }

        // ── 转盘 ──────────────────────────────────────────────────────────────
        val selectedIndex by viewModel.selectedSetIndex.collectAsState()
        val spinResult by viewModel.spinResult.collectAsState()
        val noRepeat by viewModel.noRepeat.collectAsState()

        ToolCard(title = "转盘", hint = "") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sets.forEachIndexed { index, set ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectSet(index) },
                        label = { Text(set.name) },
                    )
                }
                TextButton(onClick = { showAddSet = true }) { Text("+ 新的一套") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "抽过的不再重复",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = noRepeat, onCheckedChange = viewModel::setNoRepeat)
            }
            spinResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.spin()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = sets.isNotEmpty(),
                ) { Text("转一下") }
                OutlinedButton(
                    onClick = { editingIndex = selectedIndex },
                    enabled = sets.isNotEmpty(),
                ) { Text("编辑选项") }
                if (sets.isNotEmpty()) {
                    TextButton(onClick = { viewModel.deleteSet(selectedIndex) }) { Text("删掉这套") }
                }
            }
        }

        // ── 随机抽一个 ────────────────────────────────────────────────────────
        val randomResult by viewModel.randomResult.collectAsState()

        ToolCard(title = "随机抽一个", hint = "") {
            if (randomList.isEmpty()) {
                Text(
                    text = "还没有名单。点下面「编辑名单」写上几个。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = randomList.joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            randomResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.pickRandom()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = randomList.isNotEmpty(),
                ) { Text("抽一个") }
                OutlinedButton(onClick = { editingRandom = true }) { Text("编辑名单") }
            }
        }

        // 1.1 改进 · C 模块：休息铃声整块去掉了（连服务与相关权限一起清）
    }

    editingIndex?.let { index ->
        OptionsEditorDialog(
            title = "编辑「${sets.getOrNull(index)?.name.orEmpty()}」的选项",
            initial = sets.getOrNull(index)?.options.orEmpty(),
            onDismiss = { editingIndex = null },
            onSave = { options ->
                viewModel.saveSetOptions(index, options)
                editingIndex = null
            },
        )
    }

    if (editingRandom) {
        OptionsEditorDialog(
            title = "编辑名单",
            initial = randomList,
            onDismiss = { editingRandom = false },
            onSave = { options ->
                viewModel.saveRandomList(options)
                editingRandom = false
            },
        )
    }

    if (showAddSet) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSet = false },
            title = { Text("新的一套选项") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（例如 晚饭吃什么）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addSet(name)
                        showAddSet = false
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showAddSet = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ToolCard(
    title: String,
    hint: String,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (hint.isNotBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun OptionsEditorDialog(
    title: String,
    initial: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "一行一个选项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.split('\n')) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

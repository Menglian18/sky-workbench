package com.nana.workbench.ui.study

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.data.db.StudyCardEntity
import com.nana.workbench.study.normalizeTaskTitle
import com.nana.workbench.ui.common.EmptyState
import kotlin.math.roundToInt

/**
 * 学习安排：学科 + 任务（1.1 改进 · B 模块）。
 *
 * 取代了原来的"今日复习 / 卡片库"：不再有复习排期，就是一科一科的清单，
 * 任务能拖动排序、行尾小方块点一下划线（划掉的沉到最下面并锁定）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTasksScreen(
    modifier: Modifier = Modifier,
    viewModel: StudyTasksViewModel = viewModel(),
) {
    val subjects by viewModel.subjects.collectAsState()
    val selected by viewModel.selectedSubject.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var newTask by remember { mutableStateOf("") }
    var showAddSubject by remember { mutableStateOf(false) }
    var showManageSubject by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            message = null
        }
    }

    val openTasks = tasks.filter { !it.done }
    val doneTasks = tasks.filter { it.done }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 学科标签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = selected.isEmpty(),
                    onClick = { viewModel.selectSubject("") },
                    label = { Text("未归科") },
                )
                subjects.forEach { subject ->
                    FilterChip(
                        selected = selected == subject,
                        onClick = { viewModel.selectSubject(subject) },
                        label = { Text(subject) },
                    )
                }
                TextButton(onClick = { showAddSubject = true }) { Text("＋ 学科") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selected.isEmpty()) "未归科的任务" else selected,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { showManageSubject = true }) { Text("改名 / 删除") }
                }
            }

            // 加任务
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTask,
                    onValueChange = { newTask = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("这一科要做什么") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        viewModel.addTask(newTask)
                        newTask = ""
                    },
                    enabled = normalizeTaskTitle(newTask) != null,
                ) { Text("添加") }
            }

            if (tasks.isEmpty()) {
                EmptyState(
                    message = if (selected.isEmpty()) {
                        "还没有任务。先在上面建一个学科（比如「数学」），再往里加要做的事。"
                    } else {
                        "「$selected」下面还没有任务，在上面输入框里加一条吧。"
                    },
                )
            } else {
                var draggingId by remember { mutableStateOf<Long?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                var rowHeight by remember { mutableFloatStateOf(0f) }
                val displayItems = openTasks + doneTasks

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = draggingId == null,
                ) {
                    itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                        val draggable = !item.done
                        StudyTaskRow(
                            item = item,
                            onToggle = { viewModel.toggle(item) },
                            onDelete = { viewModel.delete(item) },
                            draggable = draggable,
                            isDragging = draggingId == item.id,
                            dragOffsetY = if (draggingId == item.id) dragOffset else 0f,
                            onMeasuredHeight = { height -> if (height > 0f) rowHeight = height },
                            dragHandle = if (!draggable) {
                                Modifier
                            } else {
                                Modifier.pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = item.id
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                        },
                                        onDragEnd = {
                                            val step = if (rowHeight > 0f) rowHeight else 1f
                                            val target = (index + (dragOffset / step).roundToInt())
                                                .coerceIn(0, openTasks.lastIndex)
                                            if (target != index) {
                                                viewModel.reorder(openTasks, index, target)
                                            }
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                    )
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                    if (doneTasks.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "已完成 ${doneTasks.size} 件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::clearDone) { Text("清空已完成") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSubject) {
        SubjectNameDialog(
            title = "新建学科",
            initial = "",
            onDismiss = { showAddSubject = false },
            onConfirm = { name ->
                val error = viewModel.addSubject(name)
                if (error == null) showAddSubject = false else message = error
            },
        )
    }

    if (showManageSubject) {
        val current = selected
        SubjectNameDialog(
            title = "学科改名",
            initial = current,
            onDismiss = { showManageSubject = false },
            onConfirm = { name ->
                val error = viewModel.renameSubject(current, name)
                if (error == null) showManageSubject = false else message = error
            },
            onDelete = {
                viewModel.deleteSubject(current)
                showManageSubject = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyTaskRow(
    item: StudyCardEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    draggable: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onMeasuredHeight: (Float) -> Unit,
    dragHandle: Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .onSizeChanged { onMeasuredHeight(it.height.toFloat()) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = dragHandle.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // 1.2 改进第 6 条：被锁（已划掉）的任务前面不要带锁的图形
                    text = if (draggable) "≡" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.front,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            )
            TextButton(onClick = onDelete) { Text("删") }
            Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun SubjectNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("学科名") },
                    singleLine = true,
                )
                if (onDelete != null) {
                    Text(
                        text = "删除会连同这个学科下的所有任务一起删掉。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除整个学科") }
                }
                Spacer(Modifier.size(4.dp))
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

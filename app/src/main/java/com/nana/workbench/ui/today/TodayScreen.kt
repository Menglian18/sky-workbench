package com.nana.workbench.ui.today

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.data.db.TodoEntity
import com.nana.workbench.today.TodayViewModel
import com.nana.workbench.ui.common.EmptyState
import com.nana.workbench.ui.common.UndoSnackbarHandler
import com.nana.workbench.util.DateKeys
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(),
) {
    val openItems by viewModel.openItems.collectAsState()
    val doneItems by viewModel.doneItems.collectAsState()
    val dateKey by viewModel.dateKey.collectAsState()
    val justDeleted by viewModel.justDeleted.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TodoEntity?>(null) }
    var postponeTarget by remember { mutableStateOf<TodoEntity?>(null) }
    var showDone by remember { mutableStateOf(false) }

    // 每次进入本页都再结算一次顺延（可能已经跨过了凌晨 4 点）
    LaunchedEffect(Unit) { viewModel.refreshToday() }

    UndoSnackbarHandler(
        deletedKey = justDeleted,
        snackbarHostState = snackbarHostState,
        message = "已删除待办",
        onUndo = viewModel::undoDelete,
        onDismiss = viewModel::clearJustDeleted,
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "加一条")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            DateBar(
                dateKey = dateKey,
                onPrev = { viewModel.shiftDays(-1) },
                onNext = { viewModel.shiftDays(1) },
                onToday = viewModel::backToToday,
                isToday = viewModel.isViewingToday(),
            )
            HorizontalDivider()

            if (openItems.isEmpty() && doneItems.isEmpty()) {
                EmptyState(
                    message = "今天还没安排。想到什么就先记下来，做完打勾；没做完的明天会自动跟过来。",
                    actionLabel = "加一条",
                    onAction = { showAdd = true },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(openItems, key = { it.id }) { item ->
                        TodoRow(
                            item = item,
                            onToggle = { viewModel.toggle(item) },
                            onEdit = { editing = item },
                            onDelete = { viewModel.delete(item) },
                            onPostpone = { postponeTarget = item },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                    if (doneItems.isNotEmpty()) {
                        item {
                            DoneHeader(
                                count = doneItems.size,
                                expanded = showDone,
                                onToggleExpand = { showDone = !showDone },
                                onClear = viewModel::clearDone,
                            )
                        }
                        if (showDone) {
                            items(doneItems, key = { "done-${it.id}" }) { item ->
                                DoneRow(item = item, onToggle = { viewModel.toggle(item) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTodoSheet(
            onDismiss = { showAdd = false },
            onAdd = { title, priority, timePoint -> viewModel.add(title, priority, timePoint) },
        )
    }

    editing?.let { item ->
        EditTodoDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.save(updated)
                editing = null
            },
        )
    }

    postponeTarget?.let { target ->
        PostponeDialog(
            onDismiss = { postponeTarget = null },
            onPick = { days ->
                viewModel.postpone(target, days)
                postponeTarget = null
            },
        )
    }
}

@Composable
private fun DateBar(
    dateKey: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    isToday: Boolean,
) {
    val date: LocalDate = DateKeys.parse(dateKey)
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val label = "${date.monthValue}月${date.dayOfMonth}日 ${weekdays[date.dayOfWeek.value - 1]}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowLeft, "前一天") }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!isToday) {
            TextButton(onClick = onToday) { Text("回今天") }
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowRight, "后一天") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    item: TodoEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPostpone: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPostpone()
                    true
                }
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("延后", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text("删除", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.done, onCheckedChange = { onToggle() })
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.priority) {
                                2 -> MaterialTheme.colorScheme.error
                                1 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.surface
                            },
                        ),
                )
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    val hints = buildList {
                        item.timePoint?.let { add(it) }
                        if (item.postponedCount > 0) add("已顺延 ${item.postponedCount} 天")
                        if (item.note.isNotBlank()) add(item.note)
                    }
                    if (hints.isNotEmpty()) {
                        Text(
                            text = hints.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑") }
            }
        }
    }
}

@Composable
private fun DoneHeader(
    count: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "已完成 $count 件",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text("清空") }
    }
}

@Composable
private fun DoneRow(item: TodoEntity, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = true, onCheckedChange = { onToggle() })
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTodoSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Int, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    var timePoint by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("加一条", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("要做的事") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "普通", 1 to "重要", 2 to "紧急").forEach { (value, label) ->
                    FilterChip(
                        selected = priority == value,
                        onClick = { priority = value },
                        label = { Text(label) },
                    )
                }
            }
            OutlinedTextField(
                value = timePoint,
                onValueChange = { timePoint = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("时间点（可留空，例如 08:30）") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAdd(title, priority, timePoint)
                            title = ""
                        }
                    },
                ) { Text("添加并继续") }
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Text(
                text = "提示：点「添加并继续」可以连着加好几条，面板不会关。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditTodoDialog(
    item: TodoEntity,
    onDismiss: () -> Unit,
    onSave: (TodoEntity) -> Unit,
) {
    var title by remember { mutableStateOf(item.title) }
    var note by remember { mutableStateOf(item.note) }
    var timePoint by remember { mutableStateOf(item.timePoint ?: "") }
    var priority by remember { mutableIntStateOf(item.priority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("内容") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = timePoint,
                    onValueChange = { timePoint = it },
                    label = { Text("时间点（可留空）") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "普通", 1 to "重要", 2 to "紧急").forEach { (value, label) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        item.copy(
                            title = title.trim().ifEmpty { item.title },
                            note = note.trim(),
                            timePoint = timePoint.trim().ifEmpty { null },
                            priority = priority,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PostponeDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("延后到") },
        text = { Text("这条今天不做了，挪到哪天？") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onPick(1) }) { Text("明天") }
                TextButton(onClick = { onPick(2) }) { Text("后天") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

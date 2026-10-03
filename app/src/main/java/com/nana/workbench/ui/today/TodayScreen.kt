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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.nana.workbench.today.Quadrant
import com.nana.workbench.today.TodoOrdering
import com.nana.workbench.today.TodoSchedule
import com.nana.workbench.today.dateLabel
import com.nana.workbench.today.shiftHour
import com.nana.workbench.today.shiftMinute
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.data.db.TodoEntity
import com.nana.workbench.today.TodoDraft
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
                var draggingId by remember { mutableStateOf<Long?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                var rowHeight by remember { mutableFloatStateOf(0f) }
                val openSorted = TodoOrdering.sortForDisplay(openItems)
                // 划掉的在最下层，且不参与拖拽（1.1 改进第 4 条：沉到最下层、锁定不动）
                val displayItems = openSorted + doneItems

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 拖拽的时候不要跟着滚，否则手感会很乱
                    userScrollEnabled = draggingId == null,
                ) {
                    itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                        val draggable = !item.done
                        TodoRow(
                            item = item,
                            onToggle = { viewModel.toggle(item) },
                            onEdit = { editing = item },
                            onDelete = { viewModel.delete(item) },
                            onPostpone = { postponeTarget = item },
                            isDragging = draggingId == item.id,
                            dragOffsetY = if (draggingId == item.id) dragOffset else 0f,
                            onMeasuredHeight = { height -> if (height > 0f) rowHeight = height },
                            draggable = draggable,
                            // openSorted 排在前面，所以这里的 index 就是它在未完成列表里的下标
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
                                                .coerceIn(0, openSorted.lastIndex)
                                            if (target != index) {
                                                viewModel.reorder(openSorted, index, target)
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
                    if (doneItems.isNotEmpty()) {
                        item {
                            DoneFooter(count = doneItems.size, onClear = viewModel::clearDone)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTodoSheet(
            onDismiss = { showAdd = false },
            onAdd = { title, priority, timePoint, dateKey ->
                viewModel.add(title, priority, timePoint, dateKey)
            },
        )
    }

    editing?.let { item ->
        EditTodoSheet(
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
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onMeasuredHeight: (Float) -> Unit = {},
    draggable: Boolean = true,
    dragHandle: Modifier = Modifier,
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
                // 左边：按住这里可以把任务拖到别的位置（1.1 改进第 3 条）
                // 划掉的会变成一把小锁，拖不动（1.1 改进第 4 条：锁定不动）
                Box(
                    modifier = dragHandle.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (draggable) "≡" else "🔒",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.priority) {
                                3 -> MaterialTheme.colorScheme.error
                                2 -> MaterialTheme.colorScheme.primary
                                1 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.outline
                            },
                        ),
                )
                Spacer(Modifier.size(8.dp))
                // 中间：点一下就能改（创建完也能改象限，1.1 改进第 3 条）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEdit)
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        // 划掉（1.1 改进第 4 条）
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
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
                // 右边：小方块，点一下就划线（1.1 改进第 4 条）
                Checkbox(checked = item.done, onCheckedChange = { onToggle() })
            }
        }
    }
}

@Composable
private fun DoneFooter(
    count: Int,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已完成 $count 件（已经在上面划线了）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text("清空已完成") }
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
    onAdd: (String, Int, String?, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val todayKey = remember { DateKeys.todayKey() }
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    var schedule by remember { mutableStateOf(TodoSchedule(dateKey = todayKey)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
            QuadrantChips(priority = priority, onPriorityChange = { priority = it })
            ScheduleFields(
                schedule = schedule,
                todayKey = todayKey,
                onScheduleChange = { schedule = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val draft = TodoDraft(
                    title = title,
                    priority = priority,
                    timePoint = schedule.timePoint,
                    dateKey = schedule.dateKey,
                )
                TextButton(
                    onClick = {
                        if (draft.isSavable) {
                            onAdd(draft.cleanTitle, draft.priority, draft.cleanTimePoint, schedule.dateKey)
                            title = ""
                        }
                    },
                ) { Text("添加并继续") }
                TextButton(
                    onClick = {
                        // 1.1 改进第 1 条：点「完成」以前只关面板、什么都不存，
                        // 用户以为添加好了，其实是一条空的。现在这里也要存。
                        if (draft.isSavable) {
                            onAdd(draft.cleanTitle, draft.priority, draft.cleanTimePoint, schedule.dateKey)
                        }
                        onDismiss()
                    },
                ) { Text("完成") }
            }
            Text(
                text = "提示：「添加并继续」可以连着加好几条、面板不关；打完字直接点「完成」也会存下来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTodoSheet(
    item: TodoEntity,
    onDismiss: () -> Unit,
    onSave: (TodoEntity) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val todayKey = remember { DateKeys.todayKey() }
    var title by remember { mutableStateOf(item.title) }
    var note by remember { mutableStateOf(item.note) }
    var priority by remember { mutableIntStateOf(item.priority) }
    var schedule by remember { mutableStateOf(TodoSchedule.from(item.dueDate, item.timePoint)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("改这条", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("内容") },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（可留空）") },
                singleLine = true,
            )
            QuadrantChips(priority = priority, onPriorityChange = { priority = it })
            ScheduleFields(
                schedule = schedule,
                todayKey = todayKey,
                onScheduleChange = { schedule = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(
                    onClick = {
                        val draft = TodoDraft(title = title, note = note, priority = priority)
                        onSave(
                            item.copy(
                                title = draft.cleanTitle.ifEmpty { item.title },
                                note = draft.cleanNote,
                                priority = draft.priority,
                                timePoint = schedule.timePoint,
                                dueDate = schedule.dateKey,
                            ),
                        )
                    },
                ) { Text("保存") }
            }
        }
    }
}

/**
 * 紧急 / 重要四象限的选择（1.1 改进第 3 条）。
 * 两行放四个，屏幕窄也不会挤。
 */
@Composable
private fun QuadrantChips(priority: Int, onPriorityChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "属于哪一格？",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Quadrant.ordered.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { quadrant ->
                    FilterChip(
                        selected = priority == quadrant.code,
                        onClick = { onPriorityChange(quadrant.code) },
                        label = { Text(quadrant.shortTitle) },
                    )
                }
            }
        }
    }
}

/**
 * 日期 + 时 + 分 的选法（1.1 改进第 2 条）。
 * 日期默认是今天，点开是日历；时间默认不设，开启后用加减来调时和分。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleFields(
    schedule: TodoSchedule,
    todayKey: String,
    onScheduleChange: (TodoSchedule) -> Unit,
) {
    var showCalendar by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showCalendar = !showCalendar },
                modifier = Modifier.weight(1f),
            ) { Text("日期 " + dateLabel(schedule.dateKey, todayKey)) }
            OutlinedButton(
                onClick = {
                    if (schedule.hasTime) {
                        onScheduleChange(schedule.clearTime())
                    } else {
                        onScheduleChange(
                            schedule.withTime(TodoSchedule.DEFAULT_HOUR, TodoSchedule.DEFAULT_MINUTE),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (schedule.hasTime) "时间 " + schedule.timePoint else "不设时间") }
        }

        if (showCalendar) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = dateKeyToMillis(schedule.dateKey),
            )
            DatePicker(state = state, showModeToggle = false)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showCalendar = false }) { Text("收起") }
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onScheduleChange(schedule.withDate(millisToDateKey(millis)))
                        }
                        showCalendar = false
                    },
                ) { Text("选好了") }
            }
        }

        if (schedule.hasTime) {
            val hour = schedule.hour ?: TodoSchedule.DEFAULT_HOUR
            val minute = schedule.minute ?: TodoSchedule.DEFAULT_MINUTE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stepper(
                    label = "时",
                    value = hour,
                    onPrev = { onScheduleChange(schedule.withTime(shiftHour(hour, false), minute)) },
                    onNext = { onScheduleChange(schedule.withTime(shiftHour(hour, true), minute)) },
                )
                Stepper(
                    label = "分",
                    value = minute,
                    onPrev = { onScheduleChange(schedule.withTime(hour, shiftMinute(minute, false))) },
                    onNext = { onScheduleChange(schedule.withTime(hour, shiftMinute(minute, true))) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0, 15, 30, 45).forEach { preset ->
                    TextButton(onClick = { onScheduleChange(schedule.withTime(hour, preset)) }) {
                        Text(":%02d".format(preset))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(text = "%s %02d".format(label, value), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onNext) { Text("＋", style = MaterialTheme.typography.titleLarge) }
    }
}

private fun dateKeyToMillis(dateKey: String): Long? = runCatching {
    DateKeys.parse(dateKey).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

private fun millisToDateKey(millis: Long): String =
    DateKeys.of(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())

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

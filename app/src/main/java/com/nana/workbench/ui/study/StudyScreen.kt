package com.nana.workbench.ui.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.data.db.StudyCardEntity
import com.nana.workbench.study.StudyGrade
import com.nana.workbench.study.StudyPhase
import com.nana.workbench.study.StudyViewModel
import com.nana.workbench.ui.common.EmptyState
import com.nana.workbench.ui.common.UndoSnackbarHandler

@Composable
fun StudyScreen(
    modifier: Modifier = Modifier,
    viewModel: StudyViewModel = viewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("今日复习") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("卡片库") })
        }
        when (tab) {
            0 -> ReviewSection(viewModel)
            else -> LibrarySection(viewModel)
        }
    }
}

@Composable
private fun ReviewSection(viewModel: StudyViewModel) {
    val dueCards by viewModel.dueCards.collectAsState()
    val doneToday by viewModel.todayDone.collectAsState()
    val total by viewModel.totalCards.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshNow() }

    val current = dueCards.firstOrNull()
    val alreadyDone = doneToday

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (current == null) {
            EmptyState(
                message = if (total == 0) {
                    "今天没有要复习的。去「卡片库」加几张（可以一次粘贴一整份清单），明天开始按遗忘节奏提醒你。"
                } else {
                    "今天复习完了 🎉 今天已完成 $alreadyDone 张。"
                },
            )
            return@Column
        }

        Text(
            text = "还剩 ${dueCards.size} 张 · 今天已完成 $alreadyDone 张",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val previews = remember(current.id) { viewModel.previews(current) }
        val totalToday = dueCards.size + alreadyDone
        LinearProgressIndicator(
            progress = { if (totalToday == 0) 0f else alreadyDone.toFloat() / totalToday.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )

        var answerShown by remember(current.id) { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { answerShown = !answerShown },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = current.front,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (answerShown) {
                    Text(
                        text = current.back,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    if (current.note.isNotBlank()) {
                        Text(
                            text = current.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    Text(
                        text = "点一下看答案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudyGrade.entries.forEach { grade ->
                val preview = previews.firstOrNull { it.grade == grade }
                Button(
                    onClick = { viewModel.review(current, grade) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (grade) {
                            StudyGrade.AGAIN -> MaterialTheme.colorScheme.error
                            StudyGrade.HARD -> MaterialTheme.colorScheme.secondary
                            StudyGrade.GOOD -> MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(grade.label)
                        preview?.let {
                            Text(
                                text = it.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "按钮下面显示的是「下次什么时候再见」。三个按钮就够了，不用纠结。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySection(viewModel: StudyViewModel) {
    val cards by viewModel.libraryCards.collectAsState()
    val query by viewModel.query.collectAsState()
    val total by viewModel.totalCards.collectAsState()
    val mastered by viewModel.masteredCards.collectAsState()
    val justDeleted by viewModel.justDeleted.collectAsState()
    val importMessage by viewModel.lastImportMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudyCardEntity?>(null) }

    UndoSnackbarHandler(
        deletedKey = justDeleted,
        snackbarHostState = snackbarHostState,
        message = "已删除卡片",
        onUndo = viewModel::undoDelete,
        onDismiss = viewModel::clearJustDeleted,
    )

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "新建卡片") }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Text(
                text = "共 $total 张 · 已掌握 $mastered 张",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜正面、背面或备注") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
                TextButton(onClick = { showImport = true }) { Text("导入") }
            }

            if (cards.isEmpty()) {
                EmptyState(
                    message = if (total == 0) {
                        "把你正在背的东西粘进来，一行一张，中间用 | 分开正反面。例如：apple | 苹果"
                    } else {
                        "没找到包含「$query」的卡片。"
                    },
                    actionLabel = if (total == 0) "粘贴导入" else null,
                    onAction = if (total == 0) ({ showImport = true }) else null,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(cards, key = { it.id }) { card ->
                        CardRow(
                            card = card,
                            onEdit = { editing = card },
                            onDelete = { viewModel.deleteCard(card) },
                            onToggleSuspend = {
                                if (card.phase == StudyPhase.SUSPENDED.ordinal) viewModel.resumeCard(card)
                                else viewModel.suspendCard(card)
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }

    if (showAdd) {
        CardEditorDialog(
            card = null,
            onDismiss = { showAdd = false },
            onSave = { front, back, note ->
                viewModel.addCard(front, back, note)
                showAdd = false
            },
        )
    }

    editing?.let { card ->
        CardEditorDialog(
            card = card,
            onDismiss = { editing = null },
            onSave = { front, back, note ->
                viewModel.saveCard(card.copy(front = front, back = back, note = note))
                editing = null
            },
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                viewModel.importCards(text)
                showImport = false
            },
        )
    }
}

@Composable
private fun CardRow(
    card: StudyCardEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSuspend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.front,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = buildString {
                    if (card.phase == StudyPhase.SUSPENDED.ordinal) append("已暂停")
                    else append(if (card.phase == StudyPhase.NEW.ordinal) "新卡" else "复习 ${card.reps} 次")
                    if (card.back.isNotBlank()) append(" · ").append(card.back)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onToggleSuspend) {
            Text(if (card.phase == StudyPhase.SUSPENDED.ordinal) "恢复" else "暂停")
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
    }
}

@Composable
private fun CardEditorDialog(
    card: StudyCardEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var front by remember { mutableStateOf(card?.front ?: "") }
    var back by remember { mutableStateOf(card?.back ?: "") }
    var note by remember { mutableStateOf(card?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (card == null) "新建卡片" else "编辑卡片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text("正面（问题 / 单词）") },
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text("背面（答案 / 释义）") },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可留空）") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (front.isNotBlank()) onSave(front, back, note) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴导入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "一行一张，正面和背面用 | 分开：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("apple | 苹果\nbanana | 香蕉") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

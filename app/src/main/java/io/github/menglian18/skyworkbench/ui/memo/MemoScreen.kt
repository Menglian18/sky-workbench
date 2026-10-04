package io.github.menglian18.skyworkbench.ui.memo

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.menglian18.skyworkbench.data.db.MemoEntity
import io.github.menglian18.skyworkbench.memo.MemoViewModel
import io.github.menglian18.skyworkbench.ui.common.EmptyState
import io.github.menglian18.skyworkbench.ui.common.UndoSnackbarHandler

@Composable
fun MemoScreen(
    modifier: Modifier = Modifier,
    viewModel: MemoViewModel = viewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("生活文本") })
        }
        when (tab) {
            0 -> MemoTextSection(viewModel = viewModel)
            else -> {
                // 1.1 改进 · D3：数字表格已按要求删除
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoTextSection(viewModel: MemoViewModel) {
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val category by viewModel.category.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val justDeleted by viewModel.justDeleted.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MemoEntity?>(null) }
    var editing by remember { mutableStateOf<MemoEntity?>(null) }

    UndoSnackbarHandler(
        deletedKey = justDeleted,
        snackbarHostState = snackbarHostState,
        message = "已删除备忘",
        onUndo = viewModel::undoDelete,
        onDismiss = viewModel::clearJustDeleted,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "新建备忘") }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("搜标题、内容或分类") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { name ->
                    FilterChip(
                        selected = name == category,
                        onClick = { viewModel.setCategory(name) },
                        label = { Text(name) },
                    )
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    message = if (query.isBlank()) {
                        "快递单号、尺寸、证件号…写进来就能搜到，置顶的会出现在首页。"
                    } else {
                        "没找到包含「$query」的备忘。换个词试试，或者点右下角新建一条。"
                    },
                    actionLabel = "新建一条",
                    onAction = { showAdd = true },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        MemoRow(
                            item = item,
                            onOpen = { detail = item },
                            onTogglePin = { viewModel.togglePin(item) },
                            onEdit = { editing = item },
                            onDelete = { viewModel.delete(item) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }

    if (showAdd) {
        EditMemoSheet(
            memo = null,
            onDismiss = { showAdd = false },
            onSave = { title, content, cat, pinned ->
                viewModel.add(title, content, cat, pinned)
                showAdd = false
            },
        )
    }

    editing?.let { item ->
        EditMemoSheet(
            memo = item,
            onDismiss = { editing = null },
            onSave = { title, content, cat, pinned ->
                viewModel.save(item.copy(title = title, content = content, category = cat, pinned = pinned))
                editing = null
            },
        )
    }

    detail?.let { item ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.content.ifBlank { "（没有内容）" })
                    Text(
                        text = "分类：${item.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(item.content.ifBlank { item.title }))
                        detail = null
                    },
                ) { Text("复制内容") }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("关闭") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoRow(
    item: MemoEntity,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("删除", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.pinned) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "已置顶",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.content.isNotBlank()) {
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = if (item.pinned) "取消置顶" else "置顶到首页",
                        tint = if (item.pinned) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMemoSheet(
    memo: MemoEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(memo?.title ?: "") }
    var content by remember { mutableStateOf(memo?.content ?: "") }
    var category by remember { mutableStateOf(memo?.category ?: "其他") }
    var pinned by remember { mutableStateOf(memo?.pinned ?: false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (memo == null) "新建备忘" else "编辑备忘", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("标题（可留空，会自动取内容开头）") },
                singleLine = true,
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("内容") },
                minLines = 2,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MemoViewModel.DEFAULT_CATEGORIES.filter { it != MemoViewModel.CATEGORY_ALL }.forEach { name ->
                    FilterChip(
                        selected = category == name,
                        onClick = { category = name },
                        label = { Text(name) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = pinned,
                    onClick = { pinned = !pinned },
                    label = { Text("置顶到首页") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = { onSave(title, content, category, pinned) }) { Text("保存") }
            }
        }
    }
}

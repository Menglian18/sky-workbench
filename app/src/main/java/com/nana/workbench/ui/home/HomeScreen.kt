package com.nana.workbench.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.data.db.TodoEntity
import com.nana.workbench.sky.SkyCountdown
import com.nana.workbench.today.Quadrant
import com.nana.workbench.ui.nav.TabDestination
import com.nana.workbench.util.DateKeys
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenTab: (TabDestination) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val dateKey by viewModel.dateKey.collectAsState()
    val openCount by viewModel.openCount.collectAsState()
    val topTodos by viewModel.topTodos.collectAsState()
    val quadrants by viewModel.quadrants.collectAsState()
    val studyDue by viewModel.studyDueCount.collectAsState()
    val studyDone by viewModel.studyDoneToday.collectAsState()
    val pinned by viewModel.pinnedMemos.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    val season by viewModel.currentSeason.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var quickText by remember { mutableStateOf("") }
    var quickPinned by remember { mutableStateOf(false) }

    // 每次回到首页都按"现在"重算今天（并结算顺延）
    LaunchedEffect(Unit) { viewModel.refreshToday() }

    val date = DateKeys.parse(dateKey)
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日 ${weekdays[date.dayOfWeek.value - 1]}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // ── 快速备忘 ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = quickText,
                    onValueChange = { quickText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("随手记一句…") },
                    singleLine = true,
                    leadingIcon = {
                        IconButton(onClick = { quickPinned = !quickPinned }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = if (quickPinned) "取消置顶" else "同时置顶到首页",
                                tint = if (quickPinned) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                            )
                        }
                    },
                )
                Button(
                    onClick = {
                        viewModel.quickAddMemo(quickText, quickPinned)
                        quickText = ""
                        quickPinned = false
                        scope.launch { snackbarHostState.showSnackbar("已存到备忘") }
                    },
                    enabled = quickText.isNotBlank(),
                ) { Text("存") }
            }

            // ── 今日待办 ──────────────────────────────────────────────
            SummaryCard("今日待办", onClick = { onOpenTab(TabDestination.TODAY) }) {
                if (openCount == 0) {
                    Text(
                        text = "今天还没有安排，点这里加一条 →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "还剩 $openCount 件",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    topTodos.forEach { item ->
                        TodoLine(item = item, onToggle = { viewModel.toggleTodo(item) })
                    }
                }
            }

            // ── 今日复习 ──────────────────────────────────────────────
            // 1.1 改进第 3 条：首页要有"紧急 / 重要四象限"
            SummaryCard("紧急 / 重要四象限", onClick = { onOpenTab(TabDestination.TODAY) }) {
                Quadrant.ordered.forEach { quadrant ->
                    val items = quadrants[quadrant].orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quadrant.shortTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (items.isEmpty()) "—" else "${items.size} 件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (items.isNotEmpty()) {
                        Text(
                            text = items.take(3).joinToString("、") { it.title } +
                                if (items.size > 3) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SummaryCard("今日复习", onClick = { onOpenTab(TabDestination.STUDY) }) {
                if (studyDue == 0 && studyDone == 0) {
                    Text(
                        text = "还没有学习卡片，点这里添加 →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "今天该复习 $studyDue 张 · 已完成 $studyDone",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // ── 复刻倒计时 ────────────────────────────────────────────
            SummaryCard("复刻倒计时", onClick = { onOpenTab(TabDestination.PLAY) }) {
                val ongoing = countdown.ongoing
                val next = countdown.next
                when {
                    ongoing != null -> {
                        Text(
                            text = if (countdown.ongoingIsCollective) {
                                "正在复刻：集体复刻（${countdown.ongoingCount} 位先祖）"
                            } else {
                                "正在复刻：${ongoing.spiritName}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "还剩 ${SkyCountdown.daysLeft(ongoing.endDate, dateKey)} 天",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    next != null -> {
                        Text(
                            text = if (countdown.nextIsCollective) {
                                "下次复刻：集体复刻（${countdown.nextCount} 位先祖）"
                            } else {
                                "下次复刻：${next.spiritName}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "还有 ${SkyCountdown.daysLeft(next.startDate, dateKey)} 天",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    else -> Text(
                        text = "数据还没获取，进「娱乐」联网拿一次 →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 当季剩余 ──────────────────────────────────────────────
            SummaryCard("当季剩余", onClick = { onOpenTab(TabDestination.PLAY) }) {
                val current = season
                if (current == null) {
                    Text(
                        text = "还没获取到季节信息 →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "${current.name} · 还剩 ${SkyCountdown.daysLeft(current.endDate, dateKey)} 天",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // ── 最近备忘 ──────────────────────────────────────────────
            SummaryCard("最近备忘", onClick = { onOpenTab(TabDestination.MEMO) }) {
                if (pinned.isEmpty()) {
                    Text(
                        text = "把常用的东西写进备忘并置顶，就会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    pinned.forEach { memo ->
                        Text(
                            text = memo.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(memo.content.ifBlank { memo.title }))
                                    scope.launch { snackbarHostState.showSnackbar("已复制") }
                                }
                                .padding(vertical = 4.dp),
                        )
                        Text(
                            text = memo.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun TodoLine(item: TodoEntity, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

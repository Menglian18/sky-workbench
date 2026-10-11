package io.github.menglian18.skyworkbench.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
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
import io.github.menglian18.skyworkbench.data.db.TodoEntity
import io.github.menglian18.skyworkbench.sky.SkyCountdown
import io.github.menglian18.skyworkbench.today.Quadrant
import io.github.menglian18.skyworkbench.ui.nav.TabDestination
import io.github.menglian18.skyworkbench.ui.sky.WikiDetailDialog
import io.github.menglian18.skyworkbench.util.DateKeys
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
    val subjectCount by viewModel.studySubjectCount.collectAsState()
    val openTaskCount by viewModel.studyOpenCount.collectAsState()
    val doneTaskCount by viewModel.studyDoneCount.collectAsState()
    val pinned by viewModel.pinnedMemos.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    val season by viewModel.currentSeason.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var quickText by remember { mutableStateOf("") }
    var quickPinned by remember { mutableStateOf(false) }
    // 1.4.0：点"看详细介绍"就查这个季节的 WIKI 页面（存页面名，空表示没开）
    var seasonDetail by remember { mutableStateOf<String?>(null) }

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
            // 1.2 改进第 2 条：改成平面直角坐标系的样子，四格颜色深浅不同
            SummaryCard("紧急 / 重要四象限", onClick = { onOpenTab(TabDestination.TODAY) }) {
                QuadrantChart(groups = quadrants)
            }

            SummaryCard("学习安排", onClick = { onOpenTab(TabDestination.STUDY) }) {
                if (subjectCount == 0) {
                    Text(
                        text = "还没有学科，点这里去建一个 →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "$subjectCount 个学科 · 没做完 $openTaskCount 件 · 已划掉 $doneTaskCount 件",
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
            // 季节的结束时间可能是"官方还没公布"（存成空串），所以统一走 seasonStatus。
            // 教训：这里以前直接拿结束时间算天数，空串解析会抛异常 → 首页一打开就闪退。
            val seasonState = SkyCountdown.seasonStatus(season, dateKey)
            SummaryCard(
                title = if (seasonState.notStarted) "下个季节" else "当季剩余",
                onClick = { onOpenTab(TabDestination.PLAY) },
            ) {
                val current = seasonState.season
                if (current == null) {
                    Text(
                        text = "还没获取到季节信息 →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = buildString {
                            append(current.name)
                            when {
                                seasonState.notStarted ->
                                    append(" · 还有 ").append(seasonState.daysUntilStart).append(" 天开始")
                                seasonState.daysLeft != null ->
                                    append(" · 还剩 ").append(seasonState.daysLeft).append(" 天")
                                else -> append(" · 结束时间官方未公布")
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    // 卡片本身是"跳到娱乐页"的快捷方式，所以介绍另外给一个小按钮，
                    // 免得把原来那个跳转抢掉了
                    TextButton(
                        onClick = { seasonDetail = current.name },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) { Text("看详细介绍 →") }
                }
            }

            seasonDetail?.let { name ->
                WikiDetailDialog(
                    title = name,
                    subtitle = seasonState.season
                        ?.let { it.startDate + " ～ " + seasonState.endLabel }
                        .orEmpty(),
                    onDismiss = { seasonDetail = null },
                )
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

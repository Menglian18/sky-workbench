package io.github.menglian18.skyworkbench.ui.sky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.menglian18.skyworkbench.sky.CodexEntry
import io.github.menglian18.skyworkbench.ui.common.EmptyState
import io.github.menglian18.skyworkbench.ui.common.RemoteImage
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import io.github.menglian18.skyworkbench.sky.CodexCategory
import io.github.menglian18.skyworkbench.sky.EmoteGrouping

/**
 * 图鉴（1.1 改进 · C3）。
 *
 * 按**游戏内分类**分组（斗篷 / 发型 / 面具…），做成**图片网格**；
 * 点某一件看它要多少、以及"怎么来的"。
 *
 * 图片是**联网现取**的（assets 里只有网址），断网时显示方块占位，不会崩。
 */
@Composable
fun CodexGridScreen(
    modifier: Modifier = Modifier,
    viewModel: CodexViewModel = viewModel(),
) {
    val groups by viewModel.groups.collectAsState()
    val search by viewModel.searchText.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val failed by viewModel.loadFailed.collectAsState()
    val total by viewModel.totalCount.collectAsState()

    var categoryIndex by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<CodexEntry, List<CodexEntry>>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.weight(1f),
                label = { Text("搜物品名") },
                singleLine = true,
            )
            Text(
                text = "共 $total 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            loading -> EmptyState(message = "正在读图鉴数据…")
            failed || groups.isEmpty() -> EmptyState(
                message = if (search.isBlank()) {
                    "图鉴数据读不出来。请重装一次 App（数据是打包在里面的）。"
                } else {
                    "没搜到「$search」。换个词试试。"
                },
            )
            else -> {
                val safeIndex = categoryIndex.coerceIn(0, groups.lastIndex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEachIndexed { index, (category, items) ->
                        FilterChip(
                            selected = index == safeIndex,
                            onClick = { categoryIndex = index },
                            label = { Text("${category.label} ${items.size}") },
                        )
                    }
                }

                val (category, entries) = groups[safeIndex]
                // 1.2 第 5 条：表情动作同款不同等级合成一个格子，点开下滑看各级
                val emoteGroups = if (category == CodexCategory.EMOTE) {
                    // 1.2.1 第 3 条：必须用能区分表情的名字来分组（显示名带的是通用部位名，
                    // 拿它分组会把所有表情并成一个），所以这里用英文名，等级后缀由分组逻辑去掉
                    EmoteGrouping.group(entries) { it.name.ifBlank { it.displayName } }
                } else {
                    emptyList()
                }
                val shown: List<Pair<CodexEntry, List<CodexEntry>>> = if (emoteGroups.isEmpty()) {
                    entries.map { it to listOf(it) }
                } else {
                    emoteGroups.map { (_, levels) -> levels.first() to levels }
                }

                Text(
                    text = "${category.label} · ${entries.size} 件" +
                        if (emoteGroups.isNotEmpty()) "（合起来 ${emoteGroups.size} 个表情）" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.first.guid }) { pick ->
                        val entry = pick.first
                        val levels = pick.second
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier.clickable { selected = entry to levels },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RemoteImage(
                                    url = entry.icon,
                                    contentDescription = entry.displayName,
                                    imageSize = 56.dp,
                                )
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                if (levels.size > 1) {
                                    Text(
                                        text = "${levels.size} 级 ▾",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { pick ->
        CodexDetailDialog(
            entry = pick.first,
            levels = pick.second,
            howTo = viewModel.howToGet(pick.first),
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun CodexDetailDialog(
    entry: CodexEntry,
    levels: List<CodexEntry>,
    howTo: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.displayName) },
        text = {
            Column(
                // 1.2 第 5 条：表情点开后可以往下滑看各级
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RemoteImage(
                    url = entry.icon,
                    contentDescription = entry.displayName,
                    imageSize = 96.dp,
                )
                if (levels.size > 1) {
                    levels.forEachIndexed { index, level ->
                        Text(
                            text = "第 ${index + 1} 级：" + level.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "　要花：" + buildString {
                                if (level.candles + level.hearts + level.ascended == 0) {
                                    append("不花白蜡（季节或活动货币）")
                                } else {
                                    append("白蜡 ${level.candles}")
                                    if (level.hearts > 0) append(" · 爱心 ${level.hearts}")
                                    if (level.ascended > 0) append(" · 升华 ${level.ascended}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                }
                Text(
                    text = "分类：" + entry.category.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "要花：" + buildString {
                        if (entry.candles + entry.hearts + entry.ascended == 0) {
                            append("不花白蜡（季节或活动货币）")
                        } else {
                            append("白蜡 ${entry.candles}")
                            if (entry.hearts > 0) append(" · 爱心 ${entry.hearts}")
                            if (entry.ascended > 0) append(" · 升华 ${entry.ascended}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "怎么来的：" + (howTo ?: "这件东西不在任何兑换树里（可能是活动或商城物品）"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

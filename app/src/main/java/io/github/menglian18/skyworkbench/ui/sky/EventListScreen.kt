package io.github.menglian18.skyworkbench.ui.sky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.menglian18.skyworkbench.data.db.SkyEventEntity
import io.github.menglian18.skyworkbench.sky.SkyCountdown
import io.github.menglian18.skyworkbench.sky.SeasonParser
import io.github.menglian18.skyworkbench.sky.SkyEventGroup
import io.github.menglian18.skyworkbench.sky.SkyViewModel
import io.github.menglian18.skyworkbench.ui.common.EmptyState

/**
 * 活动查看（1.3.0 新增，1.4.0 改过）。
 *
 * 数据来自 WIKI 的「分类:节日活动」—— 60 多个活动页，每个都带开启/结束时间，所以能自动抓。
 * 只分两堆：**进行中 / 即将开始**。
 *
 * 1.4.0 的两条用户要求：
 *  - 「活动里已经结束的就不要了」：以前还留一块「已结束」，现在整个不显示了；
 *  - 「进行中的能点击查看详细介绍」：点卡片弹出这个活动的 WIKI 页面正文（见 [WikiDetailDialog]）。
 *    每一条都是从那个分类里抓来的，所以一定有对应的页面，点了不会扑空。
 *
 * 结束时间官方还没公布的活动（WIKI 上写占位值 2099 年那种）显示成「结束时间未公布」，
 * 而不是编一个天数出来。
 */
@Composable
fun EventListScreen(
    modifier: Modifier = Modifier,
    viewModel: SkyViewModel = viewModel(),
) {
    val events by viewModel.events.collectAsState()
    val todayKey by viewModel.todayKey.collectAsState()
    val status by viewModel.fetchStatus.collectAsState()
    val buckets = SkyEventGroup.split(events, todayKey)
    var detail by remember { mutableStateOf<SkyEventEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append("更新于 ").append(formatTime(status.lastFetchedAt))
                            if (status.loading) append(" · 正在更新…")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "手动更新")
                    }
                }
            }

            status.message?.takeIf { status.failed }?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!buckets.hasShown) {
                item {
                    EmptyState(
                        message = "现在没有进行中或即将开始的活动。点右上角刷新去拿一次；\n" +
                            "抓不到也不影响你自己记的东西。",
                        actionLabel = "现在更新",
                        onAction = { viewModel.refresh(force = true) },
                    )
                }
            }

            if (buckets.ongoing.isNotEmpty()) {
                item { EventHeader("进行中") }
                items(buckets.ongoing, key = { it.id }) {
                    EventCard(it, todayKey, highlighted = true, onClick = { detail = it })
                }
            }

            if (buckets.upcoming.isNotEmpty()) {
                item { EventHeader("即将开始") }
                items(buckets.upcoming, key = { it.id }) {
                    EventCard(it, todayKey, highlighted = false, onClick = { detail = it })
                }
            }
        }

        detail?.let { event ->
            WikiDetailDialog(
                title = event.name,
                subtitle = event.startDate + " ～ " +
                    SeasonParser.effectiveEnd(event.endDate).ifBlank { "结束时间未公布" },
                onDismiss = { detail = null },
            )
        }
    }
}

@Composable
private fun EventHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EventCard(
    event: SkyEventEntity,
    todayKey: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = event.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = event.startDate + " ～ " +
                    SeasonParser.effectiveEnd(event.endDate).ifBlank { "结束时间未公布" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusLine(event, todayKey),
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "点一下看详细介绍 →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** 一句话说清"还剩几天 / 还有几天开始 / 已经结束几天" */
private fun statusLine(event: SkyEventEntity, todayKey: String): String = when {
    event.startDate > todayKey ->
        "还有 " + SkyCountdown.daysLeft(event.startDate, todayKey) + " 天开始"
    event.endDate.isBlank() -> "进行中 · 结束时间官方未公布"
    event.endDate >= todayKey ->
        "进行中 · 还剩 " + SkyCountdown.daysLeft(event.endDate, todayKey) + " 天"
    else ->
        "已结束 " + (-SkyCountdown.daysLeft(event.endDate, todayKey)) + " 天"
}

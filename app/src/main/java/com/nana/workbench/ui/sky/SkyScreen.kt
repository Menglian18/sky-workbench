package com.nana.workbench.ui.sky

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nana.workbench.data.db.SkyAccountEntity
import com.nana.workbench.data.db.SkyTravelingSpiritEntity
import com.nana.workbench.data.db.SkyWishEntity
import com.nana.workbench.sky.CodexItem
import com.nana.workbench.sky.SkyCountdown
import com.nana.workbench.sky.SkyViewModel
import com.nana.workbench.ui.common.EmptyState
import com.nana.workbench.ui.tools.ToolsSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.zIndex
import com.nana.workbench.today.moveItem
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size

@Composable
fun SkyScreen(
    modifier: Modifier = Modifier,
    viewModel: SkyViewModel = viewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("复刻季节") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("图鉴") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("账号") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("小工具") })
        }
        when (tab) {
            0 -> TravelingSection(viewModel)
            1 -> CodexGridScreen()
            2 -> AccountSection(viewModel)
            else -> ToolsSection()
        }
    }
}

private fun formatTime(millis: Long?): String {
    if (millis == null) return "还没更新过"
    val format = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
    return format.format(Date(millis))
}

/**
 * 物品花费的显示。
 *
 * 注意：光遇的花费分好几种货币。本 App 的"心愿单算账"只关心**能攒、能规划**的三样
 * （白蜡 / 爱心 / 升华蜡烛）。季节物品花的是季节蜡烛、活动物品花的是活动货币，
 * 那两种攒不了、也没法提前规划，所以这里**如实说明**，而不是显示成"白蜡 0"让人以为坏了。
 */
private fun costText(candles: Int, hearts: Int, ascended: Int): String {
    if (candles + hearts + ascended == 0) {
        return "花的是季节蜡烛或活动货币，不占白蜡预算"
    }
    return buildString {
        append("白蜡 ").append(candles)
        if (hearts > 0) append(" · 爱心 ").append(hearts)
        if (ascended > 0) append(" · 升华 ").append(ascended)
    }
}

@Composable
private fun TravelingSection(
    viewModel: SkyViewModel,
    codex: CodexViewModel = viewModel(),
) {
    var treeSpiritName by remember { mutableStateOf<String?>(null) }
    val spirits by viewModel.travelingSpirits.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    val season by viewModel.currentSeason.collectAsState()
    val status by viewModel.fetchStatus.collectAsState()
    val todayKey by viewModel.todayKey.collectAsState()

    // 进页面自动尝试更新一次（ViewModel 内部有一小时限速）
    LaunchedEffect(Unit) { viewModel.refresh(force = false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

        status.message?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (status.failed) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (status.failed) {
                        TextButton(onClick = { viewModel.refresh(force = true) }) { Text("重试") }
                    }
                }
            }
        }

        val ongoing = countdown.ongoing
        val next = countdown.next
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    ongoing != null -> {
                        Text("正在复刻", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = if (countdown.ongoingIsCollective) {
                                "集体复刻 · ${countdown.ongoingCount} 位先祖"
                            } else {
                                ongoing.spiritName
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (countdown.ongoingIsCollective) {
                            Text(
                                text = spirits
                                    .filter { it.startDate == ongoing.startDate }
                                    .joinToString("、") { it.spiritName },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            text = "还剩 ${SkyCountdown.daysLeft(ongoing.endDate, todayKey)} 天",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { treeSpiritName = ongoing.spiritName }) {
                            Text("看兑换树 →")
                        }
                        Text(
                            text = "${ongoing.startDate} ～ ${ongoing.endDate} · ${ongoing.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (ongoing.items.isNotBlank()) {
                            Text(
                                text = "可换：${ongoing.items}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    next != null -> {
                        Text("下次复刻", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = if (countdown.nextIsCollective) {
                                "集体复刻 · ${countdown.nextCount} 位先祖"
                            } else {
                                next.spiritName
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (countdown.nextIsCollective) {
                            Text(
                                text = spirits
                                    .filter { it.startDate == next.startDate }
                                    .joinToString("、") { it.spiritName },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            text = "还有 ${SkyCountdown.daysLeft(next.startDate, todayKey)} 天",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { treeSpiritName = next.spiritName }) {
                            Text("看兑换树 →")
                        }
                    }
                    else -> Text(
                        text = "还没有复刻数据。点右上角的刷新去拿一次；拿不到也不影响你自己记的东西。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("当季", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                if (season == null) {
                    Text(
                        text = "还没获取到季节信息 →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "${season!!.name} · 还剩 ${SkyCountdown.daysLeft(season!!.endDate, todayKey)} 天",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${season!!.startDate} ～ ${season!!.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        treeSpiritName?.let { name ->
            SpiritTreeDialog(
                spiritName = name,
                tree = codex.treeForName(name),
                onDismiss = { treeSpiritName = null },
            )
        }

        // 1.1 改进 · C 模块：历史复刻列表不要了（只留"正在复刻 / 下次复刻"）
    }
}

@Composable
private fun CodexSection(viewModel: SkyViewModel) {
    val query by viewModel.codexQuery.collectAsState()
    val results by viewModel.codexResults.collectAsState()
    val spirit by viewModel.codexSpirit.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setCodexQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("搜物品或先祖，例如 斗篷") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        spirit?.let { selected ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selected.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.setCodexSpirit(null) }) { Text("取消筛选") }
                    }
                    Text(
                        text = "全收要 白蜡 ${selected.totalCandles} · 爱心 ${selected.totalHearts}" +
                            if (selected.totalAscended > 0) " · 升华 ${selected.totalAscended}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!selected.hasChineseName) {
                        Text(
                            text = "这位先祖暂时没有中文名（WIKI 未收录），显示的是英文名",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (results.isEmpty()) {
            EmptyState(
                message = if (query.isBlank()) {
                    "输入物品或先祖的名字找找看，比如「斗篷」。图鉴是打包在 App 里的，不联网也能查。"
                } else {
                    "没找到包含「$query」的物品。换个词试试，比如只搜「斗篷」。"
                },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.guid }) { item ->
                    CodexRow(item = item, onAdd = { viewModel.addWishFromCodex(item) }, onSpirit = {
                        viewModel.setCodexSpirit(item.spiritId)
                        viewModel.setCodexQuery("")
                    })
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                }
            }
        }
    }
}

@Composable
private fun CodexRow(item: CodexItem, onAdd: () -> Unit, onSpirit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.nameZh.ifBlank { item.nameEn },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = buildString {
                    append(item.spiritNameZh.ifBlank { item.spiritNameEn })
                    if (item.seasonZh.isNotBlank()) append(" · ").append(item.seasonZh)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onSpirit),
            )
            Text(
                text = costText(item.candles, item.hearts, item.ascended),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSection(viewModel: SkyViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SkyAccountEntity?>(null) }
    var revealed by remember { mutableStateOf<Long?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "加一个号") }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "按住左边的 \u2261 能调顺序；点一行可以改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u26a0\ufe0f 密码是明文存在这台手机里的，没有加密。手机被别人拿到就能看到，想清楚再记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (accounts.isEmpty()) {
                EmptyState(message = "还没有加号。点右下角 + 加一个。", actionLabel = "加一个号") { showAdd = true }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = draggingId == null,
                ) {
                    itemsIndexed(accounts, key = { _, account -> account.id }) { index, account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (draggingId == account.id) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (draggingId == account.id) dragOffset else 0f
                                }
                                .onSizeChanged { if (it.height > 0) rowHeight = it.height.toFloat() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .pointerInput(account.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingId = account.id
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                            },
                                            onDragEnd = {
                                                val step = if (rowHeight > 0f) rowHeight else 1f
                                                val target = (index + (dragOffset / step).roundToInt())
                                                    .coerceIn(0, accounts.lastIndex)
                                                if (target != index) {
                                                    viewModel.reorderAccounts(accounts.moveItem(index, target))
                                                }
                                                draggingId = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffset = 0f
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "\u2261",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { editing = account },
                            ) {
                                Text(
                                    text = account.nickname,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = listOfNotNull(
                                        account.loginMethod.takeIf { it.isNotBlank() },
                                        account.server.takeIf { it.isNotBlank() },
                                        account.note.takeIf { it.isNotBlank() },
                                    ).joinToString(" \u00b7 "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (account.password.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "密码：" + if (revealed == account.id) account.password else "\u2022\u2022\u2022\u2022\u2022\u2022",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(onClick = {
                                            revealed = if (revealed == account.id) null else account.id
                                        }) { Text(if (revealed == account.id) "隐藏" else "显示") }
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.deleteAccount(account) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AccountDialog(
            account = null,
            onDismiss = { showAdd = false },
            onSave = { nickname, login, server, note, password ->
                viewModel.saveAccount(null, nickname, login, server, note, password)
                showAdd = false
            },
        )
    }

    editing?.let { account ->
        AccountDialog(
            account = account,
            onDismiss = { editing = null },
            onSave = { nickname, login, server, note, password ->
                viewModel.saveAccount(account, nickname, login, server, note, password)
                editing = null
            },
        )
    }
}

@Composable
private fun AccountDialog(
    account: SkyAccountEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var nickname by remember { mutableStateOf(account?.nickname ?: "") }
    var login by remember { mutableStateOf(account?.loginMethod ?: "") }
    var server by remember { mutableStateOf(account?.server ?: "国服") }
    var note by remember { mutableStateOf(account?.note ?: "") }
    var password by remember { mutableStateOf(account?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "加一个号" else "改这个号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("备注名（比如 小号1）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("登录方式（华为 / 网易 / 微信…）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("区服") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "隐藏" else "显示")
                        }
                    },
                )
                Text(
                    text = "\u26a0\ufe0f 明文存在本机、没有加密。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nickname, login, server, note, password) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

package io.github.menglian18.skyworkbench.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.menglian18.skyworkbench.data.backup.BackupCodec
import io.github.menglian18.skyworkbench.data.backup.formatSize
import io.github.menglian18.skyworkbench.ui.theme.FontScale
import io.github.menglian18.skyworkbench.ui.theme.SkyPalette
import io.github.menglian18.skyworkbench.ui.theme.SkyPalettes
import io.github.menglian18.skyworkbench.ui.theme.ThemeViewModel
import io.github.menglian18.skyworkbench.ui.theme.composeAccent
import io.github.menglian18.skyworkbench.ui.theme.composeBackground
import io.github.menglian18.skyworkbench.ui.theme.composeOnBackground
import io.github.menglian18.skyworkbench.ui.theme.composeOnSurfaceVariant
import io.github.menglian18.skyworkbench.ui.theme.composePrimary
import io.github.menglian18.skyworkbench.ui.theme.composeSurface
import io.github.menglian18.skyworkbench.util.DateKeys
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel = viewModel(),
    dataViewModel: SettingsViewModel = viewModel(),
) {
    val themeState by themeViewModel.state.collectAsState()
    val busy by dataViewModel.busy.collectAsState()
    val message by dataViewModel.message.collectAsState()
    val usage by dataViewModel.usage.collectAsState()
    val lastExportAt by dataViewModel.lastExportAt.collectAsState()
    val cleanPreview by dataViewModel.cleanPreview.collectAsState()
    val canUndoImport by dataViewModel.canUndoImport.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showClearAll by remember { mutableStateOf(false) }
    var showUndoImport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { dataViewModel.refresh() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            dataViewModel.consumeMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(dataViewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(dataViewModel::import) }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 数据备份（换手机用） ──────────────────────────────────────────
            SectionTitle("数据备份（换手机用）")
            Text(
                text = "数据只存在这台手机上。换手机时：旧手机导出 → 把文件发到新手机 → 新手机导入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "上次导出：" + formatTime(lastExportAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        exportLauncher.launch(BackupCodec.suggestedFileName(DateKeys.todayKey()))
                    },
                    enabled = !busy,
                ) { Text("导出数据文件") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !busy,
                ) { Text("导入数据文件") }
            }
            if (canUndoImport) {
                OutlinedButton(onClick = { showUndoImport = true }, enabled = !busy) {
                    Text("撤销这次导入（还原到导入前）")
                }
            }
            Text(
                text = "导入会替换手机上现在的数据；导入前会自动存一份，可点「撤销这次导入」还原。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 数据管理 ──────────────────────────────────────────────────────
            SectionTitle("数据管理")
            Text(
                text = "占用空间：" + usage.describe() + "（合计 " + formatSize(usage.totalBytes) + "）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = dataViewModel::previewClean, enabled = !busy) {
                    Text("清理缓存与过期数据")
                }
                OutlinedButton(onClick = { showClearAll = true }, enabled = !busy) {
                    Text("全部重置（清空所有数据）")
                }
            }
            Text(
                text = "只清缓存与过期数据，你自己记的东西不动。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 主题与外观 ────────────────────────────────────────────────────
            SectionTitle("主题与外观")
            SkyPalettes.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { palette ->
                        ThemeSwatch(
                            palette = palette,
                            selected = palette.id == themeState.palette.id,
                            onClick = { themeViewModel.selectTheme(palette.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Text(
                text = "字体大小",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontScale.entries.forEach { scale ->
                    FilterChip(
                        selected = scale.id == themeState.fontScale.id,
                        onClick = { themeViewModel.selectFontScale(scale.id) },
                        label = { Text(scale.displayName) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "跟随系统深色模式",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = themeState.followSystemDark,
                    onCheckedChange = { themeViewModel.setFollowSystemDark(it) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 关于 ──────────────────────────────────────────────────────────
            SectionTitle("关于")
            Text(
                text = "光遇工作台 · 版本 " + io.github.menglian18.skyworkbench.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "隐私：数据只存在这台手机上，不会上传到任何地方。唯一会联网的时候，是去抓光遇的复刻与季节数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "数据来源：光遇复刻/季节数据来自 B站光遇WIKI（CC BY-NC-SA 4.0，署名、非商用）；" +
                    "物品与价格数据来自 SkyGame-Data（MIT）。游戏素材版权归 thatgamecompany / 网易。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "简版使用说明",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            listOf(
                "首页顶上有个输入框，随手记一句，回车就存进备忘。",
                "「今日」里没做完的事，第二天会自动跟过来，并标上顺延了几天。",
                "「学习」里可以一次粘贴一整份清单导入（一行一张，正面 | 背面）。",
                "「娱乐 · 图鉴」是打包在 App 里的，不联网也能查物品要多少蜡烛。",
                "换手机：本页最上面的「导出数据文件」→ 发到新手机 →「导入数据文件」。",
            ).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("·", color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 一键清理：先告诉用户能清多少，确认后才清
    cleanPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = dataViewModel::cancelCleanPreview,
            title = { Text("一键清理") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("可以清出约 ${formatSize(preview.cacheBytes)}。")
                    Text("还会清掉早就过期的复刻 ${preview.spirits} 条、季节 ${preview.seasons} 条。**正在复刻的不会动**，需要时重新联网获取即可。")
                    Text(
                        text = "你自己记的计划、学习卡、备忘、数字表格一条都不会动。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = dataViewModel::doClean) { Text("确认清理") } },
            dismissButton = { TextButton(onClick = dataViewModel::cancelCleanPreview) { Text("取消") } },
        )
    }

    if (showUndoImport) {
        AlertDialog(
            onDismissRequest = { showUndoImport = false },
            title = { Text("撤销这次导入") },
            text = {
                Text("会把手机数据还原成「上一次导入之前」的样子（那之后你新加的东西也会被换掉）。确定吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dataViewModel.undoImport()
                        showUndoImport = false
                    },
                ) { Text("确认还原") }
            },
            dismissButton = { TextButton(onClick = { showUndoImport = false }) { Text("取消") } },
        )
    }

    if (showClearAll) {
        ClearAllDialog(
            onDismiss = { showClearAll = false },
            onConfirm = {
                dataViewModel.clearEverything()
                showClearAll = false
            },
        )
    }
}

@Composable
private fun ClearAllDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空全部数据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("这会删掉手机上所有数据（计划、学习卡、备忘、表格、光遇账号与心愿）。")
                Text(
                    text = "建议先「导出数据文件」存一份。确定要清空，请在下面打出「清空」两个字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("在这里输入：清空") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.trim() == "清空") { Text("确认清空") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun formatTime(millis: Long?): String {
    if (millis == null) return "还没导出过"
    return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))
}

@Composable
private fun ThemeSwatch(
    palette: SkyPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = palette.composeSurface()),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(palette.composeBackground()),
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(palette.composePrimary()),
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(palette.composeAccent()),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.composeOnBackground(),
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选中",
                        tint = palette.composePrimary(),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = if (palette.isDark) "深色" else "浅色",
                style = MaterialTheme.typography.labelSmall,
                color = palette.composeOnSurfaceVariant(),
            )
        }
    }
}

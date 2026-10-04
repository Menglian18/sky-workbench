package com.nana.workbench.ui.sky

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nana.workbench.sky.SpiritTree
import com.nana.workbench.sky.TreeItem
import com.nana.workbench.ui.common.RemoteImage

/**
 * 先祖兑换树（1.1 改进 · C2）。
 *
 * 点"正在复刻"的先祖就打开这里：一层一层往下，每层是这一层能换到的东西
 * （**图标 + 名称 + 要多少**）。图片联网现取，断网显示方块占位。
 *
 * 用"分层"而不是画连线，是因为手机屏幕窄：分层 + 横向滚动看得清、也点得准。
 */
@Composable
fun SpiritTreeDialog(
    spiritName: String,
    tree: SpiritTree?,
    /** 匹配到的先祖名；用来在顶部写明"这是谁的树"（1.2.2 第 1 条） */
    matchedName: String? = null,
    treeCount: Int = 0,
    candidates: List<Pair<String, String>> = emptyList(),
    onPick: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = spiritName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (matchedName != null && matchedName != spiritName) {
                    Text(
                        text = "这是「$matchedName」的兑换树",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                if (tree == null || tree.nodes.isEmpty()) {
                    Text(
                        // 1.2.1 第 5 条：把"数据载入情况"显示出来，一眼看出是数据问题还是名字没对上
                        text = "没自动对上这位先祖（App 里已载入 $treeCount 棵兑换树）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        // 1.2.3 第 1 条：不要"所有先祖"的列表，只需要知道这次没查到就行
                        text = "这位先祖的兑换树暂时没找到（多半是名字对不上）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = tree.totalText() + " · 共 ${tree.nodes.size} 件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "从上往下解锁：上一层换到了，才能换下一层。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    tree.levels().forEachIndexed { levelIndex, level ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                        Text(
                            text = "第 ${levelIndex + 1} 层（${level.size} 件）",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            level.forEach { node -> TreeNodeCard(node) }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("关上") }
                }
            }
        }
    }
}

@Composable
private fun TreeNodeCard(node: TreeItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RemoteImage(
                url = node.icon,
                contentDescription = node.displayName,
                imageSize = 48.dp,
            )
            Text(
                text = node.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = node.costText(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

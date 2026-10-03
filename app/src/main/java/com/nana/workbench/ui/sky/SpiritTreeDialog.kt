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

                if (tree == null || tree.nodes.isEmpty()) {
                    Text(
                        text = "这位先祖没有打包兑换树数据（可能是活动或商城物品）。",
                        style = MaterialTheme.typography.bodyMedium,
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

package io.github.menglian18.skyworkbench.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.menglian18.skyworkbench.data.db.TodoEntity
import io.github.menglian18.skyworkbench.today.Quadrant

/**
 * 首页的"紧急 / 重要四象限"（1.2 改进 · 第 2 条）。
 *
 * 按**平面直角坐标系**的样子摆：
 *   竖轴是"重不重要"（上=重要，下=不重要）
 *   横轴是"急不急"（右=紧急，左=不紧急）
 * 四个格子**颜色深浅不同**：越该先做，颜色越重。
 *
 * 用普通布局而不是画布，是为了文字位置稳定、不同屏幕都不跑版。
 */
@Composable
fun QuadrantChart(
    groups: Map<Quadrant, List<TodoEntity>>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shades = mapOf(
        // 越靠"马上做"，底色越重
        Quadrant.DO_FIRST to colors.error.copy(alpha = 0.34f),
        Quadrant.SCHEDULE to colors.primary.copy(alpha = 0.24f),
        Quadrant.DELEGATE to colors.secondary.copy(alpha = 0.16f),
        Quadrant.ELIMINATE to colors.onSurfaceVariant.copy(alpha = 0.08f),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "重要 ↑",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "不紧急",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "紧急",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }

        // 上半：重要的两格（左＝不紧急，右＝紧急）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QuadrantCell(Quadrant.SCHEDULE, groups, shades, Modifier.weight(1f))
            QuadrantCell(Quadrant.DO_FIRST, groups, shades, Modifier.weight(1f))
        }
        // 下半：不重要的两格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QuadrantCell(Quadrant.ELIMINATE, groups, shades, Modifier.weight(1f))
            QuadrantCell(Quadrant.DELEGATE, groups, shades, Modifier.weight(1f))
        }

        Text(
            text = "不重要 ↓",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuadrantCell(
    quadrant: Quadrant,
    groups: Map<Quadrant, List<TodoEntity>>,
    shades: Map<Quadrant, Color>,
    modifier: Modifier = Modifier,
) {
    val items = groups[quadrant].orEmpty()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(88.dp)
            .clip(shape)
            .background(shades[quadrant] ?: Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), shape)
            .padding(6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = quadrant.shortTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (items.isEmpty()) "—" else "${items.size} 件",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (items.isNotEmpty()) {
                Text(
                    text = items.take(2).joinToString("、") { it.title },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

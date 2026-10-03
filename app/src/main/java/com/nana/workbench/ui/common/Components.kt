package com.nana.workbench.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nana.workbench.memo.NumberPoint

/**
 * 空状态：一句人话 + 一个明确的开始按钮。
 * PRD 要求每个空页面都要说清"这里能干什么、怎么开始"，所以不允许出现纯空白页。
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * 删除后的"撤销"提示。
 *
 * 全 App 统一规则：**不弹"确定删除吗"**，删完在底部给 5 秒左右的撤销机会。
 */
@Composable
fun UndoSnackbarHandler(
    deletedKey: Any?,
    snackbarHostState: SnackbarHostState,
    message: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(deletedKey) {
        if (deletedKey != null) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤销",
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo() else onDismiss()
        }
    }
}

/**
 * 近 N 天的迷你趋势图。
 *
 * 自己用画布画，不引第三方图形库：只需要一条折线，少一个依赖就少一类风险。
 */
@Composable
fun MiniTrendChart(
    points: List<NumberPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        if (points.size < 2) return@Canvas

        val values = points.map { it.value }
        val minValue = values.minOrNull() ?: return@Canvas
        val maxValue = values.maxOrNull() ?: return@Canvas
        val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1).toFloat()
        val paddingY = 6f

        fun yOf(value: Double): Float {
            val ratio = ((value - minValue) / span).toFloat()
            return size.height - paddingY - ratio * (size.height - paddingY * 2)
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = stepX * index
            val y = yOf(point.value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        val lastX = stepX * (points.size - 1)
        val lastY = yOf(points.last().value)
        drawCircle(color = dotColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(lastX, lastY))
    }
}

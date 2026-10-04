package io.github.menglian18.skyworkbench.tools

/** 倒计时的显示格式。 */
object TimerFormat {

    /** 把剩余秒数格式化成 `分:秒`（超过 1 小时显示 `时:分:秒`）。 */
    fun remaining(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    /** 剩余比例（0 到 1），用于画圆环。 */
    fun progress(totalSeconds: Long, remainingSeconds: Long): Float {
        if (totalSeconds <= 0) return 0f
        return (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    }
}

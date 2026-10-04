package io.github.menglian18.skyworkbench.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private fun Long.toColor(): Color = Color(this)

/** 给界面（比如设置页的配色预览块）用的取色方法。 */
fun SkyPalette.composeBackground(): Color = background.toColor()
fun SkyPalette.composeSurface(): Color = surface.toColor()
fun SkyPalette.composePrimary(): Color = primary.toColor()
fun SkyPalette.composeAccent(): Color = accent.toColor()
fun SkyPalette.composeOnBackground(): Color = onBackground.toColor()
fun SkyPalette.composeOnSurfaceVariant(): Color = onSurfaceVariant.toColor()

private fun SkyPalette.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = primary.toColor(),
        onPrimary = onPrimary.toColor(),
        background = background.toColor(),
        onBackground = onBackground.toColor(),
        surface = surface.toColor(),
        onSurface = onBackground.toColor(),
        surfaceVariant = surface.toColor(),
        onSurfaceVariant = onSurfaceVariant.toColor(),
        secondary = accent.toColor(),
        onSecondary = onPrimary.toColor(),
        tertiary = accent.toColor(),
        outline = onSurfaceVariant.toColor(),
    )
} else {
    lightColorScheme(
        primary = primary.toColor(),
        onPrimary = onPrimary.toColor(),
        background = background.toColor(),
        onBackground = onBackground.toColor(),
        surface = surface.toColor(),
        onSurface = onBackground.toColor(),
        surfaceVariant = surface.toColor(),
        onSurfaceVariant = onSurfaceVariant.toColor(),
        secondary = accent.toColor(),
        onSecondary = onPrimary.toColor(),
        tertiary = accent.toColor(),
        outline = onSurfaceVariant.toColor(),
    )
}

/**
 * 全局外观：一套配色 + 字体档位。
 *
 * [followSystemDark] 打开时，会用系统是否深色来决定"明/暗"，但仍保留所选主题的色相
 * （做法：系统深色时优先用所选主题；系统浅色而所选主题是深色时，仍按所选主题走——第一版不强行反色）。
 */
@Composable
fun SkyTheme(
    palette: SkyPalette,
    fontScale: FontScale,
    followSystemDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effective = if (followSystemDark && !isSystemInDarkTheme() && palette.isDark) {
        // 跟随系统浅色时，把深色主题换成同样"性格"的浅色主题（星空/像素 → 清爽白，深夜黑 → 清爽白）
        SkyPalettes.clean
    } else {
        palette
    }

    val density = LocalDensity.current
    val scaled = Density(
        density = density.density,
        fontScale = density.fontScale * fontScale.factor,
    )

    MaterialTheme(colorScheme = effective.toColorScheme()) {
        CompositionLocalProvider(LocalDensity provides scaled) {
            content()
        }
    }
}

package com.nana.workbench.ui.theme

/**
 * 一套主题的配色。
 *
 * 颜色用 Long（0xFFRRGGBB）存，不直接用 Compose 的 Color —— 这样"六套主题是否齐全、颜色是否重复"
 * 这类检查可以在电脑上用普通单元测试跑，不需要手机。
 */
data class NanaPalette(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val background: Long,
    val surface: Long,
    val primary: Long,
    val accent: Long,
    val onBackground: Long,
    val onSurfaceVariant: Long,
    val onPrimary: Long,
)

/** 六套预设主题（取自《开发计划》§5.1 的配色表）。 */
object NanaPalettes {

    val midnight = NanaPalette(
        id = "midnight", displayName = "深夜黑", isDark = true,
        background = 0xFF0F1115, surface = 0xFF171A21, primary = 0xFF7AA2F7,
        accent = 0xFFBB9AF7, onBackground = 0xFFE6E8EE, onSurfaceVariant = 0xFF9AA3B2,
        onPrimary = 0xFF0B0E14,
    )

    val clean = NanaPalette(
        id = "clean", displayName = "清爽白", isDark = false,
        background = 0xFFFFFFFF, surface = 0xFFF5F7FA, primary = 0xFF2F6FED,
        accent = 0xFF00B4A6, onBackground = 0xFF14181F, onSurfaceVariant = 0xFF6B7280,
        onPrimary = 0xFFFFFFFF,
    )

    val green = NanaPalette(
        id = "green", displayName = "护眼绿", isDark = false,
        background = 0xFFF2F7F1, surface = 0xFFFFFFFF, primary = 0xFF3D8B5F,
        accent = 0xFFA3C9A8, onBackground = 0xFF1E2A22, onSurfaceVariant = 0xFF66756B,
        onPrimary = 0xFFFFFFFF,
    )

    val pink = NanaPalette(
        id = "pink", displayName = "粉紫", isDark = false,
        background = 0xFFFDF6FA, surface = 0xFFFFFFFF, primary = 0xFFC2569B,
        accent = 0xFF8B7BD8, onBackground = 0xFF2A1F2A, onSurfaceVariant = 0xFF7A6B78,
        onPrimary = 0xFFFFFFFF,
    )

    val sky = NanaPalette(
        id = "sky", displayName = "光遇星空", isDark = true,
        background = 0xFF0B1A2E, surface = 0xFF122842, primary = 0xFFF2C879,
        accent = 0xFF6FA8DC, onBackground = 0xFFEAF2FA, onSurfaceVariant = 0xFF9FB6CE,
        onPrimary = 0xFF2A1E05,
    )

    val pixel = NanaPalette(
        id = "pixel", displayName = "像素复古", isDark = true,
        background = 0xFF1B1B2F, surface = 0xFF26264A, primary = 0xFFFF5D8F,
        accent = 0xFF4ED8A0, onBackground = 0xFFF0F0F5, onSurfaceVariant = 0xFF9A9AC0,
        onPrimary = 0xFF1B1B2F,
    )

    /** 顺序就是设置页里显示的顺序。 */
    val all: List<NanaPalette> = listOf(midnight, clean, green, pink, sky, pixel)

    /** 第一版的默认主题。 */
    val default: NanaPalette = midnight

    fun byId(id: String?): NanaPalette = all.firstOrNull { it.id == id } ?: default
}

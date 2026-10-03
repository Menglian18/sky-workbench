package com.nana.workbench.ui.theme

/**
 * 字体大小三档（见《开发计划》§5.2）。
 * 用"整体放大"实现，配合自适应高度的控件，避免放大后文字被挤掉。
 */
enum class FontScale(val id: String, val displayName: String, val factor: Float) {
    STANDARD("standard", "标准", 1.0f),
    LARGE("large", "大", 1.15f),
    XLARGE("xlarge", "特大", 1.3f);

    companion object {
        val default: FontScale = STANDARD

        fun byId(id: String?): FontScale = entries.firstOrNull { it.id == id } ?: default
    }
}

package com.nana.workbench.ui.nav

/**
 * 底部导航的五个格子。这是 PRD §2.1 定下的导航契约，专门写成不含界面代码的纯数据，
 * 好在电脑上用普通单元测试检查（不需要手机）。
 */
enum class TabDestination(val route: String, val label: String) {
    HOME("home", "首页"),
    TODAY("today", "今日"),
    PLAY("play", "娱乐"),
    STUDY("study", "学习"),
    MEMO("memo", "备忘");

    companion object {
        val default: TabDestination = HOME

        fun fromRoute(route: String?): TabDestination? = entries.firstOrNull { it.route == route }
    }
}

/** 全部页面路径。设置不是格子，而是首页右上角齿轮进入的二级页面。 */
object Routes {
    const val SETTINGS = "settings"

    val tabs: List<TabDestination> = TabDestination.entries

    val all: List<String> = tabs.map { it.route } + SETTINGS
}

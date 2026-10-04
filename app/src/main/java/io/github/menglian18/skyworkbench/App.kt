package io.github.menglian18.skyworkbench

import android.app.Application
import io.github.menglian18.skyworkbench.data.db.SkyDatabase
import io.github.menglian18.skyworkbench.data.prefs.SettingsStore
import io.github.menglian18.skyworkbench.today.TodoRolloverRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App 的入口对象。
 *
 * 这里充当一个极简的"全局对象持有处"（不用依赖注入框架，减少依赖与出错面）。
 */
class App : Application() {

    /** 手机本地的数据库：唯一的数据存放地。 */
    val database: SkyDatabase by lazy { SkyDatabase.get(this) }

    /** 界面偏好（主题、字体）。 */
    val settings: SettingsStore by lazy { SettingsStore(this) }

    /** App 级别的后台范围（目前只用来做启动时的顺延结算）。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 一启动就把"昨天没做完、又不是今天"的事挪到今天。
        // 放在这里（而不是只放在"今日计划"页面里）是因为：用户很可能先打开首页，
        // 首页的待办摘要也必须是今天的，不能等他点进"今日"才结算。
        appScope.launch {
            runCatching { TodoRolloverRunner.run(database.todoDao()) }
        }
    }

    companion object {
        lateinit var instance: App
            private set

        fun from(context: android.content.Context): App = context.applicationContext as App
    }
}

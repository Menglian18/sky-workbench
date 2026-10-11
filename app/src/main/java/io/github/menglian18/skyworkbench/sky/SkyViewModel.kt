package io.github.menglian18.skyworkbench.sky

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.menglian18.skyworkbench.App
import io.github.menglian18.skyworkbench.data.db.SkyAccountEntity
import io.github.menglian18.skyworkbench.data.db.SkyHoldingEntity
import io.github.menglian18.skyworkbench.data.db.SkyEventEntity
import io.github.menglian18.skyworkbench.data.db.SkySeasonEntity
import io.github.menglian18.skyworkbench.data.db.SkyTravelingSpiritEntity
import io.github.menglian18.skyworkbench.data.db.SkyWishEntity
import io.github.menglian18.skyworkbench.util.DateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 抓取状态，给界面显示"更新于…/抓取失败，显示的是上次数据"。 */
data class SkyFetchStatus(
    val loading: Boolean = false,
    val lastFetchedAt: Long? = null,
    val message: String? = null,
    val failed: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SkyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = App.from(application)
    private val dao = app.database.skyDao()
    private val stateDao = app.database.appStateDao()
    private val codex = CodexRepository(application)
    private val client = SkyFetchClient()

    /**
     * 保存一个光遇账号（1.1 改进 · C5：带密码）。
     * [existing] 为空表示新增，否则是修改。
     */
    fun saveAccount(
        existing: SkyAccountEntity?,
        nickname: String,
        loginMethod: String,
        server: String,
        note: String,
        password: String,
    ) {
        val name = nickname.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val base = existing ?: SkyAccountEntity(nickname = name)
            val entity = base.copy(
                nickname = name,
                loginMethod = loginMethod.trim(),
                server = server.trim().ifEmpty { "国服" },
                note = note.trim(),
                password = password,
            )
            if (existing == null) dao.insertAccount(entity) else dao.updateAccount(entity)
        }
    }

    /** 拖动排序（1.1 改进 · C5）：按传进来的新顺序把 sortOrder 重排一遍 */
    fun reorderAccounts(ordered: List<SkyAccountEntity>) {
        viewModelScope.launch {
            ordered.forEachIndexed { index, account ->
                if (account.sortOrder != index + 1) {
                    dao.updateAccount(account.copy(sortOrder = index + 1))
                }
            }
        }
    }

    private val _todayKey = MutableStateFlow(DateKeys.todayKey())
    val todayKey: StateFlow<String> = _todayKey.asStateFlow()

    // ── 复刻与季节 ────────────────────────────────────────────────────────────

    val travelingSpirits: StateFlow<List<SkyTravelingSpiritEntity>> = dao.watchTravelingSpirits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val seasons: StateFlow<List<SkySeasonEntity>> = dao.watchSeasons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 节日活动（1.3.0 新增，抓 WIKI 的「分类:节日活动」） */
    val events: StateFlow<List<SkyEventEntity>> = dao.watchEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val countdown: StateFlow<SkyCountdownPicks> =
        combine(travelingSpirits, todayKey) { list, today -> SkyCountdown.pick(list, today) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkyCountdownPicks(null, null))

    val currentSeason: StateFlow<SkySeasonEntity?> =
        combine(seasons, todayKey) { list, today -> SkyCountdown.currentSeason(list, today) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _fetchStatus = MutableStateFlow(SkyFetchStatus())
    val fetchStatus: StateFlow<SkyFetchStatus> = _fetchStatus.asStateFlow()

    // ── 离线图鉴 ──────────────────────────────────────────────────────────────

    private val _codexQuery = MutableStateFlow("")
    val codexQuery: StateFlow<String> = _codexQuery.asStateFlow()

    private val _codexSpiritId = MutableStateFlow<String?>(null)
    val codexSpiritId: StateFlow<String?> = _codexSpiritId.asStateFlow()

    val codexResults: StateFlow<List<CodexItem>> =
        combine(_codexQuery, _codexSpiritId) { query, spiritId ->
            codex.searchItems(query, spiritId).take(120)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val codexSpirit: StateFlow<CodexSpirit?> =
        _codexSpiritId.map { codex.findSpirit(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── 愿望清单 ──────────────────────────────────────────────────────────────

    val wishes: StateFlow<List<SkyWishEntity>> = dao.watchWishes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val holding: StateFlow<SkyHoldingEntity> = dao.watchHolding()
        .map { it ?: SkyHoldingEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkyHoldingEntity())

    val wishPlan: StateFlow<WishPlan> =
        combine(wishes, holding) { list, holding ->
            WishPlanner.plan(
                items = list.map {
                    WishItem(it.id, it.costCandle, it.costHeart, it.costAscended, it.owned, it.obtained)
                },
                holdingCandles = holding.candles,
                holdingHearts = holding.hearts,
                holdingAscended = holding.ascended,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WishPlan(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )

    // ── 账号档案 ──────────────────────────────────────────────────────────────

    val accounts: StateFlow<List<SkyAccountEntity>> = dao.watchAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // 持有蜡烛数只有一行，没有就先建一个
            if (dao.findHolding() == null) dao.saveHolding(SkyHoldingEntity())
            _fetchStatus.value = SkyFetchStatus(lastFetchedAt = readLastFetchedAt())
        }
    }

    // ── 抓取 ──────────────────────────────────────────────────────────────────

    /**
     * 抓复刻与季节。默认**一小时最多一次**（PRD 定的限速），[force] 用于用户手点刷新。
     * 抓不到时**保留旧数据**，只把状态标成失败。
     */
    fun refresh(force: Boolean = false) {
        if (_fetchStatus.value.loading) return

        // 每次进页面都按"现在"重算"今天"（App 可能一直开着跨过了凌晨 4 点）
        _todayKey.value = DateKeys.todayKey()

        viewModelScope.launch {
            val last = readLastFetchedAt()
            val now = System.currentTimeMillis()
            if (!force && last != null && now - last < FETCH_INTERVAL_MILLIS) {
                _fetchStatus.value = _fetchStatus.value.copy(
                    lastFetchedAt = last,
                    message = "刚刚已经更新过，一小时最多更新一次",
                    failed = false,
                )
                return@launch
            }

            _fetchStatus.value = _fetchStatus.value.copy(loading = true, message = null)
            val fetchedAt = System.currentTimeMillis()
            try {
                val result = withContext(Dispatchers.IO) {
                    val spiritText = client.fetchTravelingSpiritWikitext()
                    val seasonJson = runCatching { client.fetchSeasonsJson() }.getOrDefault("")
                    val eventJson = runCatching { client.fetchEventsJson() }.getOrDefault("")
                    Triple(spiritText, seasonJson, eventJson)
                }
                val spirits = TravelingSpiritParser.parse(result.first, fetchedAt)
                val seasons = if (result.second.isBlank()) emptyList() else SeasonParser.parse(result.second, fetchedAt)
                val events = if (result.third.isBlank()) emptyList() else SkyEventParser.parse(result.third, fetchedAt)

                if (spirits.isEmpty()) {
                    _fetchStatus.value = SkyFetchStatus(
                        loading = false,
                        lastFetchedAt = readLastFetchedAt(),
                        failed = true,
                        message = "这次没解析到复刻数据，显示的还是上次的数据",
                    )
                    return@launch
                }

                // 只替换"抓来的"，手动改过的保留
                dao.deleteFetchedTravelingSpirits()
                dao.insertTravelingSpirits(spirits)
                if (seasons.isNotEmpty()) {
                    dao.deleteFetchedSeasons()
                    dao.insertSeasons(seasons)
                }
                if (events.isNotEmpty()) {
                    dao.deleteFetchedEvents()
                    dao.insertEvents(events)
                }
                stateDao.put(io.github.menglian18.skyworkbench.data.db.AppStateEntity(KEY_LAST_FETCH, fetchedAt.toString()))

                _fetchStatus.value = SkyFetchStatus(
                    loading = false,
                    lastFetchedAt = fetchedAt,
                    failed = false,
                    message = buildString {
                        append("已更新：复刻 ").append(spirits.size).append(" 条")
                        if (seasons.isNotEmpty()) append("、季节 ").append(seasons.size).append(" 条")
                        if (events.isNotEmpty()) append("、活动 ").append(events.size).append(" 条")
                    },
                )
            } catch (error: Exception) {
                _fetchStatus.value = SkyFetchStatus(
                    loading = false,
                    lastFetchedAt = readLastFetchedAt(),
                    failed = true,
                    message = "联网获取失败（${error.message ?: "网络不通"}），显示的是上次的数据",
                )
            }
        }
    }

    private suspend fun readLastFetchedAt(): Long? =
        stateDao.get(KEY_LAST_FETCH)?.toLongOrNull()

    // ── 图鉴与心愿单操作 ──────────────────────────────────────────────────────

    fun setCodexQuery(query: String) {
        _codexQuery.value = query
    }

    fun setCodexSpirit(spiritId: String?) {
        _codexSpiritId.value = spiritId
    }

    fun addWishFromCodex(item: CodexItem) {
        viewModelScope.launch {
            val existing = dao.findWishByGuid(item.guid)
            if (existing != null) {
                if (!existing.obtained) return@launch
                dao.updateWish(existing.copy(obtained = false))
                return@launch
            }
            dao.insertWish(
                SkyWishEntity(
                    itemGuid = item.guid,
                    itemName = item.nameZh.ifBlank { item.nameEn },
                    spiritName = item.spiritNameZh.ifBlank { item.spiritNameEn },
                    costCandle = item.candles,
                    costHeart = item.hearts,
                    costAscended = item.ascended,
                ),
            )
        }
    }

    fun toggleOwned(wish: SkyWishEntity) {
        viewModelScope.launch { dao.updateWish(wish.copy(owned = !wish.owned)) }
    }

    fun toggleObtained(wish: SkyWishEntity) {
        viewModelScope.launch { dao.updateWish(wish.copy(obtained = !wish.obtained)) }
    }

    fun deleteWish(wish: SkyWishEntity) {
        viewModelScope.launch { dao.deleteWish(wish.id) }
    }

    fun saveHolding(candles: Int, hearts: Int, ascended: Int) {
        viewModelScope.launch {
            dao.saveHolding(SkyHoldingEntity(candles = candles, hearts = hearts, ascended = ascended))
        }
    }

    // ── 账号档案 ──────────────────────────────────────────────────────────────

    fun addAccount(nickname: String, loginMethod: String, server: String, note: String) {
        val name = nickname.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            dao.insertAccount(
                SkyAccountEntity(
                    nickname = name,
                    loginMethod = loginMethod.trim(),
                    server = server.trim().ifBlank { "国服" },
                    note = note.trim(),
                ),
            )
        }
    }

    fun saveAccount(account: SkyAccountEntity) {
        viewModelScope.launch { dao.updateAccount(account) }
    }

    fun deleteAccount(account: SkyAccountEntity) {
        viewModelScope.launch { dao.deleteAccount(account.id) }
    }

    companion object {
        const val FETCH_INTERVAL_MILLIS = 60L * 60L * 1000L
        private const val KEY_LAST_FETCH = "sky_last_fetch"
    }
}

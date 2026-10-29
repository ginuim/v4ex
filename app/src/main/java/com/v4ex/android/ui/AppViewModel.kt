package com.v4ex.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v4ex.android.data.Api
import com.v4ex.android.data.ApiException
import com.v4ex.android.data.HistoryEntry
import com.v4ex.android.data.HomeCacheCodec
import com.v4ex.android.data.Me
import com.v4ex.android.data.MemberPermissions
import com.v4ex.android.data.NodeDetail
import com.v4ex.android.data.Notice
import com.v4ex.android.data.PageInfo
import com.v4ex.android.ui.theme.ThemeMode
import com.v4ex.android.data.Prefs
import com.v4ex.android.data.RememberedUser
import com.v4ex.android.data.RateLimit
import com.v4ex.android.data.Reply
import com.v4ex.android.data.TokenInfo
import com.v4ex.android.data.Topic
import com.v4ex.android.data.TopicAction
import com.v4ex.android.data.VNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUi(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** 已有内容、正在后台拉新的：不显示下拉指示器，只在头部提示「正在同步…」 */
    val updating: Boolean = false,
    /** 当前列表来自本地缓存的时间戳；拉取成功后清空 */
    val cachedAt: Long? = null,
    val hot: List<Topic> = emptyList(),
    val latest: List<Topic> = emptyList(),
    val page: Int = 1,
    val moreLoading: Boolean = false,
    val canLoadMore: Boolean = false,
    /** 没有令牌时翻不了页，给个说明 */
    val hint: String? = null,
    val error: String? = null,
)

data class NodesUi(
    val loading: Boolean = true,
    val nodes: List<VNode> = emptyList(),
    val query: String = "",
    val error: String? = null,
)

data class TopicUi(
    val loading: Boolean = true,
    val topic: Topic? = null,
    val replies: List<Reply> = emptyList(),
    val error: String? = null,
    /** 回复是否走 v2 分页（有令牌且 v2 拉成功）；false = v1 一次返回的全部楼层 */
    val repliesPaged: Boolean = false,
    val repliesPage: Int = 1,
    val moreRepliesLoading: Boolean = false,
    val hasMoreReplies: Boolean = false,
    /** 置顶 / 放到首页的瞬时状态；null = 还没操作过 */
    val action: TopicAction? = null,
)

data class NodeTopicsUi(
    val node: String = "",
    val loading: Boolean = true,
    val topics: List<Topic> = emptyList(),
    val page: Int = 1,
    val moreLoading: Boolean = false,
    val canLoadMore: Boolean = false,
    val hint: String? = null,
    val error: String? = null,
    /** v2 节点详情（需要令牌）；没有令牌时为 null，节点页就不显示说明 */
    val detail: NodeDetail? = null,
)

data class NoticesUi(
    val loading: Boolean = false,
    val items: List<Notice> = emptyList(),
    val error: String? = null,
    val page: Int = 1,
    val moreLoading: Boolean = false,
    val canLoadMore: Boolean = false,
)

data class MemberUi(
    val username: String = "",
    val loading: Boolean = false,
    val profile: com.v4ex.android.data.MemberProfile? = null,
    val error: String? = null,
)

data class MeUi(
    val loading: Boolean = false,
    val me: Me? = null,
    val error: String? = null,
    /** GET /token 的信息，用来展示配额、到期日 */
    val tokenInfo: TokenInfo? = null,
    val creatingToken: Boolean = false,
    /** 刚创建出来的令牌。明文只在创建响应里出现一次，关掉就没了 */
    val createdToken: TokenInfo? = null,
    val createError: String? = null,
)

data class PublishUi(
    val sending: Boolean = false,
    val error: String? = null,
    val completedTopicId: Int? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val _rememberedUser = MutableStateFlow(RememberedUser())
    val rememberedUser = _rememberedUser.asStateFlow()
    private var identityRequest = 0
    private val _publish = MutableStateFlow(PublishUi())
    val publish = _publish.asStateFlow()

    fun resetPublish() {
        if (!_publish.value.sending) _publish.value = PublishUi()
    }

    val permissions: MemberPermissions
        get() = MemberPermissions.resolve(tokenValue.isNotBlank(), _me.value.me, !_me.value.loading && _me.value.error == null)
            .copy(canPublish = true) // TEMP: 预览 PRO 发布和回复入口。

    fun publishTopic(node: String, title: String, content: String, syntax: String) {
        if (!permissions.canPublish) return
        publish { api.createTopic(node, title, content, syntax).id }
    }

    fun publishReply(topicId: Int, content: String) {
        if (!permissions.canPublish) return
        publish { api.createReply(topicId, content); topicId }
    }

    private fun publish(block: () -> Int) {
        if (_publish.value.sending || _publish.value.completedTopicId != null) return
        _publish.value = PublishUi(sending = true)
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { block() }
                _publish.value = PublishUi(completedTopicId = id)
            } catch (e: Exception) {
                val message = if (e is java.io.IOException) {
                    "网络中断，发布结果尚不确定。请先到 V2EX 确认是否已发布，再决定是否重试。"
                } else friendly(e)
                _publish.value = PublishUi(error = message)
            }
        }
    }

    private val prefs = Prefs(app)
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()
    fun setThemeMode(value: ThemeMode) {
        _themeMode.value = value
        viewModelScope.launch { prefs.setThemeMode(value.name) }
    }

    private val _token = MutableStateFlow<String?>(null)
    /** null = 还没从磁盘读出来 */
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _readIds = MutableStateFlow<Set<String>>(emptySet())
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    @Volatile
    private var tokenValue = ""

    /** 最近一次请求的限流信息（X-Rate-Limit-*），「我的」页展示本小时剩余配额 */
    private val _rateLimit = MutableStateFlow<RateLimit?>(null)
    val rateLimit: StateFlow<RateLimit?> = _rateLimit.asStateFlow()

    private val api = Api(
        tokenProvider = { tokenValue },
        // 请求可能发生在 IO 线程，StateFlow 本身线程安全
        onRateLimit = { _rateLimit.value = it },
    )

    private val _home = MutableStateFlow(HomeUi())
    val home = _home.asStateFlow()

    private val _nodes = MutableStateFlow(NodesUi())
    val nodes = _nodes.asStateFlow()

    private val _topic = MutableStateFlow(TopicUi())
    val topic = _topic.asStateFlow()

    private val _nodeTopics = MutableStateFlow(NodeTopicsUi())
    val nodeTopics = _nodeTopics.asStateFlow()

    private val _notices = MutableStateFlow(NoticesUi())
    val notices = _notices.asStateFlow()

    private val _member = MutableStateFlow(MemberUi())
    val member = _member.asStateFlow()

    private val _me = MutableStateFlow(MeUi())
    val me = _me.asStateFlow()

    /** 常用节点（纯本地） */
    private val _favNodes = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val favNodes = _favNodes.asStateFlow()

    /** 浏览历史（纯本地，最近的在前） */
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history = _history.asStateFlow()

    /** 历史是否已经从磁盘读出来了。没读完不能说「没有记录」。 */
    private val _historyLoaded = MutableStateFlow(false)
    val historyLoaded = _historyLoaded.asStateFlow()

    /** 首页只看未读。过滤在 UI 层做，不动原始列表，关掉开关立刻恢复。 */
    private val _hotCollapsedDay = MutableStateFlow("")
    val hotCollapsedDay = _hotCollapsedDay.asStateFlow()
    fun setHotExpanded(expanded: Boolean) {
        val day = if (expanded) "" else java.time.LocalDate.now().toString()
        _hotCollapsedDay.value = day
        viewModelScope.launch { prefs.setHotCollapsedDay(day) }
    }

    private val _unreadOnly = MutableStateFlow(false)
    val unreadOnly = _unreadOnly.asStateFlow()

    fun setUnreadOnly(value: Boolean) {
        _unreadOnly.value = value
        viewModelScope.launch { prefs.setUnreadOnly(value) }
    }

    /** 帖子回复视图：false = 楼层顺序，true = 按对话。 */
    private val _threaded = MutableStateFlow(false)
    val threaded = _threaded.asStateFlow()

    fun setThreaded(value: Boolean) {
        _threaded.value = value
        viewModelScope.launch { prefs.setReplyThreaded(value) }
    }

    fun removeHistory(id: Int) {
        viewModelScope.launch { prefs.removeHistory(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { prefs.clearHistory() }
    }

    fun toggleFavNode(name: String, title: String) {
        viewModelScope.launch { prefs.toggleFavNode(name, title) }
    }

    init {
        viewModelScope.launch {
            prefs.themeMode.collect { _themeMode.value = ThemeMode.fromStored(it) }
        }
        viewModelScope.launch {
            prefs.readIds.collect { _readIds.value = it }
        }
        viewModelScope.launch {
            prefs.favNodes.collect { _favNodes.value = it }
        }
        viewModelScope.launch {
            prefs.history.collect {
                _history.value = it
                _historyLoaded.value = true
            }
        }
        viewModelScope.launch { prefs.hotCollapsedDay.collect { _hotCollapsedDay.value = it } }
        viewModelScope.launch {
            prefs.unreadOnly.collect { _unreadOnly.value = it }
        }
        viewModelScope.launch {
            prefs.replyThreaded.collect { _threaded.value = it }
        }
        // 顺序很重要：缓存 → token → 网络。
        // 如果缓存和刷新放在两个协程里并发跑，网络先回来、缓存后到，就会用旧数据覆盖新数据。
        viewModelScope.launch {
            runCatching {
                val raw = prefs.homeCache.first()
                HomeCacheCodec.decode(raw)?.let { (hot, latest) ->
                    if (hot.isNotEmpty() || latest.isNotEmpty()) {
                        _home.value = HomeUi(
                            loading = false,
                            hot = hot,
                            latest = latest,
                            // 缓存不知道能不能翻页，先按「没令牌」保守处理，拿到新数据会纠正
                            canLoadMore = false,
                            hint = null,
                            cachedAt = System.currentTimeMillis(),
                        )
                    }
                }
            }
            // token 是异步读盘的：等它第一次落定再拉首页，否则会把「有令牌」误判成「没令牌」
            _token.filterNotNull().first()
            val hasCache = _home.value.hot.isNotEmpty() || _home.value.latest.isNotEmpty()
            // 有缓存时走静默刷新，不在启动瞬间弹下拉指示器
            refreshHome(silent = hasCache)
            loadNodes()
        }
        viewModelScope.launch {
            prefs.rememberedUser.collect { _rememberedUser.value = it }
        }
        viewModelScope.launch {
            prefs.token.collect { value ->
                if (tokenValue != value) {
                    identityRequest++
                    _me.value = MeUi()
                }
                tokenValue = value
                _token.value = value
                if (value.isNotBlank()) {
                    loadMe()
                    loadNotices()
                } else {
                    _me.value = MeUi(loading = false)
                    _notices.value = NoticesUi()
                    _rateLimit.value = null
                }
            }
        }
    }

    // ------------------------------------------------------------------ home
    /**
     * 拉首页。
     * @param silent true = 启动时用缓存先把界面填上、后台静默更新（不显示下拉指示器）；
     *               false = 用户主动下拉/点刷新。
     */
    fun refreshHome(silent: Boolean = false) {
        val current = _home.value
        val hasContent = current.hot.isNotEmpty() || current.latest.isNotEmpty()
        _home.value = current.copy(
            loading = !hasContent && !silent,
            refreshing = hasContent && !silent,
            updating = hasContent && silent,
            moreLoading = false,
            error = null,
        )
        viewModelScope.launch {
            try {
                val hasToken = tokenValue.isNotBlank()
                val hot = withContext(Dispatchers.IO) { api.hot() }
                val (latest, page) = withContext(Dispatchers.IO) {
                    if (hasToken) api.latestPage(1) else api.latest() to PageInfo(pages = 1)
                }
                _home.value = HomeUi(
                    loading = false,
                    refreshing = false,
                    updating = false,
                    hot = hot,
                    latest = latest,
                    page = 1,
                    canLoadMore = hasToken && page.hasMore(1),
                    hint = if (hasToken) null else "填写令牌后可继续翻页",
                    cachedAt = null,
                )
                // 成功拿到新数据才落盘：失败时保留旧缓存，下次打开至少还有东西看
                withContext(Dispatchers.IO) {
                    prefs.saveHomeCache(HomeCacheCodec.encode(hot, latest))
                }
            } catch (e: Exception) {
                _home.value = _home.value.copy(
                    loading = false,
                    refreshing = false,
                    updating = false,
                    error = friendly(e),
                )
            }
        }
    }

    fun loadMoreLatest() {
        val current = _home.value
        if (current.moreLoading || !current.canLoadMore) return
        _home.value = current.copy(moreLoading = true)
        viewModelScope.launch {
            try {
                val next = current.page + 1
                val (items, page) = withContext(Dispatchers.IO) { api.latestPage(next) }
                val seen = current.latest.map { it.id }.toSet()
                _home.value = current.copy(
                    latest = current.latest + items.filterNot { it.id in seen },
                    page = next,
                    moreLoading = false,
                    canLoadMore = page.hasMore(next),
                )
            } catch (e: Exception) {
                _home.value = current.copy(moreLoading = false, error = friendly(e))
            }
        }
    }

    // ----------------------------------------------------------------- nodes
    fun loadNodes(force: Boolean = false) {
        if (!force && _nodes.value.nodes.isNotEmpty()) return
        _nodes.value = _nodes.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { api.nodes() }
                _nodes.value = _nodes.value.copy(loading = false, nodes = list)
            } catch (e: Exception) {
                _nodes.value = _nodes.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    fun setNodeQuery(q: String) {
        _nodes.value = _nodes.value.copy(query = q)
    }

    fun loadNodeTopics(node: String) {
        val hasToken = tokenValue.isNotBlank()
        _nodeTopics.value = if (_nodeTopics.value.node == node) _nodeTopics.value.copy(loading = true, error = null) else NodeTopicsUi(node = node, loading = true)
        viewModelScope.launch {
            try {
                // v2 节点详情只有令牌能取；拿不到就不显示节点说明，不影响主题列表
                val detail = if (hasToken) {
                    withContext(Dispatchers.IO) { runCatching { api.node(node) }.getOrNull() }
                } else {
                    null
                }
                val (list, page) = withContext(Dispatchers.IO) {
                    if (hasToken) api.nodeTopicsPage(node, 1) else api.nodeTopics(node) to PageInfo(pages = 1)
                }
                _nodeTopics.value = NodeTopicsUi(
                    node = node,
                    loading = false,
                    topics = list,
                    canLoadMore = hasToken && page.hasMore(1),
                    hint = if (hasToken) null else "填写令牌后可以继续往下翻",
                    detail = detail,
                )
            } catch (e: Exception) {
                _nodeTopics.value = NodeTopicsUi(node = node, loading = false, error = friendly(e))
            }
        }
    }

    fun loadMoreNodeTopics() {
        val current = _nodeTopics.value
        if (current.moreLoading || !current.canLoadMore || current.node.isBlank()) return
        _nodeTopics.value = current.copy(moreLoading = true)
        viewModelScope.launch {
            try {
                val next = current.page + 1
                val (items, page) = withContext(Dispatchers.IO) {
                    api.nodeTopicsPage(current.node, next)
                }
                val seen = current.topics.map { it.id }.toSet()
                _nodeTopics.value = current.copy(
                    topics = current.topics + items.filterNot { it.id in seen },
                    page = next,
                    moreLoading = false,
                    canLoadMore = page.hasMore(next),
                )
            } catch (e: Exception) {
                _nodeTopics.value = current.copy(moreLoading = false, error = friendly(e))
            }
        }
    }

    // ----------------------------------------------------------------- topic
    fun markRead(id: Int) {
        _readIds.value = _readIds.value + id.toString()
        viewModelScope.launch { prefs.markRead(id) }
    }

    fun clearRead() {
        _readIds.value = emptySet()
        viewModelScope.launch { prefs.clearRead() }
    }

    /**
     * 打开帖子。有令牌时优先走 v2（正文 + 首页 20 条回复），v2 挂了退回 v1，
     * 保证「帖子总能打开」——哪怕令牌过期、v2 接口抽风。
     */
    fun loadTopic(id: Int) {
        markRead(id)
        _topic.value = if (_topic.value.topic?.id == id) _topic.value.copy(loading = true, error = null) else TopicUi(loading = true)
        viewModelScope.launch {
            try {
                val hasToken = tokenValue.isNotBlank()
                // 先出标题和正文（一般 1~2s），回复到了再补上，避免整页干等
                val v2Topic = if (hasToken) {
                    withContext(Dispatchers.IO) { runCatching { api.topicV2(id) }.getOrNull() }
                } else {
                    null
                }
                val topic = v2Topic ?: withContext(Dispatchers.IO) { api.topic(id) }
                _topic.value = TopicUi(loading = true, topic = topic)
                recordHistory(topic)

                // v2 回复分页（20 条/页）；失败就退回 v1 全量楼层
                val firstPage = if (hasToken) {
                    withContext(Dispatchers.IO) { runCatching { api.repliesPage(id, 1) }.getOrNull() }
                } else {
                    null
                }
                if (firstPage != null) {
                    val (replies, page) = firstPage
                    _topic.value = TopicUi(
                        loading = false,
                        topic = topic,
                        replies = replies,
                        repliesPaged = true,
                        repliesPage = 1,
                        hasMoreReplies = page.hasMore(1),
                    )
                } else {
                    val replies = withContext(Dispatchers.IO) { api.replies(id) }
                    _topic.value = TopicUi(loading = false, topic = topic, replies = replies)
                }
            } catch (e: Exception) {
                _topic.value = TopicUi(loading = false, error = friendly(e))
            }
        }
    }

    /** 帖子页「加载更多回复」（只有 v2 分页模式才有下一页）。 */
    fun loadMoreReplies() {
        val current = _topic.value
        val topic = current.topic ?: return
        if (current.moreRepliesLoading || !current.hasMoreReplies) return
        _topic.value = current.copy(moreRepliesLoading = true)
        viewModelScope.launch {
            try {
                val next = current.repliesPage + 1
                val (items, page) = withContext(Dispatchers.IO) { api.repliesPage(topic.id, next) }
                val seen = current.replies.map { it.id }.toSet()
                _topic.value = current.copy(
                    replies = current.replies + items.filterNot { it.id in seen },
                    repliesPage = next,
                    moreRepliesLoading = false,
                    hasMoreReplies = page.hasMore(next),
                )
            } catch (e: Exception) {
                _topic.value = current.copy(
                    moreRepliesLoading = false,
                    action = TopicAction(message = friendly(e)),
                )
            }
        }
    }

    /**
     * 置顶自己的主题。需要 PRO，没开会员时服务端返回 403，
     * 错误会以提示条的形式显示在帖子页顶部。
     */
    fun setSticky(duration: String) {
        if (!permissions.canManage(_topic.value.topic)) return
        val id = _topic.value.topic?.id ?: return
        runTopicAction("已置顶（$duration）") { api.setSticky(id, duration) }
    }

    /** 把自己的主题放到首页（PRO / 5 万 token / 1 万铜币，花 100 铜币）。 */
    fun boostTopic() {
        if (!permissions.canManage(_topic.value.topic)) return
        val id = _topic.value.topic?.id ?: return
        runTopicAction("已放到首页") { api.boost(id) }
    }

    fun dismissTopicAction() {
        _topic.value = _topic.value.copy(action = null)
    }

    private fun runTopicAction(fallback: String, block: () -> String) {
        val current = _topic.value
        if (current.topic == null || current.action?.loading == true) return
        _topic.value = current.copy(action = TopicAction(loading = true))
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) { block() }
                _topic.value = _topic.value.copy(
                    action = TopicAction(message = message.ifBlank { fallback }, ok = true),
                )
            } catch (e: Exception) {
                _topic.value = _topic.value.copy(
                    action = TopicAction(message = friendly(e), ok = false),
                )
            }
        }
    }

    private suspend fun recordHistory(topic: Topic) {
        // 拿到正文才算看过：加载失败的不进浏览历史
        withContext(Dispatchers.IO) {
            prefs.recordHistory(
                HistoryEntry(
                    id = topic.id,
                    title = topic.title,
                    nodeName = topic.node.name,
                    nodeTitle = topic.node.title,
                    author = topic.member.username,
                    replies = topic.replies,
                    viewedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun loadMember(username: String) {
        if (username.isBlank()) return
        _member.value = MemberUi(username = username, loading = true)
        viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { api.member(username) }
                _member.value = MemberUi(username = username, loading = false, profile = profile)
            } catch (e: Exception) {
                _member.value = MemberUi(username = username, loading = false, error = friendly(e))
            }
        }
    }

    // --------------------------------------------------------------- account
    fun loadNotices() {
        loadNoticesPage(1)
    }

    private fun loadNoticesPage(page: Int) {
        if (tokenValue.isBlank()) return
        _notices.value = _notices.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val (list, info) = withContext(Dispatchers.IO) { api.notices(page) }
                _notices.value = NoticesUi(
                    loading = false,
                    items = list,
                    page = page,
                    canLoadMore = info.hasMore(page),
                )
            } catch (e: Exception) {
                _notices.value = _notices.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    /** 提醒翻页：v2 每页 20 条，底部「加载更多」触发。 */
    fun loadMoreNotices() {
        val current = _notices.value
        if (current.moreLoading || !current.canLoadMore) return
        _notices.value = current.copy(moreLoading = true)
        viewModelScope.launch {
            try {
                val next = current.page + 1
                val (list, info) = withContext(Dispatchers.IO) { api.notices(next) }
                val seen = current.items.map { it.id }.toSet()
                _notices.value = current.copy(
                    items = current.items + list.filterNot { it.id in seen },
                    page = next,
                    moreLoading = false,
                    canLoadMore = info.hasMore(next),
                )
            } catch (e: Exception) {
                _notices.value = current.copy(moreLoading = false, error = friendly(e))
            }
        }
    }

    /**
     * 点开一条通知 = 已读。先从列表里摘掉（角标立刻减一），再异步同步到服务端；
     * 服务端失败就整体回滚，避免「本地看不见、下次刷新又冒出来」。
     */
    fun clearNotice(id: Int) {
        if (tokenValue.isBlank()) return
        val before = _notices.value.items
        if (before.none { it.id == id }) return
        _notices.value = _notices.value.copy(items = before.filterNot { it.id == id })
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { api.clearNotice(id) }.getOrDefault(false)
            }
            if (!ok) {
                _notices.value = _notices.value.copy(items = before)
            }
        }
    }

    /** 全部已读：本地立刻清空角标，服务端逐条删；删失败的不回滚，下次刷新会重新出现。 */
    fun clearAllNotices() {
        if (tokenValue.isBlank()) return
        val before = _notices.value.items
        if (before.isEmpty()) return
        _notices.value = _notices.value.copy(items = emptyList())
        viewModelScope.launch {
            val failed = withContext(Dispatchers.IO) {
                before.filterNot { n -> runCatching { api.clearNotice(n.id) }.getOrDefault(false) }
            }
            if (failed.isNotEmpty()) {
                _notices.value = _notices.value.copy(items = failed)
            }
        }
    }

    fun loadMe() {
        if (tokenValue.isBlank()) return
        val request = ++identityRequest
        val requestedToken = tokenValue
        _me.value = _me.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val me = withContext(Dispatchers.IO) { api.me() }
                if (request != identityRequest || requestedToken != tokenValue) return@launch
                if (me.id > 0 && me.username.isNotBlank()) {
                    prefs.rememberUser(requestedToken, me.username, me.avatar)
                }
                if (request != identityRequest || requestedToken != tokenValue) return@launch
                _me.value = _me.value.copy(
                    loading = false,
                    me = me,
                    // me() 内部已经问过 /token，这里直接拼出展示用的令牌信息，省一次请求
                    tokenInfo = if (me.tokenDays > 0 || me.tokenTotalUsed > 0 || me.tokenScope != "-") {
                        TokenInfo(
                            scope = me.tokenScope,
                            expiration = me.tokenExpiration,
                            goodForDays = me.tokenDays,
                            totalUsed = me.tokenTotalUsed,
                        )
                    } else {
                        null
                    },
                )
            } catch (e: Exception) {
                if (request == identityRequest && requestedToken == tokenValue) {
                    _me.value = _me.value.copy(loading = false, error = friendly(e))
                }
            }
        }
    }

    /**
     * 在 App 内创建新令牌（POST /token）。新令牌明文只在这次响应里出现，
     * 必须让用户当场复制；关掉对话框后就再也看不到了。
     */
    fun createToken(scope: String, expirationSeconds: Int) {
        if (!permissions.canCreateToken || _me.value.creatingToken) return
        _me.value = _me.value.copy(creatingToken = true, createError = null)
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) { api.createToken(scope, expirationSeconds) }
                _me.value = _me.value.copy(creatingToken = false, createdToken = created)
            } catch (e: Exception) {
                _me.value = _me.value.copy(creatingToken = false, createError = friendly(e))
            }
        }
    }

    fun dismissCreatedToken() {
        _me.value = _me.value.copy(createdToken = null, createError = null)
    }

    fun saveToken(value: String) {
        if (value.trim() != tokenValue) {
            identityRequest++
            _me.value = MeUi()
            _rememberedUser.value = RememberedUser()
        }
        viewModelScope.launch {
            prefs.setToken(value)
            if (value.isBlank()) {
                _me.value = MeUi(loading = false)
                _notices.value = NoticesUi()
                _rateLimit.value = null
            }
            refreshHome()
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> e.message ?: "出错了"
        is java.net.UnknownHostException -> "网络不可用"
        else -> e.message ?: e.javaClass.simpleName
    }
}

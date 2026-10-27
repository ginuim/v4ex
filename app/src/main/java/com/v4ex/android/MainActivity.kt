package com.v4ex.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v4ex.android.ui.AppViewModel
import com.v4ex.android.ui.BottomBar
import com.v4ex.android.ui.screens.HistoryScreen
import com.v4ex.android.ui.screens.HomeScreen
import com.v4ex.android.ui.screens.MeScreen
import com.v4ex.android.ui.screens.MemberScreen
import com.v4ex.android.ui.screens.NodeTopicsScreen
import com.v4ex.android.ui.screens.NodesScreen
import com.v4ex.android.ui.screens.NoticesScreen
import com.v4ex.android.ui.screens.TopicScreen
import com.v4ex.android.ui.theme.Ink
import com.v4ex.android.ui.theme.V4exTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            V4exTheme {
                V4exApp()
            }
        }
    }
}

@Composable
private fun V4exApp(vm: AppViewModel = viewModel()) {
    val home by vm.home.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val topic by vm.topic.collectAsStateWithLifecycle()
    val nodeTopics by vm.nodeTopics.collectAsStateWithLifecycle()
    val member by vm.member.collectAsStateWithLifecycle()
    val notices by vm.notices.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val token by vm.token.collectAsStateWithLifecycle()
    val favNodes by vm.favNodes.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val historyLoaded by vm.historyLoaded.collectAsStateWithLifecycle()
    val unreadOnly by vm.unreadOnly.collectAsStateWithLifecycle()
    val threaded by vm.threaded.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openTopic by rememberSaveable { mutableStateOf<Int?>(null) }
    var openNode by rememberSaveable { mutableStateOf<String?>(null) }
    var openMember by rememberSaveable { mutableStateOf<String?>(null) }
    var openHistory by rememberSaveable { mutableStateOf(false) }
    var authDialog by remember { mutableStateOf(false) }
    val homeList = rememberLazyListState()
    val readIds by vm.readIds.collectAsStateWithLifecycle()
    val homeState by vm.home.collectAsStateWithLifecycle()
    val nodeState by vm.nodeTopics.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val hasToken = (token ?: "").isNotBlank()

    LaunchedEffect(openTopic) {
        openTopic?.let { vm.loadTopic(it) }
    }
    LaunchedEffect(openNode) {
        openNode?.let { vm.loadNodeTopics(it) }
    }
    LaunchedEffect(openMember) {
        openMember?.let { vm.loadMember(it) }
    }

    BackHandler(
        enabled = openTopic != null || openNode != null || openMember != null || openHistory,
    ) {
        // 返回顺序必须和「显示顺序」相反，一层一层退：
        // 个人页 → 帖子页 → 节点页 → 浏览历史 → 首页/我的。
        // 历史页只是从「我的」点开的子页；从历史点进帖子时它留在栈里（被帖子页盖住），
        // 所以帖子返回要回到历史，而不是直接掉回「我的」。
        when {
            openMember != null -> openMember = null
            openTopic != null -> openTopic = null
            openNode != null -> openNode = null
            openHistory -> openHistory = false
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.Surface)) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when {
                // 个人页叠在帖子页之上。先判 openMember，否则从帖子里点作者永远进不去。
                openMember != null -> MemberScreen(
                    loading = member.loading,
                    profile = member.profile,
                    error = member.error,
                    username = openMember ?: member.username,
                    onBack = { openMember = null },
                    onRetry = { openMember?.let { vm.loadMember(it) } },
                    onOpenTopic = {
                        openTopic = it.id
                        openMember = null
                    },
                )

                openTopic != null -> TopicScreen(
                    loading = topic.loading,
                    topic = topic.topic,
                    replies = topic.replies,
                    error = topic.error,
                    threaded = threaded,
                    onToggleThreaded = { vm.setThreaded(!threaded) },
                    onBack = { openTopic = null },
                    onRetry = { openTopic?.let { vm.loadTopic(it) } },
                    onAuthor = { openMember = it },
                )

                openNode != null -> NodeTopicsScreen(
                    node = nodeState.node,
                    topics = nodeState.topics,
                    readIds = readIds,
                    loading = nodeState.loading,
                    moreLoading = nodeState.moreLoading,
                    canLoadMore = nodeState.canLoadMore,
                    hint = nodeState.hint,
                    error = nodeState.error,
                    onBack = { openNode = null },
                    onTopic = { openTopic = it.id },
                    onRetry = { openNode?.let { vm.loadNodeTopics(it) } },
                    onLoadMore = vm::loadMoreNodeTopics,
                )

                // 浏览历史压在帖子/节点页下面：从历史点进帖子时它不关闭，
                // 帖子返回后回到历史列表，而不是直接掉回「我的」。
                openHistory -> HistoryScreen(
                    items = history,
                    loaded = historyLoaded,
                    onBack = { openHistory = false },
                    onOpen = { entry -> openTopic = entry.id },
                    onRemove = vm::removeHistory,
                    onClearAll = vm::clearHistory,
                )

                tab == 0 -> HomeScreen(
                    listState = homeList,
                    hot = home.hot,
                    latest = home.latest,
                    readIds = readIds,
                    unreadOnly = unreadOnly,
                    onUnreadOnly = vm::setUnreadOnly,
                    nodeCount = nodes.nodes.size,
                    me = me.me,
                    refreshing = home.refreshing,
                    updating = home.updating,
                    cachedAt = home.cachedAt,
                    moreLoading = home.moreLoading,
                    canLoadMore = home.canLoadMore,
                    hint = home.hint,
                    error = home.error,
                    onTopic = { openTopic = it.id },
                    onRefresh = {
                        vm.refreshHome()
                        vm.loadMe()
                    },
                    onLoadMore = vm::loadMoreLatest,
                    onAuthor = { openMember = it },
                )

                tab == 1 -> NodesScreen(
                    nodes = nodes.nodes,
                    query = nodes.query,
                    loading = nodes.loading,
                    favNodes = favNodes,
                    onQuery = vm::setNodeQuery,
                    onNode = { openNode = it.name },
                    onNodeByName = { openNode = it },
                    onToggleFav = vm::toggleFavNode,
                )

                tab == 2 -> NoticesScreen(
                    hasToken = hasToken,
                    items = notices.items,
                    loading = notices.loading,
                    error = notices.error,
                    onRefresh = vm::loadNotices,
                    onAuth = { authDialog = true },
                    onAuthor = { if (it.isNotBlank()) openMember = it },
                    onClearAll = vm::clearAllNotices,
                    onOpenTopic = { notice ->
                        // 点开即已读：先摘角标，再同步服务端。
                        vm.clearNotice(notice.id)
                        // 通知里没解析出主题 id 时（比如只是 @ 提到），退到浏览器看收件箱。
                        val id = notice.topicId
                        if (id != null) {
                            openTopic = id
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.v2ex.com/notifications"))
                                )
                            }
                        }
                    },
                )

                else -> MeScreen(
                    hasToken = hasToken,
                    me = me.me,
                    error = me.error,
                    historyCount = history.size,
                    onOpenHistory = { openHistory = true },
                    onAuth = { authDialog = true },
                    onSignOut = { vm.saveToken("") },
                )
            }
        }

        if (openTopic == null && openNode == null && openMember == null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
            ) {
                BottomBar(
                    selected = tab,
                    unread = notices.items.size,
                    onSelect = { index ->
                        tab = index
                        when (index) {
                            0 -> if (homeState.hot.isEmpty()) vm.refreshHome()
                            1 -> vm.loadNodes()
                            2 -> vm.loadNotices()
                            else -> vm.loadMe()
                        }
                    },
                )
            }
        }
    }

    if (authDialog) {
        TokenDialog(
            initial = token.orEmpty(),
            onDismiss = { authDialog = false },
            onSave = { value ->
                vm.saveToken(value)
                authDialog = false
            },
        )
    }
}

@Composable
private fun TokenDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("个人访问令牌", fontSize = 17.sp) },
        text = {
            Column(Modifier.widthIn(min = 260.dp)) {
                Text(
                    "在 v2ex.com/settings/token 创建一个令牌，粘贴到下面。只保存在本机。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Ink.Secondary,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("bd1f2c67-…", fontSize = 13.sp, color = Ink.Tertiary) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

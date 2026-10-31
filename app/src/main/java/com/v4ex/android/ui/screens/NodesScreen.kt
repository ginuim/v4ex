package com.v4ex.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.stateDescription
import com.v4ex.android.ui.DisclosureArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.v4ex.android.data.groupNodes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.NodeDetail
import com.v4ex.android.data.VNode
import com.v4ex.android.ui.Avatar
import com.v4ex.android.ui.EmptyArt
import com.v4ex.android.ui.EmptyState
import com.v4ex.android.ui.theme.Ink

@Composable
fun NodesScreen(
    nodes: List<VNode>,
    query: String,
    loading: Boolean,
    favNodes: List<Pair<String, String>>,
    onQuery: (String) -> Unit,
    onNode: (VNode) -> Unit,
    onNodeByName: (String) -> Unit,
    onToggleFav: (String, String) -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    fun closeSearch() { searchOpen = false; onQuery("") }
    val groups = remember(nodes) { groupNodes(nodes) }
    var categorized by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val filtered = if (query.isBlank()) nodes else nodes.filter {
        it.name.contains(query, true) || it.title.contains(query, true)
    }
    val favNames = remember(favNodes) { favNodes.map { it.first }.toSet() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("探索节点", Modifier.weight(1f), fontSize = 24.sp,
                fontWeight = FontWeight.Medium, color = Ink.Primary)
            IconButton(onClick = { searchOpen = true }) {
                Icon(Icons.Filled.Search, "搜索节点", Modifier.size(22.dp), tint = Ink.Primary)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            item(key = "node-overview") {
                Column {
                    Text(
                        if (nodes.isEmpty()) "找到感兴趣的话题" else "${nodes.size} 个节点，总有你感兴趣的话题",
                        Modifier.padding(start = 22.dp, top = 5.dp, bottom = 4.dp),
                        fontSize = 12.5.sp, color = Ink.Secondary,
                    )

                    // 常用节点快捷入口：纯本地存储，V2EX 没有收藏/关注接口
                    if (favNodes.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(favNodes, key = { it.first }) { (name, title) ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Ink.ChipBg)
                                        .clickable { onNodeByName(name) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        title.ifBlank { name },
                                        fontSize = 13.sp,
                                        color = Ink.Primary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 三种情形要分开：还在加载 / 搜不到 / 节点根本没拉下来
                    if (nodes.isEmpty() && loading) {
                        com.v4ex.android.ui.LoadingBlock("正在读取节点…")
                    }
                    if (nodes.isEmpty() && !loading) {
                        EmptyState(
                            title = "节点还没到手",
                            hint = "网络可能断了，也可能 V2EX 正在打个盹。",
                            art = EmptyArt.Offline,
                        )
                    }

                    if (nodes.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(true to "分类", false to "全部").forEach { (mode, label) ->
                                Text(
                                    label,
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                        .background(if (categorized == mode) Ink.Primary else Ink.ChipBg)
                                        .clickable { categorized = mode }
                                        .padding(horizontal = 15.dp, vertical = 8.dp),
                                    fontSize = 13.sp,
                                    color = if (categorized == mode) Ink.OnPrimary else Ink.Secondary,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(if (categorized) "按节点归属" else "按主题数量", fontSize = 11.5.sp, color = Ink.Tertiary)
                        }
                    }
                }
            }
            if (categorized) {
                groups.forEach { group ->
                    val isExpanded = group.key in expanded
                    item(key = "group:${group.key}") {
                        Column(
                            Modifier.fillMaxWidth().semantics {
                                stateDescription = if (isExpanded) "已展开" else "已收起"
                            }.clickable {
                                expanded = if (isExpanded) expanded - group.key else expanded + group.key
                            }.padding(horizontal = 22.dp, vertical = 15.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(group.title, Modifier.weight(1f), fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium, color = Ink.Primary)
                                Text("${group.nodes.size} 个", fontSize = 12.sp, color = Ink.Tertiary)
                                Spacer(Modifier.width(6.dp))
                                DisclosureArrow(isExpanded)
                            }
                            if (!isExpanded) {
                                Spacer(Modifier.height(6.dp))
                                Text(group.nodes.take(4).joinToString(" · ") { it.title },
                                    fontSize = 12.sp, color = Ink.Secondary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (isExpanded) {
                        items(group.nodes, key = { "node:${it.name}" }) { node ->
                            NodeListRow(node, node.name in favNames, onNode, onToggleFav, showPath = false)
                        }
                    }
                }
            } else {
                items(nodes, key = { "node:${it.name}" }) { node ->
                    NodeListRow(node, node.name in favNames, onNode, onToggleFav)
                }
            }
        }
    }
    if (searchOpen) {
        NodeSearchDialog(
            query = query, nodes = filtered, loading = loading,
            favNames = favNames, onQuery = onQuery, onDismiss = ::closeSearch,
            onNode = { closeSearch(); onNode(it) }, onToggleFav = onToggleFav,
        )
    }

}

@Composable
private fun NodeSearchDialog(
    query: String,
    nodes: List<VNode>,
    loading: Boolean,
    favNames: Set<String>,
    onQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onNode: (VNode) -> Unit,
    onToggleFav: (String, String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val inputStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = Ink.Primary,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp), color = Ink.Surface,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = Ink.Secondary)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                            value = query, onValueChange = onQuery, singleLine = true,
                            textStyle = inputStyle,
                            cursorBrush = SolidColor(Ink.Primary),
                            modifier = Modifier.weight(1f).height(48.dp).focusRequester(focus)
                                .semantics { contentDescription = "搜索全部节点" },
                            decorationBox = { innerTextField ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text("名称或英文名", style = inputStyle.copy(color = Ink.Tertiary))
                                    }
                                    innerTextField()
                                }
                            },
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "关闭搜索", Modifier.size(20.dp), tint = Ink.Secondary)
                    }
                }
                Text(
                    if (query.isBlank()) "全部节点 · ${nodes.size}" else "找到 ${nodes.size} 个节点",
                    Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    fontSize = 12.sp, color = Ink.Tertiary,
                )
                when {
                    loading && nodes.isEmpty() -> com.v4ex.android.ui.LoadingBlock("正在读取节点…")
                    nodes.isEmpty() -> EmptyState(
                        title = "没有匹配的节点", hint = "换个关键词，或者试试节点的英文名。",
                        art = EmptyArt.Search, action = "清空搜索" to { onQuery("") },
                    )
                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(nodes, key = { it.name }) { node ->
                            NodeListRow(node, node.name in favNames, onNode, onToggleFav)
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    }
}

@Composable
private fun NodeListRow(
    node: VNode,
    starred: Boolean,
    onNode: (VNode) -> Unit,
    onToggleFav: (String, String) -> Unit,
    showPath: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onNode(node) }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(node.avatar, node.name, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(node.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (showPath) "/go/" else ""}${node.name}",
                    Modifier.widthIn(max = 140.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Ink.ChipBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.5.sp, lineHeight = 15.sp, color = Ink.Secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "${node.topics} 主题", Modifier.weight(1f),
                    fontSize = 12.5.sp, color = Ink.Secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .semantics { contentDescription = if (starred) "取消收藏${node.title}" else "收藏${node.title}" }
                .clip(CircleShape)
                .clickable { onToggleFav(node.name, node.title) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (starred) "★" else "☆",
                fontSize = 18.sp,
                color = if (starred) Ink.Primary else Ink.Tertiary,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NodeTopicsScreen(
    node: String,
    topics: List<com.v4ex.android.data.Topic>,
    readIds: Set<String>,
    loading: Boolean,
    moreLoading: Boolean,
    canLoadMore: Boolean,
    hint: String?,
    error: String?,
    onBack: () -> Unit,
    onTopic: (com.v4ex.android.data.Topic) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    /** v2 节点详情（GET /nodes/:name），只有填了令牌才有；null 时这块整体不显示 */
    detail: NodeDetail? = null,
    nodeTitle: String = "",
    updating: Boolean = false,
) {
    var pullRefreshing by remember(node) { mutableStateOf(false) }
    LaunchedEffect(loading, updating) { if (!loading && !updating) pullRefreshing = false }
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val nearEnd by remember(canLoadMore) {
        derivedStateOf {
            if (!canLoadMore) {
                false
            } else {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = listState.layoutInfo.totalItemsCount
                total > 0 && last >= total - 4
            }
        }
    }
    LaunchedEffect(nearEnd, canLoadMore, loading, updating) {
        if (nearEnd && !loading && !updating) onLoadMore()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", fontSize = 26.sp, color = Ink.Primary)
            }
            Text(
                nodeTitle.ifBlank { detail?.title.orEmpty() }.ifBlank { node },
                Modifier.weight(1f),
                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink.Primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { uriHandler.openUri("https://www.v2ex.com/go/$node") }) {
                Text("打开原网页", fontSize = 12.sp, color = Ink.Secondary)
            }
        }
        PullToRefreshBox(isRefreshing = pullRefreshing, onRefresh = { if (!loading && !updating) { pullRefreshing = true; onRetry() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "node-info") {
                // v2 节点详情：节点说明 + 主题数/关注数。只有令牌能取到；取不到就整块不占位。
                // 节点名称已显示在顶栏，这里只显示说明和统计。
                if (detail != null && (detail.header.isNotBlank() || detail.topics > 0 || detail.stars > 0)) {
                    Column(Modifier.padding(horizontal = 22.dp)) {
                        if (detail.header.isNotBlank()) {
                            Text(
                                detail.header,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = Ink.Secondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (detail.topics > 0 || detail.stars > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${detail.topics} 主题 · ${detail.stars} 关注",
                                fontSize = 11.5.sp,
                                color = Ink.Tertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (error != null) {
                    com.v4ex.android.ui.ErrorLine(error, onRetry)
                }
            }
            items(topics, key = { it.id }) { topic ->
                com.v4ex.android.ui.TopicRow(
                    topic, read = topic.id.toString() in readIds, showNode = false,
                ) { onTopic(topic) }
            }
            if (loading && !pullRefreshing) {
                item {
                    com.v4ex.android.ui.LoadingBlock("正在读取节点主题…")
                }
            } else if (!loading && (error == null || topics.isNotEmpty())) {
                item {
                    com.v4ex.android.ui.ListFooter(
                        canLoadMore = canLoadMore,
                        loading = moreLoading,
                        hint = hint,
                        onLoadMore = onLoadMore,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

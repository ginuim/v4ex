package com.v4ex.android.ui.screens

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import com.v4ex.android.data.hotAuthors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.selected
import com.v4ex.android.data.HomeCategories
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.RememberedUser
import com.v4ex.android.data.Me
import com.v4ex.android.data.Time
import com.v4ex.android.data.Topic
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.v4ex.android.ui.DisclosureArrow
import com.v4ex.android.ui.Avatar
import com.v4ex.android.ui.ErrorLine
import com.v4ex.android.ui.InlineChip
import com.v4ex.android.ui.ListFooter
import com.v4ex.android.ui.PixelGridLoader
import com.v4ex.android.ui.LoadingBlock
import com.v4ex.android.ui.SectionLabel
import com.v4ex.android.ui.TopicRow
import com.v4ex.android.ui.theme.Ink

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    listState: LazyListState,
    hot: List<Topic>,
    latest: List<Topic>,
    readIds: Set<String>,
    unreadOnly: Boolean,
    onUnreadOnly: (Boolean) -> Unit,
    nodeCount: Int,
    me: Me?,
    rememberedUser: RememberedUser,
    refreshing: Boolean,
    updating: Boolean,
    cachedAt: Long?,
    nodeState: com.v4ex.android.ui.NodeTopicsUi,
    onSelectNode: (String) -> Unit,
    onLoadMoreNode: (String) -> Unit,
    error: String?,
    onTopic: (Topic) -> Unit,
    onAuthor: (String) -> Unit,
    onNode: (String) -> Unit,
    canCompose: Boolean,
    onCompose: () -> Unit,
    onRefresh: () -> Unit,
) {
    var hotExpanded by rememberSaveable { mutableStateOf(false) }
    var categoryKey by rememberSaveable { mutableStateOf("tech") }
    var selectedNode by rememberSaveable { mutableStateOf(HomeCategories.first().nodes.first().name) }
    val category = HomeCategories.first { it.key == categoryKey }
    val firstLoad = hot.isEmpty() && latest.isEmpty() && error == null
    val nodeMatches = nodeState.node == selectedNode
    val canLoadMore = nodeMatches && nodeState.canLoadMore && !nodeState.loading && !nodeState.updating && !nodeState.moreLoading && nodeState.error == null
    val displayedTopics = if (nodeMatches) nodeState.topics else emptyList()
    LaunchedEffect(selectedNode, firstLoad) {
        if (!firstLoad && nodeState.node != selectedNode) onSelectNode(selectedNode)
    }
    // 只看未读：在渲染层过滤，不改动 HomeUi 里的原始数据，
    // 所以关掉开关能立刻恢复，也不需要重新请求。
    val visibleHot = if (unreadOnly) hot.filter { it.id.toString() !in readIds } else hot
    val visibleLatest = if (unreadOnly) displayedTopics.filter { it.id.toString() !in readIds } else displayedTopics

    // 滚到倒数第 4 个就开始加载下一页
    val nearEnd by remember(canLoadMore, selectedNode) {
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
    LaunchedEffect(nearEnd, canLoadMore, selectedNode) {
        if (nearEnd) onLoadMoreNode(selectedNode)
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { onRefresh(); onSelectNode(selectedNode) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {

            item(key = "header") {
                HomeHeader(
                    me = me,
                    rememberedUser = rememberedUser,
                    hotCount = visibleHot.size,
                    latestCount = visibleLatest.size,
                    nodeCount = nodeCount,
                    loading = firstLoad,
                    updating = updating,
                    cachedAt = cachedAt,
                    unreadOnly = unreadOnly,
                    hiddenCount = (hot.size - visibleHot.size) + (displayedTopics.size - visibleLatest.size),
                    onUnreadOnly = onUnreadOnly,
                    canCompose = canCompose,
                    onCompose = onCompose,
                )
            }

            if (error != null) {
                item(key = "error") { ErrorLine(error, onRefresh) }
            }

            if (firstLoad) {
                item(key = "loading") { LoadingBlock("正在读取热门和最新…") }
            }

            if (hot.isNotEmpty()) {
                item(key = "sec-hot") {
                    HotSummary(
                        hot = hot, readIds = readIds, unreadOnly = unreadOnly,
                        expanded = hotExpanded, onClick = { hotExpanded = !hotExpanded },
                    )
                }
                if (hotExpanded) items(visibleHot, key = { "h${it.id}" }) { topic ->
                    TopicRow(
                        topic = topic,
                        read = topic.id.toString() in readIds,
                        onAuthor = { onAuthor(topic.member.username) },
                        onNode = onNode,
                    ) { onTopic(topic) }
                }
            }

            if (!firstLoad) {
                item(key = "categories") {
                    HomeCategoryNavigation(
                        categoryKey = categoryKey, selectedNode = selectedNode,
                        nodeLoading = !nodeMatches || nodeState.loading || nodeState.updating || nodeState.moreLoading,
                        onCategory = { key ->
                            categoryKey = key
                            selectedNode = HomeCategories.first { it.key == key }.nodes.first().name
                        },
                        onNode = { selectedNode = it },
                    )
                }
                if (nodeMatches && nodeState.error != null) {
                    item(key = "node-error") { ErrorLine(nodeState.error) { onSelectNode(selectedNode) } }
                } else if (nodeMatches && !nodeState.loading && !nodeState.updating && visibleLatest.isEmpty()) {
                    item(key = "node-empty") {
                        SectionLabel(if (displayedTopics.isEmpty()) "这个节点还没有帖子" else "这个节点的帖子都读过了")
                    }
                }
            }

            if (visibleLatest.isNotEmpty()) {
                item(key = "sec-new") { SectionLabel("${category.nodes.firstOrNull { it.name == selectedNode }?.title.orEmpty()} ${visibleLatest.size}") }
                items(visibleLatest, key = { "n${it.id}" }) { topic ->
                    TopicRow(
                        topic = topic,
                        read = topic.id.toString() in readIds,
                        onAuthor = { onAuthor(topic.member.username) },
                        onNode = onNode,
                    ) { onTopic(topic) }
                }

                item(key = "footer") {
                    ListFooter(
                        canLoadMore = canLoadMore,
                        loading = nodeState.moreLoading,
                        hint = nodeState.hint,
                        onLoadMore = { onLoadMoreNode(selectedNode) },
                    )
                }
            }
            item(key = "gap") { Spacer(Modifier.height(112.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeHeader(
    me: Me?,
    rememberedUser: RememberedUser,
    hotCount: Int,
    latestCount: Int,
    nodeCount: Int,
    loading: Boolean,
    updating: Boolean,
    cachedAt: Long?,
    unreadOnly: Boolean,
    hiddenCount: Int,
    onUnreadOnly: (Boolean) -> Unit,
    canCompose: Boolean,
    onCompose: () -> Unit,
) {
    Column {
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "V4EX",
                fontSize = 12.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink.Primary,
            )
            Spacer(Modifier.weight(1f))
            if (canCompose) {
                androidx.compose.material3.TextButton(onClick = onCompose) {
                    Text("发帖", fontSize = 13.sp, color = Ink.Primary)
                }
            }
            // 只看未读：整个胶囊可点，状态用底色和文字颜色区分（不用开关控件，视觉太吵）
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (unreadOnly) Ink.FilterSelected else Ink.ChipBg)
                    .clickable { onUnreadOnly(!unreadOnly) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "只看未读",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (unreadOnly) Ink.OnFilterSelected else Ink.Secondary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Time.greeting(), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
            val username = me?.username?.takeIf { it.isNotBlank() } ?: rememberedUser.username
            val avatar = if (me?.username == username) me.avatar else rememberedUser.avatar
            if (username.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Avatar(avatar, username, 26.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(6.dp))
            }
            Text(
                username.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "v 友",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.Primary,
            )
            Text("。", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
        }

        Spacer(Modifier.height(10.dp))
        val showFiltered = !loading && unreadOnly && hiddenCount > 0
        FlowRow(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (nodeCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InlineChip("$nodeCount", Ink.Blue)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (showFiltered) " 个节点可以逛，" else " 个节点可以逛。",
                        fontSize = 13.5.sp,
                        color = Ink.Secondary,
                    )
                }
            }
            if (showFiltered) {
                Text(
                    "已过滤掉 $hiddenCount 篇读过的",
                    fontSize = 13.5.sp,
                    color = Ink.Secondary,
                )
            }
        }
        if (loading || updating) {
            Spacer(Modifier.height(6.dp))
            Text(
                "正在同步…" + (if (updating && !loading) cachedAt?.let { " · 缓存于${Time.ago(it)}" }.orEmpty() else ""),
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = if (loading) 13.5.sp else 12.5.sp,
                color = Ink.Tertiary,
            )
            if (loading) {
                Spacer(Modifier.height(8.dp))
                return@Column
            }
        }
        // 开了只看未读但全读过了：给个明确出口，不然像 Bug
        if (unreadOnly && hotCount == 0 && latestCount == 0 && !loading) {
            Spacer(Modifier.height(4.dp))
            Text(
                "都读完了，去干点别的吧 · 点右上角关掉筛选",
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = 13.sp,
                color = Ink.Tertiary,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Website tabs are editorial collections, distinct from the node parent hierarchy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeCategoryNavigation(
    categoryKey: String, selectedNode: String, nodeLoading: Boolean,
    onCategory: (String) -> Unit, onNode: (String) -> Unit,
) {
    var expanded by rememberSaveable(categoryKey) { mutableStateOf(false) }
    val category = HomeCategories.firstOrNull { it.key == categoryKey } ?: HomeCategories.first()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(HomeCategories, key = { it.key }) { item ->
                val active = item.key == category.key
                Text(
                    item.title,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Ink.OnFilterSelected else Ink.Secondary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (active) Ink.FilterSelected else Color.Transparent)
                        .semantics { selected = active }
                        .clickable { onCategory(item.key) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (if (expanded) category.nodes else category.nodes.take(4)).forEach { node ->
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Ink.ChipBg).semantics {
                            selected = node.name == selectedNode
                            if (node.name == selectedNode && nodeLoading) stateDescription = "正在加载"
                        }
                        .clickable { onNode(node.name) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        node.title, fontSize = 12.sp,
                        color = if (node.name == selectedNode) Ink.Primary else Ink.Secondary,
                        fontWeight = if (node.name == selectedNode) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    // Keep the same footprint while loading changes so chips never reflow.
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                        if (node.name == selectedNode && nodeLoading) {
                            PixelGridLoader(size = 2.dp, gap = 1.dp, color = Ink.Secondary)
                        }
                    }
                }
            }
            if (category.nodes.size > 4) {
                Text(
                    if (expanded) "收起" else "更多",
                    fontSize = 12.sp, color = Ink.Secondary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HotSummary(
    hot: List<Topic>,
    readIds: Set<String>,
    unreadOnly: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val authors = hotAuthors(hot, readIds, unreadOnly)
    val shown = authors.take(5)
    val unreadCount = hot.count { it.id.toString() !in readIds }
    Row(
        Modifier.fillMaxWidth()
            .semantics { stateDescription = if (expanded) "热门已展开" else "热门已收起" }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (shown.isNotEmpty()) {
            Box(Modifier.width((30 + (shown.size - 1) * 19).dp).height(30.dp)) {
                shown.forEachIndexed { index, author ->
                    Box(
                        Modifier.offset(x = (index * 19).dp)
                            .zIndex((shown.size - index).toFloat())
                            .size(30.dp).clip(CircleShape).background(Ink.Surface)
                            .padding(2.dp),
                    ) {
                        Avatar(
                            author.member.avatar, author.member.username, 26.dp,
                            Modifier.alpha(if (author.unread) 0.82f else 0.32f),
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (unreadOnly) "$unreadCount 篇未读热门" else "${hot.size} 篇热门",
                    fontSize = 13.sp, lineHeight = 16.sp, color = Ink.Primary,
                    style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                )
                Spacer(Modifier.width(4.dp))
                DisclosureArrow(expanded)
            }
            Text(
                when {
                    unreadCount == 0 -> "热门都读过了"
                    unreadOnly -> "只显示未读 · 点击${if (expanded) "收起" else "展开"}"
                    else -> "$unreadCount 篇未读 · ${hot.size - unreadCount} 篇已读"
                },
                fontSize = 11.5.sp, lineHeight = 14.sp, color = Ink.Secondary,
                style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}

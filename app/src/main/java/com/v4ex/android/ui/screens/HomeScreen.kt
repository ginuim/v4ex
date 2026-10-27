package com.v4ex.android.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import com.v4ex.android.data.Me
import com.v4ex.android.data.Time
import com.v4ex.android.data.Topic
import com.v4ex.android.ui.Avatar
import com.v4ex.android.ui.ErrorLine
import com.v4ex.android.ui.InlineChip
import com.v4ex.android.ui.ListFooter
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
    refreshing: Boolean,
    updating: Boolean,
    cachedAt: Long?,
    moreLoading: Boolean,
    canLoadMore: Boolean,
    hint: String?,
    error: String?,
    onTopic: (Topic) -> Unit,
    onAuthor: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // 只看未读：在渲染层过滤，不改动 HomeUi 里的原始数据，
    // 所以关掉开关能立刻恢复，也不需要重新请求。
    val visibleHot = if (unreadOnly) hot.filter { it.id.toString() !in readIds } else hot
    val visibleLatest = if (unreadOnly) latest.filter { it.id.toString() !in readIds } else latest

    // 滚到倒数第 4 个就开始加载下一页
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
    LaunchedEffect(nearEnd, canLoadMore) {
        if (nearEnd) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            val firstLoad = hot.isEmpty() && latest.isEmpty() && error == null

            item(key = "header") {
                HomeHeader(
                    me = me,
                    hotCount = visibleHot.size,
                    latestCount = visibleLatest.size,
                    nodeCount = nodeCount,
                    loading = firstLoad,
                    updating = updating,
                    cachedAt = cachedAt,
                    unreadOnly = unreadOnly,
                    hiddenCount = (hot.size - visibleHot.size) + (latest.size - visibleLatest.size),
                    onUnreadOnly = onUnreadOnly,
                    onRefresh = onRefresh,
                )
            }

            if (error != null) {
                item(key = "error") { ErrorLine(error, onRefresh) }
            }

            if (firstLoad) {
                item(key = "loading") { LoadingBlock("正在读取热门和最新…") }
            }

            if (visibleHot.isNotEmpty()) {
                item(key = "sec-hot") { SectionLabel("热门 ${visibleHot.size}") }
                items(visibleHot, key = { "h${it.id}" }) { topic ->
                    TopicRow(
                        topic = topic,
                        read = topic.id.toString() in readIds,
                        onAuthor = { onAuthor(topic.member.username) },
                    ) { onTopic(topic) }
                }
            }

            if (visibleLatest.isNotEmpty()) {
                item(key = "sec-new") { SectionLabel("最新 ${visibleLatest.size}") }
                items(visibleLatest, key = { "n${it.id}" }) { topic ->
                    TopicRow(
                        topic = topic,
                        read = topic.id.toString() in readIds,
                        onAuthor = { onAuthor(topic.member.username) },
                    ) { onTopic(topic) }
                }

                item(key = "footer") {
                    ListFooter(
                        canLoadMore = canLoadMore,
                        loading = moreLoading,
                        hint = hint,
                        onLoadMore = onLoadMore,
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
    hotCount: Int,
    latestCount: Int,
    nodeCount: Int,
    loading: Boolean,
    updating: Boolean,
    cachedAt: Long?,
    unreadOnly: Boolean,
    hiddenCount: Int,
    onUnreadOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
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
            // 只看未读：整个胶囊可点，状态用底色和文字颜色区分（不用开关控件，视觉太吵）
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (unreadOnly) Ink.Primary else Ink.ChipBg)
                    .clickable { onUnreadOnly(!unreadOnly) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "只看未读",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (unreadOnly) Color.White else Ink.Secondary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Refresh, "刷新", Modifier.size(18.dp), tint = Ink.Secondary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Time.greeting(), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
            if (me != null) {
                Spacer(Modifier.width(8.dp))
                Avatar(me.avatar, me.username, 26.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(6.dp))
            }
            Text(
                me?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "v 友",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.Primary,
            )
            Text("。", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
        }

        Spacer(Modifier.height(10.dp))
        if (loading) {
            Text(
                "正在同步…",
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = 13.5.sp,
                color = Ink.Tertiary,
            )
            Spacer(Modifier.height(8.dp))
            return@Column
        }
        // 有缓存先亮出来了，后台还在拉新的：明确告诉用户这不是最新数据
        if (updating) {
            Text(
                "正在同步…" + (cachedAt?.let { " · 缓存于${Time.ago(it)}" } ?: ""),
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = 12.5.sp,
                color = Ink.Tertiary,
            )
            Spacer(Modifier.height(6.dp))
        }
        FlowRow(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("你有 ", fontSize = 13.5.sp, color = Ink.Secondary)
                Spacer(Modifier.width(4.dp))
                InlineChip("$hotCount", Ink.Blue)
                Spacer(Modifier.width(4.dp))
                Text(" 条热门、", fontSize = 13.5.sp, color = Ink.Secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                InlineChip("$latestCount", Ink.Purple)
                Spacer(Modifier.width(4.dp))
                Text(" 条最新。", fontSize = 13.5.sp, color = Ink.Secondary)
            }
            if (nodeCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InlineChip("$nodeCount", Ink.Blue)
                    Spacer(Modifier.width(4.dp))
                    Text(" 个节点可以逛。", fontSize = 13.5.sp, color = Ink.Secondary)
                }
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
        } else if (unreadOnly && hiddenCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "已过滤掉 $hiddenCount 篇读过的",
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = 12.5.sp,
                color = Ink.Tertiary,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

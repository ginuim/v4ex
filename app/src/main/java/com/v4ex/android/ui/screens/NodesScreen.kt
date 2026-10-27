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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val filtered = if (query.isBlank()) nodes else nodes.filter {
        it.name.contains(query, true) || it.title.contains(query, true)
    }
    val favNames = remember(favNodes) { favNodes.map { it.first }.toSet() }

    Column(Modifier.fillMaxSize()) {
        Text(
            "节点",
            modifier = Modifier.padding(start = 22.dp, top = 10.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Ink.Primary,
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.ChipBg)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, Modifier.size(16.dp), tint = Ink.Tertiary)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("搜索节点", fontSize = 14.sp, color = Ink.Tertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Ink.Primary),
                    cursorBrush = SolidColor(Ink.Primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 三种情形要分开：还在加载 / 搜不到 / 节点根本没拉下来
        if (filtered.isEmpty() && loading) {
            com.v4ex.android.ui.LoadingBlock("正在读取节点…")
        }
        if (filtered.isEmpty() && !loading && query.isNotBlank()) {
            EmptyState(
                title = "没有匹配的节点",
                hint = "「$query」让 1379 个节点集体沉默了。\n换个关键词，或者试试 /go/ 后面的英文名。",
                art = EmptyArt.Search,
                action = "清空搜索" to { onQuery("") },
            )
        } else if (filtered.isEmpty() && !loading && query.isBlank()) {
            EmptyState(
                title = "节点还没到手",
                hint = "网络可能断了，也可能 V2EX 正在打个盹。",
                art = EmptyArt.Offline,
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.name }) { node ->
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
                        Text(
                            "/go/${node.name} · ${node.topics} 主题",
                            fontSize = 12.5.sp,
                            color = Ink.Secondary,
                        )
                    }
                    val starred = node.name in favNames
                    Box(
                        Modifier
                            .size(36.dp)
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
            item { Spacer(Modifier.height(110.dp)) }
        }
    }
}

@Composable
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
) {
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
    LaunchedEffect(nearEnd, canLoadMore) {
        if (nearEnd) onLoadMore()
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
            Text("/go/$node", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
            if (topics.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Text("${topics.size} 条", fontSize = 12.sp, color = Ink.Tertiary)
            }
        }
        if (error != null) {
            com.v4ex.android.ui.ErrorLine(error, onRetry)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(topics, key = { it.id }) { topic ->
                com.v4ex.android.ui.TopicRow(topic, read = topic.id.toString() in readIds) { onTopic(topic) }
            }
            if (loading) {
                item {
                    Text(
                        "正在读取…",
                        Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        fontSize = 12.5.sp,
                        color = Ink.Tertiary,
                    )
                }
            }
            item {
                com.v4ex.android.ui.ListFooter(
                    canLoadMore = canLoadMore,
                    loading = moreLoading,
                    hint = hint,
                    onLoadMore = onLoadMore,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

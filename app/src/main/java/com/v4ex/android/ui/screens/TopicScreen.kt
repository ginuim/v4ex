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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.Conversation
import com.v4ex.android.data.Reply
import com.v4ex.android.data.SharePayload
import com.v4ex.android.data.Time
import com.v4ex.android.data.Topic
import com.v4ex.android.data.replySharePayload
import com.v4ex.android.data.topicSharePayload
import com.v4ex.android.ui.Avatar
import com.v4ex.android.ui.EmptyArt
import com.v4ex.android.ui.EmptyState
import com.v4ex.android.ui.ErrorLine
import com.v4ex.android.ui.LoadingBlock
import com.v4ex.android.ui.PixelLoader
import com.v4ex.android.ui.RichContent
import com.v4ex.android.ui.ShareSheet
import com.v4ex.android.ui.replyItems
import com.v4ex.android.ui.theme.Ink

@Composable
fun TopicScreen(
    loading: Boolean,
    topic: Topic?,
    replies: List<Reply>,
    error: String?,
    threaded: Boolean,
    onToggleThreaded: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAuthor: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    // 分享面板的内容：帖子本身，或长按楼层时的「带 #replyN 锚点」的楼层摘录。
    var sharePayload by remember { mutableStateOf<SharePayload?>(null) }
    // 对话视图只是「同一批回复换一种排序」，条数不变，所以切换模式时 LazyColumn 的
    // 滚动位置基本原地不动（item key 也刻意保持 "reply-${id}" 不变）。
    val rows = remember(replies, threaded) { Conversation.rows(replies, threaded) }
    val conversationCount = remember(rows) { rows.count { it.depth == 0 && it.childCount > 0 } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", fontSize = 26.sp, color = Ink.Primary)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { topic?.let { sharePayload = topicSharePayload(it) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "分享",
                    tint = Ink.Secondary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Text(
                "打开原帖",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { topic?.let { uriHandler.openUri(it.url) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 13.sp,
                color = Ink.Secondary,
            )
        }

        when {
            error != null -> ErrorLine(error, onRetry)
            topic == null && loading -> LoadingBlock("正在读取主题…")
            topic == null -> EmptyState(
                title = "这帖子不见了",
                hint = "可能被删了，也可能作者改主意了。\n返回上一页，换个帖子看看。",
                art = EmptyArt.Topic,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(horizontal = 22.dp)) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            topic.title,
                            fontSize = 21.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink.Primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(
                                url = topic.member.avatar,
                                name = topic.member.username,
                                size = 22.dp,
                                modifier = Modifier.clickable { onAuthor(topic.member.username) },
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                buildString {
                                    append(topic.member.username)
                                    append(" · /go/").append(topic.node.name)
                                    append(" · ").append(Time.ago(topic.created))
                                    append(" · ").append(topic.replies).append(" 回复")
                                },
                                fontSize = 12.5.sp,
                                color = Ink.Secondary,
                            )
                        }
                        if (topic.content.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            RichContent(
                                text = topic.content,
                                fontSize = 15.sp,
                                lineHeight = 25.sp,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                    ReplyHeader(
                        count = replies.size,
                        conversationCount = if (threaded) conversationCount else 0,
                        threaded = threaded,
                        onToggle = onToggleThreaded,
                    )
                }

                replyItems(
                    rows = rows,
                    onAuthor = onAuthor,
                    onShareReply = { reply ->
                        topic?.let { sharePayload = replySharePayload(it, reply) }
                    },
                )

                item {
                    if (loading) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            PixelLoader(size = 6.dp, gap = 3.dp, color = Ink.Secondary)
                            Text("正在读取回复…", fontSize = 12.5.sp, color = Ink.Tertiary)
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    sharePayload?.let { payload ->
        ShareSheet(
            payload = payload,
            onDismiss = { sharePayload = null },
            // 楼层分享的链接带 #replyN，浏览器会直接跳到那一楼
            onOpenInBrowser = { runCatching { uriHandler.openUri(payload.link) } },
        )
    }
}

/**
 * 回复区头部：左边「回复 N · M 组对话」，右边「楼层 / 对话」切换。
 * 对话数只在对话模式下显示——楼层模式下它没有意义。
 */
@Composable
private fun ReplyHeader(
    count: Int,
    conversationCount: Int,
    threaded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append("回复 ").append(count)
                if (threaded && conversationCount > 0) {
                    append(" · ").append(conversationCount).append(" 组对话")
                }
            },
            fontSize = 12.5.sp,
            color = Ink.Secondary,
        )
        Spacer(Modifier.weight(1f))
        ReplyModeToggle(threaded = threaded, onToggle = onToggle)
    }
}

@Composable
private fun ReplyModeToggle(threaded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Ink.ChipBg)
            .padding(2.dp),
    ) {
        ModeTab("楼层", selected = !threaded) { if (threaded) onToggle() }
        ModeTab("对话", selected = threaded) { if (!threaded) onToggle() }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp,
        color = if (selected) Ink.Primary else Ink.Tertiary,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
    )
}

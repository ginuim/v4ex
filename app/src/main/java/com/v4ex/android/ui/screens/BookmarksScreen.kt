package com.v4ex.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.Bookmark
import com.v4ex.android.data.Time
import com.v4ex.android.ui.EmptyArt
import com.v4ex.android.ui.EmptyState
import com.v4ex.android.ui.LoadingBlock
import com.v4ex.android.ui.theme.Ink

/**
 * 收藏的帖子。纯本地数据，离线也能看；
 * 点某一项会按 id 重新拉帖子全文，所以收藏里只存展示字段。
 */
@Composable
fun BookmarksScreen(
    items: List<Bookmark>,
    loaded: Boolean,
    onBack: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    onRemove: (Int) -> Unit,
) {
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
            Text(
                "收藏的帖子",
                modifier = Modifier.padding(start = 6.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.Primary,
            )
            if (items.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text("${items.size}", fontSize = 13.sp, color = Ink.Tertiary)
            }
        }

        if (items.isEmpty()) {
            // 同历史页：没读完磁盘前不能说「还没有收藏」
            if (!loaded) {
                LoadingBlock("正在读取本机收藏…")
            } else {
                EmptyState(
                    title = "还没有收藏",
                    hint = "在帖子顶部点 ☆ 就能收下。\n只记在本机，不会同步到 V2EX。",
                    art = EmptyArt.History,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { entry ->
                BookmarkRow(entry, onClick = { onOpen(entry) }, onRemove = { onRemove(entry.id) })
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
    }
}

@Composable
private fun BookmarkRow(entry: Bookmark, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.title.ifBlank { "（无标题）" },
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.nodeName.isNotBlank()) {
                    Text(
                        entry.nodeName,
                        Modifier.widthIn(max = 140.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Ink.ChipBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.5.sp, lineHeight = 15.sp, color = Ink.Secondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    buildString {
                        if (entry.author.isNotBlank()) append("@").append(entry.author)
                        if (entry.replies > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(entry.replies).append(" 回复")
                        }
                        if (entry.savedAt > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("收藏于 ").append(Time.ago(entry.savedAt))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 12.5.sp,
                    color = Ink.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // 单项取消收藏，避免误点整行就清掉
        Box(
            Modifier
                .size(34.dp)
                .semantics { contentDescription = "取消收藏 ${entry.title}" }
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 13.sp, color = Ink.Tertiary)
        }
    }
}

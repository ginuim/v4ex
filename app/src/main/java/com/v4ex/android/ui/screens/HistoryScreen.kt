package com.v4ex.android.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.HistoryEntry
import com.v4ex.android.data.Time
import com.v4ex.android.ui.EmptyArt
import com.v4ex.android.ui.EmptyState
import com.v4ex.android.ui.LoadingBlock
import com.v4ex.android.ui.theme.Ink

/**
 * 浏览历史。纯本地数据，离线也能看；
 * 点某一项会按 id 重新拉帖子全文，所以历史里只存展示字段。
 */
@Composable
fun HistoryScreen(
    items: List<HistoryEntry>,
    loaded: Boolean,
    onBack: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onRemove: (Int) -> Unit,
    onClearAll: () -> Unit,
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
                "浏览历史",
                modifier = Modifier.padding(start = 6.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.Primary,
            )
            if (items.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text("${items.size}", fontSize = 13.sp, color = Ink.Tertiary)
            }
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    "清空",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    color = Ink.Secondary,
                )
            }
        }

        if (items.isEmpty()) {
            // 读本地 DataStore 也要一帧：先按「还没读完」显示，读完才敢说「没有记录」。
            // 用 loaded 区分，避免启动瞬间闪一下「还没有浏览记录」的插画。
            if (!loaded) {
                LoadingBlock("正在读取本机记录…")
            } else {
                EmptyState(
                    title = "一片空白",
                    hint = "你还没点开过任何帖子。\n放心，这里只记在本机，不会有人知道你偷看了什么。",
                    art = EmptyArt.History,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { entry ->
                HistoryRow(entry, onClick = { onOpen(entry) }, onRemove = { onRemove(entry.id) })
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, onRemove: () -> Unit) {
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
            Text(
                buildString {
                    if (entry.nodeName.isNotBlank()) append("/go/").append(entry.nodeName)
                    if (entry.author.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("@").append(entry.author)
                    }
                    if (entry.replies > 0) append(" · ").append(entry.replies).append(" 回复")
                    if (isNotEmpty()) append(" · ")
                    append(Time.ago(entry.viewedAt)).append("看过")
                },
                fontSize = 12.5.sp,
                color = Ink.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        // 单项删除，避免误点整行就清掉
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 13.sp, color = Ink.Tertiary)
        }
    }
}

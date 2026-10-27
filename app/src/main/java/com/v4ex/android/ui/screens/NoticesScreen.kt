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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.Notice
import com.v4ex.android.ui.EmptyArt
import com.v4ex.android.ui.EmptyState
import com.v4ex.android.ui.ErrorLine
import com.v4ex.android.ui.NoticeRow
import com.v4ex.android.ui.theme.Ink

@Composable
fun NoticesScreen(
    hasToken: Boolean,
    items: List<Notice>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onAuth: () -> Unit,
    onAuthor: (String) -> Unit,
    onOpenTopic: (Notice) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("通知", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
            if (hasToken && items.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text("${items.size}", fontSize = 13.sp, color = Ink.Tertiary)
            }
            Spacer(Modifier.weight(1f))
            if (hasToken) {
                if (items.isNotEmpty()) {
                    Text(
                        "全部已读",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onClearAll)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        color = Ink.Secondary,
                    )
                    Spacer(Modifier.size(2.dp))
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Refresh, "刷新", Modifier.size(18.dp), tint = Ink.Secondary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            !hasToken -> AuthGate(onAuth)
            error != null -> ErrorLine(error, onRefresh)
            loading && items.isEmpty() -> com.v4ex.android.ui.LoadingBlock("正在读取提醒…")
            items.isEmpty() -> EmptyState(
                title = "世界很安静",
                hint = "没有未读提醒。这可能是好事，\n也可能你只是很久没发帖了。",
                art = EmptyArt.Notices,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { notice ->
                    NoticeRow(
                        notice = notice,
                        onAuthor = { onAuthor(notice.member) },
                        onClick = { onOpenTopic(notice) },
                    )
                }
                item { Spacer(Modifier.height(110.dp)) }
            }
        }
    }
}

@Composable
private fun AuthGate(onAuth: () -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {
        Text("提醒需要登录", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "V2EX 只提供 Personal Access Token 认证。去网页版设置里创建一个令牌，粘贴进来就能看到未读提醒和你的资料。",
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Ink.Secondary,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.Primary)
                .clickable(onClick = onAuth)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("填写令牌", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "v2ex.com/settings/token",
            fontSize = 12.5.sp,
            color = Ink.Tertiary,
        )
    }
}

package com.v4ex.android.ui.screens

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.MemberProfile
import com.v4ex.android.data.Time
import com.v4ex.android.ui.ProBadge
import com.v4ex.android.ui.Avatar
import com.v4ex.android.ui.EmptyArt
import com.v4ex.android.ui.EmptyState
import com.v4ex.android.ui.ErrorLine
import com.v4ex.android.ui.LoadingBlock
import com.v4ex.android.ui.SectionLabel
import com.v4ex.android.ui.theme.Ink

@Composable
fun MemberScreen(
    loading: Boolean,
    profile: MemberProfile?,
    error: String?,
    username: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenTopic: (com.v4ex.android.data.Topic) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val topicsUrl = "https://www.v2ex.com/member/$username/topics"

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", fontSize = 26.sp, color = Ink.Primary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "TA 的帖子",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { uriHandler.openUri(topicsUrl) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 13.sp,
                color = Ink.Secondary,
            )
        }

        when {
            error != null -> ErrorLine(error, onRetry)
            profile == null && loading -> LoadingBlock("正在读取资料…")
            // 数据回来了但没拿到人：这是「空」，不是「加载中」，别再用转圈的样子
            profile == null -> EmptyState(
                title = "查无此人",
                hint = "这个账号可能注销了，也可能只是改名了。\n反正这里没有 TA 的档案。",
                art = EmptyArt.Nobody,
            )
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile.avatar, profile.username, 66.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "@${profile.username}",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink.Primary,
                            )
                            if (profile.pro) {
                                Spacer(Modifier.width(8.dp))
                                ProBadge()
                            }
                        }
                        if (profile.tagline.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(profile.tagline, fontSize = 13.sp, color = Ink.Secondary)
                        }
                    }
                }

                if (profile.bio.isNotBlank()) {
                    Text(
                        profile.bio,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = Ink.Primary,
                    )
                }

                Spacer(Modifier.height(12.dp))
                SectionLabel("资料")
                if (profile.created > 0) ProfileRow("注册于", Time.date(profile.created))
                if (profile.location.isNotBlank()) ProfileRow("位置", profile.location)
                if (profile.website.isNotBlank()) {
                    ProfileRow("网站", profile.website, onClick = {
                        uriHandler.openUri(normalizeUrl(profile.website))
                    })
                }
                if (profile.github.isNotBlank()) {
                    ProfileRow("GitHub", profile.github, onClick = {
                        uriHandler.openUri("https://github.com/${profile.github}")
                    })
                }
                if (profile.url.isNotBlank()) {
                    ProfileRow("个人主页", profile.url) { uriHandler.openUri(normalizeUrl(profile.url)) }
                }
                if (profile.twitter.isNotBlank()) {
                    ProfileRow("Twitter", profile.twitter) {
                        val handle = profile.twitter.trim().removePrefix("@")
                        uriHandler.openUri(if (handle.startsWith("https://") || handle.startsWith("http://")) handle else "https://twitter.com/$handle")
                    }
                }
                ProfileRow("PSN", profile.psn)
                ProfileRow("BTC 地址", profile.btc)
                if (profile.lastModified > 0) ProfileRow("资料更新于", Time.date(profile.lastModified))
                if (profile.id > 0) ProfileRow("会员 ID", profile.id.toString())

                Spacer(Modifier.height(22.dp))
                Text(
                    "V2EX 已经关掉了「某人的主题」接口，所以这里只有资料。点右上角可以在浏览器里看 TA 的帖子。",
                    modifier = Modifier.padding(horizontal = 22.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Ink.Tertiary,
                )
                Spacer(Modifier.height(40.dp))
                @Suppress("UNUSED_EXPRESSION") onOpenTopic
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    if (value.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, fontSize = 14.sp, color = Ink.Secondary)
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                value,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                fontSize = 14.sp,
                color = if (onClick != null) Ink.Blue else Ink.Primary,
            )
        }
    }
}

private fun normalizeUrl(url: String): String =
    if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"

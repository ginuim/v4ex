package com.v4ex.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.Me
import com.v4ex.android.data.Time
import com.v4ex.android.ui.Avatar
import com.v4ex.android.ui.SectionLabel
import com.v4ex.android.ui.theme.Ink

@Composable
fun MeScreen(
    hasToken: Boolean,
    me: Me?,
    error: String?,
    historyCount: Int,
    onOpenHistory: () -> Unit,
    onAuth: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "我的",
            modifier = Modifier.padding(start = 22.dp, top = 10.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Ink.Primary,
        )

        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(me?.avatar, me?.username ?: "V", 58.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (hasToken && me != null) "@${me.username}" else "@未登录",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink.Primary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        !hasToken -> "填写令牌后可查看资料与提醒"
                        error != null -> error
                        me?.tagline?.isNotBlank() == true -> me.tagline
                        else -> " "
                    },
                    fontSize = 13.sp,
                    color = Ink.Secondary,
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("账号")
        InfoRow("会员", if (me?.pro == true) "PRO" else "普通")
        if (hasToken && me != null) {
            InfoRow("会员 id", me.id.toString())
            InfoRow("令牌有效期", "${me.tokenDays.toInt()} 天 · ${me.tokenScope}")
            InfoRow("注册于", Time.date(me.created))
        }
        InfoRow("数据来源", "V2EX API 2.0")

        Spacer(Modifier.height(26.dp))
        SectionLabel("阅读")
        ActionRow(
            if (historyCount > 0) "浏览历史 · $historyCount" else "浏览历史",
            onOpenHistory,
        )

        Spacer(Modifier.height(26.dp))
        SectionLabel("操作")
        ActionRow(if (hasToken) "更换令牌" else "填写令牌", onAuth)
        ActionRow("在浏览器打开 V2EX", onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.v2ex.com"))
            )
        })
        if (hasToken) ActionRow("清空令牌", onSignOut, danger = true)

        Spacer(Modifier.height(26.dp))
        Text(
            "V4EX ${com.v4ex.android.BuildConfig.VERSION_NAME} · 只读客户端\n内容来自 V2EX，与官方无关",
            modifier = Modifier.padding(horizontal = 22.dp),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Ink.Tertiary,
        )
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = Ink.Secondary)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = Ink.Primary)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        fontSize = 15.sp,
        color = if (danger) Ink.Badge else Ink.Primary,
    )
}

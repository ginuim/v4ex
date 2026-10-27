package com.v4ex.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.Me
import com.v4ex.android.R
import com.v4ex.android.data.RateLimit
import com.v4ex.android.data.Time
import com.v4ex.android.data.TokenInfo
import com.v4ex.android.ui.ProBadge
import com.v4ex.android.ui.Avatar
import com.v4ex.android.ui.SectionLabel
import com.v4ex.android.ui.theme.Ink
import com.v4ex.android.ui.theme.ThemeMode

@Composable
fun MeScreen(
    hasToken: Boolean,
    me: Me?,
    error: String?,
    historyCount: Int,
    tokenInfo: TokenInfo?,
    rateLimit: RateLimit?,
    creatingToken: Boolean,
    canCreateToken: Boolean,
    createdToken: TokenInfo?,
    createError: String?,
    onOpenHistory: () -> Unit,
    onAuth: () -> Unit,
    onSignOut: () -> Unit,
    onCreateToken: (scope: String, expirationSeconds: Int) -> Unit,
    onDismissCreate: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val authorWebsite = stringResource(R.string.author_website_url)
    var showCreateDialog by remember { mutableStateOf(false) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hasToken && me != null) "@${me.username}" else "@未登录",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink.Primary,
                    )
                    if (hasToken && me?.pro == true) {
                        Spacer(Modifier.width(8.dp))
                        ProBadge()
                    }
                }
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
        if (hasToken && me?.pro == true) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("会员", fontSize = 14.sp, color = Ink.Secondary)
                Spacer(Modifier.weight(1f))
                ProBadge()
            }
        } else InfoRow("会员", when {
            !hasToken -> "未登录"
            me == null -> "身份未确认"
            me.pro -> "PRO"
            else -> "普通"
        })
        if (hasToken && me != null) {
            InfoRow("会员 id", me.id.toString())
            InfoRow("令牌有效期", "${me.tokenDays.toInt()} 天 · ${me.tokenScope}")
            InfoRow("注册于", Time.date(me.created))
            InfoRow("令牌已用", "${tokenInfo?.totalUsed?.takeIf { it > 0 } ?: me.tokenTotalUsed} 次")
            val expiration = tokenInfo?.expiration?.takeIf { it > 0 } ?: me.tokenExpiration
            if (expiration > 0) InfoRow("令牌到期", Time.date(expiration))
            if (rateLimit != null && rateLimit.limit > 0) {
                InfoRow("本小时配额", "${rateLimit.remaining} / ${rateLimit.limit} 次")
            }
        }
        InfoRow("数据来源", "V2EX API 2.0")

        Spacer(Modifier.height(26.dp))
        SectionLabel("阅读")
        ActionRow(
            if (historyCount > 0) "浏览历史 · $historyCount" else "浏览历史",
            onOpenHistory,
        )

        Spacer(Modifier.height(26.dp))
        SectionLabel("外观")
        AppearanceToggle(themeMode, onThemeMode)

        Spacer(Modifier.height(26.dp))
        SectionLabel("操作")
        ActionRow(if (hasToken) "更换令牌" else "填写令牌", onAuth)
        if (canCreateToken) {
            ActionRow("创建新令牌", onClick = {
                if (createdToken != null) onDismissCreate()
                showCreateDialog = true
            })
        }
        ActionRow("在浏览器打开 V2EX", onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.v2ex.com"))
            )
        })
        if (hasToken) ActionRow("清空令牌", onSignOut, danger = true)

        Spacer(Modifier.height(26.dp))
        Text(
            "V4EX ${com.v4ex.android.BuildConfig.VERSION_NAME} · 以阅读为主\n内容来自 V2EX，与官方无关",
            modifier = Modifier.padding(horizontal = 22.dp),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Ink.Tertiary,
        )
        Text(
            stringResource(R.string.author_copyright),
            modifier = Modifier.padding(horizontal = 22.dp).clickable(role = Role.Button) {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(authorWebsite)))
            },
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Ink.Tertiary,
        )
        Spacer(Modifier.height(120.dp))
    }

    if (showCreateDialog && canCreateToken) {
        CreateTokenDialog(
            creating = creatingToken,
            created = createdToken,
            error = createError,
            onConfirm = onCreateToken,
            onDismiss = {
                showCreateDialog = false
                onDismissCreate()
            },
        )
    }
}

@Composable
private fun AppearanceToggle(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.ChipBg)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val active = selected == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Ink.Elevated else Color.Transparent)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mode.label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) Ink.Primary else Ink.Secondary,
                )
            }
        }
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

/**
 * 创建 Personal Access Token 的应用内对话框。
 *
 * 新令牌的明文只在 `POST /token` 的响应里出现一次，所以同一对话框分两态：
 * 表单态（`created == null`）选 scope 与有效期；结果态把明文用等宽字体展示，
 * 并提醒用户当场复制走。
 */
@Composable
private fun CreateTokenDialog(
    creating: Boolean,
    created: TokenInfo?,
    error: String?,
    onConfirm: (scope: String, expirationSeconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var scope by remember { mutableStateOf("everything") }
    var days by remember { mutableStateOf(30) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor = Ink.Surface,
        title = {
            Text(
                if (created == null) "创建新令牌" else "新令牌",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.Primary,
            )
        },
        text = {
            if (created == null) {
                CreateTokenForm(
                    scope = scope,
                    onScopeChange = { scope = it },
                    days = days,
                    onDaysChange = { days = it },
                    error = error,
                )
            } else {
                CreatedTokenBody(created)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (created == null) {
                        val seconds = when (days) {
                            30 -> 2592000
                            60 -> 5184000
                            90 -> 7776000
                            else -> 15552000
                        }
                        onConfirm(scope, seconds)
                    } else {
                        onDismiss()
                    }
                },
                enabled = if (created == null) !creating else true,
            ) {
                Text(
                    when {
                        created != null -> "我已保存"
                        creating -> "正在创建…"
                        else -> "创建"
                    },
                    fontSize = 15.sp,
                    color = when {
                        created != null -> Ink.Primary
                        creating -> Ink.Tertiary
                        else -> Ink.Primary
                    },
                )
            }
        },
        dismissButton = if (created == null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("取消", fontSize = 15.sp, color = Ink.Secondary)
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun CreateTokenForm(
    scope: String,
    onScopeChange: (String) -> Unit,
    days: Int,
    onDaysChange: (Int) -> Unit,
    error: String?,
) {
    Column {
        Text(
            "新令牌只在这里显示一次，关掉就再也看不到；最多只能有 10 个令牌。",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Ink.Secondary,
        )

        Spacer(Modifier.height(16.dp))
        DialogFieldLabel("权限")
        Spacer(Modifier.height(6.dp))
        ChoiceRow(
            label = "everything · 可再创建令牌",
            selected = scope == "everything",
            onClick = { onScopeChange("everything") },
        )
        ChoiceRow(
            label = "regular · 仅读写",
            selected = scope == "regular",
            onClick = { onScopeChange("regular") },
        )

        Spacer(Modifier.height(16.dp))
        DialogFieldLabel("有效期")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in listOf(30, 60, 90, 180)) {
                DayChip(
                    label = "$option 天",
                    selected = days == option,
                    onClick = { onDaysChange(option) },
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.Badge)
        }
    }
}

@Composable
private fun CreatedTokenBody(created: TokenInfo) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Ink.ChipBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            SelectionContainer {
                Text(
                    created.token,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Ink.Primary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "现在复制走，关掉后无法再次查看。",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Ink.Secondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "有效期 ${created.goodForDays.toInt()} 天 · scope ${created.scope}",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Ink.Tertiary,
        )
    }
}

@Composable
private fun DialogFieldLabel(label: String) {
    Text(label, fontSize = 13.sp, color = Ink.Tertiary)
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Ink.ChipBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(1.dp, if (selected) Ink.Primary else Ink.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Ink.Primary))
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            fontSize = 13.5.sp,
            color = if (selected) Ink.Primary else Ink.Secondary,
        )
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ink.Primary else Ink.ChipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) Ink.OnPrimary else Ink.Secondary,
        )
    }
}

package com.v4ex.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import coil.compose.AsyncImage
import com.v4ex.android.data.Notice
import com.v4ex.android.data.Time
import com.v4ex.android.data.Topic
import com.v4ex.android.ui.theme.Ink

@Composable
fun Avatar(url: String?, name: String, size: Dp = 38.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Ink.ChipBg),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                fontSize = (size.value * 0.42f).sp,
                color = Ink.Secondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        fontSize = 12.5.sp,
        color = Ink.Secondary,
    )
}

/** 行内小胶囊，参考图里「你有 [2] 3 封来信」那种。 */
@Composable
fun InlineChip(text: String, color: Color = Ink.Blue, bg: Color = color.copy(alpha = 0.10f)) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 11.5.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TopicRow(
    topic: Topic,
    read: Boolean = false,
    onAuthor: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
    ) {
        Avatar(
            url = topic.member.avatar,
            name = topic.member.username,
            size = 38.dp,
            modifier = if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = topic.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (read) Ink.Tertiary else Ink.Primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(Time.ago(topic.created), fontSize = 12.sp, color = Ink.Tertiary)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append("/go/").append(topic.node.name)
                    append(" · ").append(topic.replies).append(" 回复")
                    if (topic.lastReplyBy.isNotBlank()) append(" · @").append(topic.lastReplyBy)
                },
                fontSize = 13.sp,
                color = Ink.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NoticeRow(
    notice: Notice,
    onAuthor: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Avatar(
            url = notice.avatar,
            name = notice.member.ifBlank { "V" },
            size = 36.dp,
            modifier = if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@" + notice.member.ifBlank { "v2ex" },
                    modifier = Modifier.then(
                        if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier,
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(Time.ago(notice.created), fontSize = 12.sp, color = Ink.Tertiary)
            }
            Spacer(Modifier.height(3.dp))
            Text(notice.text, fontSize = 13.5.sp, lineHeight = 19.sp, color = Ink.Secondary)
            if (notice.payload.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    notice.payload,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Ink.Tertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class Nav(val label: String, val icon: ImageVector)

@Composable
fun BottomBar(selected: Int, unread: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Nav("首页", Icons.Filled.Home),
        Nav("节点", Icons.Filled.Search),
        Nav("通知", Icons.Filled.Notifications),
        Nav("我的", Icons.Filled.Person),
    )
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.97f),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                // 注意：外层不要 clip，否则角标会被圆角裁掉（之前那个奇怪形状就是这个）
                Box(Modifier.size(50.dp)) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(if (active) Ink.ChipBg else Color.Transparent)
                            .clickable { onSelect(index) },
                    )
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.align(Alignment.Center).size(22.dp),
                        tint = if (active) Ink.Primary else Ink.Tertiary,
                    )
                    if (index == 2 && unread > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-8).dp, y = 5.dp)
                                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                .clip(CircleShape)
                                .background(Ink.Badge)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unread > 99) "99+" else unread.toString(),
                                modifier = Modifier.padding(horizontal = 4.dp),
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8-bit 像素块 loading：一排方块上一道亮波扫过去，亮度被量化成 4 档，
 * 所以是"跳"的而不是渐变——和 V2EX 头像那种点阵味道一致。
 */
private fun waveLevel(phase: Float): Float {
    val wave = (sin((phase * 2f * Math.PI).toDouble()) + 1.0) / 2.0
    return (wave * 4).toInt() / 4f
}

@Composable
fun PixelLoader(
    size: Dp = 7.dp,
    gap: Dp = 4.dp,
    color: Color = Ink.Primary,
    count: Int = 3,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "pixel")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(count) { i ->
            val level = waveLevel((progress + i.toFloat() / count) % 1f)
            Box(
                Modifier
                    .size(size)
                    .background(color.copy(alpha = 0.12f + 0.88f * level)),
            )
        }
    }
}

/** 首屏用的 3×3 方块阵，按对角线扫过。 */
@Composable
fun PixelGridLoader(
    size: Dp = 9.dp,
    gap: Dp = 4.dp,
    color: Color = Ink.Primary,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "pixel-grid")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(3) { col ->
                    val level = waveLevel((progress + (row + col) / 5f) % 1f)
                    Box(
                        Modifier
                            .size(size)
                            .background(color.copy(alpha = 0.12f + 0.88f * level)),
                    )
                }
            }
        }
    }
}

/**
 * 首屏 / 空列表的统一 loading 样子。
 * 注意与 EmptyState 的分工：这里是「还在等数据」，会持续动；
 * EmptyState 是「数据已经回来，确实没有」，是静止的插画。
 */
@Composable
fun LoadingBlock(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelGridLoader()
        Spacer(Modifier.height(16.dp))
        Text(text, fontSize = 12.5.sp, color = Ink.Tertiary)
    }
}

/**
 * 列表骨架屏：首屏等数据时先把「大概长什么样」占住，
 * 比一个居中转圈更少跳动，也不会让人误以为列表是空的。
 */
@Composable
fun ListSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Column(modifier.fillMaxWidth()) {
        repeat(rows) { i ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Ink.ChipBg.copy(alpha = alpha)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // 标题两行，宽度交替，避免看着像整齐的表格
                    Box(
                        Modifier
                            .fillMaxWidth(if (i % 2 == 0) 0.92f else 0.74f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Ink.ChipBg.copy(alpha = alpha)),
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(if (i % 2 == 0) 0.58f else 0.68f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Ink.ChipBg.copy(alpha = alpha * 0.7f)),
                    )
                    Spacer(Modifier.height(9.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.36f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Ink.ChipBg.copy(alpha = alpha * 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
fun ListFooter(canLoadMore: Boolean, loading: Boolean, hint: String?, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                PixelLoader(size = 6.dp, gap = 3.dp, color = Ink.Secondary)
                Spacer(Modifier.width(9.dp))
                Text("正在加载…", fontSize = 12.5.sp, color = Ink.Tertiary)
            }
            hint != null -> Text(hint, fontSize = 12.5.sp, color = Ink.Tertiary)
            canLoadMore -> Text(
                "加载更多",
                modifier = Modifier.clickable(onClick = onLoadMore).padding(6.dp),
                fontSize = 12.5.sp,
                color = Ink.Secondary,
            )
            else -> Text("到底了", fontSize = 12.5.sp, color = Ink.Tertiary)
        }
    }
}

/**
 * 空状态：请求已经回来、但确实没有内容。
 * 和 LoadingBlock 是两件事——这里不会转圈，插画也是「安顿下来」的样子。
 */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    art: EmptyArt? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            EmptyArtCanvas(art)
            Spacer(Modifier.height(26.dp))
        }
        Text(
            title,
            fontSize = 15.sp,
            color = Ink.Secondary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            hint,
            modifier = Modifier.widthIn(max = 280.dp),
            fontSize = 13.sp,
            color = Ink.Tertiary,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                action.first,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.ChipBg)
                    .clickable(onClick = action.second)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
                color = Ink.Primary,
            )
        }
    }
}

@Composable
fun ErrorLine(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(text, fontSize = 13.5.sp, color = Ink.Secondary)
        Spacer(Modifier.height(6.dp))
        Text(
            "重试",
            modifier = Modifier.clickable(onClick = onRetry),
            fontSize = 13.5.sp,
            color = Ink.Blue,
            fontWeight = FontWeight.Medium,
        )
    }
}

package com.v4ex.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.Reply
import com.v4ex.android.data.ReplyRow
import com.v4ex.android.data.Time
import com.v4ex.android.ui.theme.Ink
import kotlinx.coroutines.withTimeout

/**
 * 回复列表的统一渲染。平铺模式和对话模式共用同一个列表、
 * 同一个 item key（"reply-${id}"），切换模式时 LazyColumn 不会整段重建。
 *
 * 传进来的 rows 已经由 [com.v4ex.android.data.Conversation.rows] 排好序：
 * - threaded=false：原顺序、全 depth=0、childCount=0，长这个样子和以前的帖子页一致；
 * - threaded=true：先根后后代，后代 depth 压到 1；根下面的「↳ N 条回复」数的是整组
 *   后代总数（含被压平的孙辈），而不是直接子回复数，否则会和实际排出来的行数对不上。
 */
fun LazyListScope.replyItems(
    rows: List<ReplyRow>,
    topicAuthor: String?,
    onAuthor: (String) -> Unit,
    onShareReply: ((Reply) -> Unit)? = null,
) {
    // 一组的行在列表里是连续的（根 + 它的全部后代），扫一遍就能得到每组后代总数。
    val groupSize = HashMap<Int, Int>(rows.size)
    var rootId: Int? = null
    for (row in rows) {
        if (row.depth == 0) {
            rootId = row.reply.id
            groupSize[rootId] = 0
        } else {
            val root = rootId ?: continue
            groupSize[root] = (groupSize[root] ?: 0) + 1
        }
    }
    items(rows, key = { "reply-${it.reply.id}" }) { row ->
        ReplyItem(
            row = row,
            isOp = topicAuthor != null && row.reply.member.username.equals(topicAuthor, ignoreCase = true),
            groupSize = groupSize[row.reply.id] ?: 0,
            onAuthor = onAuthor,
            onShareReply = onShareReply,
        )
    }
}

@Composable
private fun ReplyItem(
    row: ReplyRow,
    isOp: Boolean,
    groupSize: Int,
    onAuthor: (String) -> Unit,
    onShareReply: ((Reply) -> Unit)?,
) {
    val reply = row.reply
    // 长按回调只在第一次组合时被 pointerInput 捕获，用 rememberUpdatedState 保证拿到最新的那个；
    // pointerInput 的 key 用 reply 本体，数据没变就不会重启手势。
    val currentShare by rememberUpdatedState(onShareReply)
    val longPressModifier = if (onShareReply == null) {
        Modifier
    } else {
        Modifier.replyLongPress(reply) { currentShare?.invoke(reply) }
    }

    if (row.depth <= 0) {
        RootReplyRow(row, isOp, groupSize, onAuthor, longPressModifier)
    } else {
        ChildReplyRow(row, isOp, onAuthor, longPressModifier)
    }
}

/** depth=0：和帖子页原来的平铺样子保持一致。 */
@Composable
private fun RootReplyRow(
    row: ReplyRow,
    isOp: Boolean,
    groupSize: Int,
    onAuthor: (String) -> Unit,
    longPress: Modifier,
) {
    val reply = row.reply
    Row(
        Modifier
            .fillMaxWidth()
            .then(longPress)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Avatar(
            url = reply.member.avatar,
            name = reply.member.username,
            size = 30.dp,
            modifier = Modifier.clickable { onAuthor(reply.member.username) },
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reply.member.username,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onAuthor(reply.member.username) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink.Primary,
                )
                if (isOp) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "OP",
                        modifier = Modifier
                            .background(Ink.Primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Ink.OnPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                    )
                }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "${reply.floor} 楼 · ${Time.ago(reply.created)}",
                    fontSize = 11.5.sp,
                    color = Ink.Tertiary,
                )
            }
            Spacer(Modifier.height(5.dp))
            RichContent(
                text = reply.content,
                fontSize = 14.5.sp,
                lineHeight = 23.sp,
            )
            if (groupSize > 0) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "↳ $groupSize 条回复",
                    fontSize = 11.5.sp,
                    color = Ink.Tertiary,
                )
            }
        }
    }
}

/**
 * depth=1：整体右移（头像从 22dp 起到 48dp），左侧一条 1.5dp 竖线贯穿整行，
 * 正文上方注明「回复 @谁 · 几楼」。
 */
@Composable
private fun ChildReplyRow(
    row: ReplyRow,
    isOp: Boolean,
    onAuthor: (String) -> Unit,
    longPress: Modifier,
) {
    val reply = row.reply
    val parent = row.replyTo
    val lineColor = Ink.Line
    Row(
        Modifier
            .fillMaxWidth()
            .then(longPress)
            .drawBehind {
                // 竖线落在父楼头像的中线上（22dp 起、30dp 宽 → 37dp），
                // 贴到最左缘会和缩进后的头像、正文脱节。
                val width = 1.5.dp.toPx()
                drawRect(
                    color = lineColor,
                    topLeft = Offset(37.dp.toPx(), 0f),
                    size = Size(width, size.height),
                )
            }
            .padding(start = 48.dp, top = 8.dp, end = 22.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Avatar(
            url = reply.member.avatar,
            name = reply.member.username,
            size = 24.dp,
            modifier = Modifier.clickable { onAuthor(reply.member.username) },
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reply.member.username,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onAuthor(reply.member.username) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink.Primary,
                )
                if (isOp) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "OP",
                        modifier = Modifier
                            .background(Ink.Primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Ink.OnPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                    )
                }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "${reply.floor} 楼 · ${Time.ago(reply.created)}",
                    fontSize = 11.5.sp,
                    color = Ink.Tertiary,
                )
            }
            if (parent != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "回复 @${parent.member.username} · ${parent.floor} 楼",
                    fontSize = 11.5.sp,
                    color = Ink.Tertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
            RichContent(
                text = reply.content,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

/**
 * 行级长按。不能用 detectTapGestures：内层的头像 / 用户名 / 正文都有自己的 clickable，
 * 它们会先在 Main pass 吃掉 down，外层的 detectTapGestures 就永远等不到手势。
 * 所以这里在 Initial pass 自己计时判定长按；命中后把剩余的移动/抬起事件消费掉，
 * 内层 clickable 看到被消费的 up 就不会在松手时误触发点击，滚动也不受影响。
 */
private fun Modifier.replyLongPress(key: Any, onLongPress: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val longPressed = try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    var stillDown = true
                    while (stillDown) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        stillDown = change != null &&
                            change.pressed &&
                            (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
                    }
                    false
                }
            } catch (_: PointerEventTimeoutCancellationException) {
                true
            }
            if (!longPressed) return@awaitEachGesture
            onLongPress()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
            }
        }
    }

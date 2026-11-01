package com.v4ex.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.data.Topic
import com.v4ex.android.ui.theme.Ink
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 左滑露出的一个操作按钮。 */
data class SwipeAction(
    val label: String,
    val background: Color,
    val foreground: Color,
    val onClick: () -> Unit,
)

/** 列表级的「同一时间只展开一行」。每个需要滑动菜单的列表各持有一个。 */
class SwipeRowController {
    var openId: Any? by mutableStateOf(null)
}

@Composable
fun rememberSwipeRowController(): SwipeRowController = remember { SwipeRowController() }

private val ActionWidth = 84.dp
private const val FLING_VELOCITY = 900f

/**
 * 左滑露出操作区。
 *
 * - 拖过一半宽度（或快速左甩）松手后停在展开态；否则弹回。
 * - 展开态下点内容只会收起，不会触发 [onTap]（比如打开帖子）。
 * - 只响应水平拖动，纵向滚动仍交给外层 LazyColumn。
 */
@Composable
fun SwipeActionRow(
    rowKey: Any,
    controller: SwipeRowController,
    actions: List<SwipeAction>,
    onTap: () -> Unit,
    content: @Composable (tap: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val maxPx = with(density) { (ActionWidth * actions.size).toPx() }
    // 位置用 Compose state 持有：拖动时每一帧直接写，不经过协程，避免快速拖动丢增量。
    val position = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    suspend fun settle(target: Float) {
        val anim = Animatable(position.floatValue)
        anim.animateTo(target, tween(durationMillis = 180)) { position.floatValue = value }
        if (target == 0f && controller.openId == rowKey) controller.openId = null
    }

    fun close() {
        scope.launch { settle(0f) }
    }

    // 别的行被展开时，本行收起。
    LaunchedEffect(controller.openId) {
        if (controller.openId != rowKey && position.floatValue != 0f) settle(0f)
    }

    val dragState = rememberDraggableState { delta ->
        position.floatValue = (position.floatValue + delta).coerceIn(-maxPx, 0f)
    }

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        // 底层：操作按钮贴右边，内容被拖走后露出来。
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            actions.forEach { action ->
                Box(
                    Modifier
                        .width(ActionWidth)
                        .fillMaxHeight()
                        .background(action.background)
                        .clickable {
                            action.onClick()
                            close()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        action.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = action.foreground,
                    )
                }
            }
        }

        // 上层：内容。背景必须不透明，否则按钮会从下面透出来。
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(position.floatValue.roundToInt(), 0) }
                .background(Ink.Surface)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { _: Offset -> controller.openId = rowKey },
                    onDragStopped = { velocity ->
                        val open = if (abs(velocity) > FLING_VELOCITY) {
                            velocity < 0f
                        } else {
                            position.floatValue < -maxPx / 2
                        }
                        settle(if (open) -maxPx else 0f)
                        if (open) controller.openId = rowKey
                    },
                ),
        ) {
            content {
                if (position.floatValue < 0f) close() else onTap()
            }
        }
    }
}

/**
 * 带滑动菜单的帖子行：左滑露出「收藏 / 取消收藏」。收藏只存本机（V2EX 没有收藏接口）。
 */
@Composable
fun SwipeTopicRow(
    topic: Topic,
    read: Boolean,
    bookmarked: Boolean,
    controller: SwipeRowController,
    onToggleBookmark: () -> Unit,
    onAuthor: (() -> Unit)? = null,
    showNode: Boolean = true,
    onNode: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    SwipeActionRow(
        rowKey = topic.id,
        controller = controller,
        actions = listOf(
            if (bookmarked) {
                SwipeAction("取消收藏", Ink.ChipBg, Ink.Secondary, onToggleBookmark)
            } else {
                SwipeAction("收藏", Ink.Primary, Ink.OnPrimary, onToggleBookmark)
            },
        ),
        onTap = onClick,
    ) { tap ->
        TopicRow(
            topic = topic,
            read = read,
            onAuthor = onAuthor,
            showNode = showNode,
            onNode = onNode,
            onClick = tap,
        )
    }
}

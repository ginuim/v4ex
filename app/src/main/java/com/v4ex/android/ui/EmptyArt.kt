package com.v4ex.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.v4ex.android.ui.theme.Ink

/**
 * 空状态插画。全部用 Canvas 手绘，不引第三方图片，
 * 线条风格和 App 一致：极细描边 + 偶尔一点彩色。
 */
enum class EmptyArt {
    /** 没有浏览记录：翻旧的纸质日历 / 空书架 */
    History,
    /** 没有通知：睡着的铃铛 */
    Notices,
    /** 没有未读：一杯凉掉的茶 */
    Topic,
    /** 搜索无果：放大镜下一片空白 */
    Search,
    /** 节点没加载出来：断线的插头 */
    Offline,
    /** 这个人不存在：空白立牌 */
    Nobody,
}

@Composable
fun EmptyArtCanvas(art: EmptyArt, modifier: Modifier = Modifier) {
    // Notion 那套黑白：没有彩色点缀，全靠三级灰 + 实心填充层次。
    // stroke = 主体描边（最深的灰，但不至于用纯黑，免得比正文还抢眼）
    // fill   = 主体内部的浅灰实心，负责「有体积感」
    // hair   = 更淡的辅助线（阴影、细节、装饰点）
    val stroke = Ink.ArtStroke
    val fill = Ink.ArtFill
    val hair = Ink.ArtHair

    Canvas(modifier.size(width = 132.dp, height = 104.dp)) {
        val w = size.width
        val h = size.height
        when (art) {
            EmptyArt.History -> drawHistoryArt(w, h, stroke, fill, hair)
            EmptyArt.Notices -> drawNoticesArt(w, h, stroke, fill, hair)
            EmptyArt.Topic -> drawTopicArt(w, h, stroke, fill, hair)
            EmptyArt.Search -> drawSearchArt(w, h, stroke, fill, hair)
            EmptyArt.Offline -> drawOfflineArt(w, h, stroke, fill, hair)
            EmptyArt.Nobody -> drawNobodyArt(w, h, stroke, fill, hair)
        }
    }
}

/**
 * 没有浏览记录：一张空白卡片 + 右上角折角 + 几个渐大的小点。
 * 黑白版：卡片用浅灰实心打底，折角用更深的灰实心，形成 Notion 那种「纸片叠在纸上」的层次。
 */
private fun DrawScope.drawHistoryArt(
    w: Float, h: Float, stroke: Color, fill: Color, hair: Color,
) {
    val sw = 1.8.dp.toPx()
    val sheetW = w * 0.44f
    val sheetH = h * 0.66f
    val cx = w * 0.50f
    val cy = h * 0.45f
    val left = cx - sheetW / 2
    val top = cy - sheetH / 2
    val radius = CornerRadius(5.dp.toPx())

    // 背后一层：只偏移一点点，做出厚度就够了；偏移太大像两个物体
    drawRoundRect(
        color = hair.copy(alpha = 0.42f),
        topLeft = Offset(left + 4.dp.toPx(), top + 4.dp.toPx()),
        size = Size(sheetW, sheetH),
        cornerRadius = radius,
    )
    // 主卡片
    drawRoundRect(color = fill, topLeft = Offset(left, top), size = Size(sheetW, sheetH), cornerRadius = radius)
    drawRoundRect(
        color = stroke, topLeft = Offset(left, top), size = Size(sheetW, sheetH),
        cornerRadius = radius, style = Stroke(sw),
    )

    // 折角：收小到卡片宽度的约 1/6，`真正`是 Notion 文档图标那种比例
    val fold = sheetW * 0.17f
    val corner = Path().apply {
        moveTo(left + sheetW - fold, top)
        lineTo(left + sheetW, top + fold)
        lineTo(left + sheetW - fold, top + fold)
        close()
    }
    drawPath(corner, color = stroke.copy(alpha = 0.26f))
    // 折痕线，让折角有"翻起来"的感觉
    drawLine(
        color = stroke.copy(alpha = 0.5f),
        start = Offset(left + sheetW - fold, top),
        end = Offset(left + sheetW - fold, top + fold),
        strokeWidth = sw * 0.8f,
    )

    // 两行内容线
    repeat(2) { i ->
        val y = top + sheetH * (0.46f + i * 0.14f)
        drawLine(
            color = hair,
            start = Offset(left + sheetW * 0.16f, y),
            end = Offset(left + sheetW * (0.80f - i * 0.22f), y),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }

    // 卡片下方三个点：挪到卡片底下，不与边缘打架
    val dotsY = top + sheetH + 15.dp.toPx()
    repeat(3) { i ->
        drawCircle(
            color = if (i == 2) stroke.copy(alpha = 0.75f) else hair,
            radius = 1.6.dp.toPx() + i * 1.4.dp.toPx(),
            center = Offset(cx - 13.dp.toPx() + i * 13.dp.toPx(), dotsY),
        )
    }
}

/**
 * 没有通知：躺着的铃铛 + Zzz。
 * 黑白版：铃铛浅灰实心，铃舌用中灰实心，Zzz 用淡灰描边，靠大小排布制造呼吸感。
 */
private fun DrawScope.drawNoticesArt(
    w: Float, h: Float, stroke: Color, fill: Color, hair: Color,
) {
    val sw = 1.8.dp.toPx()
    val cx = w * 0.42f
    val cy = h * 0.60f
    val r = w * 0.17f

    // 铃身：顶部圆、底部外扩
    val bell = Path().apply {
        moveTo(cx - r, cy)
        cubicTo(cx - r, cy - r * 1.6f, cx + r, cy - r * 1.6f, cx + r, cy)
        lineTo(cx + r * 1.18f, cy + r * 0.62f)
        lineTo(cx - r * 1.18f, cy + r * 0.62f)
        close()
    }
    drawPath(bell, color = fill)
    drawPath(bell, color = stroke, style = Stroke(sw))

    // 铃舌：实心，作为深色落点
    drawCircle(stroke, radius = r * 0.24f, center = Offset(cx, cy + r * 0.86f))

    // 顶环
    drawCircle(
        stroke,
        radius = r * 0.17f,
        center = Offset(cx, cy - r * 1.48f),
        style = Stroke(sw),
    )

    // Zzz：三个大小递增的 Z，最远的那个最淡
    data class Z(val dx: Float, val dy: Float, val s: Float, val a: Float)
    listOf(
        Z(r * 1.52f, -r * 0.95f, 10f, 0.95f),
        Z(r * 2.05f, -r * 1.65f, 8f, 0.7f),
        Z(r * 2.45f, -r * 2.25f, 6.5f, 0.45f),
    ).forEach { z ->
        val size = z.s.dp.toPx()
        val zx = cx + z.dx
        val zy = cy + z.dy
        val p = Path().apply {
            moveTo(zx, zy)
            lineTo(zx + size, zy)
            lineTo(zx, zy + size)
            lineTo(zx + size, zy + size)
        }
        drawPath(p, color = stroke.copy(alpha = z.a), style = Stroke(sw * 0.85f, cap = StrokeCap.Round))
    }

    // 地面：一条实心的小椭圆表示「放着」
    drawOval(
        color = hair.copy(alpha = 0.6f),
        topLeft = Offset(cx - r * 1.5f, cy + r * 1.02f),
        size = Size(r * 3f, r * 0.30f),
    )
}

/** 帖子为空：一张折角的空文档 + 一根卷起来的线头。 */
private fun DrawScope.drawTopicArt(
    w: Float, h: Float, stroke: Color, fill: Color, hair: Color,
) {
    val sw = 1.8.dp.toPx()
    val dx = w * 0.31f
    val dy = h * 0.16f
    val dw = w * 0.36f
    val dh = h * 0.70f
    val radius = CornerRadius(4.dp.toPx())

    drawRoundRect(color = fill, topLeft = Offset(dx, dy), size = Size(dw, dh), cornerRadius = radius)
    drawRoundRect(
        color = stroke,
        topLeft = Offset(dx, dy),
        size = Size(dw, dh),
        cornerRadius = radius,
        style = Stroke(sw),
    )

    // 右上角折角（实心）
    val fold = 15.dp.toPx()
    drawPath(
        Path().apply {
            moveTo(dx + dw - fold, dy)
            lineTo(dx + dw, dy + fold)
            lineTo(dx + dw - fold, dy + fold)
            close()
        },
        color = stroke.copy(alpha = 0.30f),
    )

    // 三行淡线，第三行故意断掉
    val startY = dy + dh * 0.36f
    repeat(2) { i ->
        drawLine(
            color = hair,
            start = Offset(dx + dw * 0.16f, startY + i * 12.dp.toPx()),
            end = Offset(dx + dw * 0.84f, startY + i * 12.dp.toPx()),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
    val brokenY = startY + 2 * 12.dp.toPx()
    drawLine(
        color = hair,
        start = Offset(dx + dw * 0.16f, brokenY),
        end = Offset(dx + dw * 0.48f, brokenY),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
    // 断掉之后卷起来的线头，用中灰和前面区分
    val curl = Path().apply {
        val sx = dx + dw * 0.56f
        moveTo(sx, brokenY)
        cubicTo(sx + 7.dp.toPx(), brokenY, sx + 11.dp.toPx(), brokenY + 15.dp.toPx(), sx + 2.dp.toPx(), brokenY + 16.dp.toPx())
        cubicTo(sx - 7.dp.toPx(), brokenY + 17.dp.toPx(), sx - 5.dp.toPx(), brokenY + 5.dp.toPx(), sx + 4.dp.toPx(), brokenY + 8.dp.toPx())
    }
    drawPath(curl, color = stroke, style = Stroke(sw * 0.9f, cap = StrokeCap.Round))
}

/** 搜不到：放大镜里空无一物，旁边一个问号。 */
private fun DrawScope.drawSearchArt(
    w: Float, h: Float, stroke: Color, fill: Color, hair: Color,
) {
    val sw = 2.dp.toPx()
    val cx = w * 0.45f
    val cy = h * 0.45f
    val r = w * 0.20f

    // 镜片：浅灰实心 + 中灰描边
    drawCircle(fill, radius = r, center = Offset(cx, cy))
    drawCircle(stroke, radius = r, center = Offset(cx, cy), style = Stroke(sw))

    // 镜片里什么都没有，只有两点灰尘，暗示「空」
    drawCircle(hair, radius = 2.dp.toPx(), center = Offset(cx - r * 0.30f, cy - r * 0.12f))
    drawCircle(hair, radius = 1.4.dp.toPx(), center = Offset(cx + r * 0.28f, cy + r * 0.32f))

    // 镜柄
    drawLine(
        color = stroke,
        start = Offset(cx + r * 0.74f, cy + r * 0.74f),
        end = Offset(cx + r * 1.58f, cy + r * 1.58f),
        strokeWidth = sw * 2.2f,
        cap = StrokeCap.Round,
    )

    // 问号
    val qx = w * 0.76f
    val qy = h * 0.24f
    val q = Path().apply {
        moveTo(qx - 5.dp.toPx(), qy)
        cubicTo(qx - 5.dp.toPx(), qy - 9.dp.toPx(), qx + 6.dp.toPx(), qy - 9.dp.toPx(), qx + 6.dp.toPx(), qy - 1.dp.toPx())
        cubicTo(qx + 6.dp.toPx(), qy + 3.dp.toPx(), qx + 1.dp.toPx(), qy + 4.dp.toPx(), qx + 1.dp.toPx(), qy + 8.dp.toPx())
    }
    drawPath(q, color = stroke, style = Stroke(sw, cap = StrokeCap.Round))
    drawCircle(stroke, radius = 1.8.dp.toPx(), center = Offset(qx + 1.dp.toPx(), qy + 14.dp.toPx()))
}

/** 加载失败 / 离线：插座和拔开的插头。 */
private fun DrawScope.drawOfflineArt(
    w: Float, h: Float, stroke: Color, fill: Color, hair: Color,
) {
    val sw = 1.8.dp.toPx()
    val cy = h * 0.50f

    // 插座：浅灰实心方块
    val sx = w * 0.17f
    val sy = cy - 17.dp.toPx()
    val sSize = Size(w * 0.21f, 34.dp.toPx())
    drawRoundRect(fill, topLeft = Offset(sx, sy), size = sSize, cornerRadius = CornerRadius(5.dp.toPx()))
    drawRoundRect(
        stroke,
        topLeft = Offset(sx, sy),
        size = sSize,
        cornerRadius = CornerRadius(5.dp.toPx()),
        style = Stroke(sw),
    )
    // 两个插孔
    drawCircle(stroke, radius = 2.dp.toPx(), center = Offset(sx + sSize.width * 0.5f, cy - 5.dp.toPx()))
    drawCircle(stroke, radius = 2.dp.toPx(), center = Offset(sx + sSize.width * 0.5f, cy + 5.dp.toPx()))

    // 插头：浅灰实心 + 描边，位置往右挪开一点表示「拔出来了」
    val px = w * 0.58f
    val ph = 28.dp.toPx()
    drawRoundRect(
        fill,
        topLeft = Offset(px, cy - ph / 2),
        size = Size(w * 0.18f, ph),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawRoundRect(
        stroke,
        topLeft = Offset(px, cy - ph / 2),
        size = Size(w * 0.18f, ph),
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(sw),
    )
    // 两根插脚朝左
    listOf(-6f, 6f).forEach { dy ->
        drawLine(
            color = stroke,
            start = Offset(px, cy + dy.dp.toPx()),
            end = Offset(px - 9.dp.toPx(), cy + dy.dp.toPx()),
            strokeWidth = sw * 1.4f,
            cap = StrokeCap.Round,
        )
    }
    // 电线向右延伸
    val cord = Path().apply {
        val startX = px + w * 0.18f
        moveTo(startX, cy)
        cubicTo(startX + 18.dp.toPx(), cy, startX + 24.dp.toPx(), cy - 24.dp.toPx(), startX + 38.dp.toPx(), cy - 20.dp.toPx())
    }
    drawPath(cord, color = hair, style = Stroke(sw * 1.3f, cap = StrokeCap.Round))

    // 断开处的两点火星
    drawCircle(stroke, radius = 1.5.dp.toPx(), center = Offset(w * 0.455f, cy - 13.dp.toPx()))
    drawCircle(hair, radius = 1.2.dp.toPx(), center = Offset(w * 0.49f, cy - 18.dp.toPx()))
}

/** 查无此人：空头像 + 问号 + 肩膀。 */
private fun DrawScope.drawNobodyArt(
    w: Float, h: Float, stroke: Color, fill: Color, hair: Color,
) {
    val sw = 1.8.dp.toPx()
    val cx = w * 0.5f
    val cy = h * 0.50f
    val r = w * 0.17f

    // 头像：浅灰实心圆
    drawCircle(fill, radius = r, center = Offset(cx, cy - h * 0.12f))
    drawCircle(stroke, radius = r, center = Offset(cx, cy - h * 0.12f), style = Stroke(sw))

    // 问号
    val qy = cy - h * 0.18f
    val q = Path().apply {
        moveTo(cx - 5.dp.toPx(), qy)
        cubicTo(cx - 5.dp.toPx(), qy - 9.dp.toPx(), cx + 6.dp.toPx(), qy - 9.dp.toPx(), cx + 6.dp.toPx(), qy - 1.dp.toPx())
        cubicTo(cx + 6.dp.toPx(), qy + 3.dp.toPx(), cx + 1.dp.toPx(), qy + 4.dp.toPx(), cx + 1.dp.toPx(), qy + 8.dp.toPx())
    }
    drawPath(q, color = stroke, style = Stroke(sw, cap = StrokeCap.Round))
    drawCircle(stroke, radius = 1.9.dp.toPx(), center = Offset(cx + 1.dp.toPx(), qy + 14.dp.toPx()))

    // 肩膀：浅灰实心的半圆，衬在头像下面
    val shoulders = Path().apply {
        moveTo(cx - r * 1.35f, cy + h * 0.30f)
        quadraticTo(cx, cy + h * 0.02f, cx + r * 1.35f, cy + h * 0.30f)
        close()
    }
    drawPath(shoulders, color = fill)
    drawPath(shoulders, color = stroke, style = Stroke(sw, cap = StrokeCap.Round))
}

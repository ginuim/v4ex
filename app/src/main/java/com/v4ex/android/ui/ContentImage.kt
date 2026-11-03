package com.v4ex.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import com.v4ex.android.ui.theme.Ink

/** 图片解码高度相对容器宽度的上限：只为防超长图一次占满内存，普通图片用不到。 */
private const val MaxImageAspect = 10

/** 尺寸未知时的占位高度，和旧渲染器保持一致，避免加载完再跳一下。 */
private val ImageLoadingHeight = 150.dp
private val ImageErrorHeight = 84.dp

/**
 * 正文里的图片：
 * - 比容器窄的图按图片自身像素 1:1 显示——表情包这类小图不会再被拉满屏、糊成一片；
 * - 比容器宽的图缩到容器宽度；
 * - 高度按原始比例换算，不再硬裁；
 * - 还在加载时是一块 [Ink.ChipBg] 占位 + 像素 loading，加载失败给一句可点的提示。
 *
 * 解码尺寸显式按容器宽度给：否则 Coil 会先按「加载占位」的布局尺寸解码，
 * 拿到的 intrinsic 比例是失真的，小图就会被误判成别的尺寸。
 */
@Composable
fun ContentImage(url: String, alt: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 正常都在有界宽度里排版（列表项 / 楼层正文）；万一无界就退回一个保守宽度。
        val containerPx = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            with(density) { 400.dp.roundToPx() }
        }
        val request = remember(url, containerPx) {
            ImageRequest.Builder(context)
                .data(url)
                .size(Size(containerPx, Dimension.Pixels(containerPx * MaxImageAspect)))
                // Precision.AUTOMATIC 在「显式给定尺寸」时会解析成 EXACT，小图会在解码时被
                // 放大到容器宽度，后面再怎么 1:1 显示也还是糊的；INEXACT 允许解码器只缩不放。
                .precision(Precision.INEXACT)
                .build()
        }
        val painter = rememberAsyncImagePainter(request)
        val state = painter.state
        val natural = (state as? AsyncImagePainter.State.Success)
            ?.result
            ?.drawable
            ?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
            ?.let { IntSize(it.intrinsicWidth, it.intrinsicHeight) }

        when {
            natural != null -> {
                // 只缩不放：小图 1:1，大图缩到容器宽。
                val (widthPx, heightPx) = imageDisplaySizePx(natural.width, natural.height, containerPx)
                val display = with(density) { DpSize(widthPx.toDp(), heightPx.toDp()) }
                Image(
                    painter = painter,
                    contentDescription = alt,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .size(display.width, display.height)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick),
                )
            }

            state is AsyncImagePainter.State.Error -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(ImageErrorHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.ChipBg)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("图片加载失败 · 点击打开", fontSize = 12.sp, color = Ink.Tertiary)
            }

            else -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(ImageLoadingHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.ChipBg),
                contentAlignment = Alignment.Center,
            ) {
                PixelLoader(size = 7.dp, gap = 3.dp, color = Ink.Secondary)
            }
        }
    }
}

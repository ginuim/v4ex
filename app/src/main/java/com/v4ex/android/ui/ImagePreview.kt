package com.v4ex.android.ui

import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest

@Composable
fun ImagePreview(url: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    var saving by remember(url) { mutableStateOf(false) }
    val saveToGallery = rememberGallerySaveAction { result ->
        saving = false
        Toast.makeText(context, when (result) {
            GallerySaveResult.Saved -> "已保存到相册"
            GallerySaveResult.Failed -> "保存失败，请重试"
            GallerySaveResult.PermissionDenied -> "需要存储权限才能保存到相册"
        }, Toast.LENGTH_SHORT).show()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)
            // 蒙层只负责「点空白处关闭」：图片是下面另一个盒子，尺寸刚好等于 Fit 后的显示区域，
            // 所以点击落在哪一层天然分得清，不用自己算坐标。
            // 放大后不再响应：此时图片已经溢出这个盒子盖住四周黑边，
            // 再按盒子判断就会把「点在图上」误当成「点在蒙层上」。
            .clickable(interactionSource = null, indication = null) {
                if (scale == 1f) onDismiss()
            }
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 图片的像素尺寸要等 Coil 加载完才知道，拿到后把承载盒子缩到 Fit 后的矩形。
                var imageSize by remember(url) { mutableStateOf<IntSize?>(null) }
                val density = LocalDensity.current
                val size = imageSize
                val fit = if (size != null && constraints.maxWidth > 0 && constraints.maxHeight > 0) {
                    minOf(constraints.maxWidth / size.width.toFloat(), constraints.maxHeight / size.height.toFloat())
                } else {
                    null
                }
                val frame = if (size != null && fit != null) {
                    Modifier.requiredSize(
                        with(density) { (size.width * fit).toDp() },
                        with(density) { (size.height * fit).toDp() },
                    )
                } else {
                    Modifier.fillMaxSize()
                }
                SubcomposeAsyncImage(
                    model = url, contentDescription = "查看大图", contentScale = ContentScale.Fit,
                    modifier = frame
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        }
                        .pointerInput(url) { detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        }) }
                        .pointerInput(url) { detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        } },
                    onSuccess = { result ->
                        val drawable = result.result.drawable
                        val next = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
                        if (next.width > 0 && next.height > 0 && imageSize != next) imageSize = next
                    },
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PixelLoader()
                    } },
                    error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("图片加载失败，请关闭后重试", color = Color.White)
                    } },
                )
            }
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    saveToGallery("V4EX-${System.currentTimeMillis()}.png") {
                        val result = context.imageLoader.execute(
                            ImageRequest.Builder(context).data(url).allowHardware(false).build(),
                        )
                        (result.drawable as? BitmapDrawable)?.bitmap
                    }
                },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            ) {
                Text(if (saving) "保存中…" else "保存到相册", color = Color.White)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                Text("关闭", color = Color.White)
            }
        }
    }
}

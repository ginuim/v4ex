package com.v4ex.android.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.v4ex.android.data.SharePayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fixed pixel dimensions keep exports independent of device density and font scaling. */
internal fun renderSharePoster(payload: SharePayload, blocks: List<MdBlock> = emptyList(), images: Map<String, Bitmap> = emptyMap(), dark: Boolean = false): Bitmap {
    fun imageHeight(url: String): Float = images[url]?.let { minOf(900f, 904f * it.height / it.width) } ?: 60f
    val posterHeight = 1440
    val bitmap = Bitmap.createBitmap(1080, posterHeight, Bitmap.Config.ARGB_8888)
    val background = if (dark) Color.rgb(25, 28, 32) else Color.rgb(248, 246, 240)
    val primary = if (dark) Color.rgb(235, 237, 240) else Color.rgb(30, 37, 43)
    val secondary = if (dark) Color.rgb(185, 193, 201) else Color.rgb(89, 96, 100)
    val accent = if (dark) Color.rgb(159, 185, 207) else Color.rgb(83, 105, 123)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)
    fun text(value: String, y: Float, size: Float, color: Int, lines: Int, bold: Boolean = false, width: Int = 904): Int {
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setLineSpacing(12f, 1f).setMaxLines(lines)
            .setEllipsize(TextUtils.TruncateAt.END).build()
        canvas.save()
        canvas.translate(88f, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }
    text("V4EX  /  社区分享", 88f, 30f, accent, 1, true)
    val titleHeight = text(payload.title, 210f, 64f, primary, 5, true)
    var bodyY = 250f + titleHeight
    fun paragraph(value: String, size: Float = 46f, bold: Boolean = false) {
        bodyY += text(value, bodyY, size, secondary, Int.MAX_VALUE, bold) + 24f
    }
    fun picture(url: String) {
        val image = images[url]
        if (image != null) {
            val height = imageHeight(url)
            val width = height * image.width / image.height
            canvas.drawBitmap(image, null, RectF(88f, bodyY, 88f + width, bodyY + height), null)
            bodyY += height + 28f
        } else paragraph("[图片加载失败，请打开原帖查看]", 30f)
    }
    canvas.save()
    canvas.clipRect(88f, bodyY, 992f, 1060f)
    if (blocks.isEmpty()) payload.excerpt?.let { paragraph(it) }
    var hasOverflow = false
    for (block in blocks) {
        if (bodyY >= 1060f) {
            hasOverflow = true
            break
        }
        when (block) {
            is MdBlock.Para -> paragraph(block.text.text)
            is MdBlock.Heading -> paragraph(block.text.text, 52f, true)
            is MdBlock.Code -> paragraph(block.code, 36f)
            is MdBlock.Quote -> paragraph("│ " + block.text.text)
            is MdBlock.Bullet -> paragraph("${block.number?.let { "$it." } ?: "•"} " + block.text.text)
            is MdBlock.Image -> picture(block.url)
            is MdBlock.InlineImage -> {
                paragraph(block.text.text.replace("\uFFFC", ""))
                picture(block.url)
            }
            MdBlock.Rule -> { bodyY += 20f }
        }
    }
    canvas.restore()
    if (hasOverflow || bodyY - 24f > 1060f) {
        val fade = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 920f, 0f, 1060f,
                background and 0x00FFFFFF, background,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(88f, 920f, 992f, 1060f, fade)
    }
    val footerY = 1112f
    val footerHeight = text(if (payload.link.contains("#reply")) "楼层摘录 · 在 V2EX 阅读讨论" else "帖子摘录 · 在 V2EX 阅读讨论",
        footerY + 78f, 34f, accent, 2, width = 620)
    text(payload.link, footerY + 78f + footerHeight + 14f, 26f, secondary, 2, width = 620)
    drawPosterQr(canvas, payload.link, 752f, footerY, 240, margin = 1)
    return bitmap
}

internal data class SelectedTopicExcerpt(val text: String, val author: String)

private class SelectedExcerptTooLong : IllegalArgumentException()

private fun renderSelectedExcerpt(
    payload: SharePayload,
    excerpt: SelectedTopicExcerpt,
    dark: Boolean,
): Bitmap {
    val background = if (dark) Color.rgb(25, 28, 32) else Color.rgb(248, 246, 240)
    val primary = if (dark) Color.rgb(235, 237, 240) else Color.rgb(30, 37, 43)
    val secondary = if (dark) Color.rgb(185, 193, 201) else Color.rgb(89, 96, 100)
    val accent = if (dark) Color.rgb(159, 185, 207) else Color.rgb(83, 105, 123)
    fun layout(value: String, size: Float, color: Int, width: Int, font: Typeface, lines: Int): StaticLayout {
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = font
        }
        return StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setLineSpacing(10f, 1.15f)
            .setMaxLines(lines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    val maxBodyLines = 56
    val body = layout(excerpt.text, 54f, primary, 888, Typeface.SERIF, maxBodyLines + 1)
    val headerBottom = 240
    val footerTop = maxOf(650, headerBottom + body.height + 88)
    val height = footerTop + 310
    if (height > 4096 || body.lineCount > maxBodyLines) throw SelectedExcerptTooLong()

    val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)
    fun draw(text: StaticLayout, left: Float, top: Float) {
        canvas.save()
        canvas.translate(left, top)
        text.draw(canvas)
        canvas.restore()
    }

    val rulePaint = android.graphics.Paint().apply { color = accent; alpha = 96 }
    canvas.drawRect(96f, 92f, 156f, 97f, rulePaint)
    draw(layout("V4EX · 帖子摘录", 29f, accent, 888, Typeface.DEFAULT_BOLD, 1), 96f, 120f)
    draw(body, 96f, headerBottom.toFloat())

    val footerY = footerTop.toFloat()
    canvas.drawRect(96f, footerY, 984f, footerY + 2f, rulePaint)
    val source = if (excerpt.author.isBlank()) "V2EX" else "@${excerpt.author} · V2EX"
    val byline = layout(source, 30f, secondary, 632, Typeface.DEFAULT, 1)
    draw(byline, 96f, footerY + 64f)
    draw(
        layout(payload.title, 38f, primary, 632, Typeface.DEFAULT_BOLD, 3),
        96f, footerY + 64f + byline.height + 16f,
    )
    drawPosterQr(canvas, payload.link, 780f, footerY + 42f, 204, margin = 4)
    draw(layout("扫码查看原帖", 23f, secondary, 204, Typeface.DEFAULT, 1), 813f, footerY + 252f)
    return bitmap
}

private fun drawPosterQr(canvas: Canvas, link: String, left: Float, top: Float, size: Int, margin: Int) {
    val qr = QRCodeWriter().encode(
        link, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to margin),
    )
    val pixels = IntArray(size * size) { index ->
        if (qr[index % size, index / size]) Color.BLACK else Color.WHITE
    }
    val qrBitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    canvas.drawBitmap(qrBitmap, left, top, null)
    qrBitmap.recycle()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SharePosterDialog(
    payload: SharePayload,
    selectedExcerpt: SelectedTopicExcerpt? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var themeOverride by remember(payload) { mutableStateOf<Boolean?>(null) }
    val posterDark = themeOverride ?: appDark
    var bitmap by remember(payload) { mutableStateOf<Bitmap?>(null) }
    var uri by remember(payload) { mutableStateOf<android.net.Uri?>(null) }
    var error by remember(payload, selectedExcerpt) { mutableStateOf<String?>(null) }
    var generating by remember(payload) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val saveToGallery = rememberGallerySaveAction { result ->
        saving = false
        Toast.makeText(context, when (result) {
            GallerySaveResult.Saved -> "海报已保存到相册"
            GallerySaveResult.Failed -> "保存失败，请重试"
            GallerySaveResult.PermissionDenied -> "需要存储权限才能保存到相册"
        }, Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(payload, selectedExcerpt, posterDark) {
        generating = true
        bitmap = null
        uri = null
        error = null
        try {
            val result = withContext(Dispatchers.IO) {
                val image = if (selectedExcerpt != null) {
                    renderSelectedExcerpt(payload, selectedExcerpt, posterDark)
                } else {
                    val blocks = payload.markdown?.let { parseMarkdown(it) }.orEmpty()
                    val images = mutableMapOf<String, Bitmap>()
                    for (url in blocks.mapNotNull { when (it) {
                        is MdBlock.Image -> it.url
                        is MdBlock.InlineImage -> it.url
                        else -> null
                    } }.distinct()) {
                        val result = context.imageLoader.execute(ImageRequest.Builder(context)
                            .data(url).size(1080, 1080).allowHardware(false).build())
                        (result.drawable as? BitmapDrawable)?.bitmap?.let { images[url] = it }
                    }
                    renderSharePoster(payload, blocks, images, posterDark)
                }
                val directory = File(context.cacheDir, "share-posters").apply { mkdirs() }
                directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86400000L }
                    ?.forEach { it.delete() }
                val file = File.createTempFile("poster-", ".png", directory)
                file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                image to FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            }
            bitmap = result.first
            uri = result.second
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: SelectedExcerptTooLong) {
            error = "选中的文字太长，请缩小选区后重试"
        } catch (_: Exception) {
            error = "海报生成失败，请关闭后重试"
        } finally {
            generating = false
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("图片海报", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeOverride == null, onClick = { themeOverride = null }, label = { Text("跟随主题") })
                    FilterChip(selected = themeOverride == false, onClick = { themeOverride = false }, label = { Text("浅色") })
                    FilterChip(selected = themeOverride == true, onClick = { themeOverride = true }, label = { Text("深色") })
                }
                Spacer(Modifier.height(12.dp))
                val preview = bitmap
                if (preview != null) {
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Image(
                            preview.asImageBitmap(), "帖子分享海报预览",
                            Modifier.fillMaxWidth().aspectRatio(preview.width.toFloat() / preview.height),
                        )
                    }
                } else if (error != null) {
                    Text(error.orEmpty())
                } else {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(enabled = bitmap != null && !saving && !generating, onClick = {
                        val image = bitmap ?: return@TextButton
                        saving = true
                        // 海报已经是 Bitmap，直接落盘，不用再走 Coil。
                        saveToGallery("V4EX-海报-${System.currentTimeMillis()}.png") { image }
                    }) { Text(if (saving) "保存中…" else "保存到相册") }
                    TextButton(enabled = uri != null && !generating, onClick = {
                        try {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri("帖子海报", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "分享图片海报"))
                        } catch (_: Exception) {
                            Toast.makeText(context, "无法打开图片分享，请稍后重试", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("分享图片") }
                }
            }
        }
    }
}

package com.v4ex.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val qr = QRCodeWriter().encode(payload.link, BarcodeFormat.QR_CODE, 240, 240,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1))
    val qrBitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
    for (y in 0 until 240) for (x in 0 until 240) {
        qrBitmap.setPixel(x, y, if (qr[x, y]) Color.BLACK else Color.WHITE)
    }
    canvas.drawBitmap(qrBitmap, 752f, footerY, null)
    qrBitmap.recycle()
    return bitmap
}

@Composable
internal fun SharePosterDialog(payload: SharePayload, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var themeOverride by remember(payload) { mutableStateOf<Boolean?>(null) }
    val posterDark = themeOverride ?: appDark
    var bitmap by remember(payload) { mutableStateOf<Bitmap?>(null) }
    var uri by remember(payload) { mutableStateOf<android.net.Uri?>(null) }
    var failed by remember(payload) { mutableStateOf(false) }
    var generating by remember(payload) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { target ->
        val image = bitmap
        if (target != null && image != null) {
            scope.launch {
                saving = true
                try {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(target)).use {
                            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    }
                    Toast.makeText(context, "海报已保存", Toast.LENGTH_SHORT).show()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                } finally {
                    saving = false
                }
            }
        }
    }
    LaunchedEffect(payload, posterDark) {
        generating = true
        failed = false
        try {
            val result = withContext(Dispatchers.IO) {
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
                val image = renderSharePoster(payload, blocks, images, posterDark)
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
        } catch (_: Exception) {
            failed = true
        } finally {
            generating = false
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("图片海报", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeOverride == null, onClick = { themeOverride = null }, label = { Text("跟随主题") })
                    FilterChip(selected = themeOverride == false, onClick = { themeOverride = false }, label = { Text("浅色") })
                    FilterChip(selected = themeOverride == true, onClick = { themeOverride = true }, label = { Text("深色") })
                }
                Spacer(Modifier.height(12.dp))
                val preview = bitmap
                if (preview != null) {
                    Image(preview.asImageBitmap(), "帖子分享海报预览",
                        Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(preview.width.toFloat() / preview.height))
                } else if (failed) {
                    Text("海报生成失败，请关闭后重试")
                } else {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(enabled = bitmap != null && !saving && !generating, onClick = {
                        saveImage.launch("V4EX-海报-${System.currentTimeMillis()}.png")
                    }) { Text(if (saving) "保存中…" else "下载图片") }
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

package com.v4ex.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import com.v4ex.android.ui.theme.Ink
import kotlin.math.roundToInt

/** V2EX 正文/回复的最小 Markdown 子集，够用就好。 */
sealed interface MdBlock {
    data class Para(val text: AnnotatedString) : MdBlock
    data class Heading(val text: AnnotatedString) : MdBlock
    data class Code(val code: String) : MdBlock
    data class Quote(val text: AnnotatedString) : MdBlock
    data class Bullet(val text: AnnotatedString, val number: Int?) : MdBlock
    data class Image(val url: String, val alt: String) : MdBlock
    data object Rule : MdBlock
}

// V2EX 导出的 Markdown 括号里常带空格：![]( https://x/y.png) / [文本]( https://x)，
// 所以一律允许括号内首尾有空白，URL 本身再 trim。
private val IMAGE_RE = Regex(
    """!\[([^\]]*)]\(\s*([^)\s]+)\s*\)|(https?://[^\s]+?\.(?:png|jpe?g|gif|webp|bmp)\b)"""
)
private val BULLET_RE = Regex("^[-*+]\\s+")
private val ORDERED_RE = Regex("^(\\d{1,3})[.)]\\s+")
private val INLINE_RE = Regex(
    """(\*\*[^*\n]+\*\*|`[^`\n]+`|\[[^\]\n]+]\(\s*[^)\s]+\s*\)|https?://[^\s，。；）)]+)"""
)

private fun inline(src: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (m in INLINE_RE.findAll(src)) {
        if (m.range.first > index) append(src.substring(index, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(token.removeSurrounding("**"))
            }

            token.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Ink.ChipBg),
            ) { append(token.trim('`')) }

            token.startsWith("[") -> {
                val close = token.indexOf(']')
                val url = token.substringAfter("(", "").substringBeforeLast(")").trim()
                val label = token.substring(1, close)
                if (url.isNotBlank()) {
                    pushStringAnnotation("url", url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(label)
                    }
                    pop()
                } else {
                    append(token)
                }
            }

            else -> {
                pushStringAnnotation("url", token)
                withStyle(SpanStyle(color = linkColor)) { append(token) }
                pop()
            }
        }
        index = m.range.last + 1
    }
    if (index < src.length) append(src.substring(index))
}

fun parseMarkdown(raw: String, linkColor: Color = Ink.Blue): List<MdBlock> {
    val text = raw.replace("\r\n", "\n").replace('\r', '\n')
    val out = mutableListOf<MdBlock>()
    val lines = text.split('\n')
    var i = 0
    val para = StringBuilder()

    fun flushParagraph() {
        val s = para.toString().trim()
        para.setLength(0)
        if (s.isEmpty()) return
        // 一段里可能混着图片，图片单独成块
        var last = 0
        for (m in IMAGE_RE.findAll(s)) {
            val before = s.substring(last, m.range.first).trim()
            if (before.isNotEmpty()) out += MdBlock.Para(inline(before, linkColor))
            val mdUrl = m.groupValues[2].trim()
            val bareUrl = m.groupValues[3]
            if (mdUrl.isNotEmpty()) {
                out += MdBlock.Image(mdUrl, m.groupValues[1])
            } else if (bareUrl.isNotEmpty()) {
                out += MdBlock.Image(bareUrl, "")
            }
            last = m.range.last + 1
        }
        val tail = s.substring(last).trim()
        if (tail.isNotEmpty()) out += MdBlock.Para(inline(tail, linkColor))
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                out += MdBlock.Code(code.toString().trimEnd())
            }

            trimmed.startsWith(">") -> {
                flushParagraph()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote.appendLine(lines[i].trimStart().removePrefix(">").trim())
                    i++
                }
                out += MdBlock.Quote(inline(quote.toString().trimEnd(), linkColor))
                continue
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                out += MdBlock.Heading(inline(trimmed.trimStart('#').trim(), linkColor))
            }

            BULLET_RE.containsMatchIn(trimmed) -> {
                flushParagraph()
                out += MdBlock.Bullet(inline(trimmed.replaceFirst(BULLET_RE, ""), linkColor), null)
            }

            ORDERED_RE.containsMatchIn(trimmed) -> {
                flushParagraph()
                val n = ORDERED_RE.find(trimmed)!!.groupValues[1]
                out += MdBlock.Bullet(
                    inline(trimmed.replaceFirst(ORDERED_RE, ""), linkColor),
                    n.toIntOrNull(),
                )
            }

            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                out += MdBlock.Rule
            }

            trimmed.isBlank() -> flushParagraph()

            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return out
}

/** 正文 / 回复统一用这个渲染：段落、粗体、行内代码、代码块、引用、列表、图片、链接。 */
@Composable
fun RichContent(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 25.sp,
    color: Color = Ink.Primary,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val uriHandler = LocalUriHandler.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Para -> LinkText(block.text, uriHandler, fontSize, lineHeight, color)

                is MdBlock.Heading -> Text(
                    text = block.text,
                    fontSize = (fontSize.value + 1.5f).sp,
                    lineHeight = (lineHeight.value + 3f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink.Primary,
                )

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .heightIn(min = 18.dp)
                            .background(Ink.Line),
                    )
                    Spacer2()
                    LinkText(
                        block.text,
                        uriHandler,
                        (fontSize.value - 1f).sp,
                        (lineHeight.value - 2f).sp,
                        Ink.Secondary,
                    )
                }

                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = block.number?.let { "$it." } ?: "·",
                        modifier = Modifier.width(16.dp),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = Ink.Secondary,
                    )
                    LinkText(block.text, uriHandler, fontSize, lineHeight, color)
                }

                is MdBlock.Code -> Text(
                    text = block.code,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Ink.ChipBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = (fontSize.value - 1.5f).sp,
                    lineHeight = (lineHeight.value - 2f).sp,
                    fontFamily = FontFamily.Monospace,
                    color = Ink.Primary,
                )

                is MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Ink.Line),
                )

                is MdBlock.Image -> RichImage(block.url, block.alt, uriHandler)
            }
        }
    }
}

/**
 * 正文里的可点击文本：段落、引用、列表项共用。
 * 之前只有段落用了 ClickableText，导致列表里的「相关链接」点不动。
 */
@Composable
private fun LinkText(
    text: AnnotatedString,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
) {
    ClickableText(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
        ),
        onClick = { offset ->
            text.getStringAnnotations("url", offset, offset)
                .firstOrNull()
                ?.let { ann -> runCatching { uriHandler.openUri(ann.item) } }
        },
    )
}

@Composable
private fun Spacer2() {
    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
}

/** 图片解码高度相对容器宽度的上限：只为防超长图一次占满内存，普通图片用不到。 */
private const val MaxImageAspect = 10

private val ImageLoadingHeight = 150.dp
private val ImageErrorHeight = 84.dp

/**
 * 图片应该占多少像素：
 * - 图片本身比容器窄：按原尺寸（1:1 物理像素）显示，绝不放大；
 * - 图片比容器宽：缩到容器宽度；
 * - 高度始终等比换算。
 * 纯函数，单测覆盖（纯像素计算，不涉及 density / dp）。
 */
internal fun imageDisplaySizePx(
    naturalWidthPx: Int,
    naturalHeightPx: Int,
    containerWidthPx: Int,
): Pair<Int, Int> {
    if (naturalWidthPx <= 0 || naturalHeightPx <= 0 || containerWidthPx <= 0) return 0 to 0
    val scale = if (naturalWidthPx <= containerWidthPx) {
        1f
    } else {
        containerWidthPx.toFloat() / naturalWidthPx
    }
    return (naturalWidthPx * scale).roundToInt().coerceAtLeast(1) to
        (naturalHeightPx * scale).roundToInt().coerceAtLeast(1)
}

/**
 * 正文里的图片：
 * - 比容器窄的图按图片自身像素 1:1 显示——表情包这类小图不会再被拉满屏、糊成一片；
 * - 比容器宽的图缩到容器宽度；
 * - 高度按原始比例换算，不再硬裁。
 *
 * 解码尺寸显式按容器宽度给：否则 Coil 会先按「加载占位」的布局尺寸解码，
 * 拿到的 intrinsic 比例是失真的，小图就会被误判成别的尺寸。
 */
@Composable
private fun RichImage(url: String, alt: String, uriHandler: UriHandler) {
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
                        .clickable { uriHandler.openUri(url) },
                )
            }

            state is AsyncImagePainter.State.Error -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(ImageErrorHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.ChipBg)
                    .clickable { uriHandler.openUri(url) },
                contentAlignment = Alignment.Center,
            ) {
                Text("图片加载失败 · 点击用浏览器打开", fontSize = 12.sp, color = Ink.Tertiary)
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

package com.v4ex.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.LocalImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import com.v4ex.android.ui.theme.Ink
import com.v4ex.android.ui.theme.LightTokens
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

    /**
     * 段落里的一张图，位置是 [text] 中的 start..end（一个占位符）。
     * 渲染时先解码看原始尺寸：够小就按行内表情画在 [text] 里、跟着文字走；
     * 否则退化成一张独占一行的 [Image]。
     */
    data class InlineImage(
        val text: AnnotatedString,
        val start: Int,
        val end: Int,
        val url: String,
    ) : MdBlock
}

/** 行内表情的宽度上限（px）：V2EX 的表情是 20~32px 的小图，再大就不当表情了。 */
internal const val EmojiMaxWidthPx = 64

/** 段落里的图片在纯文本里的占位符：先占位、再在 styled 文本里找回它的位置。 */
internal const val EMOJI_PLACEHOLDER = '\uFFFC'

/** 段落里的一张图：URL 及它在渲染文本里的占位位置。 */
internal data class ImageMark(
    val url: String,
    val placeholderStart: Int,
    val placeholderEnd: Int,
    val isEmoji: Boolean = false,
)

/** 段落里所有占位符的位置，按出现顺序。 */
internal fun placeholderRanges(text: String): List<IntRange> {
    val out = mutableListOf<IntRange>()
    var i = text.indexOf(EMOJI_PLACEHOLDER)
    while (i >= 0) {
        out += i..i
        i = text.indexOf(EMOJI_PLACEHOLDER, i + 1)
    }
    return out
}

/**
 * 把段落里的图片落到占位符上：第 n 张图 = 第 n 个占位符。
 * 占位符不够时多出来的图直接丢弃——宁可少画一张，也不要把表情错配到别的位置。
 * 纯函数，单测覆盖。
 */
internal fun foldInlineImages(slots: List<IntRange>, urls: List<String>): List<ImageMark> =
    urls.mapIndexedNotNull { index, url ->
        val slot = slots.getOrNull(index) ?: return@mapIndexedNotNull null
        ImageMark(url = url, placeholderStart = slot.first, placeholderEnd = slot.last)
    }

/**
 * 是不是「表情」而不是一张正文图片：行内图片的宽高都不超过 [EmojiMaxWidthPx]
 * 才当表情，跟着文字排；尺寸未知时按「不是表情」处理，走原来的独立图片渲染。
 * 纯函数，单测覆盖。
 */
internal fun isInlineEmoji(naturalWidthPx: Int?, naturalHeightPx: Int? = naturalWidthPx): Boolean =
    naturalWidthPx != null && naturalHeightPx != null &&
        naturalWidthPx in 1..EmojiMaxWidthPx && naturalHeightPx in 1..EmojiMaxWidthPx

// V2EX 导出的 Markdown 括号里常带空格：![]( https://x/y.png) / [文本]( https://x)，
// 所以一律允许括号内首尾有空白，URL 本身再 trim。
private val IMAGE_RE = Regex(
    """!\[([^\]]*)]\(\s*([^)\s]+)\s*\)|(https?://[^\s]+?\.(?:png|jpe?g|gif|webp|bmp)\b)"""
)
private val BULLET_RE = Regex("^[-*+]\\s+")
private val ORDERED_RE = Regex("^(\\d{1,3})[.)]\\s+")
private val INLINE_RE = Regex(
    """(!\[[^\]\n]*]\(\s*[^)\s]+\s*\)|\*\*[^*\n]+\*\*|`[^`\n]+`|\[[^\]\n]+]\(\s*[^)\s]+\s*\)|https?://[^\s，。；）)]+)"""
)

private fun inline(src: String, linkColor: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (m in INLINE_RE.findAll(src)) {
        if (m.range.first > index) append(src.substring(index, m.range.first))
        val token = m.value
        when {
            // 图片不在这里处理：段落里的图片由 flushParagraph 先切成 InlineImage / Image，
            // 否则 ![](url) 会落进「裸链接」分支，把图片当链接显示成一串文字。
            token.startsWith("![") -> {}

            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(token.removeSurrounding("**"))
            }

            token.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
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

fun parseMarkdown(raw: String, linkColor: Color = LightTokens.Blue, codeBackground: Color = LightTokens.ChipBg): List<MdBlock> {
    val text = raw.replace("\r\n", "\n").replace('\r', '\n')
    val out = mutableListOf<MdBlock>()
    val lines = text.split('\n')
    var i = 0
    val para = StringBuilder()

    fun flushParagraph() {
        val s = para.toString().trim()
        para.setLength(0)
        if (s.isEmpty()) return
        // 段落里的图片先换成一个占位符，段落按「图文混排」统一处理：
        // 表情（很小的行内图）跟着文字走，正文图片独占一行。
        val urls = mutableListOf<String>()
        val withSlots = StringBuilder()
        var last = 0
        for (m in IMAGE_RE.findAll(s)) {
            val url = m.groupValues[2].trim().ifEmpty { m.groupValues[3] }
            if (url.isEmpty()) continue
            withSlots.append(s, last, m.range.first).append(EMOJI_PLACEHOLDER)
            urls += url
            last = m.range.last + 1
        }
        withSlots.append(s, last, s.length)
        val styled = inline(withSlots.toString(), linkColor, codeBackground)

        if (urls.isEmpty()) {
            out += MdBlock.Para(styled)
            return
        }
        // 只有一张图、除了它什么字都没有 → 正文图片，单独成块。
        val loneImage = urls.singleOrNull()
        if (loneImage != null && styled.text.replace(EMOJI_PLACEHOLDER.toString(), "").isBlank()) {
            out += MdBlock.Image(loneImage, "")
            return
        }
        // 图文混排：每张图一个 InlineImage，指向同一段 styled 文本里的占位位置。
        val marks = foldInlineImages(placeholderRanges(styled.text), urls)
        if (marks.isEmpty()) out += MdBlock.Para(styled)
        for (mark in marks) {
            out += MdBlock.InlineImage(styled, mark.placeholderStart, mark.placeholderEnd, mark.url)
        }
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
                out += MdBlock.Quote(inline(quote.toString().trimEnd(), linkColor, codeBackground))
                continue
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                out += MdBlock.Heading(inline(trimmed.trimStart('#').trim(), linkColor, codeBackground))
            }

            BULLET_RE.containsMatchIn(trimmed) -> {
                flushParagraph()
                out += MdBlock.Bullet(inline(trimmed.replaceFirst(BULLET_RE, ""), linkColor, codeBackground), null)
            }

            ORDERED_RE.containsMatchIn(trimmed) -> {
                flushParagraph()
                val n = ORDERED_RE.find(trimmed)!!.groupValues[1]
                out += MdBlock.Bullet(
                    inline(trimmed.replaceFirst(ORDERED_RE, ""), linkColor, codeBackground),
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
    paragraphSpacing: Dp = 8.dp,
) {
    val linkColor = Ink.Blue
    val codeBackground = Ink.ChipBg
    val blocks = remember(text, linkColor, codeBackground) { parseMarkdown(text, linkColor, codeBackground) }
    val uriHandler = LocalUriHandler.current

    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            // 正文段落增加留白；连续列表项仍沿用紧凑的块间距。
            if (index > 0) {
                val previous = blocks[index - 1]
                val bordersParagraph = previous is MdBlock.Para || previous is MdBlock.InlineImage ||
                    block is MdBlock.Para || block is MdBlock.InlineImage
                Spacer(Modifier.height(if (bordersParagraph) paragraphSpacing else 8.dp))
            }
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

                is MdBlock.InlineImage -> InlineImageBlock(
                    block = block,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = color,
                    uriHandler = uriHandler,
                )
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

/** 图片解码高度相对容器宽度的上限：只为防超长图一次占满内存，普通图片用不到。 */
private const val MaxImageAspect = 10

/** 行内表情比字号略大，保持可辨认且不额外撑高正文行。 */
private const val EmojiScale = 1.25f

/** 探测尺寸必须大于表情阈值，避免把解码后的小缩略图误判成表情。 */
internal const val EmojiProbeSizePx = EmojiMaxWidthPx * 2

/** emoji 在 `InlineTextContent` 里的占位 id；同一个段落里每张图都不同。 */
private fun emojiSlot(url: String) = "emoji:$url"

private fun imageRequest(context: android.content.Context, url: String, widthPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(Size(widthPx, Dimension.Pixels(widthPx * MaxImageAspect)))
        // 注意：这里不要设 INEXACT 以外的精度，否则解码器会把小表情放大。
        .precision(Precision.INEXACT)
        .build()

/** 同步查内存缓存：命中就返回原始尺寸，没命中返回 null。 */
private fun cachedImageSize(loader: coil.ImageLoader, request: ImageRequest): IntSize? {
    val key = request.memoryCacheKey ?: return null
    val bitmap = loader.memoryCache?.get(key)?.bitmap ?: return null
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    return IntSize(bitmap.width, bitmap.height)
}

/**
 * 段落里的一张图：先用大于表情阈值的探测尺寸加载，再决定怎么排。
 * - 已知且够小 → 行内表情，跟着文字走（InlineTextContent）；
 * - 已知但太大 → 正文图片，独占一行，和以前一样；
 * - 还没取到 → 先按「表情」的占位排：表情是行内小图，就算排错也只是一张小方框，
 *   而正文图片按表情排错会变成一条细线，视觉上更糟。尺寸到手后如果确实是大图，
 *   下一帧会换成独占一行。
 */
@Composable
private fun InlineImageBlock(
    block: MdBlock.InlineImage,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    uriHandler: UriHandler,
) {
    val context = LocalContext.current
    val probe = remember(block.url) {
        ImageRequest.Builder(context)
            .data(block.url)
            .size(EmojiProbeSizePx, EmojiProbeSizePx)
            .scale(coil.size.Scale.FIT)
            .precision(Precision.INEXACT)
            .memoryCacheKey("emoji-probe-v2:${block.url}")
            .build()
    }
    val natural = probeImageSize(block.url, probe)

    // 尺寸未知时按表情排版（见上面的取舍）；明确是大图才退回独占一行。
    val knownNotEmoji = natural != null && !isInlineEmoji(natural.width, natural.height)
    if (knownNotEmoji) {
        RichImage(block.url, "", uriHandler)
        return
    }
    InlineEmoji(block, fontSize, lineHeight, color, uriHandler)
}

/**
 * 取图片的原始尺寸：先看内存缓存（同步），没有就挂起等一次真实加载。
 * 结果按 URL 记住，同一个表情在多个楼层出现时只探一次。
 */
@Composable
private fun probeImageSize(url: String, request: ImageRequest): IntSize? {
    val loader = LocalImageLoader.current
    val cached = remember(url) { cachedImageSize(loader, request) }
    if (cached != null) return cached
    var result by remember(url) { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(url) {
        val size = runCatching {
            val drawable = loader.execute(request).drawable ?: return@runCatching null
            if (drawable.intrinsicWidth > 0) IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight) else null
        }.getOrNull()
        if (size != null) result = size
    }
    return result
}

/** 像素表情放大时用高质量滤波，圆角方形比像素方块更耐看。 */
@Composable
private fun InlineEmoji(
    block: MdBlock.InlineImage,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    uriHandler: UriHandler,
) {
    val density = LocalDensity.current
    val size = (fontSize.value * EmojiScale).sp
    val sidePx = with(density) { size.toDp().roundToPx() }
    val painter = rememberAsyncImagePainter(
        imageRequest(LocalContext.current, block.url, sidePx),
    )
    val slot = remember(block.url) { emojiSlot(block.url) }
    val inlineContent = remember(slot, painter, size, uriHandler) {
        mapOf(
            slot to InlineTextContent(
                Placeholder(
                    width = size,
                    height = size,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                // 行内子项只有最大尺寸约束，必须填满占位；否则 20px 原图会按物理像素
                // 缩在占位左上角，在高密度屏上显得很小且偏上。
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { uriHandler.openUri(block.url) },
                )
            },
        )
    }
    val shown = remember(block.text, block.start, block.end, slot) {
        buildAnnotatedString {
            append(block.text.subSequence(0, block.start))
            // alternateText 不允许为空串（Compose 会直接抛 IllegalArgumentException 崩溃），
            // 无障碍读屏也正好拿它当表情的描述。
            appendInlineContent(slot, "表情")
            append(block.text.subSequence(block.end + 1, block.text.length))
        }
    }
    BasicText(
        text = shown,
        style = TextStyle(fontSize = fontSize, lineHeight = lineHeight, color = color),
        inlineContent = inlineContent,
    )
}

@Composable
private fun Spacer2() {
    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
}

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

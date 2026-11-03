package com.v4ex.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.CurrentComponentsBridge
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.v4ex.android.ui.theme.Ink
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

/**
 * 正文自绘组件：
 * - 代码块交给 [ArticleCode]：等宽 + 语法高亮 + 横向滚动，长行不再被强行折行；
 * - 独占一段的图片交给 [ContentImage]：和列表里的图片共用「占位 → loading → 等比尺寸」，
 *   而不是让渲染器把整张图当成行内占位塞进文字里（那样加载期间只剩一块看不见的空白）。
 */
internal fun articleMarkdownComponents(onImageClick: (String) -> Unit): MarkdownComponents =
    markdownComponents(
        paragraph = { model ->
            // 树每次重组都会重建，key 只能用内容 + 段落偏移，不能用节点实例。
            val images = remember(model.content, model.node.startOffset, model.node.endOffset) {
                standaloneImages(model.content, model.node)
            }
            if (images == null) {
                CurrentComponentsBridge.paragraph.invoke(this, model)
            } else {
                images.forEach { image ->
                    ContentImage(image.url, image.alt) { onImageClick(image.url) }
                }
            }
        },
        codeFence = { model ->
            MarkdownCodeFence(model.content, model.node) { code, language -> ArticleCode(code, language) }
        },
        codeBlock = { model ->
            MarkdownCodeBlock(model.content, model.node) { code, language -> ArticleCode(code, language) }
        },
    )

/** 段落里的一张图：URL 和它的 alt 文本。 */
internal data class ParagraphImage(val url: String, val alt: String)

/**
 * 一段里如果只有图片（V2EX 的图片几乎都单独成段），返回这些图片；
 * 只要还有一个字就返回 null，交回默认渲染，避免把图文混排拆散。
 * 纯函数，单测覆盖。
 */
internal fun standaloneImages(content: String, node: ASTNode): List<ParagraphImage>? {
    val images = mutableListOf<ASTNode>()
    node.collectImages(images)
    if (images.isEmpty()) return null
    // 段落文本里去掉图片本身，还剩字就说明是图文混排。
    var text = content.substring(node.startOffset, node.endOffset)
    for (image in images) text = text.replace(content.substring(image.startOffset, image.endOffset), "")
    if (text.isNotBlank()) return null

    val parsed = images.mapNotNull { image ->
        val url = image.findNode(MarkdownElementTypes.LINK_DESTINATION)
            ?.getUnescapedTextInNode(content)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        val alt = image.findNode(MarkdownElementTypes.LINK_TEXT)
            ?.getUnescapedTextInNode(content)
            ?.trim('[', ']')
            .orEmpty()
        ParagraphImage(url = url, alt = alt)
    }
    return parsed.ifEmpty { null }
}

private fun ASTNode.collectImages(out: MutableList<ASTNode>) {
    children.forEach { child ->
        if (child.type == MarkdownElementTypes.IMAGE) out += child else child.collectImages(out)
    }
}

private fun ASTNode.findNode(type: IElementType): ASTNode? {
    children.forEach { child ->
        if (child.type == type) return child
        child.findNode(type)?.let { return it }
    }
    return null
}

/**
 * 代码块：底色沿用行内代码的 chip 底色，语种名缩在左上角，代码整块可横向滚动。
 */
@Composable
internal fun ArticleCode(code: String, language: String?) {
    val palette = codePalette()
    val highlighted = remember(code, language, palette) { highlightCode(code, language, palette) }
    val label = remember(language) { codeFenceLabel(language) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Ink.ChipBg),
    ) {
        if (label != null) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Ink.Secondary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicText(
                text = highlighted,
                style = TextStyle(
                    color = Ink.Primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                // 代码宁可不折行，也不要把缩进折成一团。
                softWrap = false,
            )
        }
    }
}

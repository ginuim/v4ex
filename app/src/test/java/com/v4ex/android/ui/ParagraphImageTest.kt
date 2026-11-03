package com.v4ex.android.ui

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 纯函数测试：段落里「只有图片」的识别。 */
class ParagraphImageTest {

    private fun paragraphs(content: String): List<ASTNode> {
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
        val out = mutableListOf<ASTNode>()
        fun walk(node: ASTNode) {
            node.children.forEach { child ->
                if (child.type == MarkdownElementTypes.PARAGRAPH) out += child else walk(child)
            }
        }
        walk(tree)
        return out
    }

    @Test
    fun loneImageParagraphIsDetected() {
        // V2EX 的原样写法：括号里带一个空格，URL 里有转义的百分号。
        val content = "![几版不同的设计方案]( https://cdn.gosniffy.com/a.png)"
        val url = standaloneImages(content, paragraphs(content).single())
        assertEquals("https://cdn.gosniffy.com/a.png", url!!.single().url)
        assertEquals("几版不同的设计方案", url.single().alt)
    }

    @Test
    fun emptyAltIsAllowedAndEmptyParagraphFallsBack() {
        val content = "![](https://cdn.gosniffy.com/a.png)"
        assertEquals("https://cdn.gosniffy.com/a.png", standaloneImages(content, paragraphs(content).single())!!.single().url)

        val text = "只有文字的一段"
        assertNull(standaloneImages(text, paragraphs(text).single()))
    }

    @Test
    fun mixedParagraphGoesBackToDefaultRendering() {
        val content = "看这张图 ![表情](https://cdn.v2ex.com/x.png) 然后继续"
        assertNull(standaloneImages(content, paragraphs(content).single()))
    }

    @Test
    fun severalImagesInOneParagraphAreAllKept() {
        val content = "![](https://cdn.v2ex.com/1.png)\n![](https://cdn.v2ex.com/2.png)"
        val images = standaloneImages(content, paragraphs(content).single())!!
        assertEquals(listOf("https://cdn.v2ex.com/1.png", "https://cdn.v2ex.com/2.png"), images.map { it.url })
    }

    @Test
    fun imageInsideListItemKeepsItsOwnParagraph() {
        // 列表里图片独占一段的情况：段落嵌套在 LIST_ITEM 下，同样要被识别出来。
        val content = "- 项\n\n  ![](https://cdn.v2ex.com/1.png)"
        val imageParagraph = paragraphs(content).last()
        assertEquals(
            listOf("https://cdn.v2ex.com/1.png"),
            standaloneImages(content, imageParagraph)!!.map { it.url },
        )
    }
}

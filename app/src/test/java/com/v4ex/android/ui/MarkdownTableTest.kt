package com.v4ex.android.ui

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableTest {
    @Test
    fun gfmRecognizesTableWithInlineFormattingAndAlignment() {
        val content = """
            正文段落

            | 名称 | 数量 |
            | :--- | ---: |
            | **项目** | `2` |
            | [链接](https://v2ex.com) | 3 |
        """.trimIndent()
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
        fun hasTable(node: ASTNode): Boolean =
            node.type == GFMElementTypes.TABLE || node.children.any(::hasTable)
        assertTrue("The renderer's GFM parser must recognize tables", hasTable(tree))
    }
    @Test
    fun v2exCrLfTableIsRecognizedAfterNormalization() {
        val raw = "## 当密码管理器用\r\n\r\n| | 1Password 个人版 | 小密盾 |\r\n|---|---|---|\r\n| 价格 | 47.88/年 | ¥69 一台 |\r\n"
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(normalizeMarkdownLineEndings(raw))
        fun hasTable(node: ASTNode): Boolean = node.type == GFMElementTypes.TABLE || node.children.any(::hasTable)
        assertTrue(hasTable(tree))
    }
}

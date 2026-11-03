package com.v4ex.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯函数测试：语种识别、围栏标签、高亮 span。 */
class CodeHighlightTest {

    private val palette = CodePalette(
        keyword = Color(0xFF000001),
        string = Color(0xFF000002),
        comment = Color(0xFF000003),
        number = Color(0xFF000004),
        function = Color(0xFF000005),
    )

    private fun spans(text: AnnotatedString): List<Pair<String, Color>> =
        text.spanStyles.map { text.text.substring(it.start, it.end) to it.item.color }

    @Test
    fun languageAliasesMapToFamilies() {
        assertEquals(CodeLang.CLike, codeLangOf("kotlin"))
        assertEquals(CodeLang.CLike, codeLangOf(" Kotlin "))
        assertEquals(CodeLang.CLike, codeLangOf("ts"))
        assertEquals(CodeLang.Python, codeLangOf("py"))
        assertEquals(CodeLang.Shell, codeLangOf("bash"))
        assertEquals(CodeLang.Sql, codeLangOf("MySQL"))
        assertEquals(CodeLang.Json, codeLangOf("json"))
        assertEquals(CodeLang.Markup, codeLangOf("html"))
        // 不认识的语种（含空围栏）一律不上色，免得把 diff / 日志染色。
        assertEquals(CodeLang.Plain, codeLangOf(null))
        assertEquals(CodeLang.Plain, codeLangOf(""))
        assertEquals(CodeLang.Plain, codeLangOf("brainfuck"))
    }

    @Test
    fun fenceLabelDropsLanguageFenceDecorations() {
        assertEquals("kotlin", codeFenceLabel("kotlin"))
        assertEquals("java", codeFenceLabel("{.java}"))
        assertEquals("js", codeFenceLabel("js title=\"a.js\""))
        assertNull(codeFenceLabel("text"))
        assertNull(codeFenceLabel("  "))
        assertNull(codeFenceLabel(null))
    }

    @Test
    fun kotlinSnippetHighlightsKeywordFunctionStringCommentAndNumber() {
        val code = "fun main() {\n  // 打招呼\n  println(\"hi\", 42)\n}"
        val out = highlightCode(code, "kotlin", palette)
        val colored = spans(out)

        assertTrue(colored.contains("fun" to palette.keyword))
        assertTrue(colored.contains("main" to palette.function))
        assertTrue(colored.contains("// 打招呼" to palette.comment))
        assertTrue(colored.contains("println" to palette.function))
        assertTrue(colored.contains("\"hi\"" to palette.string))
        assertTrue(colored.contains("42" to palette.number))
        // 上色不改文本：高亮前后字符完全一致。
        assertEquals(code, out.text)
    }

    @Test
    fun capitalizedNamesReadAsTypes() {
        val colored = spans(highlightCode("val r = ImageRequest()", "kotlin", palette))
        assertTrue(colored.contains("ImageRequest" to palette.function))
    }

    @Test
    fun pythonCommentsAndTripleQuotedStrings() {
        val colored = spans(highlightCode("# 注释\ndef f():\n    return \"\"\"doc\"\"\"", "python", palette))
        assertTrue(colored.contains("# 注释" to palette.comment))
        assertTrue(colored.contains("def" to palette.keyword))
        assertTrue(colored.contains("\"\"\"doc\"\"\"" to palette.string))
    }

    @Test
    fun shellVariablesAreFunctionColored() {
        val colored = spans(highlightCode("echo \$HOME # home", "bash", palette))
        assertTrue(colored.contains("# home" to palette.comment))
        assertTrue(colored.contains("HOME" to palette.function))
    }

    @Test
    fun sqlKeywordsIgnoreCase() {
        val colored = spans(highlightCode("SELECT id FROM users", "sql", palette))
        assertTrue(colored.contains("SELECT" to palette.keyword))
        assertTrue(colored.contains("FROM" to palette.keyword))
    }

    @Test
    fun markupTagsAndAttributes() {
        val colored = spans(highlightCode("<div class=\"a\">x</div>", "html", palette))
        assertTrue(colored.contains("div" to palette.keyword))
        assertTrue(colored.contains("\"a\"" to palette.string))
    }

    @Test
    fun unterminatedStringStopsAtLineEnd() {
        val code = "val a = \"x\nval b = 2"
        val colored = spans(highlightCode(code, "kotlin", palette))
        // 字符串没闭合时不能把后面的代码整段吃掉。
        assertTrue(colored.contains("\"x" to palette.string))
        assertTrue(colored.contains("2" to palette.number))
    }

    @Test
    fun unknownLanguageIsLeftUntouched() {
        val out = highlightCode("让 AI 更美观一点", "diff", palette)
        assertEquals("让 AI 更美观一点", out.text)
        assertFalse(out.spanStyles.any())
    }

    @Test
    fun blockCommentSpansMultipleLines() {
        val colored = spans(highlightCode("a /* x\ny */ b", "js", palette))
        assertTrue(colored.contains("/* x\ny */" to palette.comment))
    }
}

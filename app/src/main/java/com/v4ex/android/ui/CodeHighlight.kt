package com.v4ex.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.v4ex.android.ui.theme.Ink

/**
 * 代码高亮的四类颜色；注释复用 [CodePalette.comment]，函数调用和类型复用 [CodePalette.function]。
 * 由主题 token 派生，深色模式自动换一套。
 */
internal data class CodePalette(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
)

@Composable
internal fun codePalette(): CodePalette = CodePalette(
    keyword = Ink.CodeKeyword,
    string = Ink.CodeString,
    // 注释用 Secondary 而不是 Tertiary：浅色底上 Tertiary 太淡，注释会糊成一片。
    comment = Ink.Secondary,
    number = Ink.CodeNumber,
    function = Ink.Blue,
)

/** 语法家族：同一家族共用一套关键字和注释规则，够 V2EX 的代码块用。 */
internal enum class CodeLang { CLike, Python, Shell, Sql, Json, Markup, Plain }

private val CLikeAliases = setOf(
    "kotlin", "kt", "kts", "java", "js", "javascript", "jsx", "mjs", "cjs", "ts", "typescript", "tsx",
    "c", "h", "cpp", "c++", "cc", "cxx", "hpp", "cs", "csharp", "go", "golang", "rust", "rs",
    "swift", "dart", "php", "scala", "groovy", "gradle", "proto", "graphql", "solidity", "vb", "vbnet",
)
private val PythonAliases = setOf(
    "python", "py", "python3", "ruby", "rb", "yaml", "yml", "toml", "ini", "conf", "cfg",
    "dockerfile", "docker", "makefile", "make", "perl", "pl", "lua", "r", "julia", "hcl", "terraform",
)
private val ShellAliases = setOf(
    "sh", "bash", "zsh", "shell", "shell-session", "console", "terminal", "fish",
    "powershell", "ps1", "ps", "bat", "cmd",
)
private val SqlAliases = setOf("sql", "mysql", "postgres", "postgresql", "psql", "sqlite", "plsql", "tsql", "hive")
private val JsonAliases = setOf("json", "json5", "jsonc", "ndjson")
private val MarkupAliases = setOf("html", "xml", "svg", "htm", "xhtml", "xsl", "plist", "wxml", "vue")

/** 围栏语言名归一：小写、去掉 `{.java}` 这类标记和后面跟着的参数。 */
internal fun normalizeFenceLanguage(raw: String?): String =
    raw.orEmpty().trim().lowercase()
        .removePrefix("{").removeSuffix("}")
        .removePrefix(".")
        .substringBefore(' ')
        .substringBefore(',')
        .substringBefore('{')
        .trim()

internal fun codeLangOf(language: String?): CodeLang {
    val key = normalizeFenceLanguage(language)
    return when {
        key.isEmpty() -> CodeLang.Plain
        key in CLikeAliases -> CodeLang.CLike
        key in PythonAliases -> CodeLang.Python
        key in ShellAliases -> CodeLang.Shell
        key in SqlAliases -> CodeLang.Sql
        key in JsonAliases -> CodeLang.Json
        key in MarkupAliases -> CodeLang.Markup
        else -> CodeLang.Plain
    }
}

/**
 * 代码块角落里显示的语种名；`text` / `plain` 这类等于没写，干脆不显示。
 * 纯函数，单测覆盖。
 */
internal fun codeFenceLabel(language: String?): String? {
    val key = normalizeFenceLanguage(language)
    return key.takeIf { it.isNotEmpty() && it != "text" && it != "txt" && it != "plain" && it != "none" }
}

private val CLikeKeywords = setOf(
    "abstract", "actual", "annotation", "as", "auto", "await", "base", "bool", "boolean", "break", "byte",
    "case", "catch", "char", "class", "companion", "const", "constructor", "continue", "data", "decimal",
    "default", "defer", "delete", "do", "double", "dynamic", "else", "enum", "except", "explicit", "export",
    "extends", "extern", "external", "fallthrough", "false", "final", "finally", "float", "fn", "for",
    "friend", "fun", "func", "function", "get", "go", "goto", "if", "impl", "implements", "import", "in",
    "init", "inline", "inner", "instanceof", "int", "interface", "internal", "is", "lambda", "lateinit",
    "let", "long", "loop", "match", "mod", "move", "mut", "namespace", "native", "new", "nil", "noinline",
    "null", "object", "open", "operator", "out", "override", "package", "pass", "private", "protected",
    "pub", "public", "readonly", "record", "ref", "reified", "return", "sealed", "self", "set", "short",
    "sizeof", "static", "struct", "super", "suspend", "switch", "synchronized", "tailrec", "this", "throw",
    "throws", "trait", "transient", "true", "try", "type", "typealias", "typedef", "typeof", "union",
    "unsafe", "unsigned", "use", "using", "val", "var", "virtual", "void", "volatile", "when", "where",
    "while", "with", "yield",
)

private val PythonKeywords = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else",
    "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "None",
    "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield", "self",
    "def", "print", "len", "range", "str", "int", "float", "dict", "list", "set", "tuple", "isinstance",
    "super", "type", "object",
)

private val ShellKeywords = setOf(
    "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac", "in",
    "function", "return", "export", "local", "readonly", "declare", "echo", "cd", "sudo", "rm", "cp",
    "mv", "mkdir", "source", "alias", "unset", "set", "shift", "exit", "trap", "wait", "read", "printf",
    "true", "false", "test", "sudo", "curl", "git", "npm", "pnpm", "yarn", "gradle", "adb",
)

private val SqlKeywords = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table",
    "index", "view", "drop", "alter", "add", "column", "primary", "key", "foreign", "references", "join",
    "inner", "left", "right", "outer", "full", "on", "group", "by", "order", "having", "limit", "offset",
    "distinct", "as", "and", "or", "not", "null", "is", "in", "like", "between", "exists", "union", "all",
    "case", "when", "then", "else", "end", "begin", "commit", "rollback", "transaction", "database", "use",
    "show", "describe", "explain", "grant", "revoke", "count", "sum", "avg", "min", "max", "default",
    "unique", "constraint", "check", "cascade", "truncate", "replace", "with", "recursive", "returning",
    "asc", "desc", "true", "false",
)

/**
 * 一种语法的扫描规则：关键字、注释前缀、字符串引号，以及几条开关。
 */
private class CodeSyntax(
    val keywords: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockComments: List<Pair<String, String>> = emptyList(),
    val quotes: Set<Char> = emptySet(),
    val tripleQuotes: Boolean = false,
    val ignoreCase: Boolean = false,
    val atMeta: Boolean = false,
    val dollarVars: Boolean = false,
    val markupTags: Boolean = false,
)

private val CLikeSyntax = CodeSyntax(
    keywords = CLikeKeywords,
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    quotes = setOf('"', '\'', '`'),
    tripleQuotes = true,
    atMeta = true,
)
private val PythonSyntax = CodeSyntax(
    keywords = PythonKeywords,
    lineComments = listOf("#"),
    quotes = setOf('"', '\''),
    tripleQuotes = true,
    atMeta = true,
)
private val ShellSyntax = CodeSyntax(
    keywords = ShellKeywords,
    lineComments = listOf("#"),
    quotes = setOf('"', '\''),
    dollarVars = true,
)
private val SqlSyntax = CodeSyntax(
    keywords = SqlKeywords,
    lineComments = listOf("--"),
    blockComments = listOf("/*" to "*/"),
    quotes = setOf('\'', '"'),
    ignoreCase = true,
)
private val JsonSyntax = CodeSyntax(
    keywords = setOf("true", "false", "null"),
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    quotes = setOf('"'),
)
private val MarkupSyntax = CodeSyntax(
    blockComments = listOf("<!--" to "-->"),
    quotes = setOf('"', '\''),
    markupTags = true,
)
private val PlainSyntax = CodeSyntax()

private fun syntaxOf(lang: CodeLang): CodeSyntax = when (lang) {
    CodeLang.CLike -> CLikeSyntax
    CodeLang.Python -> PythonSyntax
    CodeLang.Shell -> ShellSyntax
    CodeLang.Sql -> SqlSyntax
    CodeLang.Json -> JsonSyntax
    CodeLang.Markup -> MarkupSyntax
    CodeLang.Plain -> PlainSyntax
}

/**
 * 给一段代码算高亮 span：注释优先于字符串，字符串优先于标识符，编号和函数调用再各自上色。
 * 语言不认识就原样返回（宁可不染色，也不要把 diff / 日志染花）。
 * 纯函数，单测覆盖。
 */
internal fun highlightCode(code: String, language: String?, palette: CodePalette): AnnotatedString {
    val syntax = syntaxOf(codeLangOf(language))
    // 不认识的语种原样返回，一个字都不染。
    if (syntax === PlainSyntax) return AnnotatedString(code)
    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            val block = syntax.blockComments.firstOrNull { code.startsWith(it.first, i) }
            if (block != null) {
                i = appendColored(code, i, blockCommentEnd(code, i, block), palette.comment)
                continue
            }
            val line = syntax.lineComments.firstOrNull { code.startsWith(it, i) }
            if (line != null) {
                i = appendColored(code, i, lineEnd(code, i), palette.comment)
                continue
            }
            val c = code[i]
            when {
                c in syntax.quotes -> i = appendColored(code, i, stringEnd(code, i, syntax), palette.string)

                syntax.markupTags && c == '<' -> i = appendMarkupTag(code, i, palette)

                c.isDigit() -> i = appendColored(code, i, numberEnd(code, i), palette.number)

                syntax.dollarVars && c == '$' -> i = appendPrefixedWord(code, i, palette)

                syntax.atMeta && c == '@' -> i = appendPrefixedWord(code, i, palette)

                c.isLetter() || c == '_' -> {
                    val end = wordEnd(code, i)
                    val word = code.substring(i, end)
                    val color = wordColor(word, code, end, syntax, palette)
                    if (color == null) append(word) else withStyle(SpanStyle(color = color)) { append(word) }
                    i = end
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendColored(code: String, start: Int, end: Int, color: Color): Int {
    withStyle(SpanStyle(color = color)) { append(code.substring(start, end)) }
    return end
}

/** `$VAR` / `@Annotation`：符号本身照常显示，名字按函数色标出来。 */
private fun AnnotatedString.Builder.appendPrefixedWord(code: String, start: Int, palette: CodePalette): Int {
    val end = wordEnd(code, start + 1)
    append(code[start])
    if (end > start + 1) {
        withStyle(SpanStyle(color = palette.function)) { append(code.substring(start + 1, end)) }
    }
    return end
}

private fun AnnotatedString.Builder.appendMarkupTag(code: String, start: Int, palette: CodePalette): Int {
    var nameStart = start + 1
    while (nameStart < code.length && code[nameStart] in "/!?") nameStart++
    var end = nameStart
    while (end < code.length && (code[end].isLetterOrDigit() || code[end] in "-_:.")) end++
    withStyle(SpanStyle(color = palette.comment)) { append(code.substring(start, nameStart)) }
    if (end > nameStart) {
        withStyle(SpanStyle(color = palette.keyword)) { append(code.substring(nameStart, end)) }
    }
    return end.coerceAtLeast(start + 1)
}

private fun wordColor(
    word: String,
    code: String,
    end: Int,
    syntax: CodeSyntax,
    palette: CodePalette,
): Color? {
    val keyword = if (syntax.ignoreCase) syntax.keywords.any { it.equals(word, true) } else word in syntax.keywords
    if (keyword) return palette.keyword
    // 首字母大写的当成类型/类名，后面紧跟 ( 的当成函数调用。
    if (word.first().isUpperCase()) return palette.function
    var i = end
    while (i < code.length && code[i] == ' ') i++
    return if (i < code.length && code[i] == '(') palette.function else null
}

private fun wordEnd(code: String, start: Int): Int {
    var i = start
    while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
    return i
}

private fun lineEnd(code: String, start: Int): Int {
    val newline = code.indexOf('\n', start)
    return if (newline < 0) code.length else newline
}

private fun blockCommentEnd(code: String, start: Int, block: Pair<String, String>): Int {
    val close = code.indexOf(block.second, start + block.first.length)
    return if (close < 0) code.length else close + block.second.length
}

private fun stringEnd(code: String, start: Int, syntax: CodeSyntax): Int {
    val quote = code[start]
    val triple = syntax.tripleQuotes && code.startsWith("$quote$quote$quote", start)
    val delimiter = if (triple) "$quote$quote$quote" else quote.toString()
    var i = start + delimiter.length
    while (i < code.length) {
        if (code[i] == '\\') {
            i += 2
            continue
        }
        if (code.startsWith(delimiter, i)) return i + delimiter.length
        // 单行字符串没闭合就到行尾收住，别把后面的整段代码都吃掉。
        if (!triple && code[i] == '\n') return i
        i++
    }
    return code.length
}

private fun numberEnd(code: String, start: Int): Int {
    var i = start
    if (code[i] == '0' && i + 1 < code.length && code[i + 1].lowercaseChar() in "xob") {
        i += 2
        while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
        return i
    }
    while (i < code.length && (code[i].isDigit() || code[i] == '_')) i++
    if (i + 1 < code.length && code[i] == '.' && code[i + 1].isDigit()) {
        i++
        while (i < code.length && (code[i].isDigit() || code[i] == '_')) i++
    }
    if (i < code.length && code[i].lowercaseChar() == 'e') {
        var j = i + 1
        if (j < code.length && (code[j] == '+' || code[j] == '-')) j++
        if (j < code.length && code[j].isDigit()) {
            i = j
            while (i < code.length && code[i].isDigit()) i++
        }
    }
    while (i < code.length && code[i].lowercaseChar() in "ulf") i++
    return i
}

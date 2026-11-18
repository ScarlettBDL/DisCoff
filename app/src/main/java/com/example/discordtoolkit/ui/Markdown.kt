package com.example.discordtoolkit.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.ui.theme.DcColors

/**
 * Markdown estilo Discord: **negrita**, *cursiva*, __subrayado__, ~~tachado~~, ||spoiler||,
 * `código`, ```bloques```, # títulos, > citas, - listas, -# texto chico y links.
 */

private val headingRegex = Regex("^(#{1,3})\\s+(.*)$")
private val bulletRegex = Regex("^\\s*[-*]\\s+(.*)$")
private val languageRegex = Regex("^\\w+$")

private val codeStyle = SpanStyle(
    fontFamily = FontFamily.Monospace,
    background = DcColors.SurfaceHigh,
    fontSize = 14.sp
)

private sealed interface Block {
    class CodeBlock(val code: String) : Block
    class TextLine(val text: String) : Block
}

private fun splitBlocks(text: String): List<Block> {
    val blocks = ArrayList<Block>()
    val lines = text.split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trim().startsWith("```")) {
            val opening = line.trim().removePrefix("```")
            val code = StringBuilder()
            var j = i
            var closed = false
            while (j < lines.size && !closed) {
                var part = if (j == i) opening else lines[j]
                if (part.trimEnd().endsWith("```")) {
                    part = part.trimEnd().removeSuffix("```")
                    closed = true
                }
                val isLanguageTag = j == i && !closed && languageRegex.matches(part)
                if (!isLanguageTag && (code.isNotEmpty() || part.isNotEmpty())) {
                    if (code.isNotEmpty()) code.append("\n")
                    code.append(part)
                }
                j++
            }
            blocks.add(Block.CodeBlock(code.toString()))
            i = j
        } else {
            blocks.add(Block.TextLine(line))
            i++
        }
    }
    return blocks
}

fun renderMarkdown(text: String): AnnotatedString {
    val blocks = splitBlocks(text)
    return buildAnnotatedString {
        blocks.forEachIndexed { index, block ->
            if (index > 0) append("\n")
            when (block) {
                is Block.CodeBlock -> withStyle(codeStyle) { append(block.code) }
                is Block.TextLine -> appendBlockLine(block.text)
            }
        }
    }
}

private fun AnnotatedString.Builder.appendBlockLine(line: String) {
    val heading = headingRegex.matchEntire(line)
    if (heading != null) {
        val size = when (heading.groupValues[1].length) {
            1 -> 26.sp
            2 -> 22.sp
            else -> 19.sp
        }
        withStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold)) {
            appendInline(heading.groupValues[2])
        }
        return
    }
    if (line.startsWith("-# ")) {
        withStyle(SpanStyle(fontSize = 12.sp, color = DcColors.TextMuted)) {
            appendInline(line.removePrefix("-# "))
        }
        return
    }
    if (line.startsWith("> ")) {
        withStyle(SpanStyle(color = DcColors.TextMuted)) { append("▎ ") }
        appendInline(line.removePrefix("> "))
        return
    }
    val bullet = bulletRegex.matchEntire(line)
    if (bullet != null) {
        append("  •  ")
        appendInline(bullet.groupValues[1])
        return
    }
    appendInline(line)
}

private fun findClose(text: String, delim: String, from: Int): Int {
    var idx = text.indexOf(delim, from)
    while (idx >= 0) {
        if (delim.length == 1) {
            val next = text.getOrNull(idx + 1)
            if (next == delim[0]) {
                idx = text.indexOf(delim, idx + 2)
                continue
            }
            if (delim == "_" && next != null && next.isLetterOrDigit()) {
                idx = text.indexOf(delim, idx + 1)
                continue
            }
        } else {
            while (idx + delim.length < text.length && text[idx + delim.length] == delim[0]) idx++
        }
        return idx
    }
    return -1
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    val n = text.length
    val plain = StringBuilder()
    var i = 0

    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }

    while (i < n) {
        val c = text[i]

        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                withStyle(codeStyle) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }

        if (c == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
            var e = i
            while (e < n && !text[e].isWhitespace() && text[e] != '<' && text[e] != '>') e++
            while (e > i && text[e - 1] in ".,;:!?)]") e--
            val url = text.substring(i, e)
            flush()
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = DcColors.Accent, textDecoration = TextDecoration.Underline))
                )
            ) { append(url) }
            i = e
            continue
        }

        val nextIsSpace = i + 1 >= n || text[i + 1].isWhitespace()
        val delim: String? = when {
            text.startsWith("**", i) -> "**"
            text.startsWith("__", i) -> "__"
            text.startsWith("~~", i) -> "~~"
            text.startsWith("||", i) -> "||"
            c == '*' && !nextIsSpace -> "*"
            c == '_' && !nextIsSpace && (i == 0 || !text[i - 1].isLetterOrDigit()) -> "_"
            else -> null
        }
        if (delim != null) {
            val end = findClose(text, delim, i + delim.length)
            if (end > i + delim.length) {
                flush()
                val inner = text.substring(i + delim.length, end)
                val style = when (delim) {
                    "**" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "__" -> SpanStyle(textDecoration = TextDecoration.Underline)
                    "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "||" -> SpanStyle(background = DcColors.SurfaceHigh)
                    else -> SpanStyle(fontStyle = FontStyle.Italic)
                }
                withStyle(style) { appendInline(inner) }
                i = end + delim.length
                continue
            }
        }

        plain.append(c)
        i++
    }
    flush()
}

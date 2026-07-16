package com.termuxcodex.client.ui

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String?, val text: String) : MarkdownBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
) {
    val cachedBlocks = remember(markdown) { markdownCache.get(markdown) }
    val hasSyntax = remember(markdown) { hasMarkdownSyntax(markdown) }
    val initialBlocks = remember(markdown, cachedBlocks, hasSyntax) {
        cachedBlocks ?: if (hasSyntax && markdown.length <= MAX_SYNC_PARSE_CHARS) {
            parseBlocks(markdown).also { markdownCache.put(markdown, it) }
        } else {
            null
        }
    }
    val blocks by produceState<List<MarkdownBlock>?>(initialBlocks, markdown, hasSyntax) {
        if (value == null && hasSyntax) {
            value = withContext(Dispatchers.Default) {
                markdownCache.get(markdown) ?: parseBlocks(markdown).also {
                    markdownCache.put(markdown, it)
                }
            }
        }
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val resolvedBlocks = blocks
    if (resolvedBlocks == null) {
        Text(markdown, modifier = modifier, style = style, color = color)
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        resolvedBlocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> InlineText(block.text, style, color, linkColor)
                is MarkdownBlock.Heading -> InlineText(
                    block.text,
                    when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color,
                    linkColor,
                    fontWeight = FontWeight.SemiBold,
                )
                is MarkdownBlock.ListItem -> Row(Modifier.fillMaxWidth()) {
                    Text(block.marker, style = style, color = color)
                    Spacer(Modifier.width(8.dp))
                    InlineText(
                        block.text,
                        style,
                        color,
                        linkColor,
                        Modifier.weight(1f),
                    )
                }
                is MarkdownBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    InlineText(
                        block.text,
                        style.copy(fontStyle = FontStyle.Italic),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        linkColor,
                    )
                }
                is MarkdownBlock.Code -> Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(12.dp)
                ) {
                    block.language?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    val annotated = remember(text, linkColor) {
        if (text.length > MAX_INLINE_MARKDOWN_CHARS) AnnotatedString(text)
        else inlineMarkdown(text, linkColor)
    }
    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        style = style,
    )
}

private fun parseBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return listOf(MarkdownBlock.Paragraph(""))
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    var index = 0
    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flush()
            val language = trimmed.removePrefix("```").trim().ifBlank { null }
            index++
            val code = mutableListOf<String>()
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                code += lines[index++]
            }
            if (index < lines.size) index++
            blocks += MarkdownBlock.Code(language, code.joinToString("\n"))
            continue
        }
        if (trimmed.isEmpty()) {
            flush()
            index++
            continue
        }
        val heading = HEADING.matchEntire(trimmed)
        val bullet = BULLET.matchEntire(line)
        val numbered = NUMBERED.matchEntire(line)
        when {
            heading != null -> {
                flush()
                blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            }
            bullet != null -> {
                flush()
                blocks += MarkdownBlock.ListItem("•", bullet.groupValues[1])
            }
            numbered != null -> {
                flush()
                blocks += MarkdownBlock.ListItem("${numbered.groupValues[1]}.", numbered.groupValues[2])
            }
            trimmed.startsWith(">") -> {
                flush()
                blocks += MarkdownBlock.Quote(trimmed.removePrefix(">").trimStart())
            }
            trimmed == "---" || trimmed == "***" -> flush()
            else -> paragraph += line
        }
        index++
    }
    flush()
    return blocks
}

private fun inlineMarkdown(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    INLINE_TOKEN.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") || token.startsWith("__") -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold)
            ) { append(token.substring(2, token.length - 2)) }
            token.startsWith("`") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = linkColor.copy(alpha = 0.12f),
                )
            ) { append(token.substring(1, token.length - 1)) }
            token.startsWith("[") -> {
                val close = token.indexOf("](")
                val label = token.substring(1, close)
                val url = token.substring(close + 2, token.length - 1)
                val displayText = if (label.isFileNameFor(url)) url else label
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )
                        ),
                    )
                ) { append(displayText) }
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.substring(1, token.length - 1))
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

private fun String.isFileNameFor(url: String): Boolean {
    if (url.isBlank() || url.startsWith("http://") || url.startsWith("https://") ||
        url.startsWith("mailto:") || '/' !in url
    ) return false
    val normalizedUrl = url.substringBefore('#').substringBefore('?').trimEnd('/')
    return normalizedUrl.substringAfterLast('/') == this
}

private fun hasMarkdownSyntax(markdown: String): Boolean {
    return markdown.lineSequence().any { line ->
        val trimmed = line.trimStart()
        trimmed.startsWith("#") || trimmed.startsWith(">") ||
            trimmed.startsWith("```") || BULLET.matches(line) || NUMBERED.matches(line)
    } || INLINE_TOKEN.containsMatchIn(markdown)
}

private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
private val BULLET = Regex("^\\s*[-+*]\\s+(.+)$")
private val NUMBERED = Regex("^\\s*(\\d+)\\.\\s+(.+)$")
private val INLINE_TOKEN = Regex(
    "\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|`[^`\\n]+`|\\[[^]\\n]+]\\([^)\\n]+\\)|\\*[^*\\n]+\\*|_[^_\\n]+_"
)

private val markdownCache = object : LruCache<String, List<MarkdownBlock>>(768) {
    override fun sizeOf(key: String, value: List<MarkdownBlock>): Int {
        return ((key.length * 4) / 1024).coerceAtLeast(1)
    }
}

private const val MAX_SYNC_PARSE_CHARS = 8_000
private const val MAX_INLINE_MARKDOWN_CHARS = 4_000

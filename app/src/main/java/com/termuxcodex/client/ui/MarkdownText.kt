package com.termuxcodex.client.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val contentColor = LocalContentColor.current
    val resolvedColor = if (color == Color.Unspecified) contentColor else color
    val tableBorderColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val tableHeaderColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val tableOddColor = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val tableEvenColor = MaterialTheme.colorScheme.surface.toArgb()
    val tablePadding = with(density) { 10.dp.roundToPx() }
    val tableBorderWidth = with(density) { 1.dp.roundToPx() }
    val markwon = remember(
        context,
        tableBorderColor,
        tableHeaderColor,
        tableOddColor,
        tableEvenColor,
        tablePadding,
        tableBorderWidth,
    ) {
        val tableTheme = TableTheme.buildWithDefaults(context)
            .tableCellPadding(tablePadding)
            .tableBorderWidth(tableBorderWidth)
            .tableBorderColor(tableBorderColor)
            .tableHeaderRowBackgroundColor(tableHeaderColor)
            .tableOddRowBackgroundColor(tableOddColor)
            .tableEvenRowBackgroundColor(tableEvenColor)
            .build()
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(tableTheme))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver(SafeLinkResolver)
                }
            })
            .build()
    }
    val rendered by produceState<RenderedMarkdown?>(null, markwon, markdown) {
        value = withContext(Dispatchers.Default) {
            RenderedMarkdown(
                source = markdown,
                content = removeUnsafeLinks(markwon.toMarkdown(markdown)),
            )
        }
    }
    val fontSizePx = remember(style.fontSize, density) {
        if (style.fontSize == TextUnit.Unspecified) null else with(density) { style.fontSize.toPx() }
    }
    val monospace = style.fontFamily == FontFamily.Monospace
    val parsed = rendered?.takeIf { it.source == markdown }?.content

    if (parsed == null) {
        SelectionContainer {
            Text(
                text = markdown,
                modifier = modifier,
                style = style,
                color = resolvedColor,
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = false
                setLineSpacing(0f, 1.08f)
                setTextColor(resolvedColor.toArgb())
                fontSizePx?.let { setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
                typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT
                markwon.setParsedMarkdown(this, parsed)
            }
        },
        update = { textView ->
            textView.setTextColor(resolvedColor.toArgb())
            fontSizePx?.let { textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
            textView.typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT
            if (textView.text !== parsed) {
                markwon.setParsedMarkdown(textView, parsed)
            }
        },
    )
}

private data class RenderedMarkdown(
    val source: String,
    val content: Spanned,
)

private fun removeUnsafeLinks(rendered: Spanned): Spanned {
    val sanitized = SpannableStringBuilder(rendered)
    sanitized.getSpans(0, sanitized.length, URLSpan::class.java)
        .filterNot { isAllowedExternalLink(it.url) }
        .sortedByDescending { sanitized.getSpanStart(it) }
        .forEach { span ->
            val start = sanitized.getSpanStart(span)
            val end = sanitized.getSpanEnd(span)
            val visibleText = sanitized.substring(start, end)
            sanitized.removeSpan(span)
            if (start >= 0 && end >= start && visibleText != span.url) {
                sanitized.replace(start, end, span.url)
            }
        }
    return sanitized
}

internal fun isAllowedExternalLink(link: String): Boolean {
    val uri = runCatching { link.toUri() }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https", "mailto") &&
        (uri.scheme.equals("mailto", ignoreCase = true) || !uri.host.isNullOrBlank())
}

private object SafeLinkResolver : LinkResolver {
    private val allowedSchemes = setOf("http", "https", "mailto")

    override fun resolve(view: View, link: String) {
        val uri = runCatching { link.toUri() }.getOrNull() ?: return
        if (!isAllowedExternalLink(link) || uri.scheme?.lowercase() !in allowedSchemes) return
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (view.context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { view.context.startActivity(intent) }
    }
}

package com.termuxcodex.client.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
    val rendered by produceState<Spanned?>(null, markwon, markdown) {
        value = withContext(Dispatchers.Default) { markwon.toMarkdown(markdown) }
    }
    val fontSizePx = remember(style.fontSize, density) {
        if (style.fontSize == TextUnit.Unspecified) null else with(density) { style.fontSize.toPx() }
    }
    val monospace = style.fontFamily == FontFamily.Monospace

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = false
                setLineSpacing(0f, 1.08f)
            }
        },
        update = { textView ->
            textView.setTextColor(resolvedColor.toArgb())
            fontSizePx?.let { textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
            textView.typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT
            val parsed = rendered
            if (parsed == null) {
                if (textView.text.toString() != markdown) textView.text = markdown
            } else if (textView.text !== parsed) {
                markwon.setParsedMarkdown(textView, parsed)
            }
        },
    )
}

private object SafeLinkResolver : LinkResolver {
    private val allowedSchemes = setOf("http", "https", "mailto")

    override fun resolve(view: View, link: String) {
        val uri = runCatching { link.toUri() }.getOrNull() ?: return
        if (uri.scheme?.lowercase() !in allowedSchemes) return
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (view.context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { view.context.startActivity(intent) }
    }
}

package com.termuxcodex.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termuxcodex.client.DiffLine
import com.termuxcodex.client.DiffLineKind
import com.termuxcodex.client.UnifiedDiffParser

@Composable
internal fun DiffViewer(
    text: String,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(text) { UnifiedDiffParser.parse(text) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp),
    ) {
        if (parsed.narrative.isNotBlank()) {
            item(key = "narrative") {
                Text(
                    parsed.narrative,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        parsed.files.forEachIndexed { fileIndex, file ->
            stickyHeader(key = "file-$fileIndex-${file.path}") {
                FileDiffHeader(
                    path = file.path,
                    oldPath = file.oldPath,
                    changeKind = file.changeKind,
                    additions = file.additions,
                    deletions = file.deletions,
                )
            }
            if (file.lines.isEmpty()) {
                item(key = "empty-file-$fileIndex") {
                    Text(
                        "仅文件元数据发生变化",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    count = file.lines.size,
                    key = { lineIndex -> "line-$fileIndex-$lineIndex" },
                ) { lineIndex ->
                    DiffLineRow(file.lines[lineIndex])
                }
            }
            item(key = "spacer-$fileIndex") { Spacer(Modifier.height(10.dp)) }
        }
        if (parsed.files.isEmpty() && parsed.narrative.isBlank()) {
            item(key = "empty") {
                Text("没有可显示的差异", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FileDiffHeader(
    path: String,
    oldPath: String?,
    changeKind: String,
    additions: Int,
    deletions: Int,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    path,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    if (oldPath == null) changeKind else "$changeKind · $oldPath → $path",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DiffCount("+$additions", Color(0xFF1A7F37))
            Spacer(Modifier.width(6.dp))
            DiffCount("-$deletions", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DiffCount(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val background = when (line.kind) {
        DiffLineKind.ADDITION -> Color(0xFF2DA44E).copy(alpha = 0.14f)
        DiffLineKind.DELETION -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val foreground = when (line.kind) {
        DiffLineKind.ADDITION -> Color(0xFF1A7F37)
        DiffLineKind.DELETION -> MaterialTheme.colorScheme.error
        DiffLineKind.META -> MaterialTheme.colorScheme.onSurfaceVariant
        DiffLineKind.CONTEXT -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            line.oldLine?.toString().orEmpty(),
            modifier = Modifier.width(42.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(
            line.newLine?.toString().orEmpty(),
            modifier = Modifier.width(42.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(
            when (line.kind) {
                DiffLineKind.ADDITION -> "+"
                DiffLineKind.DELETION -> "−"
                else -> " "
            },
            modifier = Modifier.width(18.dp),
            color = foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            SelectionContainer {
                Text(
                    line.text.ifEmpty { " " },
                    modifier = Modifier.fillMaxWidth(),
                    color = foreground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

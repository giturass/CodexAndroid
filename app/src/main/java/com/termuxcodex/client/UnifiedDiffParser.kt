package com.termuxcodex.client

import com.github.difflib.patch.AbstractDelta
import com.github.difflib.unifieddiff.UnifiedDiffFile
import com.github.difflib.unifieddiff.UnifiedDiffReader
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

enum class DiffLineKind { CONTEXT, ADDITION, DELETION, META }

data class DiffLine(
    val text: String,
    val kind: DiffLineKind,
    val oldLine: Int? = null,
    val newLine: Int? = null,
)

data class FileDiff(
    val path: String,
    val oldPath: String?,
    val changeKind: String,
    val lines: List<DiffLine>,
) {
    val additions: Int get() = lines.count { it.kind == DiffLineKind.ADDITION }
    val deletions: Int get() = lines.count { it.kind == DiffLineKind.DELETION }
}

data class ParsedDiff(
    val narrative: String,
    val files: List<FileDiff>,
)

object UnifiedDiffParser {
    private val changeHeader = Regex(
        "^(add|added|create|created|update|updated|modify|modified|delete|deleted|rename|renamed|copy|copied|change|changed|新增|创建|修改|删除|重命名|复制)\\s*·\\s*(.+)$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): ParsedDiff {
        if (raw.isBlank()) return ParsedDiff("", emptyList())
        val sections = splitSections(raw)
        val files = mutableListOf<FileDiff>()
        val narrative = mutableListOf<String>()

        sections.forEach { section ->
            val prepared = prepareUnifiedDiff(section)
            if (prepared == null) {
                narrative += section.text
            } else {
                val parsedFiles = parseWithJavaDiffUtils(prepared.diff, prepared.kind, prepared.path)
                if (parsedFiles.isEmpty()) narrative += section.text else files += parsedFiles
            }
        }

        return ParsedDiff(
            narrative = narrative.joinToString("\n").trim(),
            files = files,
        )
    }

    private fun parseWithJavaDiffUtils(
        rawDiff: String,
        hintedKind: String?,
        hintedPath: String?,
    ): List<FileDiff> = runCatching {
        val parsed = ByteArrayInputStream(rawDiff.toByteArray(StandardCharsets.UTF_8)).use {
            UnifiedDiffReader.parseUnifiedDiff(it)
        }
        parsed.files.map { file -> file.toFileDiff(hintedKind, hintedPath) }
    }.getOrDefault(emptyList())

    private fun UnifiedDiffFile.toFileDiff(hintedKind: String?, hintedPath: String?): FileDiff {
        val from = fromFile?.let(::normalizePath)?.takeUnless { it == "/dev/null" }
        val to = toFile?.let(::normalizePath)?.takeUnless { it == "/dev/null" }
        val path = renameTo ?: copyTo ?: to ?: from ?: hintedPath ?: "文件修改"
        val kind = hintedKind ?: when {
            newFileMode != null || from == null -> "add"
            deletedFileMode != null || to == null -> "delete"
            renameTo != null -> "rename"
            copyTo != null -> "copy"
            else -> "update"
        }
        return FileDiff(
            path = normalizePath(path),
            oldPath = (renameFrom ?: copyFrom ?: from)?.let(::normalizePath)?.takeIf {
                it != normalizePath(path)
            },
            changeKind = kind,
            lines = patch.deltas.flatMap(::mergeDelta),
        )
    }

    private fun mergeDelta(delta: AbstractDelta<String>): List<DiffLine> {
        val oldLines = delta.source.lines
        val newLines = delta.target.lines
        val deleted = delta.source.changePosition.orEmpty().toSet()
        val added = delta.target.changePosition.orEmpty().toSet()
        var oldIndex = 0
        var newIndex = 0
        val lines = mutableListOf<DiffLine>()

        while (oldIndex < oldLines.size || newIndex < newLines.size) {
            val oldNumber = delta.source.position + oldIndex + 1
            val newNumber = delta.target.position + newIndex + 1
            val oldChanged = oldIndex < oldLines.size && oldNumber in deleted
            val newChanged = newIndex < newLines.size && newNumber in added

            when {
                oldChanged -> {
                    lines += DiffLine(oldLines[oldIndex], DiffLineKind.DELETION, oldNumber, null)
                    oldIndex++
                }

                newChanged -> {
                    lines += DiffLine(newLines[newIndex], DiffLineKind.ADDITION, null, newNumber)
                    newIndex++
                }

                oldIndex < oldLines.size && newIndex < newLines.size -> {
                    val oldText = oldLines[oldIndex]
                    val newText = newLines[newIndex]
                    if (oldText == newText) {
                        lines += DiffLine(oldText, DiffLineKind.CONTEXT, oldNumber, newNumber)
                        oldIndex++
                        newIndex++
                    } else {
                        lines += DiffLine(oldText, DiffLineKind.DELETION, oldNumber, null)
                        oldIndex++
                    }
                }

                oldIndex < oldLines.size -> {
                    lines += DiffLine(oldLines[oldIndex], DiffLineKind.DELETION, oldNumber, null)
                    oldIndex++
                }

                else -> {
                    lines += DiffLine(newLines[newIndex], DiffLineKind.ADDITION, null, newNumber)
                    newIndex++
                }
            }
        }
        return lines
    }

    private fun splitSections(raw: String): List<RawSection> {
        val sections = mutableListOf<RawSection>()
        val current = mutableListOf<String>()
        var currentIsDiff = false

        fun flush() {
            if (current.isNotEmpty()) sections += RawSection(current.joinToString("\n"), currentIsDiff)
            current.clear()
        }

        raw.lineSequence().forEach { line ->
            val startsDiff = line.startsWith("diff --git ") || changeHeader.matches(line) ||
                (line.startsWith("--- ") && !currentIsDiff)
            if (startsDiff && current.isNotEmpty()) {
                flush()
            }
            if (startsDiff) currentIsDiff = true
            current += line
        }
        flush()
        return sections
    }

    private fun prepareUnifiedDiff(section: RawSection): PreparedDiff? {
        if (!section.isDiff) return null
        val lines = section.text.lines().toMutableList()
        val match = lines.firstOrNull()?.let(changeHeader::matchEntire)
        if (match != null) {
            val kind = match.groupValues[1]
            val path = normalizePath(match.groupValues[2])
            lines.removeAt(0)
            val firstHunk = lines.indexOfFirst { it.startsWith("@@ ") }
            if (firstHunk < 0) return null
            val isAddition = kind.lowercase() in setOf("add", "added", "create", "created", "新增", "创建")
            val isDeletion = kind.lowercase() in setOf("delete", "deleted", "删除")
            lines.add(firstHunk, if (isDeletion) "+++ /dev/null" else "+++ b/$path")
            lines.add(firstHunk, if (isAddition) "--- /dev/null" else "--- a/$path")
            lines.add(firstHunk, "diff --git a/$path b/$path")
            return PreparedDiff(lines.joinToString("\n"), kind, path)
        }
        return PreparedDiff(section.text, null, null)
    }

    private fun normalizePath(path: String): String = path.trim()
        .substringBefore('\t')
        .removePrefix("a/")
        .removePrefix("b/")

    private data class RawSection(val text: String, val isDiff: Boolean)
    private data class PreparedDiff(val diff: String, val kind: String?, val path: String?)
}

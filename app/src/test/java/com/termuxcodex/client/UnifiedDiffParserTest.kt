package com.termuxcodex.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDiffParserTest {
    @Test
    fun parsesAppServerChangeHeaderAndLineNumbers() {
        val parsed = UnifiedDiffParser.parse(
            """
            Codex 请求应用文件修改。

            update · app/src/main/Test.kt
            @@ -10,2 +10,3 @@
             context
            -old
            +new
            +extra
            """.trimIndent()
        )

        assertEquals("Codex 请求应用文件修改。", parsed.narrative)
        assertEquals(1, parsed.files.size)
        val file = parsed.files.single()
        assertEquals("app/src/main/Test.kt", file.path)
        assertEquals(2, file.additions)
        assertEquals(1, file.deletions)
        assertEquals(10, file.lines.first { it.kind == DiffLineKind.CONTEXT }.oldLine)
        assertEquals(10, file.lines.first { it.kind == DiffLineKind.CONTEXT }.newLine)
    }

    @Test
    fun parsesMultipleGitDiffFiles() {
        val parsed = UnifiedDiffParser.parse(
            """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1 +1 @@
            -a
            +b
            diff --git a/b.txt b/b.txt
            --- a/b.txt
            +++ b/b.txt
            @@ -0,0 +1 @@
            +new
            """.trimIndent()
        )

        assertEquals(listOf("a.txt", "b.txt"), parsed.files.map { it.path })
        assertTrue(parsed.files.all { it.additions == 1 })
    }

    @Test
    fun buildsMergedRowsFromJavaDiffUtilsDeltas() {
        val parsed = UnifiedDiffParser.parse(
            """
            diff --git a/sample.txt b/sample.txt
            --- a/sample.txt
            +++ b/sample.txt
            @@ -1,4 +1,4 @@
             first
            -old one
            -old two
            +new one
            +new two
             last
            """.trimIndent()
        )

        val lines = parsed.files.single().lines
        assertEquals(
            listOf(
                DiffLineKind.CONTEXT,
                DiffLineKind.DELETION,
                DiffLineKind.DELETION,
                DiffLineKind.ADDITION,
                DiffLineKind.ADDITION,
                DiffLineKind.CONTEXT,
            ),
            lines.map { it.kind },
        )
        assertEquals(listOf(1, 2, 3, null, null, 4), lines.map { it.oldLine })
        assertEquals(listOf(1, null, null, 2, 3, 4), lines.map { it.newLine })
    }

    @Test
    fun keepsNarrativeBeforeAppServerChange() {
        val parsed = UnifiedDiffParser.parse(
            """
            请审阅以下修改。

            update · src/Main.kt
            @@ -1 +1 @@
            -old
            +new
            """.trimIndent()
        )

        assertEquals("请审阅以下修改。", parsed.narrative)
        assertEquals("src/Main.kt", parsed.files.single().path)
    }

    @Test
    fun parsesPlainUnifiedDiffWithoutGitHeader() {
        val parsed = UnifiedDiffParser.parse(
            """
            --- a/old.txt
            +++ b/new.txt
            @@ -1 +1 @@
            -old
            +new
            """.trimIndent()
        )

        assertEquals("new.txt", parsed.files.single().path)
        assertEquals(1, parsed.files.single().additions)
        assertEquals(1, parsed.files.single().deletions)
    }
}

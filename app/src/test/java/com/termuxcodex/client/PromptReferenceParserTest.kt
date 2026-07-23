package com.termuxcodex.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptReferenceParserTest {
    @Test
    fun parsesReferenceAtCursor() {
        assertEquals(
            PromptReferenceToken('@', "Codex", 5, 11),
            activePromptReferenceToken("查看一下 @Codex", 11),
        )
        assertEquals(
            PromptReferenceToken('$', "review", 0, 7),
            activePromptReferenceToken("\$review 后继续", 7),
        )
        assertEquals(
            PromptReferenceToken('/', "rev", 0, 4),
            activePromptReferenceToken("/rev", 4),
        )
    }

    @Test
    fun ignoresEmailAndCompletedToken() {
        assertNull(activePromptReferenceToken("", 0))
        assertNull(activePromptReferenceToken("me@example.com", 6))
        assertNull(activePromptReferenceToken("@file name", 10))
        assertNull(activePromptReferenceToken("打开 /home", 8))
    }

    @Test
    fun replacesOnlyActiveToken() {
        val token = requireNotNull(activePromptReferenceToken("检查 @cod", 7))
        assertEquals(
            "检查 @app/src/main.kt " to 20,
            replacePromptReferenceToken("检查 @cod", token, "app/src/main.kt"),
        )
    }
}

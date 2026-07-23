package com.termuxcodex.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilitiesTest {
    @Test
    fun checksInputModalityCaseInsensitively() {
        val model = CodexModel(
            id = "text-only",
            model = "text-only",
            displayName = "Text only",
            description = "",
            isDefault = false,
            defaultReasoningEffort = "",
            supportedReasoningEfforts = emptyList(),
            inputModalities = listOf("text"),
        )

        assertTrue(model.supportsInputModality("TEXT"))
        assertFalse(model.supportsInputModality("image"))
    }
}

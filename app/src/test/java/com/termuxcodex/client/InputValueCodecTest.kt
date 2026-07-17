package com.termuxcodex.client

import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputValueCodecTest {
    @Test
    fun rejectsInvalidTypedValuesInsteadOfReplacingThemWithZero() {
        assertNull(encodeInputValue("abc", "integer"))
        assertNull(encodeInputValue("NaN", "number"))
        assertNull(encodeInputValue("maybe", "boolean"))
    }

    @Test
    fun encodesValidTypedValues() {
        assertEquals(JsonPrimitive(42L), encodeInputValue("42", "integer"))
        assertEquals(JsonPrimitive(true), encodeInputValue("true", "boolean"))
    }

    @Test
    fun validatesRequiredAndTypedAnswers() {
        val question = InputQuestion("count", "数量", "输入数量", emptyList(), false, "integer", true)
        assertEquals("此项为必填", inputValidationError(question, ""))
        assertEquals("请输入有效整数", inputValidationError(question, "abc"))
        assertNull(inputValidationError(question, "12"))
    }
}

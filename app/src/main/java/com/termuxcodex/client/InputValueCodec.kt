package com.termuxcodex.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

internal fun encodeInputValue(value: String, type: String): JsonElement? = when (type) {
    "boolean" -> when (value.trim().lowercase()) {
        "true", "1" -> JsonPrimitive(true)
        "false", "0" -> JsonPrimitive(false)
        "" -> JsonPrimitive("")
        else -> null
    }

    "integer" -> value.toLongOrNull()?.let(::JsonPrimitive)
    "number" -> value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(::JsonPrimitive)
    "array" -> JsonArray().apply {
        value.split(',').map(String::trim).filter(String::isNotEmpty).forEach(::add)
    }

    else -> JsonPrimitive(value)
}

internal fun inputValidationError(question: InputQuestion, value: String): String? {
    if (value.isBlank()) return if (question.required) "此项为必填" else null
    return when (question.valueType) {
        "boolean" -> if (encodeInputValue(value, "boolean") == null) "请输入 true、false、1 或 0" else null
        "integer" -> if (encodeInputValue(value, "integer") == null) "请输入有效整数" else null
        "number" -> if (encodeInputValue(value, "number") == null) "请输入有效数字" else null
        else -> null
    }
}

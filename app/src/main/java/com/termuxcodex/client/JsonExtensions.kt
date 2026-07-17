package com.termuxcodex.client

import com.google.gson.JsonObject

internal fun JsonObject.string(name: String): String? = try {
    get(name)?.takeUnless { it.isJsonNull }?.asString
} catch (_: RuntimeException) {
    null
}

internal fun JsonObject.long(name: String): Long? = try {
    get(name)?.takeUnless { it.isJsonNull }?.asLong
} catch (_: RuntimeException) {
    null
}

internal fun JsonObject.bool(name: String): Boolean? = try {
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean
} catch (_: RuntimeException) {
    null
}

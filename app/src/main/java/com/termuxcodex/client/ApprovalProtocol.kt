package com.termuxcodex.client

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

enum class ApprovalProtocol {
    CURRENT,
    LEGACY,
}

internal fun normalizeApprovalDecision(
    protocol: ApprovalProtocol?,
    decision: JsonElement,
): JsonElement {
    if (protocol == null || !decision.isJsonPrimitive || !decision.asJsonPrimitive.isString) {
        return decision.deepCopy()
    }
    val normalized = when (protocol) {
        ApprovalProtocol.CURRENT -> when (decision.asString) {
            "approved" -> "accept"
            "approved_for_session" -> "acceptForSession"
            "denied" -> "decline"
            "abort" -> "cancel"
            else -> decision.asString
        }

        ApprovalProtocol.LEGACY -> when (decision.asString) {
            "accept" -> "approved"
            "acceptForSession" -> "approved_for_session"
            "decline" -> "denied"
            "cancel" -> "abort"
            else -> decision.asString
        }
    }
    return JsonPrimitive(normalized)
}

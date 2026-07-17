package com.termuxcodex.client

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalProtocolTest {
    @Test
    fun currentProtocolConvertsLegacySessionDecision() {
        assertEquals(
            JsonPrimitive("acceptForSession"),
            normalizeApprovalDecision(
                ApprovalProtocol.CURRENT,
                JsonPrimitive("approved_for_session"),
            ),
        )
    }

    @Test
    fun legacyProtocolConvertsCurrentSessionDecision() {
        assertEquals(
            JsonPrimitive("approved_for_session"),
            normalizeApprovalDecision(
                ApprovalProtocol.LEGACY,
                JsonPrimitive("acceptForSession"),
            ),
        )
    }

    @Test
    fun structuredDecisionIsPreserved() {
        val decision = JsonObject().apply {
            add("acceptWithExecpolicyAmendment", JsonObject())
        }

        assertEquals(
            decision,
            normalizeApprovalDecision(ApprovalProtocol.CURRENT, decision),
        )
    }
}

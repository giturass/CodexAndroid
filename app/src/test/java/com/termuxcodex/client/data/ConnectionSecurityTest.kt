package com.termuxcodex.client.data

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSecurityTest {
    @Test
    fun allowsLoopbackPlainWebSocket() {
        assertNull(ConnectionSecurity.validate("ws://127.0.0.1:4500", "token").error)
        assertNull(ConnectionSecurity.validate("ws://[::1]:4500", "").error)
    }

    @Test
    fun requiresTlsForRemoteHosts() {
        assertTrue(ConnectionSecurity.validate("ws://example.com:4500", "").error!!.contains("wss"))
        assertNull(ConnectionSecurity.validate("wss://example.com:4500", "token").error)
    }

    @Test
    fun rejectsHeaderInjection() {
        assertTrue(ConnectionSecurity.validate("ws://127.0.0.1:4500", "a\r\nb").error!!.contains("换行"))
    }
}

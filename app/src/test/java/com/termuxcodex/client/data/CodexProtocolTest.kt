package com.termuxcodex.client.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CodexProtocolTest {
    @Test
    fun usesDocumentedThreadLifecycleMethods() {
        assertEquals("thread/name/set", CodexProtocol.ClientRequest.THREAD_NAME_SET)
        assertEquals("thread/unsubscribe", CodexProtocol.ClientRequest.THREAD_UNSUBSCRIBE)
        assertEquals("fs/getMetadata", CodexProtocol.ClientRequest.FS_GET_METADATA)
    }
}

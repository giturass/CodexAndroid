package com.termuxcodex.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceValidationTest {
    @Test
    fun acceptsAbsoluteNulFreePaths() {
        assertTrue(isValidWorkspacePath("/data/data/com.termux/files/home"))
        assertTrue(isValidWorkspacePath(" /tmp/project "))
    }

    @Test
    fun rejectsRelativeEmptyAndNulPaths() {
        assertFalse(isValidWorkspacePath(""))
        assertFalse(isValidWorkspacePath("project"))
        assertFalse(isValidWorkspacePath("/tmp\u0000project"))
    }
}

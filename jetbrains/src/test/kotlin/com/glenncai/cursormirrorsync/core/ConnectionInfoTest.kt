package com.glenncai.cursormirrorsync.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import com.glenncai.cursormirrorsync.core.models.ConnectionInfo

class ConnectionInfoTest {
    
    @Test
    fun `test valid connection info`() {
        val connectionInfo = ConnectionInfo(
            port = 8080,
            projectName = "test-project",
            projectPath = "/path/to/project",
            createdAt = "2023-01-01T00:00:00Z"
        )

        assertTrue(connectionInfo.isValid())
        assertEquals(8080, connectionInfo.port)
        assertEquals("test-project", connectionInfo.projectName)
        assertEquals("/path/to/project", connectionInfo.projectPath)
    }
    
    @Test
    fun `test invalid connection info - invalid port`() {
        val connectionInfo = ConnectionInfo(
            port = 0,
            projectName = "test-project",
            projectPath = "/path/to/project",
            createdAt = null
        )

        assertFalse(connectionInfo.isValid())
    }

    @Test
    fun `test invalid connection info - empty project name`() {
        val connectionInfo = ConnectionInfo(
            port = 8080,
            projectName = "",
            projectPath = "/path/to/project",
            createdAt = null
        )

        assertFalse(connectionInfo.isValid())
    }

    @Test
    fun `test invalid connection info - null project path`() {
        val connectionInfo = ConnectionInfo(
            port = 8080,
            projectName = "test-project",
            projectPath = null,
            createdAt = null
        )

        assertFalse(connectionInfo.isValid())
    }
}

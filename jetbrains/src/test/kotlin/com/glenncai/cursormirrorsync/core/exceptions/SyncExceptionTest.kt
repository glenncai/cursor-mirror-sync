package com.glenncai.cursormirrorsync.core.exceptions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class SyncExceptionTest {
    
    @Test
    fun `test basic SyncException creation`() {
        val exception = SyncException(
            message = "Test error",
            errorCode = "TEST_ERROR",
            severity = SyncException.Severity.ERROR,
            component = "TestComponent"
        )
        
        assertEquals("Test error", exception.message)
        assertEquals("TEST_ERROR", exception.errorCode)
        assertEquals(SyncException.Severity.ERROR, exception.severity)
        assertEquals("TestComponent", exception.component)
        assertTrue(exception.isRecoverable())
        assertFalse(exception.shouldRetry()) // ERROR severity should not retry by default
    }
    
    @Test
    fun `test SyncException with context`() {
        val context = mapOf("key1" to "value1", "key2" to 42)
        val exception = SyncException(
            message = "Test with context",
            context = context
        )
        
        val errorInfo = exception.getErrorInfo()
        assertTrue(errorInfo.containsKey("context"))
        val contextMap = errorInfo["context"] as Map<*, *>
        assertEquals("value1", contextMap["key1"])
        assertEquals(42, contextMap["key2"])
    }
    
    @Test
    fun `test SyncException formatted message`() {
        val exception = SyncException(
            message = "Test error",
            errorCode = "TEST_001",
            severity = SyncException.Severity.WARN,
            component = "TestComponent",
            context = mapOf("file" to "test.txt")
        )
        
        val formatted = exception.getFormattedMessage()
        assertTrue(formatted.contains("[TEST_001]"))
        assertTrue(formatted.contains("[Warning]"))
        assertTrue(formatted.contains("[TestComponent]"))
        assertTrue(formatted.contains("Test error"))
        assertTrue(formatted.contains("file=test.txt"))
    }
    
    @Test
    fun `test SyncException severity levels`() {
        val infoException = SyncException("Info message", severity = SyncException.Severity.INFO)
        val warnException = SyncException("Warn message", severity = SyncException.Severity.WARN)
        val errorException = SyncException("Error message", severity = SyncException.Severity.ERROR)
        val fatalException = SyncException("Fatal message", severity = SyncException.Severity.FATAL)
        
        assertTrue(infoException.isRecoverable())
        assertTrue(warnException.isRecoverable())
        assertTrue(errorException.isRecoverable())
        assertFalse(fatalException.isRecoverable())
        
        assertTrue(infoException.shouldRetry())
        assertTrue(warnException.shouldRetry())
        assertFalse(errorException.shouldRetry())
        assertFalse(fatalException.shouldRetry())
    }
    
    @Test
    fun `test SyncException retry delays`() {
        val infoException = SyncException("Info message", severity = SyncException.Severity.INFO)
        val warnException = SyncException("Warn message", severity = SyncException.Severity.WARN)
        val errorException = SyncException("Error message", severity = SyncException.Severity.ERROR)
        val fatalException = SyncException("Fatal message", severity = SyncException.Severity.FATAL)
        
        assertEquals(1000L, infoException.getRetryDelay())
        assertEquals(2000L, warnException.getRetryDelay())
        assertEquals(5000L, errorException.getRetryDelay())
        assertEquals(0L, fatalException.getRetryDelay())
    }
    
    @Test
    fun `test SyncException withContext`() {
        val original = SyncException("Original message")
        val additional = mapOf("newKey" to "newValue")
        
        val updated = original.withContext(additional)
        
        assertEquals("Original message", updated.message)
        assertTrue(updated.context.containsKey("newKey"))
        assertEquals("newValue", updated.context["newKey"])
    }
    
    @Test
    fun `test SyncException withSeverity`() {
        val original = SyncException("Test", severity = SyncException.Severity.INFO)
        val updated = original.withSeverity(SyncException.Severity.FATAL)
        
        assertEquals("Test", updated.message)
        assertEquals(SyncException.Severity.FATAL, updated.severity)
        assertEquals(SyncException.Severity.INFO, original.severity) // Original unchanged
    }
    
    @Test
    fun `test SyncException companion methods`() {
        val infoException = SyncException.info("Info message", "TestComponent")
        assertEquals(SyncException.Severity.INFO, infoException.severity)
        assertEquals("INFO", infoException.errorCode)
        
        val warnException = SyncException.warn("Warning message", "TestComponent")
        assertEquals(SyncException.Severity.WARN, warnException.severity)
        assertEquals("WARNING", warnException.errorCode)
        
        val errorException = SyncException.error("Error message", "TestComponent")
        assertEquals(SyncException.Severity.ERROR, errorException.severity)
        assertEquals("ERROR", errorException.errorCode)
        
        val fatalException = SyncException.fatal("Fatal message", "TestComponent")
        assertEquals(SyncException.Severity.FATAL, fatalException.severity)
        assertEquals("FATAL", fatalException.errorCode)
    }
    
    @Test
    fun `test SyncException fromException`() {
        val originalException = RuntimeException("Original error")
        val syncException = SyncException.fromException(
            originalException,
            "CONVERTED_ERROR",
            SyncException.Severity.ERROR,
            "TestComponent"
        )
        
        assertEquals("Original error", syncException.message)
        assertEquals("CONVERTED_ERROR", syncException.errorCode)
        assertEquals(SyncException.Severity.ERROR, syncException.severity)
        assertEquals("TestComponent", syncException.component)
        assertEquals(originalException, syncException.cause)
    }
    
    @Test
    fun `test ConnectionException creation and properties`() {
        val connectionException = ConnectionTimeoutException(
            message = "Connection timed out",
            timeoutMs = 5000L,
            host = "localhost",
            port = 8080
        )
        
        assertEquals("Connection timed out", connectionException.message)
        assertEquals("CONNECTION_TIMEOUT", connectionException.errorCode)
        assertEquals(SyncException.Severity.WARN, connectionException.severity)
        assertTrue(connectionException.shouldRetry())
        assertEquals(3000L, connectionException.getRetryDelay())
    }
    
    @Test
    fun `test StateException creation and properties`() {
        val stateException = StateValidationException(
            message = "State validation failed",
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            validationErrors = listOf("Invalid line number", "Invalid column")
        )
        
        assertEquals("State validation failed", stateException.message)
        assertEquals("STATE_VALIDATION_FAILED", stateException.errorCode)
        assertEquals("/test/file.txt", stateException.stateInfo["filePath"])
        assertEquals(10, stateException.stateInfo["line"])
        assertEquals(5, stateException.stateInfo["column"])
        assertFalse(stateException.shouldRetry())
    }
    
    @Test
    fun `test MessageException creation and properties`() {
        val messageException = MessageParsingException(
            message = "Failed to parse message",
            rawMessage = "invalid json",
            expectedFormat = "JSON",
            parsePosition = 5
        )
        
        assertEquals("Failed to parse message", messageException.message)
        assertEquals("MESSAGE_PARSING_FAILED", messageException.errorCode)
        assertEquals("JSON", messageException.messageInfo["expectedFormat"])
        assertEquals(5, messageException.messageInfo["parsePosition"])
        assertFalse(messageException.shouldRetry())
    }
    
    @Test
    fun `test ConfigurationException creation and properties`() {
        val configException = ConfigFileNotFoundException(
            message = "Config file not found",
            filePath = "/path/to/config.json",
            searchPaths = listOf("/path1", "/path2"),
            createDefault = true
        )
        
        assertEquals("Config file not found", configException.message)
        assertEquals("CONFIG_FILE_NOT_FOUND", configException.errorCode)
        assertEquals("/path/to/config.json", configException.configInfo["filePath"])
        assertTrue(configException.shouldRetry())
        assertEquals(2000L, configException.getRetryDelay())
    }
    
    @Test
    fun `test EditorException creation and properties`() {
        val editorException = EditorFocusException(
            message = "Failed to focus editor",
            operation = "focus",
            editorId = "editor123",
            filePath = "/test/file.txt",
            isDisplayable = false
        )
        
        assertEquals("Failed to focus editor", editorException.message)
        assertEquals("EDITOR_FOCUS_FAILED", editorException.errorCode)
        assertEquals("focus", editorException.editorInfo["operation"])
        assertEquals("editor123", editorException.editorInfo["editorId"])
        assertTrue(editorException.shouldRetry())
        assertEquals(200L, editorException.getRetryDelay())
    }
    
    @Test
    fun `test exception hierarchy and inheritance`() {
        val connectionException = ConnectionTimeoutException("Connection timeout")
        val stateException = StateValidationException("State validation failed")
        val messageException = MessageParsingException("Message parsing failed")
        val configException = ConfigFileNotFoundException("Config file not found")
        val editorException = EditorFocusException("Editor focus failed")
        
        assertTrue(connectionException is SyncException)
        assertTrue(stateException is SyncException)
        assertTrue(messageException is SyncException)
        assertTrue(configException is SyncException)
        assertTrue(editorException is SyncException)
        
        assertTrue(connectionException is ConnectionException)
        assertTrue(stateException is StateException)
        assertTrue(messageException is MessageException)
        assertTrue(configException is ConfigurationException)
        assertTrue(editorException is EditorException)
    }
    
    @Test
    fun `test exception error info serialization`() {
        val exception = ConnectionRefusedException(
            message = "Connection refused",
            host = "localhost",
            port = 8080,
            retryCount = 3,
            connectionFileExists = true
        )

        val errorInfo = exception.getErrorInfo()

        assertNotNull(errorInfo["errorCode"])
        assertNotNull(errorInfo["message"])
        assertNotNull(errorInfo["severity"])
        assertNotNull(errorInfo["component"])
        assertNotNull(errorInfo["timestamp"])
        assertNotNull(errorInfo["context"])

        val context = errorInfo["context"] as Map<*, *>
        assertEquals("localhost", context["host"])
        assertEquals(8080, context["port"])
        assertEquals(3, context["retryCount"])
        assertEquals(true, context["connectionFileExists"])
    }
}

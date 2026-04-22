package com.glenncai.cursormirrorsync.core.managers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import com.glenncai.cursormirrorsync.core.exceptions.*
import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

class ConfigFileExceptionClassifierTest {

    private lateinit var classifier: ConfigFileExceptionClassifier

    @BeforeEach
    fun setUp() {
        classifier = ConfigFileExceptionClassifier()
    }

    @Test
    fun `test file not found exception classification`() {
        val exception = FileNotFoundException("File not found")
        val context = ConfigFileContext(
            filePath = "/path/to/config.json",
            operation = "read"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigFileNotFoundException)
        assertEquals("/path/to/config.json", (result as ConfigFileNotFoundException).filePath)
        assertTrue(result.createDefault)
    }

    @Test
    fun `test no such file exception classification`() {
        val exception = NoSuchFileException("No such file")
        val context = ConfigFileContext(
            filePath = "/path/to/missing.json",
            operation = "read"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigFileNotFoundException)
        assertEquals("/path/to/missing.json", (result as ConfigFileNotFoundException).filePath)
    }

    @Test
    fun `test access denied exception classification`() {
        val exception = AccessDeniedException("Access denied")
        val context = ConfigFileContext(
            filePath = "/path/to/protected.json",
            operation = "write",
            canRead = true,
            canWrite = false
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigPermissionException)
        assertEquals("/path/to/protected.json", (result as ConfigPermissionException).filePath)
        assertEquals("write", result.operation)
        assertEquals("rw-", result.requiredPermissions)
    }

    @Test
    fun `test JSON syntax exception classification`() {
        val exception = JsonSyntaxException("Unexpected character at line 5 column 10")
        val context = ConfigFileContext(
            filePath = "/path/to/invalid.json",
            operation = "parse",
            expectedFormat = "JSON",
            fileContent = "{ invalid json }"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigParsingException)
        assertEquals("/path/to/invalid.json", (result as ConfigParsingException).filePath)
        assertEquals("JSON", result.expectedFormat)
        assertEquals(5, result.lineNumber)
        assertEquals(10, result.columnNumber)
    }

    @Test
    fun `test corruption detection by file size`() {
        val exception = IOException("Unexpected end of file")
        val context = ConfigFileContext(
            filePath = "/path/to/empty.json",
            fileSize = 0,
            operation = "read"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigFileCorruptedException)
        assertEquals("/path/to/empty.json", (result as ConfigFileCorruptedException).filePath)
        assertEquals("empty_file", result.corruptionType)
    }

    @Test
    fun `test format error message classification`() {
        val exception = RuntimeException("Invalid JSON format detected")
        val context = ConfigFileContext(
            filePath = "/path/to/malformed.json",
            expectedFormat = "JSON",
            fileContent = "not json content"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigFormatException)
        assertEquals("/path/to/malformed.json", (result as ConfigFormatException).filePath)
        assertEquals("JSON", result.expectedFormat)
        assertTrue(result.formatErrors.contains("Invalid JSON syntax"))
    }

    @Test
    fun `test version incompatibility message classification`() {
        val exception = RuntimeException("Configuration version 2.0 is not supported")
        val context = ConfigFileContext(
            filePath = "/path/to/version.json",
            expectedVersion = "1.0",
            detectedVersion = "2.0"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigVersionException)
        assertEquals("/path/to/version.json", (result as ConfigVersionException).filePath)
        assertEquals("2.0", result.fileVersion)
        assertTrue(result.migrationAvailable)
    }

    @Test
    fun `test validation error message classification`() {
        val exception = RuntimeException("Configuration validation failed: missing required field")
        val context = ConfigFileContext(
            filePath = "/path/to/incomplete.json",
            validationRules = listOf("port required", "projectName required"),
            requiredFields = listOf("port", "projectName", "projectPath")
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigValidationException)
        assertEquals("Configuration validation failed: missing required field", (result as ConfigValidationException).message)
        assertEquals(listOf("port required", "projectName required"), result.validationRules)
    }

    @Test
    fun `test corruption message classification`() {
        val exception = RuntimeException("File appears to be corrupted or truncated")
        val context = ConfigFileContext(
            filePath = "/path/to/corrupt.json",
            fileSize = 1024
        )

        val result = classifier.classifyException(exception, context)

        assertTrue(result is ConfigFileCorruptedException)
        assertEquals("/path/to/corrupt.json", (result as ConfigFileCorruptedException).filePath)
        assertEquals("truncated", result.corruptionType)
        assertFalse(result.recoverable)
    }

    @Test
    fun `test permission message classification`() {
        val exception = RuntimeException("Permission denied while accessing file")
        val context = ConfigFileContext(
            filePath = "/path/to/readonly.json",
            operation = "write",
            canRead = true,
            canWrite = false,
            isDirectory = true  // Set to true so canFix will be false
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigPermissionException)
        assertEquals("/path/to/readonly.json", (result as ConfigPermissionException).filePath)
        assertEquals("write", result.operation)
        assertFalse(result.canFix) // Directory check would be false
    }

    @Test
    fun `test null exception classification`() {
        val context = ConfigFileContext(filePath = "/path/to/unknown.json")
        
        val result = classifier.classifyException(null, context)
        
        assertTrue(result is ConfigurationException)
        assertEquals("Unknown configuration error", result.message)
        assertEquals("CONFIG_UNKNOWN_ERROR", result.errorCode)
    }

    @Test
    fun `test generic exception classification`() {
        val exception = RuntimeException("Some unknown configuration error")
        val context = ConfigFileContext(
            filePath = "/path/to/generic.json",
            operation = "validate",
            fileSize = 512
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigurationException)
        assertEquals("Configuration error: Some unknown configuration error", result.message)
        assertEquals("CONFIG_GENERIC_ERROR", result.errorCode)
    }

    @Test
    fun `test statistics tracking`() {
        val context = ConfigFileContext()
        
        // Classify several exceptions with specific context for format error
        val formatContext = ConfigFileContext(fileContent = "invalid json content", expectedFormat = "JSON")

        classifier.classifyException(FileNotFoundException("Not found"), context)
        classifier.classifyException(AccessDeniedException("Access denied"), context)
        classifier.classifyException(JsonSyntaxException("JSON error"), context)
        classifier.classifyException(RuntimeException("Invalid format detected"), formatContext)
        classifier.classifyException(RuntimeException("Configuration validation failed"), context)
        classifier.classifyException(null, context)
        
        val stats = classifier.getStatistics()
        
        assertTrue(stats.contains("Total classifications: 6"))
        assertTrue(stats.contains("File not found errors: 1"))
        assertTrue(stats.contains("Permission errors: 1"))
        assertTrue(stats.contains("Parsing errors: 1"))
        assertTrue(stats.contains("Format errors: 1"))
        assertTrue(stats.contains("Validation errors: 1"))
        assertTrue(stats.contains("Unknown errors: 1"))
    }

    @Test
    fun `test statistics reset`() {
        val context = ConfigFileContext()
        
        // Classify some exceptions
        classifier.classifyException(FileNotFoundException("Not found"), context)
        classifier.classifyException(JsonSyntaxException("JSON error"), context)
        
        // Reset statistics
        classifier.resetStatistics()
        
        val stats = classifier.getStatistics()
        assertTrue(stats.contains("Total classifications: 0"))
        assertTrue(stats.contains("File not found errors: 0"))
        assertTrue(stats.contains("Parsing errors: 0"))
    }

    @Test
    fun `test line and column number extraction`() {
        val exception = JsonSyntaxException("Unexpected character '}' at line 15 column 25")
        val context = ConfigFileContext(
            filePath = "/path/to/syntax.json",
            expectedFormat = "JSON"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigParsingException)
        assertEquals(15, (result as ConfigParsingException).lineNumber)
        assertEquals(25, result.columnNumber)
    }

    @Test
    fun `test format detection`() {
        val exception = RuntimeException("Invalid format")
        val jsonContext = ConfigFileContext(
            fileContent = """{"key": "value"}""",
            expectedFormat = "JSON"
        )
        
        val result = classifier.classifyException(exception, jsonContext)
        
        // Should not be classified as format error since content is valid JSON
        assertFalse(result is ConfigFormatException)
    }

    @Test
    fun `test checksum calculation`() {
        val exception = RuntimeException("Checksum mismatch")
        val context = ConfigFileContext(
            filePath = "/path/to/checksum.json",
            fileContent = "test content"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConfigFileCorruptedException)
        assertNotEquals("unknown", (result as ConfigFileCorruptedException).actualChecksum)
    }
}

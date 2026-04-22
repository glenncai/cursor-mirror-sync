package com.glenncai.cursormirrorsync.core.managers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import com.glenncai.cursormirrorsync.core.exceptions.*
import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException

class ConfigFileExceptionClassifierDebugTest {

    private lateinit var classifier: ConfigFileExceptionClassifier

    @BeforeEach
    fun setUp() {
        classifier = ConfigFileExceptionClassifier()
    }

    @Test
    fun `debug classification results`() {
        val context = ConfigFileContext()

        // Test each exception type and verify classifications
        val fileNotFound = classifier.classifyException(FileNotFoundException("Not found"), context)
        assert(fileNotFound is ConfigFileNotFoundException) { "Expected ConfigFileNotFoundException, got ${fileNotFound::class.simpleName}" }

        val accessDenied = classifier.classifyException(AccessDeniedException("Access denied"), context)
        assert(accessDenied is ConfigPermissionException) { "Expected ConfigPermissionException, got ${accessDenied::class.simpleName}" }

        val jsonSyntax = classifier.classifyException(JsonSyntaxException("JSON error"), context)
        assert(jsonSyntax is ConfigParsingException) { "Expected ConfigParsingException, got ${jsonSyntax::class.simpleName}" }

        val formatError = classifier.classifyException(RuntimeException("Invalid format detected"), context.copy(fileContent = "invalid json content"))
        // This should be classified as format error since message contains "format" and content is invalid
        assert(formatError is ConfigFormatException) { "Expected ConfigFormatException, got ${formatError::class.simpleName}" }

        val validationError = classifier.classifyException(RuntimeException("Configuration validation failed"), context)
        // This should be classified as validation error since message contains "validation"
        assert(validationError is ConfigValidationException) { "Expected ConfigValidationException, got ${validationError::class.simpleName}" }

        val nullException = classifier.classifyException(null, context)
        assert(nullException is ConfigurationException) { "Expected ConfigurationException, got ${nullException::class.simpleName}" }

        // Check statistics
        val stats = classifier.getStatistics()
        assert(stats.contains("Total classifications: 6")) { "Expected 6 total classifications in stats: $stats" }
    }
}

package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.diagnostic.Logger
import com.glenncai.cursormirrorsync.core.exceptions.*
import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.io.File

/**
 * Context information for configuration file exception classification
 */
data class ConfigFileContext(
    // File information
    val filePath: String = "unknown",
    val fileName: String = "unknown",
    val fileSize: Long = 0,
    val operation: String = "unknown", // "read", "write", "parse", "validate"
    
    // Content information
    val expectedFormat: String = "JSON",
    val fileContent: String = "",
    val contentPreview: String = "",
    
    // Version information
    val expectedVersion: String = "1.0",
    val detectedVersion: String = "unknown",

    // Permission information
    val canRead: Boolean = false,
    val canWrite: Boolean = false,
    val isDirectory: Boolean = false,
    
    // Validation information
    val validationRules: List<String> = emptyList(),
    val requiredFields: List<String> = emptyList()
)

/**
 * Intelligent configuration file exception classifier that analyzes exceptions
 * and converts them to appropriate ConfigurationException subtypes.
 * Provides detailed classification based on exception types, file states, and content analysis.
 */
class ConfigFileExceptionClassifier {
    
    private val log: Logger = Logger.getInstance(ConfigFileExceptionClassifier::class.java)
    
    // Classification statistics
    private val totalClassifications = AtomicLong(0)
    private val fileNotFoundErrors = AtomicLong(0)
    private val permissionErrors = AtomicLong(0)
    private val parsingErrors = AtomicLong(0)
    private val formatErrors = AtomicLong(0)
    private val corruptionErrors = AtomicLong(0)
    private val versionErrors = AtomicLong(0)
    private val validationErrors = AtomicLong(0)
    private val unknownErrors = AtomicLong(0)
    
    /**
     * Classifies a generic exception into a specific ConfigurationException subtype
     */
    fun classifyException(
        exception: Exception?,
        context: ConfigFileContext = ConfigFileContext()
    ): ConfigurationException {
        totalClassifications.incrementAndGet()
        
        return when {
            exception == null -> createUnknownException(context)
            
            // File system related exceptions
            exception is FileNotFoundException || exception is NoSuchFileException -> {
                fileNotFoundErrors.incrementAndGet()
                createFileNotFoundException(exception, context)
            }
            
            exception is AccessDeniedException -> {
                permissionErrors.incrementAndGet()
                createPermissionException(exception, context)
            }
            
            exception is IOException -> {
                when {
                    isCorruptionError(exception, context) -> {
                        corruptionErrors.incrementAndGet()
                        createCorruptionException(exception, context)
                    }
                    else -> {
                        unknownErrors.incrementAndGet()
                        createGenericConfigException(exception, context)
                    }
                }
            }
            
            // JSON parsing related exceptions
            exception is JsonSyntaxException -> {
                parsingErrors.incrementAndGet()
                createParsingException(exception, context)
            }
            
            // Message-based classification
            else -> classifyByMessage(exception, context)
        }
    }
    
    /**
     * Classifies exception based on error message content and context
     */
    private fun classifyByMessage(exception: Exception, context: ConfigFileContext): ConfigurationException {
        val message = exception.message?.lowercase() ?: ""

        // File not found errors - early return
        if (message.contains("no such file") || message.contains("file not found")) {
            fileNotFoundErrors.incrementAndGet()
            return createFileNotFoundException(exception, context)
        }

        // Permission issues - early return
        if (message.contains("permission") || message.contains("access denied")) {
            permissionErrors.incrementAndGet()
            return createPermissionException(exception, context)
        }

        // JSON/Format issues - early return
        if (message.contains("json") || message.contains("syntax") || message.contains("parse") || message.contains("format")) {
            return if (isFormatError(message, context)) {
                formatErrors.incrementAndGet()
                createFormatException(exception, context)
            } else {
                parsingErrors.incrementAndGet()
                createParsingException(exception, context)
            }
        }

        // Version issues - early return
        if (message.contains("version") || message.contains("incompatible")) {
            versionErrors.incrementAndGet()
            return createVersionException(exception, context)
        }

        // Validation issues - early return
        if (message.contains("validation") || message.contains("invalid")) {
            validationErrors.incrementAndGet()
            return createValidationException(exception, context)
        }

        // Corruption issues - early return
        if (message.contains("corrupt") || message.contains("truncated") || message.contains("checksum")) {
            corruptionErrors.incrementAndGet()
            return createCorruptionException(exception, context)
        }

        // Default to generic configuration exception
        unknownErrors.incrementAndGet()
        return createGenericConfigException(exception, context)
    }
    
    private fun createFileNotFoundException(exception: Exception, context: ConfigFileContext): ConfigFileNotFoundException {
        return ConfigFileNotFoundException(
            message = "Configuration file not found: ${exception.message}",
            cause = exception,
            filePath = context.filePath,
            searchPaths = listOf(context.filePath),
            createDefault = true
        )
    }
    
    private fun createPermissionException(exception: Exception, context: ConfigFileContext): ConfigPermissionException {
        return ConfigPermissionException(
            message = "Configuration file permission denied: ${exception.message}",
            cause = exception,
            filePath = context.filePath,
            operation = context.operation,
            requiredPermissions = when (context.operation) {
                "read" -> "r--"
                "write" -> "rw-"
                else -> "rw-"
            },
            currentPermissions = "unknown",
            canFix = !context.isDirectory
        )
    }
    
    private fun createParsingException(exception: Exception, context: ConfigFileContext): ConfigParsingException {
        val lineNumber = extractLineNumber(exception.message)
        val columnNumber = extractColumnNumber(exception.message)
        
        return ConfigParsingException(
            message = "Configuration file parsing failed: ${exception.message}",
            cause = exception,
            filePath = context.filePath,
            lineNumber = lineNumber,
            columnNumber = columnNumber,
            expectedFormat = context.expectedFormat,
            parseError = exception.message ?: "unknown"
        )
    }
    
    private fun createFormatException(exception: Exception, context: ConfigFileContext): ConfigFormatException {
        val formatErrors = analyzeFormatErrors(context)
        
        return ConfigFormatException(
            message = "Configuration file format is invalid: ${exception.message}",
            cause = exception,
            filePath = context.filePath,
            expectedFormat = context.expectedFormat,
            actualFormat = detectActualFormat(context),
            formatErrors = formatErrors,
            suggestedFix = generateFormatFix(formatErrors)
        )
    }
    
    private fun createCorruptionException(exception: Exception, context: ConfigFileContext): ConfigFileCorruptedException {
        val corruptionType = detectCorruptionType(exception, context)

        return ConfigFileCorruptedException(
            message = "Configuration file is corrupted: ${exception.message}",
            cause = exception,
            filePath = context.filePath,
            fileSize = context.fileSize,
            expectedChecksum = "unknown",
            actualChecksum = calculateChecksum(context.fileContent),
            corruptionType = corruptionType,
            recoverable = false
        )
    }
    
    private fun createVersionException(exception: Exception, context: ConfigFileContext): ConfigVersionException {
        return ConfigVersionException(
            message = "Configuration file version is incompatible: ${exception.message}",
            cause = exception,
            filePath = context.filePath,
            fileVersion = context.detectedVersion,
            supportedVersions = listOf("1.0", "1.1", "2.0"),
            migrationAvailable = true,
            migrationPath = "auto"
        )
    }
    
    private fun createValidationException(exception: Exception, context: ConfigFileContext): ConfigValidationException {
        return ConfigValidationException(
            message = exception.message ?: "Configuration validation failed",
            cause = exception,
            configKey = "unknown",
            configValue = null,
            validationRules = context.validationRules,
            validationErrors = listOf(exception.message ?: "unknown")
        )
    }
    
    private fun createGenericConfigException(exception: Exception, context: ConfigFileContext): ConfigurationException {
        return ConfigurationException(
            message = "Configuration error: ${exception.message}",
            cause = exception,
            errorCode = "CONFIG_GENERIC_ERROR",
            severity = SyncException.Severity.ERROR,
            configInfo = mapOf(
                "filePath" to context.filePath,
                "operation" to context.operation,
                "fileSize" to context.fileSize
            )
        )
    }
    
    private fun createUnknownException(context: ConfigFileContext): ConfigurationException {
        unknownErrors.incrementAndGet()
        return ConfigurationException(
            message = "Unknown configuration error",
            errorCode = "CONFIG_UNKNOWN_ERROR",
            severity = SyncException.Severity.ERROR,
            configInfo = mapOf(
                "filePath" to context.filePath,
                "operation" to context.operation
            )
        )
    }
    
    // Helper methods
    private fun isCorruptionError(exception: IOException, context: ConfigFileContext): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("corrupt") || 
               message.contains("truncated") || 
               context.fileSize == 0L ||
               (context.fileContent.isNotEmpty() && !isValidFormat(context))
    }
    
    private fun isFormatError(message: String, context: ConfigFileContext): Boolean {
        // Only consider it a format error if the content is actually invalid
        // or if the message specifically mentions format/structure issues
        return (message.contains("format") && context.fileContent.isNotEmpty() && !isValidFormat(context)) ||
               (message.contains("structure") && context.fileContent.isNotEmpty() && !isValidFormat(context)) ||
               (context.fileContent.isNotEmpty() && !isValidFormat(context))
    }
    
    private fun isValidFormat(context: ConfigFileContext): Boolean {
        return when (context.expectedFormat.uppercase()) {
            "JSON" -> isValidJson(context.fileContent)
            else -> true
        }
    }
    
    private fun isValidJson(content: String): Boolean {
        return try {
            com.google.gson.JsonParser.parseString(content)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun extractLineNumber(message: String?): Int {
        return message?.let { msg ->
            Regex("line (\\d+)").find(msg)?.groupValues?.get(1)?.toIntOrNull()
        } ?: -1
    }
    
    private fun extractColumnNumber(message: String?): Int {
        return message?.let { msg ->
            Regex("column (\\d+)").find(msg)?.groupValues?.get(1)?.toIntOrNull()
        } ?: -1
    }
    
    private fun analyzeFormatErrors(context: ConfigFileContext): List<String> {
        val errors = mutableListOf<String>()
        
        if (context.fileContent.isEmpty()) {
            errors.add("File is empty")
        } else if (context.expectedFormat == "JSON" && !isValidJson(context.fileContent)) {
            errors.add("Invalid JSON syntax")
        }
        
        return errors
    }
    
    private fun detectActualFormat(context: ConfigFileContext): String {
        val content = context.fileContent.trim()
        return when {
            content.isEmpty() -> "empty"
            content.startsWith("{") && content.endsWith("}") -> "JSON"
            content.startsWith("[") && content.endsWith("]") -> "JSON_ARRAY"
            content.contains("=") -> "PROPERTIES"
            else -> "unknown"
        }
    }
    
    private fun generateFormatFix(formatErrors: List<String>): String {
        return when {
            formatErrors.contains("File is empty") -> "Create a valid JSON configuration file"
            formatErrors.contains("Invalid JSON syntax") -> "Fix JSON syntax errors"
            else -> "Check file format and structure"
        }
    }
    
    private fun detectCorruptionType(exception: Exception, context: ConfigFileContext): String {
        val message = exception.message?.lowercase() ?: ""
        return when {
            message.contains("truncated") -> "truncated"
            context.fileSize == 0L -> "empty_file"
            message.contains("encoding") -> "encoding_error"
            message.contains("checksum") -> "checksum_mismatch"
            else -> "unknown"
        }
    }
    
    private fun calculateChecksum(content: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(content.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Gets classification statistics
     */
    fun getStatistics(): String {
        val total = totalClassifications.get()
        return buildString {
            appendLine("Configuration File Exception Classification Statistics:")
            appendLine("  Total classifications: $total")
            appendLine("  File not found errors: ${fileNotFoundErrors.get()}")
            appendLine("  Permission errors: ${permissionErrors.get()}")
            appendLine("  Parsing errors: ${parsingErrors.get()}")
            appendLine("  Format errors: ${formatErrors.get()}")
            appendLine("  Corruption errors: ${corruptionErrors.get()}")
            appendLine("  Version errors: ${versionErrors.get()}")
            appendLine("  Validation errors: ${validationErrors.get()}")
            appendLine("  Unknown errors: ${unknownErrors.get()}")
            
            if (total > 0) {
                appendLine("  Success rate: ${String.format("%.1f", (total - unknownErrors.get()).toDouble() / total * 100)}%")
            }
        }
    }
    
    /**
     * Resets classification statistics
     */
    fun resetStatistics() {
        totalClassifications.set(0)
        fileNotFoundErrors.set(0)
        permissionErrors.set(0)
        parsingErrors.set(0)
        formatErrors.set(0)
        corruptionErrors.set(0)
        versionErrors.set(0)
        validationErrors.set(0)
        unknownErrors.set(0)
    }
}

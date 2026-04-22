package com.glenncai.cursormirrorsync.core.managers

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.glenncai.cursormirrorsync.core.exceptions.*
import com.glenncai.cursormirrorsync.core.validators.ConfigurationValidator
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Configuration file validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)



/**
 * Configuration file validator for JetBrains IDE plugin.
 * Provides read-only validation capabilities for configuration files.
 * Note: This validator only reads and validates files - it never modifies them.
 */
class ConfigFileValidator {

    private val log: Logger = Logger.getInstance(ConfigFileValidator::class.java)
    private val exceptionClassifier = ConfigFileExceptionClassifier()

    // Validation statistics
    private val totalValidations = AtomicLong(0)
    private val successfulValidations = AtomicLong(0)

    companion object {
        // Required fields for connection info
        private val REQUIRED_FIELDS = listOf("port", "projectName", "projectPath")
        // Optional field names must match the serialized keys used by VSCode /
        // ConnectionInfo's @SerializedName annotations; _reconnectSignal is prefixed
        // with an underscore in the actual JSON payload.
        private val OPTIONAL_FIELDS = listOf(
            "status", "createdAt", "lastModified", "lastActiveAt", "_utcTime", "_reconnectSignal"
        )
    }

    /**
     * Validates a configuration file and returns detailed validation results.
     *
     * The file is read at most once; the resulting content is threaded through
     * the validation pipeline and the exception-classifier context to avoid
     * TOCTOU races between repeated reads.
     */
    fun validateConfigFile(filePath: String): ValidationResult {
        totalValidations.incrementAndGet()

        val file = File(filePath)

        // Check file existence and permissions before touching the content.
        val fileChecks = validateFileAccess(file)
        if (!fileChecks.isValid) {
            return fileChecks
        }

        val content = try {
            file.readText()
        } catch (e: Exception) {
            val context = createContext(file, content = "")
            val configException = exceptionClassifier.classifyException(e, context)
            return ValidationResult(
                isValid = false,
                errors = listOf("Validation failed: ${configException.getFormattedMessage()}"),
                suggestions = generateSuggestions(configException)
            )
        }

        return try {
            val context = createContext(file, content = content)
            val contentValidation = validateContent(content, context)
            if (contentValidation.isValid) {
                successfulValidations.incrementAndGet()
            }
            contentValidation
        } catch (e: Exception) {
            val context = createContext(file, content = content)
            val configException = exceptionClassifier.classifyException(e, context)
            ValidationResult(
                isValid = false,
                errors = listOf("Validation failed: ${configException.getFormattedMessage()}"),
                suggestions = generateSuggestions(configException)
            )
        }
    }
    

    
    private fun validateFileAccess(file: File): ValidationResult {
        // Early return for non-existent file
        if (!file.exists()) {
            val errors = listOf("Configuration file does not exist: ${file.absolutePath}")
            val suggestions = listOf("Create a new configuration file or check if VSCode extension is running")
            return ValidationResult(false, errors, emptyList(), suggestions)
        }

        // Early return for unreadable file
        if (!file.canRead()) {
            val errors = listOf("Cannot read configuration file: ${file.absolutePath}")
            val suggestions = listOf("Check file permissions")
            return ValidationResult(false, errors, emptyList(), suggestions)
        }

        // Early return for empty file
        if (file.length() == 0L) {
            val warnings = listOf("Configuration file is empty")
            val suggestions = listOf("File will be auto-generated when VSCode extension connects")
            return ValidationResult(false, listOf("File is empty"), warnings, suggestions)
        }

        return ValidationResult(true)
    }
    
    private fun validateContent(content: String, context: ConfigFileContext): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // Parse JSON - early return on parsing failure
        val jsonObject = try {
            JsonParser.parseString(content).asJsonObject
        } catch (e: Exception) {
            errors.add("Invalid JSON format: ${e.message}")
            suggestions.add("Check JSON syntax and fix formatting errors")
            return ValidationResult(false, errors, warnings, suggestions)
        }

        // Validate required fields
        validateRequiredFields(jsonObject, errors)

        // Validate field values using unified validator
        validateFieldValues(jsonObject, errors, warnings)

        // Check for unknown fields
        validateUnknownFields(jsonObject, warnings)

        val isValid = errors.isEmpty()

        return ValidationResult(isValid, errors, warnings, suggestions)
    }

    /**
     * Validates required fields in the JSON object
     */
    private fun validateRequiredFields(jsonObject: JsonObject, errors: MutableList<String>) {
        for (field in REQUIRED_FIELDS) {
            if (!jsonObject.has(field) || jsonObject.get(field).isJsonNull) {
                errors.add("Missing required field: $field")
            }
        }
    }

    /**
     * Validates field values using the unified validator.
     *
     * Each field is guarded against unexpected JSON shapes (non-primitive,
     * wrong primitive kind) so a malformed payload yields a clear error
     * instead of bubbling a ClassCastException or NumberFormatException
     * out of Gson's asInt / asString accessors.
     */
    private fun validateFieldValues(jsonObject: JsonObject, errors: MutableList<String>, warnings: MutableList<String>) {
        val portElement = jsonObject.get("port")
        if (portElement != null && !portElement.isJsonNull) {
            val primitive = portElement.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            val portValue = primitive
                ?.takeIf { it.isNumber }
                ?.let {
                    try { it.asInt } catch (e: NumberFormatException) { null }
                }
            if (portValue == null) {
                errors.add("Field 'port' must be an integer")
            } else {
                warnings.addAll(ConfigurationValidator.validatePort(portValue, "config-file"))
            }
        }

        val nameElement = jsonObject.get("projectName")
        if (nameElement != null && !nameElement.isJsonNull) {
            val primitive = nameElement.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            val nameValue = primitive?.takeIf { it.isString }?.asString
            if (nameValue == null) {
                errors.add("Field 'projectName' must be a string")
            } else {
                errors.addAll(ConfigurationValidator.validateProjectName(nameValue))
            }
        }

        val pathElement = jsonObject.get("projectPath")
        if (pathElement != null && !pathElement.isJsonNull) {
            val primitive = pathElement.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            val pathValue = primitive?.takeIf { it.isString }?.asString
            if (pathValue == null) {
                errors.add("Field 'projectPath' must be a string")
            } else {
                errors.addAll(ConfigurationValidator.validateProjectPath(pathValue))
            }
        }
    }

    /**
     * Validates for unknown fields in the JSON object
     */
    private fun validateUnknownFields(jsonObject: JsonObject, warnings: MutableList<String>) {
        for (key in jsonObject.keySet()) {
            if (key !in REQUIRED_FIELDS && key !in OPTIONAL_FIELDS) {
                warnings.add("Unknown field: $key")
            }
        }
    }
    

    
    /**
     * Builds a context object for the exception classifier. The caller is
     * expected to have already read (or attempted to read) the file content,
     * which is passed in so the file system is touched only once per
     * validation pass.
     */
    private fun createContext(file: File, content: String): ConfigFileContext {
        return ConfigFileContext(
            filePath = file.absolutePath,
            fileName = file.name,
            fileSize = if (file.exists()) file.length() else 0,
            operation = "validate",
            expectedFormat = "JSON",
            fileContent = content,
            canRead = file.canRead(),
            canWrite = file.canWrite(),
            isDirectory = file.isDirectory(),
            requiredFields = REQUIRED_FIELDS
        )
    }
    
    private fun generateSuggestions(exception: ConfigurationException): List<String> {
        return when (exception) {
            is ConfigFileNotFoundException -> listOf(
                "Check if VSCode extension is running",
                "Verify project path is correct",
                "Create configuration file manually if needed"
            )
            is ConfigParsingException -> listOf(
                "Check JSON syntax",
                "Validate file encoding",
                "Use JSON validator tool"
            )
            is ConfigPermissionException -> listOf(
                "Check file permissions",
                "Run IDE with appropriate privileges",
                "Verify file is not locked by another process"
            )
            else -> listOf("Check configuration file format and content")
        }
    }

    /**
     * Gets validation statistics
     */
    fun getStatistics(): String {
        val total = totalValidations.get()
        return buildString {
            appendLine("Configuration File Validator Statistics:")
            appendLine("  Total validations: $total")
            appendLine("  Successful validations: ${successfulValidations.get()}")

            if (total > 0) {
                val rate = successfulValidations.get().toDouble() / total * 100
                appendLine("  Success rate: ${String.format(java.util.Locale.ROOT, "%.1f", rate)}%")
            }

            appendLine()
            appendLine(exceptionClassifier.getStatistics())
        }
    }
    
    /**
     * Resets validation statistics
     */
    fun resetStatistics() {
        totalValidations.set(0)
        successfulValidations.set(0)
        exceptionClassifier.resetStatistics()
    }
}

package com.glenncai.cursormirrorsync.core.exceptions

import java.time.Instant

/**
 * Base exception class for configuration related errors.
 * Handles configuration file parsing, validation, and management issues.
 */
open class ConfigurationException(
    message: String,
    cause: Throwable? = null,
    errorCode: String = "CONFIG_ERROR",
    severity: Severity = Severity.ERROR,
    context: Map<String, Any> = emptyMap(),
    timestamp: Instant = Instant.now(),
    val configInfo: Map<String, Any> = emptyMap()
) : SyncException(
    message = message,
    cause = cause,
    errorCode = errorCode,
    severity = severity,
    context = context + configInfo,
    timestamp = timestamp,
    component = "ConfigurationManager"
) {

    override fun shouldRetry(): Boolean {
        return when (this) {
            is ConfigFileNotFoundException -> true
            is ConfigParsingException -> false
            is ConfigValidationException -> false
            is ConfigMigrationException -> true
            else -> super.shouldRetry()
        }
    }

    override fun getRetryDelay(): Long {
        return when (this) {
            is ConfigFileNotFoundException -> 2000L
            is ConfigMigrationException -> 1000L
            else -> super.getRetryDelay()
        }
    }
}

/**
 * Exception thrown when configuration file is not found.
 */
class ConfigFileNotFoundException(
    message: String = "Configuration file not found",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val searchPaths: List<String> = emptyList(),
    val createDefault: Boolean = false
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_FILE_NOT_FOUND",
    severity = Severity.WARN,
    configInfo = mapOf(
        "filePath" to filePath,
        "searchPaths" to searchPaths,
        "createDefault" to createDefault
    )
)

/**
 * Exception thrown when configuration file parsing fails.
 */
class ConfigParsingException(
    message: String = "Configuration parsing failed",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val lineNumber: Int = -1,
    val columnNumber: Int = -1,
    val expectedFormat: String = "JSON",
    val parseError: String = "unknown"
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_PARSING_FAILED",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "filePath" to filePath,
        "lineNumber" to lineNumber,
        "columnNumber" to columnNumber,
        "expectedFormat" to expectedFormat,
        "parseError" to parseError
    )
)

/**
 * Exception thrown when configuration validation fails.
 */
class ConfigValidationException(
    message: String = "Configuration validation failed",
    cause: Throwable? = null,
    val configKey: String = "unknown",
    val configValue: Any? = null,
    val validationRules: List<String> = emptyList(),
    val validationErrors: List<String> = emptyList()
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_VALIDATION_FAILED",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "configKey" to configKey,
        "configValue" to (configValue?.toString() ?: "null"),
        "validationRules" to validationRules,
        "validationErrors" to validationErrors
    )
)

/**
 * Exception thrown when configuration migration fails.
 */
class ConfigMigrationException(
    message: String = "Configuration migration failed",
    cause: Throwable? = null,
    val fromVersion: String = "unknown",
    val toVersion: String = "unknown",
    val migrationStep: String = "unknown",
    val backupPath: String = "unknown"
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_MIGRATION_FAILED",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "fromVersion" to fromVersion,
        "toVersion" to toVersion,
        "migrationStep" to migrationStep,
        "backupPath" to backupPath
    )
)

/**
 * Exception thrown when configuration access fails.
 */
class ConfigAccessException(
    message: String = "Configuration access failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "read", "write", "delete", etc.
    val filePath: String = "unknown",
    val permissions: String = "unknown"
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_ACCESS_FAILED",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "operation" to operation,
        "filePath" to filePath,
        "permissions" to permissions
    )
)

/**
 * Exception thrown when configuration synchronization fails.
 */
class ConfigSyncException(
    message: String = "Configuration synchronization failed",
    cause: Throwable? = null,
    val sourceConfig: String = "unknown",
    val targetConfig: String = "unknown",
    val syncDirection: String = "unknown", // "push", "pull", "bidirectional"
    val conflictResolution: String = "none"
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_SYNC_FAILED",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "sourceConfig" to sourceConfig,
        "targetConfig" to targetConfig,
        "syncDirection" to syncDirection,
        "conflictResolution" to conflictResolution
    )
)



/**
 * Exception thrown when configuration schema validation fails.
 */
class ConfigSchemaException(
    message: String = "Configuration schema validation failed",
    cause: Throwable? = null,
    val schemaVersion: String = "unknown",
    val configVersion: String = "unknown",
    val schemaErrors: List<String> = emptyList(),
    val requiredFields: List<String> = emptyList()
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_SCHEMA_FAILED",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "schemaVersion" to schemaVersion,
        "configVersion" to configVersion,
        "schemaErrors" to schemaErrors,
        "requiredFields" to requiredFields
    )
)

/**
 * Exception thrown when configuration file is corrupted.
 */
class ConfigFileCorruptedException(
    message: String = "Configuration file is corrupted",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val fileSize: Long = 0,
    val expectedChecksum: String = "unknown",
    val actualChecksum: String = "unknown",
    val corruptionType: String = "unknown", // "truncated", "binary_data", "encoding_error", "checksum_mismatch"
    val recoverable: Boolean = false
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_FILE_CORRUPTED",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "filePath" to filePath,
        "fileSize" to fileSize,
        "expectedChecksum" to expectedChecksum,
        "actualChecksum" to actualChecksum,
        "corruptionType" to corruptionType,
        "recoverable" to recoverable
    )
)

/**
 * Exception thrown when configuration file format is invalid.
 */
class ConfigFormatException(
    message: String = "Configuration file format is invalid",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val expectedFormat: String = "JSON",
    val actualFormat: String = "unknown",
    val formatErrors: List<String> = emptyList(),
    val suggestedFix: String = "unknown"
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_FORMAT_INVALID",
    severity = Severity.ERROR,
    configInfo = mapOf(
        "filePath" to filePath,
        "expectedFormat" to expectedFormat,
        "actualFormat" to actualFormat,
        "formatErrors" to formatErrors,
        "suggestedFix" to suggestedFix
    )
)

/**
 * Exception thrown when configuration file permission issues occur.
 */
class ConfigPermissionException(
    message: String = "Configuration file permission denied",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val operation: String = "unknown", // "read", "write", "create", "delete"
    val requiredPermissions: String = "unknown",
    val currentPermissions: String = "unknown",
    val canFix: Boolean = false
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_PERMISSION_DENIED",
    severity = Severity.WARN,
    configInfo = mapOf(
        "filePath" to filePath,
        "operation" to operation,
        "requiredPermissions" to requiredPermissions,
        "currentPermissions" to currentPermissions,
        "canFix" to canFix
    )
)

/**
 * Exception thrown when configuration file version is incompatible.
 */
class ConfigVersionException(
    message: String = "Configuration file version is incompatible",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val fileVersion: String = "unknown",
    val supportedVersions: List<String> = emptyList(),
    val migrationAvailable: Boolean = false,
    val migrationPath: String = "unknown"
) : ConfigurationException(
    message = message,
    cause = cause,
    errorCode = "CONFIG_VERSION_INCOMPATIBLE",
    severity = Severity.WARN,
    configInfo = mapOf(
        "filePath" to filePath,
        "fileVersion" to fileVersion,
        "supportedVersions" to supportedVersions,
        "migrationAvailable" to migrationAvailable,
        "migrationPath" to migrationPath
    )
)



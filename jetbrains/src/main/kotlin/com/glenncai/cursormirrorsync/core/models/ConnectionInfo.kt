package com.glenncai.cursormirrorsync.core.models

import com.google.gson.annotations.SerializedName
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.validators.ConfigurationValidator

/**
 * Connection information read from .cursor-mirror-sync.json file.
 * Represents the configuration data for establishing WebSocket connections with VSCode.
 */
data class ConnectionInfo(
    @SerializedName("port")
    val port: Int,

    @SerializedName("projectName")
    val projectName: String?,

    @SerializedName("projectPath")
    val projectPath: String?,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("lastModified")
    val lastModified: String? = null,

    @SerializedName("lastActiveAt")
    val lastActiveAt: String? = null,

    @SerializedName("_utcTime")
    val utcTime: String? = null,

    @SerializedName("_reconnectSignal")
    val reconnectSignal: Long? = null
) {
    companion object {
        // Port validation constants
        const val MIN_VALID_PORT = 1
        const val MAX_VALID_PORT = Constants.ABSOLUTE_MAX_PORT

        // Valid status values
        const val STATUS_ACTIVE = Constants.STATUS_ACTIVE
        const val STATUS_INACTIVE = Constants.STATUS_INACTIVE
        
        /**
         * Normalizes a file path for cross-platform comparison.
         */
        fun normalizePath(filePath: String): String {
            // Convert backslashes to forward slashes
            val normalized = filePath.replace('\\', '/')
            // Convert to lowercase on Windows for case-insensitive comparison
            return if (System.getProperty("os.name").lowercase().contains("windows")) {
                normalized.lowercase()
            } else {
                normalized
            }
        }
        
        /**
         * Creates a ConnectionInfo instance with validation.
         */
        fun create(
            port: Int,
            projectName: String,
            projectPath: String,
            status: String? = null,
            createdAt: String? = null
        ): ConnectionInfo? {
            val info = ConnectionInfo(
                port = port,
                projectName = projectName,
                projectPath = projectPath,
                status = status,
                createdAt = createdAt
            )
            return if (info.isValid()) info else null
        }
    }

    /**
     * Validates if the connection info is valid.
     * Note: createdAt is optional and not required for validation.
     */
    fun isValid(): Boolean {
        return port in MIN_VALID_PORT..MAX_VALID_PORT
            && !projectName.isNullOrBlank()
            && !projectPath.isNullOrBlank()
    }

    /**
     * Gets the normalized project path for cross-platform comparison.
     */
    fun getNormalizedProjectPath(): String? {
        return projectPath?.let { normalizePath(it) }
    }

    /**
     * Checks if the connection is currently active.
     */
    fun isActive(): Boolean {
        return status == STATUS_ACTIVE
    }

    /**
     * Validates if the status field has a valid value.
     */
    fun hasValidStatus(): Boolean {
        return status == null || status == STATUS_ACTIVE || status == STATUS_INACTIVE
    }

    /**
     * Checks if the connection status is recent enough to be trusted.
     * @param maxAgeMinutes Maximum age in minutes for the status to be considered valid
     */
    fun isStatusFresh(maxAgeMinutes: Long = Constants.DEFAULT_MAX_AGE_MINUTES): Boolean {
        val lastActiveTime = lastActiveAt ?: createdAt ?: return false
        return try {
            // Try to parse as ISO 8601 format first (old format or UTC time)
            val lastUpdate = try {
                java.time.Instant.parse(lastActiveTime)
            } catch (e: Exception) {
                // If that fails, try to use UTC time field if available
                utcTime?.let { java.time.Instant.parse(it) } ?: return false
            }
            val now = java.time.Instant.now()
            val minutesOld = java.time.Duration.between(lastUpdate, now).toMinutes()
            minutesOld <= maxAgeMinutes
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets information about when this file was generated.
     */
    fun getGenerationInfo(): String {
        val created = createdAt?.let {
            "Created: $it"
        } ?: "Creation time unknown"

        val modified = lastModified?.let {
            "Last modified: $it"
        } ?: ""

        val utc = utcTime?.let {
            try {
                val instant = java.time.Instant.parse(it)
                val localTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                "UTC time: $localTime (Local) / $it (UTC)"
            } catch (e: Exception) {
                "UTC time: $it"
            }
        } ?: ""

        return listOf(created, modified, utc).filter { it.isNotEmpty() }.joinToString("\n")
    }

    /**
     * Returns a summary of the connection information.
     */
    fun getSummary(): String {
        return buildString {
            appendLine("Connection Info:")
            appendLine("  Port: $port")
            appendLine("  Project: ${projectName ?: "Unknown"}")
            appendLine("  Path: ${projectPath ?: "Unknown"}")
            appendLine("  Status: ${status ?: "Not specified"}")
            appendLine("  Valid: ${isValid()}")
            if (status != null) {
                appendLine("  Active: ${isActive()}")
                appendLine("  Status Fresh: ${isStatusFresh()}")
            }
        }
    }

    /**
     * Validates all aspects of the connection info and returns validation errors.
     */
    fun validate(): List<String> {
        return ConfigurationValidator.validateConfiguration(
            port = port,
            projectName = projectName,
            projectPath = projectPath,
            status = status,
            context = "connection"
        )
    }
}

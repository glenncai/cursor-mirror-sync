package com.glenncai.cursormirrorsync.core.validators

import com.glenncai.cursormirrorsync.core.Constants

/**
 * Unified configuration validator that provides centralized validation logic
 * for all configuration-related components in the plugin.
 * 
 * This validator consolidates validation logic that was previously scattered
 * across different components (Settings, ConnectionInfo, ConfigFileValidator)
 * to ensure consistency and reduce code duplication.
 */
object ConfigurationValidator {

    /**
     * Validates a port number against the plugin's unified port range
     * (Constants.MIN_PORT..Constants.MAX_PORT).
     *
     * The [context] parameter is retained for API compatibility and for enriching
     * error messages with a caller-specific prefix. All contexts share the same
     * acceptance range so validation and correction stay in lock-step.
     *
     * @param port The port number to validate
     * @param context Optional context label ("settings", "connection", "config-file", "general")
     * @return List of validation error messages, empty if valid
     */
    fun validatePort(port: Int, context: String = "general"): List<String> {
        if (port in Constants.MIN_PORT..Constants.MAX_PORT) {
            return emptyList()
        }

        val prefix = when (context) {
            "settings" -> "Manual port"
            else -> "Port"
        }
        return listOf("$prefix $port is outside valid range (${Constants.MIN_PORT}-${Constants.MAX_PORT})")
    }

    /**
     * Corrects a port number so it lies within Constants.MIN_PORT..Constants.MAX_PORT.
     * The correction rule is identical across every context - values below the
     * minimum fall back to DEFAULT_PORT and values above the maximum are clamped
     * to MAX_PORT - which keeps the behaviour of [validatePort] and
     * [validateAndCorrectPort] consistent with each other.
     *
     * @param port The port number to validate and correct
     * @param context Optional context label, kept for API compatibility
     * @return Corrected port number within the valid range
     */
    @Suppress("UNUSED_PARAMETER")
    fun validateAndCorrectPort(port: Int, context: String = "general"): Int {
        return when {
            port < Constants.MIN_PORT -> Constants.DEFAULT_PORT
            port > Constants.MAX_PORT -> Constants.MAX_PORT
            else -> port
        }
    }

    /**
     * Validates a project name according to the plugin's naming rules.
     * 
     * @param projectName The project name to validate
     * @param allowNull Whether null values are allowed
     * @return List of validation error messages, empty if valid
     */
    fun validateProjectName(projectName: String?, allowNull: Boolean = false): List<String> {
        return validateNonBlank(projectName, Constants.ValidationMessages.PROJECT_NAME_BLANK, allowNull)
    }

    /**
     * Validates a project path according to the plugin's path rules.
     * 
     * @param projectPath The project path to validate
     * @param allowNull Whether null values are allowed
     * @return List of validation error messages, empty if valid
     */
    fun validateProjectPath(projectPath: String?, allowNull: Boolean = false): List<String> {
        return validateNonBlank(projectPath, Constants.ValidationMessages.PROJECT_PATH_BLANK, allowNull)
    }

    /**
     * Shared helper: validates that a string field is non-null (unless allowed)
     * and non-blank. Returns an empty list when the value is valid.
     */
    private fun validateNonBlank(value: String?, blankMessage: String, allowNull: Boolean): List<String> {
        return when {
            value == null -> if (allowNull) emptyList() else listOf(blankMessage)
            value.isBlank() -> listOf(blankMessage)
            else -> emptyList()
        }
    }

    /**
     * Validates a status value according to the plugin's status rules.
     *
     * @param status The status value to validate
     * @return List of validation error messages, empty if valid
     */
    fun validateStatus(status: String?): List<String> {
        if (status == null || status == Constants.STATUS_ACTIVE || status == Constants.STATUS_INACTIVE) {
            return emptyList()
        }
        return listOf(Constants.ValidationMessages.INVALID_STATUS)
    }

    /**
     * Checks if a port is valid according to absolute minimum standards.
     * 
     * @param port The port number to check
     * @return true if the port is valid, false otherwise
     */
    fun isAbsoluteValidPort(port: Int): Boolean {
        return port in Constants.ABSOLUTE_MIN_PORT..Constants.MAX_PORT
    }

    /**
     * Checks if a port is valid according to standard plugin standards.
     * 
     * @param port The port number to check
     * @return true if the port is valid, false otherwise
     */
    fun isValidPort(port: Int): Boolean {
        return port in Constants.MIN_PORT..Constants.MAX_PORT
    }

    /**
     * Checks if a status value is valid.
     * 
     * @param status The status value to check
     * @return true if the status is valid, false otherwise
     */
    fun isValidStatus(status: String?): Boolean {
        return status == null || status in listOf(Constants.STATUS_ACTIVE, Constants.STATUS_INACTIVE)
    }

    /**
     * Validates a complete configuration object with port, project name, and project path.
     * 
     * @param port The port number
     * @param projectName The project name
     * @param projectPath The project path
     * @param status Optional status value
     * @param context Validation context for port validation
     * @return List of all validation error messages, empty if all valid
     */
    fun validateConfiguration(
        port: Int,
        projectName: String?,
        projectPath: String?,
        status: String? = null,
        context: String = "general"
    ): List<String> {
        val errors = mutableListOf<String>()
        
        errors.addAll(validatePort(port, context))
        errors.addAll(validateProjectName(projectName))
        errors.addAll(validateProjectPath(projectPath))
        errors.addAll(validateStatus(status))
        
        return errors
    }
}

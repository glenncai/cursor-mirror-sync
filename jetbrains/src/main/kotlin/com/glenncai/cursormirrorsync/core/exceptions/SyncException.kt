package com.glenncai.cursormirrorsync.core.exceptions

import java.time.Instant

/**
 * Base exception class for all cursor mirror sync related exceptions.
 * Provides structured error information including error codes, severity levels,
 * and contextual information for better error handling and debugging.
 */
open class SyncException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String = "SYNC_ERROR",
    val severity: Severity = Severity.ERROR,
    val context: Map<String, Any> = emptyMap(),
    val timestamp: Instant = Instant.now(),
    val component: String = "Unknown"
) : RuntimeException(message, cause) {

    /**
     * Severity levels for exceptions
     */
    enum class Severity(val level: Int, val displayName: String) {
        INFO(1, "Info"),
        WARN(2, "Warning"), 
        ERROR(3, "Error"),
        FATAL(4, "Fatal")
    }

    /**
     * Gets a structured error information map
     */
    fun getErrorInfo(): Map<String, Any> {
        return mapOf(
            "errorCode" to errorCode,
            "message" to (message ?: "Unknown error"),
            "severity" to severity.displayName,
            "component" to component,
            "timestamp" to timestamp.toString(),
            "context" to context,
            "cause" to (cause?.message ?: "None")
        )
    }

    /**
     * Gets a formatted error message for logging
     */
    fun getFormattedMessage(): String {
        val contextStr = if (context.isNotEmpty()) {
            context.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else {
            "No context"
        }
        
        return buildString {
            append("[$errorCode] ")
            append("[${severity.displayName}] ")
            append("[$component] ")
            append(message ?: "Unknown error")
            if (contextStr != "No context") {
                append(" | Context: $contextStr")
            }
            cause?.let { append(" | Caused by: ${it.message}") }
        }
    }

    /**
     * Checks if this exception is recoverable based on severity
     */
    fun isRecoverable(): Boolean {
        return severity.level < Severity.FATAL.level
    }

    /**
     * Checks if this exception should trigger a retry
     */
    open fun shouldRetry(): Boolean {
        return severity.level <= Severity.WARN.level
    }

    /**
     * Gets the retry delay in milliseconds for recoverable exceptions
     */
    open fun getRetryDelay(): Long {
        return when (severity) {
            Severity.INFO -> 1000L
            Severity.WARN -> 2000L
            Severity.ERROR -> 5000L
            Severity.FATAL -> 0L // No retry for fatal errors
        }
    }

    /**
     * Creates a copy of this exception with additional context
     */
    fun withContext(additionalContext: Map<String, Any>): SyncException {
        val newContext = context.toMutableMap()
        newContext.putAll(additionalContext)
        return SyncException(
            message = message ?: "Unknown error",
            cause = cause,
            errorCode = errorCode,
            severity = severity,
            context = newContext,
            timestamp = timestamp,
            component = component
        )
    }

    /**
     * Creates a copy of this exception with a different severity
     */
    fun withSeverity(newSeverity: Severity): SyncException {
        return SyncException(
            message = message ?: "Unknown error",
            cause = cause,
            errorCode = errorCode,
            severity = newSeverity,
            context = context,
            timestamp = timestamp,
            component = component
        )
    }

    override fun toString(): String {
        return getFormattedMessage()
    }

    companion object {
        /**
         * Creates a SyncException from a generic Exception
         */
        fun fromException(
            exception: Exception,
            errorCode: String = "GENERIC_ERROR",
            severity: Severity = Severity.ERROR,
            component: String = "Unknown",
            context: Map<String, Any> = emptyMap()
        ): SyncException {
            return SyncException(
                message = exception.message ?: "Unknown error",
                cause = exception,
                errorCode = errorCode,
                severity = severity,
                context = context,
                component = component
            )
        }

        /**
         * Creates an info-level SyncException
         */
        fun info(
            message: String,
            component: String = "Unknown",
            context: Map<String, Any> = emptyMap()
        ): SyncException {
            return SyncException(
                message = message,
                errorCode = "INFO",
                severity = Severity.INFO,
                component = component,
                context = context
            )
        }

        /**
         * Creates a warning-level SyncException
         */
        fun warn(
            message: String,
            component: String = "Unknown",
            context: Map<String, Any> = emptyMap(),
            cause: Throwable? = null
        ): SyncException {
            return SyncException(
                message = message,
                cause = cause,
                errorCode = "WARNING",
                severity = Severity.WARN,
                component = component,
                context = context
            )
        }

        /**
         * Creates an error-level SyncException
         */
        fun error(
            message: String,
            component: String = "Unknown",
            context: Map<String, Any> = emptyMap(),
            cause: Throwable? = null
        ): SyncException {
            return SyncException(
                message = message,
                cause = cause,
                errorCode = "ERROR",
                severity = Severity.ERROR,
                component = component,
                context = context
            )
        }

        /**
         * Creates a fatal-level SyncException
         */
        fun fatal(
            message: String,
            component: String = "Unknown",
            context: Map<String, Any> = emptyMap(),
            cause: Throwable? = null
        ): SyncException {
            return SyncException(
                message = message,
                cause = cause,
                errorCode = "FATAL",
                severity = Severity.FATAL,
                component = component,
                context = context
            )
        }
    }
}

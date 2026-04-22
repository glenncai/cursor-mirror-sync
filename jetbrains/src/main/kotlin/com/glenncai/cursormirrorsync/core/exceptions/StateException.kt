package com.glenncai.cursormirrorsync.core.exceptions

import java.time.Instant

/**
 * Base exception class for editor state related errors.
 * Handles state validation, application, and serialization issues.
 */
open class StateException(
    message: String,
    cause: Throwable? = null,
    errorCode: String = "STATE_ERROR",
    severity: Severity = Severity.ERROR,
    context: Map<String, Any> = emptyMap(),
    timestamp: Instant = Instant.now(),
    val stateInfo: Map<String, Any> = emptyMap()
) : SyncException(
    message = message,
    cause = cause,
    errorCode = errorCode,
    severity = severity,
    context = context + stateInfo,
    timestamp = timestamp,
    component = "EditorSyncManager"
) {

    override fun shouldRetry(): Boolean {
        return when (this) {
            is StateValidationException -> false
            is StateApplicationException -> true
            is StateSerializationException -> false
            is StateComparisonException -> true
            else -> super.shouldRetry()
        }
    }

    override fun getRetryDelay(): Long {
        return when (this) {
            is StateApplicationException -> 1000L
            is StateComparisonException -> 500L
            else -> super.getRetryDelay()
        }
    }
}

/**
 * Exception thrown when editor state validation fails.
 */
class StateValidationException(
    message: String = "Editor state validation failed",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val line: Int = -1,
    val column: Int = -1,
    val validationErrors: List<String> = emptyList()
) : StateException(
    message = message,
    cause = cause,
    errorCode = "STATE_VALIDATION_FAILED",
    severity = Severity.WARN,
    stateInfo = mapOf(
        "filePath" to filePath,
        "line" to line,
        "column" to column,
        "validationErrors" to validationErrors
    )
)

/**
 * Exception thrown when applying editor state fails.
 */
class StateApplicationException(
    message: String = "Failed to apply editor state",
    cause: Throwable? = null,
    val filePath: String = "unknown",
    val targetLine: Int = -1,
    val targetColumn: Int = -1,
    val currentLine: Int = -1,
    val currentColumn: Int = -1,
    val hasSelection: Boolean = false
) : StateException(
    message = message,
    cause = cause,
    errorCode = "STATE_APPLICATION_FAILED",
    severity = Severity.ERROR,
    stateInfo = mapOf(
        "filePath" to filePath,
        "targetLine" to targetLine,
        "targetColumn" to targetColumn,
        "currentLine" to currentLine,
        "currentColumn" to currentColumn,
        "hasSelection" to hasSelection
    )
)

/**
 * Exception thrown when state serialization/deserialization fails.
 */
class StateSerializationException(
    message: String = "State serialization failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "serialize" or "deserialize"
    val stateData: String = "",
    val expectedFormat: String = "EditorState"
) : StateException(
    message = message,
    cause = cause,
    errorCode = "STATE_SERIALIZATION_FAILED",
    severity = Severity.ERROR,
    stateInfo = mapOf(
        "operation" to operation,
        "stateDataLength" to stateData.length,
        "expectedFormat" to expectedFormat
    )
)

/**
 * Exception thrown when state comparison fails.
 */
class StateComparisonException(
    message: String = "State comparison failed",
    cause: Throwable? = null,
    val currentStateHash: String = "unknown",
    val newStateHash: String = "unknown",
    val comparisonStrategy: String = "unknown"
) : StateException(
    message = message,
    cause = cause,
    errorCode = "STATE_COMPARISON_FAILED",
    severity = Severity.WARN,
    stateInfo = mapOf(
        "currentStateHash" to currentStateHash,
        "newStateHash" to newStateHash,
        "comparisonStrategy" to comparisonStrategy
    )
)

/**
 * Exception thrown when state synchronization fails.
 */
class StateSynchronizationException(
    message: String = "State synchronization failed",
    cause: Throwable? = null,
    val sourceIde: String = "unknown",
    val targetIde: String = "unknown",
    val syncDirection: String = "unknown", // "incoming" or "outgoing"
    val conflictResolution: String = "none"
) : StateException(
    message = message,
    cause = cause,
    errorCode = "STATE_SYNC_FAILED",
    severity = Severity.ERROR,
    stateInfo = mapOf(
        "sourceIde" to sourceIde,
        "targetIde" to targetIde,
        "syncDirection" to syncDirection,
        "conflictResolution" to conflictResolution
    )
)

/**
 * Exception thrown when state cache operations fail.
 */
class StateCacheException(
    message: String = "State cache operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "get", "put", "clear", etc.
    val cacheKey: String = "unknown",
    val cacheSize: Int = 0
) : StateException(
    message = message,
    cause = cause,
    errorCode = "STATE_CACHE_FAILED",
    severity = Severity.WARN,
    stateInfo = mapOf(
        "operation" to operation,
        "cacheKey" to cacheKey,
        "cacheSize" to cacheSize
    )
)

/**
 * Exception thrown when state history tracking fails.
 */
class StateHistoryException(
    message: String = "State history operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "add", "get", "clear", etc.
    val filePath: String = "unknown",
    val historySize: Int = 0
) : StateException(
    message = message,
    cause = cause,
    errorCode = "STATE_HISTORY_FAILED",
    severity = Severity.WARN,
    stateInfo = mapOf(
        "operation" to operation,
        "filePath" to filePath,
        "historySize" to historySize
    )
)

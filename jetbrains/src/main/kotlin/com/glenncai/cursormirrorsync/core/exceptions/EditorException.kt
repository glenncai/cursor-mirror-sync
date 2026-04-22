package com.glenncai.cursormirrorsync.core.exceptions

import java.time.Instant

/**
 * Base exception class for editor operation related errors.
 * Handles editor listener management, focus operations, and editor lifecycle issues.
 */
open class EditorException(
    message: String,
    cause: Throwable? = null,
    errorCode: String = "EDITOR_ERROR",
    severity: Severity = Severity.ERROR,
    context: Map<String, Any> = emptyMap(),
    timestamp: Instant = Instant.now(),
    val editorInfo: Map<String, Any> = emptyMap()
) : SyncException(
    message = message,
    cause = cause,
    errorCode = errorCode,
    severity = severity,
    context = context + editorInfo,
    timestamp = timestamp,
    component = "EditorManager"
) {

    override fun shouldRetry(): Boolean {
        return when (this) {
            is EditorListenerException -> true
            is EditorFocusException -> true
            is EditorAccessException -> false
            is EditorLifecycleException -> false
            else -> super.shouldRetry()
        }
    }

    override fun getRetryDelay(): Long {
        return when (this) {
            is EditorListenerException -> 500L
            is EditorFocusException -> 200L
            else -> super.getRetryDelay()
        }
    }
}

/**
 * Exception thrown when editor listener operations fail.
 */
class EditorListenerException(
    message: String = "Editor listener operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "add", "remove", "activate", "deactivate"
    val listenerType: String = "unknown", // "caret", "selection", "document"
    val editorId: String = "unknown",
    val filePath: String = "unknown"
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_LISTENER_FAILED",
    severity = Severity.WARN,
    editorInfo = mapOf(
        "operation" to operation,
        "listenerType" to listenerType,
        "editorId" to editorId,
        "filePath" to filePath
    )
)

/**
 * Exception thrown when editor focus operations fail.
 */
class EditorFocusException(
    message: String = "Editor focus operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "focus", "blur", "request"
    val editorId: String = "unknown",
    val filePath: String = "unknown",
    val isDisplayable: Boolean = false
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_FOCUS_FAILED",
    severity = Severity.WARN,
    editorInfo = mapOf(
        "operation" to operation,
        "editorId" to editorId,
        "filePath" to filePath,
        "isDisplayable" to isDisplayable
    )
)

/**
 * Exception thrown when editor access fails.
 */
class EditorAccessException(
    message: String = "Editor access failed",
    cause: Throwable? = null,
    val editorId: String = "unknown",
    val filePath: String = "unknown",
    val accessType: String = "unknown", // "read", "write", "caret", "selection"
    val isDisposed: Boolean = false
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_ACCESS_FAILED",
    severity = Severity.ERROR,
    editorInfo = mapOf(
        "editorId" to editorId,
        "filePath" to filePath,
        "accessType" to accessType,
        "isDisposed" to isDisposed
    )
)

/**
 * Exception thrown when editor lifecycle operations fail.
 */
class EditorLifecycleException(
    message: String = "Editor lifecycle operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "create", "open", "close", "dispose"
    val editorId: String = "unknown",
    val filePath: String = "unknown",
    val lifecycleState: String = "unknown"
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_LIFECYCLE_FAILED",
    severity = Severity.ERROR,
    editorInfo = mapOf(
        "operation" to operation,
        "editorId" to editorId,
        "filePath" to filePath,
        "lifecycleState" to lifecycleState
    )
)

/**
 * Exception thrown when editor caret operations fail.
 */
class EditorCaretException(
    message: String = "Editor caret operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "move", "position", "scroll"
    val targetLine: Int = -1,
    val targetColumn: Int = -1,
    val currentLine: Int = -1,
    val currentColumn: Int = -1,
    val editorId: String = "unknown"
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_CARET_FAILED",
    severity = Severity.WARN,
    editorInfo = mapOf(
        "operation" to operation,
        "targetLine" to targetLine,
        "targetColumn" to targetColumn,
        "currentLine" to currentLine,
        "currentColumn" to currentColumn,
        "editorId" to editorId
    )
)

/**
 * Exception thrown when editor selection operations fail.
 */
class EditorSelectionException(
    message: String = "Editor selection operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "set", "clear", "extend"
    val startLine: Int = -1,
    val startColumn: Int = -1,
    val endLine: Int = -1,
    val endColumn: Int = -1,
    val editorId: String = "unknown"
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_SELECTION_FAILED",
    severity = Severity.WARN,
    editorInfo = mapOf(
        "operation" to operation,
        "startLine" to startLine,
        "startColumn" to startColumn,
        "endLine" to endLine,
        "endColumn" to endColumn,
        "editorId" to editorId
    )
)

/**
 * Exception thrown when editor scrolling operations fail.
 */
class EditorScrollException(
    message: String = "Editor scroll operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "scroll", "center", "make_visible"
    val targetLine: Int = -1,
    val targetColumn: Int = -1,
    val scrollType: String = "unknown",
    val editorId: String = "unknown"
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_SCROLL_FAILED",
    severity = Severity.WARN,
    editorInfo = mapOf(
        "operation" to operation,
        "targetLine" to targetLine,
        "targetColumn" to targetColumn,
        "scrollType" to scrollType,
        "editorId" to editorId
    )
)

/**
 * Exception thrown when editor pool operations fail.
 */
class EditorPoolException(
    message: String = "Editor pool operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "acquire", "release", "cleanup"
    val poolSize: Int = 0,
    val activeEditors: Int = 0,
    val maxPoolSize: Int = 0
) : EditorException(
    message = message,
    cause = cause,
    errorCode = "EDITOR_POOL_FAILED",
    severity = Severity.WARN,
    editorInfo = mapOf(
        "operation" to operation,
        "poolSize" to poolSize,
        "activeEditors" to activeEditors,
        "maxPoolSize" to maxPoolSize
    )
)

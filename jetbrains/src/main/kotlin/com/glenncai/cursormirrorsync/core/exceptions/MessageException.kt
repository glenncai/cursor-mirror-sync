package com.glenncai.cursormirrorsync.core.exceptions

import java.time.Instant

/**
 * Base exception class for message processing related errors.
 * Handles message parsing, validation, and routing issues.
 */
open class MessageException(
    message: String,
    cause: Throwable? = null,
    errorCode: String = "MESSAGE_ERROR",
    severity: Severity = Severity.ERROR,
    context: Map<String, Any> = emptyMap(),
    timestamp: Instant = Instant.now(),
    val messageInfo: Map<String, Any> = emptyMap()
) : SyncException(
    message = message,
    cause = cause,
    errorCode = errorCode,
    severity = severity,
    context = context + messageInfo,
    timestamp = timestamp,
    component = "MessageHandler"
) {

    override fun shouldRetry(): Boolean {
        return when (this) {
            is MessageParsingException -> false
            is MessageValidationException -> false
            is UnknownMessageTypeException -> false
            is MessageRoutingException -> true
            is MessageBatchException -> true
            else -> super.shouldRetry()
        }
    }

    override fun getRetryDelay(): Long {
        return when (this) {
            is MessageRoutingException -> 1000L
            is MessageBatchException -> 500L
            else -> super.getRetryDelay()
        }
    }
}

/**
 * Exception thrown when message parsing fails.
 */
class MessageParsingException(
    message: String = "Message parsing failed",
    cause: Throwable? = null,
    val rawMessage: String = "",
    val expectedFormat: String = "JSON",
    val parsePosition: Int = -1
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_PARSING_FAILED",
    severity = Severity.ERROR,
    messageInfo = mapOf(
        "rawMessageLength" to rawMessage.length,
        "expectedFormat" to expectedFormat,
        "parsePosition" to parsePosition,
        "messagePreview" to rawMessage.take(100)
    )
)

/**
 * Exception thrown when message validation fails.
 */
class MessageValidationException(
    message: String = "Message validation failed",
    cause: Throwable? = null,
    val messageType: String = "unknown",
    val validationErrors: List<String> = emptyList(),
    val requiredFields: List<String> = emptyList(),
    val missingFields: List<String> = emptyList()
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_VALIDATION_FAILED",
    severity = Severity.WARN,
    messageInfo = mapOf(
        "messageType" to messageType,
        "validationErrors" to validationErrors,
        "requiredFields" to requiredFields,
        "missingFields" to missingFields
    )
)

/**
 * Exception thrown when an unknown message type is received.
 */
class UnknownMessageTypeException(
    message: String = "Unknown message type received",
    cause: Throwable? = null,
    val receivedType: String = "unknown",
    val supportedTypes: List<String> = emptyList(),
    val rawMessage: String = ""
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "UNKNOWN_MESSAGE_TYPE",
    severity = Severity.WARN,
    messageInfo = mapOf(
        "receivedType" to receivedType,
        "supportedTypes" to supportedTypes,
        "messagePreview" to rawMessage.take(100)
    )
)

/**
 * Exception thrown when message routing fails.
 */
class MessageRoutingException(
    message: String = "Message routing failed",
    cause: Throwable? = null,
    val messageType: String = "unknown",
    val targetHandler: String = "unknown",
    val routingPath: String = "unknown"
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_ROUTING_FAILED",
    severity = Severity.ERROR,
    messageInfo = mapOf(
        "messageType" to messageType,
        "targetHandler" to targetHandler,
        "routingPath" to routingPath
    )
)

/**
 * Exception thrown when message batch processing fails.
 */
class MessageBatchException(
    message: String = "Message batch processing failed",
    cause: Throwable? = null,
    val batchSize: Int = 0,
    val processedCount: Int = 0,
    val failedCount: Int = 0,
    val batchId: String = "unknown"
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_BATCH_FAILED",
    severity = Severity.ERROR,
    messageInfo = mapOf(
        "batchSize" to batchSize,
        "processedCount" to processedCount,
        "failedCount" to failedCount,
        "batchId" to batchId
    )
)

/**
 * Exception thrown when message queue operations fail.
 */
class MessageQueueException(
    message: String = "Message queue operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "enqueue", "dequeue", "peek", etc.
    val queueSize: Int = 0,
    val queueCapacity: Int = 0,
    val messageType: String = "unknown"
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_QUEUE_FAILED",
    severity = Severity.WARN,
    messageInfo = mapOf(
        "operation" to operation,
        "queueSize" to queueSize,
        "queueCapacity" to queueCapacity,
        "messageType" to messageType
    )
)

/**
 * Exception thrown when message serialization fails.
 */
class MessageSerializationException(
    message: String = "Message serialization failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "serialize" or "deserialize"
    val messageType: String = "unknown",
    val dataFormat: String = "JSON"
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_SERIALIZATION_FAILED",
    severity = Severity.ERROR,
    messageInfo = mapOf(
        "operation" to operation,
        "messageType" to messageType,
        "dataFormat" to dataFormat
    )
)

/**
 * Exception thrown when message priority handling fails.
 */
class MessagePriorityException(
    message: String = "Message priority handling failed",
    cause: Throwable? = null,
    val messagePriority: String = "unknown",
    val expectedPriority: String = "unknown",
    val queuePosition: Int = -1
) : MessageException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_PRIORITY_FAILED",
    severity = Severity.WARN,
    messageInfo = mapOf(
        "messagePriority" to messagePriority,
        "expectedPriority" to expectedPriority,
        "queuePosition" to queuePosition
    )
)

package com.glenncai.cursormirrorsync.core.exceptions

import java.time.Instant

/**
 * Base exception class for WebSocket connection related errors.
 * Handles various connection scenarios including timeouts, refused connections,
 * network issues, and protocol errors.
 */
open class ConnectionException(
    message: String,
    cause: Throwable? = null,
    errorCode: String = "CONNECTION_ERROR",
    severity: Severity = Severity.ERROR,
    context: Map<String, Any> = emptyMap(),
    timestamp: Instant = Instant.now(),
    val connectionInfo: Map<String, Any> = emptyMap()
) : SyncException(
    message = message,
    cause = cause,
    errorCode = errorCode,
    severity = severity,
    context = context + connectionInfo,
    timestamp = timestamp,
    component = "ConnectionManager"
) {

    override fun shouldRetry(): Boolean {
        return when (this) {
            is ConnectionTimeoutException -> true
            is NetworkUnreachableException -> true
            is ConnectionRefusedException -> severity.level <= Severity.WARN.level
            is AuthenticationFailedException -> false
            is ProtocolException -> false
            else -> super.shouldRetry()
        }
    }

    override fun getRetryDelay(): Long {
        return when (this) {
            is ConnectionTimeoutException -> 3000L
            is NetworkUnreachableException -> 5000L
            is ConnectionRefusedException -> 2000L
            else -> super.getRetryDelay()
        }
    }
}

/**
 * Exception thrown when a connection attempt times out.
 */
class ConnectionTimeoutException(
    message: String = "Connection attempt timed out",
    cause: Throwable? = null,
    val timeoutMs: Long = 0,
    val host: String = "unknown",
    val port: Int = 0
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "CONNECTION_TIMEOUT",
    severity = Severity.WARN,
    connectionInfo = mapOf(
        "timeoutMs" to timeoutMs,
        "host" to host,
        "port" to port
    )
)

/**
 * Exception thrown when a connection is refused by the remote host.
 */
class ConnectionRefusedException(
    message: String = "Connection refused by remote host",
    cause: Throwable? = null,
    val host: String = "unknown",
    val port: Int = 0,
    val retryCount: Int = 0,
    val connectionFileExists: Boolean = true
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "CONNECTION_REFUSED",
    severity = when {
        // When connection file doesn't exist (VSCode not running), use INFO level
        !connectionFileExists -> Severity.INFO
        // Normal retry logic for when VSCode is running but connection fails
        retryCount > 3 -> Severity.ERROR
        else -> Severity.WARN
    },
    connectionInfo = mapOf(
        "host" to host,
        "port" to port,
        "retryCount" to retryCount,
        "connectionFileExists" to connectionFileExists
    )
)

/**
 * Exception thrown when the network is unreachable.
 */
class NetworkUnreachableException(
    message: String = "Network is unreachable",
    cause: Throwable? = null,
    val host: String = "unknown"
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "NETWORK_UNREACHABLE",
    severity = Severity.WARN,
    connectionInfo = mapOf("host" to host)
)

/**
 * Exception thrown when authentication fails.
 */
class AuthenticationFailedException(
    message: String = "Authentication failed",
    cause: Throwable? = null,
    val authMethod: String = "unknown"
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "AUTHENTICATION_FAILED",
    severity = Severity.ERROR,
    connectionInfo = mapOf("authMethod" to authMethod)
)

/**
 * Exception thrown when there's a protocol error.
 */
class ProtocolException(
    message: String = "Protocol error occurred",
    cause: Throwable? = null,
    val protocolVersion: String = "unknown",
    val expectedVersion: String = "unknown"
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "PROTOCOL_ERROR",
    severity = Severity.ERROR,
    connectionInfo = mapOf(
        "protocolVersion" to protocolVersion,
        "expectedVersion" to expectedVersion
    )
)

/**
 * Exception thrown when WebSocket connection is lost unexpectedly.
 */
class ConnectionLostException(
    message: String = "WebSocket connection lost",
    cause: Throwable? = null,
    val closeCode: Int = 0,
    val closeReason: String = "unknown",
    val wasCleanClose: Boolean = false,
    val connectionDuration: Long = 0
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "CONNECTION_LOST",
    severity = if (wasCleanClose) Severity.INFO else Severity.WARN,
    connectionInfo = mapOf(
        "closeCode" to closeCode,
        "closeReason" to closeReason,
        "wasCleanClose" to wasCleanClose,
        "connectionDurationMs" to connectionDuration
    )
)

/**
 * Exception thrown when SSL/TLS handshake fails.
 */
class SslHandshakeException(
    message: String = "SSL handshake failed",
    cause: Throwable? = null,
    val certificateInfo: String = "unknown"
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "SSL_HANDSHAKE_FAILED",
    severity = Severity.ERROR,
    connectionInfo = mapOf("certificateInfo" to certificateInfo)
)

/**
 * Exception thrown when connection pool is exhausted.
 */
class ConnectionPoolExhaustedException(
    message: String = "Connection pool exhausted",
    cause: Throwable? = null,
    val poolSize: Int = 0,
    val activeConnections: Int = 0
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "CONNECTION_POOL_EXHAUSTED",
    severity = Severity.WARN,
    connectionInfo = mapOf(
        "poolSize" to poolSize,
        "activeConnections" to activeConnections
    )
)

/**
 * Exception thrown when WebSocket handshake fails.
 */
class WebSocketHandshakeException(
    message: String = "WebSocket handshake failed",
    cause: Throwable? = null,
    val handshakeStatus: Int = 0,
    val handshakeResponse: String = "unknown",
    val expectedProtocol: String = "unknown",
    val actualProtocol: String = "unknown"
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "WEBSOCKET_HANDSHAKE_FAILED",
    severity = Severity.ERROR,
    connectionInfo = mapOf(
        "handshakeStatus" to handshakeStatus,
        "handshakeResponse" to handshakeResponse,
        "expectedProtocol" to expectedProtocol,
        "actualProtocol" to actualProtocol
    )
)

/**
 * Exception thrown when WebSocket frame processing fails.
 */
class WebSocketFrameException(
    message: String = "WebSocket frame processing failed",
    cause: Throwable? = null,
    val frameType: String = "unknown",
    val frameSize: Long = 0,
    val maxFrameSize: Long = 0,
    val frameOpcode: Int = -1
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "WEBSOCKET_FRAME_FAILED",
    severity = Severity.ERROR,
    connectionInfo = mapOf(
        "frameType" to frameType,
        "frameSize" to frameSize,
        "maxFrameSize" to maxFrameSize,
        "frameOpcode" to frameOpcode
    )
)

/**
 * Exception thrown when WebSocket compression/decompression fails.
 */
class WebSocketCompressionException(
    message: String = "WebSocket compression failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "compress" or "decompress"
    val compressionMethod: String = "unknown",
    val originalSize: Long = 0,
    val compressedSize: Long = 0
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "WEBSOCKET_COMPRESSION_FAILED",
    severity = Severity.WARN,
    connectionInfo = mapOf(
        "operation" to operation,
        "compressionMethod" to compressionMethod,
        "originalSize" to originalSize,
        "compressedSize" to compressedSize
    )
)

/**
 * Exception thrown when WebSocket state transition fails.
 */
class WebSocketStateException(
    message: String = "WebSocket state transition failed",
    cause: Throwable? = null,
    val currentState: String = "unknown",
    val targetState: String = "unknown",
    val operation: String = "unknown",
    val stateTransitionValid: Boolean = false
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "WEBSOCKET_STATE_FAILED",
    severity = Severity.ERROR,
    connectionInfo = mapOf(
        "currentState" to currentState,
        "targetState" to targetState,
        "operation" to operation,
        "stateTransitionValid" to stateTransitionValid
    )
)

/**
 * Exception thrown when WebSocket buffer operations fail.
 */
class WebSocketBufferException(
    message: String = "WebSocket buffer operation failed",
    cause: Throwable? = null,
    val operation: String = "unknown", // "read", "write", "allocate", "resize"
    val bufferSize: Long = 0,
    val maxBufferSize: Long = 0,
    val availableMemory: Long = 0
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "WEBSOCKET_BUFFER_FAILED",
    severity = Severity.WARN,
    connectionInfo = mapOf(
        "operation" to operation,
        "bufferSize" to bufferSize,
        "maxBufferSize" to maxBufferSize,
        "availableMemory" to availableMemory
    )
)

/**
 * Exception thrown when WebSocket message sending fails.
 */
class WebSocketSendException(
    message: String = "WebSocket message send failed",
    cause: Throwable? = null,
    val messageType: String = "unknown",
    val messageSize: Long = 0,
    val queueSize: Int = 0,
    val connectionState: String = "unknown"
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "WEBSOCKET_SEND_FAILED",
    severity = Severity.ERROR,
    connectionInfo = mapOf(
        "messageType" to messageType,
        "messageSize" to messageSize,
        "queueSize" to queueSize,
        "connectionState" to connectionState
    )
)

/**
 * Exception thrown when WebSocket close operation fails.
 */
class WebSocketCloseException(
    message: String = "WebSocket close operation failed",
    cause: Throwable? = null,
    val closeCode: Int = 0,
    val closeReason: String = "unknown",
    val timeoutMs: Long = 0,
    val forceClosed: Boolean = false
) : ConnectionException(
    message = message,
    cause = cause,
    errorCode = "WEBSOCKET_CLOSE_FAILED",
    severity = Severity.WARN,
    connectionInfo = mapOf(
        "closeCode" to closeCode,
        "closeReason" to closeReason,
        "timeoutMs" to timeoutMs,
        "forceClosed" to forceClosed
    )
)

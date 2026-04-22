package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.diagnostic.Logger
import com.glenncai.cursormirrorsync.core.exceptions.*
import org.java_websocket.exceptions.WebsocketNotConnectedException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import javax.net.ssl.SSLHandshakeException
import java.util.concurrent.atomic.AtomicLong

/**
 * Context information for WebSocket exception classification
 */
data class WebSocketContext(
    // Connection information
    val host: String = "localhost",
    val port: Int = 0,
    val retryCount: Int = 0,
    val timeoutMs: Long = 0,
    val connectionDuration: Long = 0,

    // WebSocket state information
    val currentState: String = "unknown",
    val targetState: String = "unknown",
    val operation: String = "unknown",

    // File context information for intelligent error classification
    val connectionFileExists: Boolean = true,
    val projectPath: String? = null,

    // Handshake information
    val handshakeStatus: Int = 0,
    val handshakeResponse: String = "unknown",
    val actualProtocol: String = "unknown",

    // Frame information
    val frameType: String = "unknown",
    val frameSize: Long = 0,
    val maxFrameSize: Long = 0,
    val frameOpcode: Int = -1,

    // Buffer information
    val bufferOperation: String = "unknown",
    val bufferSize: Long = 0,
    val maxBufferSize: Long = 0,
    val availableMemory: Long = 0,

    // Compression information
    val compressionOperation: String = "unknown",
    val compressionMethod: String = "unknown",
    val originalSize: Long = 0,
    val compressedSize: Long = 0,

    // Message information
    val messageType: String = "unknown",
    val messageSize: Long = 0,
    val queueSize: Int = 0,

    // Close information
    val closeCode: Int = 0,
    val closeReason: String = "unknown",
    val forceClosed: Boolean = false,

    // SSL information
    val certificateInfo: String = "unknown"
)

/**
 * Intelligent WebSocket exception classifier that analyzes exceptions
 * and converts them to appropriate ConnectionException subtypes.
 * Provides detailed classification based on exception types, messages, and context.
 */
class WebSocketExceptionClassifier {
    
    private val log: Logger = Logger.getInstance(WebSocketExceptionClassifier::class.java)
    
    // Classification statistics
    private val totalClassifications = AtomicLong(0)
    private val handshakeFailures = AtomicLong(0)
    private val timeoutFailures = AtomicLong(0)
    private val connectionRefusedFailures = AtomicLong(0)
    private val networkFailures = AtomicLong(0)
    private val stateFailures = AtomicLong(0)
    private val unknownFailures = AtomicLong(0)
    
    /**
     * Classifies a generic exception into a specific ConnectionException subtype
     */
    fun classifyException(
        exception: Exception?,
        context: WebSocketContext = WebSocketContext()
    ): ConnectionException {
        totalClassifications.incrementAndGet()
        
        return when {
            exception == null -> createUnknownException(context)
            
            // SSL/TLS related exceptions
            exception is SSLHandshakeException -> {
                handshakeFailures.incrementAndGet()
                createSslHandshakeException(exception, context)
            }
            
            // WebSocket specific exceptions
            exception is WebsocketNotConnectedException -> {
                stateFailures.incrementAndGet()
                createWebSocketStateException(exception, context)
            }
            
            // Network connectivity exceptions
            exception is ConnectException -> {
                connectionRefusedFailures.incrementAndGet()
                createConnectionRefusedException(exception, context)
            }
            
            exception is SocketTimeoutException -> {
                timeoutFailures.incrementAndGet()
                createConnectionTimeoutException(exception, context)
            }
            
            exception is UnknownHostException -> {
                networkFailures.incrementAndGet()
                createNetworkUnreachableException(exception, context)
            }
            
            exception is ClosedChannelException -> {
                stateFailures.incrementAndGet()
                createConnectionLostException(exception, context)
            }
            
            // Message-based classification
            else -> classifyByMessage(exception, context)
        }
    }
    
    /**
     * Classifies exception based on error message content
     */
    private fun classifyByMessage(exception: Exception, context: WebSocketContext): ConnectionException {
        val message = exception.message?.lowercase() ?: ""
        
        return when {
            // Handshake related errors
            message.contains("handshake") || message.contains("upgrade") -> {
                handshakeFailures.incrementAndGet()
                createWebSocketHandshakeException(exception, context)
            }
            
            // Connection refused
            message.contains("connection refused") || message.contains("refused") -> {
                connectionRefusedFailures.incrementAndGet()
                createConnectionRefusedException(exception, context)
            }
            
            // Timeout related
            message.contains("timeout") || message.contains("timed out") -> {
                timeoutFailures.incrementAndGet()
                createConnectionTimeoutException(exception, context)
            }
            
            // Network unreachable
            message.contains("network") && (message.contains("unreachable") || message.contains("down")) -> {
                networkFailures.incrementAndGet()
                createNetworkUnreachableException(exception, context)
            }
            
            // Frame related errors
            message.contains("frame") || message.contains("payload") -> {
                createWebSocketFrameException(exception, context)
            }
            
            // Buffer related errors
            message.contains("buffer") || message.contains("memory") || message.contains("overflow") -> {
                createWebSocketBufferException(exception, context)
            }
            
            // Compression related errors
            message.contains("compression") || message.contains("deflate") || message.contains("inflate") -> {
                createWebSocketCompressionException(exception, context)
            }
            
            // Send related errors
            message.contains("send") || message.contains("write") -> {
                createWebSocketSendException(exception, context)
            }
            
            // Close related errors
            message.contains("close") || message.contains("closing") -> {
                createWebSocketCloseException(exception, context)
            }
            
            // Default to generic connection exception
            else -> {
                unknownFailures.incrementAndGet()
                createGenericConnectionException(exception, context)
            }
        }
    }
    
    private fun createSslHandshakeException(exception: Exception, context: WebSocketContext): SslHandshakeException {
        return SslHandshakeException(
            message = "SSL handshake failed: ${exception.message}",
            cause = exception,
            certificateInfo = context.certificateInfo
        )
    }
    
    private fun createWebSocketStateException(exception: Exception, context: WebSocketContext): WebSocketStateException {
        return WebSocketStateException(
            message = "WebSocket state error: ${exception.message}",
            cause = exception,
            currentState = context.currentState,
            targetState = context.targetState,
            operation = context.operation
        )
    }
    
    private fun createConnectionRefusedException(exception: Exception, context: WebSocketContext): ConnectionRefusedException {
        val message = if (!context.connectionFileExists) {
            "Connection refused: VSCode appears to be closed (connection file not found)"
        } else {
            "Connection refused: ${exception.message}"
        }

        return ConnectionRefusedException(
            message = message,
            cause = exception,
            host = context.host,
            port = context.port,
            retryCount = context.retryCount,
            connectionFileExists = context.connectionFileExists
        )
    }
    
    private fun createConnectionTimeoutException(exception: Exception, context: WebSocketContext): ConnectionTimeoutException {
        return ConnectionTimeoutException(
            message = "Connection timeout: ${exception.message}",
            cause = exception,
            timeoutMs = context.timeoutMs,
            host = context.host,
            port = context.port
        )
    }
    
    private fun createNetworkUnreachableException(exception: Exception, context: WebSocketContext): NetworkUnreachableException {
        return NetworkUnreachableException(
            message = "Network unreachable: ${exception.message}",
            cause = exception,
            host = context.host
        )
    }
    
    private fun createConnectionLostException(exception: Exception, context: WebSocketContext): ConnectionLostException {
        return ConnectionLostException(
            message = "Connection lost: ${exception.message}",
            cause = exception,
            closeCode = context.closeCode,
            closeReason = context.closeReason,
            wasCleanClose = false,
            connectionDuration = context.connectionDuration
        )
    }
    
    private fun createWebSocketHandshakeException(exception: Exception, context: WebSocketContext): WebSocketHandshakeException {
        return WebSocketHandshakeException(
            message = "WebSocket handshake failed: ${exception.message}",
            cause = exception,
            handshakeStatus = context.handshakeStatus,
            handshakeResponse = context.handshakeResponse,
            expectedProtocol = "websocket",
            actualProtocol = context.actualProtocol
        )
    }
    
    private fun createWebSocketFrameException(exception: Exception, context: WebSocketContext): WebSocketFrameException {
        return WebSocketFrameException(
            message = "WebSocket frame error: ${exception.message}",
            cause = exception,
            frameType = context.frameType,
            frameSize = context.frameSize,
            maxFrameSize = context.maxFrameSize,
            frameOpcode = context.frameOpcode
        )
    }
    
    private fun createWebSocketBufferException(exception: Exception, context: WebSocketContext): WebSocketBufferException {
        return WebSocketBufferException(
            message = "WebSocket buffer error: ${exception.message}",
            cause = exception,
            operation = context.bufferOperation,
            bufferSize = context.bufferSize,
            maxBufferSize = context.maxBufferSize,
            availableMemory = context.availableMemory
        )
    }
    
    private fun createWebSocketCompressionException(exception: Exception, context: WebSocketContext): WebSocketCompressionException {
        return WebSocketCompressionException(
            message = "WebSocket compression error: ${exception.message}",
            cause = exception,
            operation = context.compressionOperation,
            compressionMethod = context.compressionMethod,
            originalSize = context.originalSize,
            compressedSize = context.compressedSize
        )
    }
    
    private fun createWebSocketSendException(exception: Exception, context: WebSocketContext): WebSocketSendException {
        return WebSocketSendException(
            message = "WebSocket send error: ${exception.message}",
            cause = exception,
            messageType = context.messageType,
            messageSize = context.messageSize,
            queueSize = context.queueSize,
            connectionState = context.currentState
        )
    }
    
    private fun createWebSocketCloseException(exception: Exception, context: WebSocketContext): WebSocketCloseException {
        return WebSocketCloseException(
            message = "WebSocket close error: ${exception.message}",
            cause = exception,
            closeCode = context.closeCode,
            closeReason = context.closeReason,
            timeoutMs = context.timeoutMs,
            forceClosed = context.forceClosed
        )
    }
    
    private fun createGenericConnectionException(exception: Exception, context: WebSocketContext): ConnectionException {
        return ConnectionException(
            message = "WebSocket error: ${exception.message}",
            cause = exception,
            errorCode = "WEBSOCKET_GENERIC_ERROR",
            severity = SyncException.Severity.ERROR,
            connectionInfo = mapOf(
                "host" to context.host,
                "port" to context.port,
                "state" to context.currentState
            )
        )
    }
    
    private fun createUnknownException(context: WebSocketContext): ConnectionException {
        unknownFailures.incrementAndGet()
        return ConnectionException(
            message = "Unknown WebSocket error",
            errorCode = "WEBSOCKET_UNKNOWN_ERROR",
            severity = SyncException.Severity.ERROR,
            connectionInfo = mapOf(
                "host" to context.host,
                "port" to context.port,
                "state" to context.currentState
            )
        )
    }
    
    /**
     * Gets classification statistics
     */
    fun getStatistics(): String {
        val total = totalClassifications.get()
        return buildString {
            appendLine("WebSocket Exception Classification Statistics:")
            appendLine("  Total classifications: $total")
            appendLine("  Handshake failures: ${handshakeFailures.get()}")
            appendLine("  Timeout failures: ${timeoutFailures.get()}")
            appendLine("  Connection refused: ${connectionRefusedFailures.get()}")
            appendLine("  Network failures: ${networkFailures.get()}")
            appendLine("  State failures: ${stateFailures.get()}")
            appendLine("  Unknown failures: ${unknownFailures.get()}")
            
            if (total > 0) {
                appendLine("  Success rate: ${String.format("%.1f", (total - unknownFailures.get()).toDouble() / total * 100)}%")
            }
        }
    }
    
    /**
     * Resets classification statistics
     */
    fun resetStatistics() {
        totalClassifications.set(0)
        handshakeFailures.set(0)
        timeoutFailures.set(0)
        connectionRefusedFailures.set(0)
        networkFailures.set(0)
        stateFailures.set(0)
        unknownFailures.set(0)
    }
}

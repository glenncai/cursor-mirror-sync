package com.glenncai.cursormirrorsync.core.managers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import com.glenncai.cursormirrorsync.core.exceptions.*
import org.java_websocket.exceptions.WebsocketNotConnectedException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import javax.net.ssl.SSLHandshakeException

class WebSocketExceptionClassifierTest {

    private lateinit var classifier: WebSocketExceptionClassifier

    @BeforeEach
    fun setUp() {
        classifier = WebSocketExceptionClassifier()
    }

    @Test
    fun `test SSL handshake exception classification`() {
        val exception = SSLHandshakeException("SSL handshake failed")
        val context = WebSocketContext(host = "localhost", port = 3000)
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is SslHandshakeException)
        assertEquals("SSL handshake failed: SSL handshake failed", result.message)
        assertEquals("unknown", (result as SslHandshakeException).certificateInfo)
    }

    @Test
    fun `test WebSocket not connected exception classification`() {
        val exception = WebsocketNotConnectedException()
        val context = WebSocketContext(
            currentState = "disconnected",
            targetState = "connected",
            operation = "send"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is WebSocketStateException)
        assertEquals("disconnected", (result as WebSocketStateException).currentState)
        assertEquals("connected", result.targetState)
        assertEquals("send", result.operation)
    }

    @Test
    fun `test connection refused exception classification`() {
        val exception = ConnectException("Connection refused")
        val context = WebSocketContext(host = "localhost", port = 3000, retryCount = 2)

        val result = classifier.classifyException(exception, context)

        assertTrue(result is ConnectionRefusedException)
        assertEquals("localhost", (result as ConnectionRefusedException).host)
        assertEquals(3000, result.port)
        assertEquals(2, result.retryCount)
        assertEquals(true, result.connectionFileExists) // Default value
    }

    @Test
    fun `test connection refused exception classification when VSCode not running`() {
        val exception = ConnectException("Connection refused")
        val context = WebSocketContext(
            host = "localhost",
            port = 3000,
            retryCount = 1,
            connectionFileExists = false
        )

        val result = classifier.classifyException(exception, context)

        assertTrue(result is ConnectionRefusedException)
        assertEquals("localhost", (result as ConnectionRefusedException).host)
        assertEquals(3000, result.port)
        assertEquals(1, result.retryCount)
        assertEquals(false, result.connectionFileExists)
        assertEquals(SyncException.Severity.INFO, result.severity) // Should be INFO when VSCode not running
        assertTrue(result.message?.contains("VSCode appears to be closed") == true)
    }

    @Test
    fun `test socket timeout exception classification`() {
        val exception = SocketTimeoutException("Read timed out")
        val context = WebSocketContext(
            host = "localhost",
            port = 3000,
            timeoutMs = 5000
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConnectionTimeoutException)
        assertEquals(5000, (result as ConnectionTimeoutException).timeoutMs)
        assertEquals("localhost", result.host)
        assertEquals(3000, result.port)
    }

    @Test
    fun `test unknown host exception classification`() {
        val exception = UnknownHostException("Unknown host")
        val context = WebSocketContext(host = "invalid-host")
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is NetworkUnreachableException)
        assertEquals("invalid-host", (result as NetworkUnreachableException).host)
    }

    @Test
    fun `test closed channel exception classification`() {
        val exception = ClosedChannelException()
        val context = WebSocketContext(
            closeCode = 1006,
            closeReason = "Abnormal closure",
            connectionDuration = 30000
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConnectionLostException)
        assertEquals(1006, (result as ConnectionLostException).closeCode)
        assertEquals("Abnormal closure", result.closeReason)
        assertEquals(30000, result.connectionDuration)
    }

    @Test
    fun `test handshake message classification`() {
        val exception = RuntimeException("WebSocket handshake failed")
        val context = WebSocketContext(
            handshakeStatus = 400,
            handshakeResponse = "Bad Request",
            actualProtocol = "http"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is WebSocketHandshakeException)
        assertEquals(400, (result as WebSocketHandshakeException).handshakeStatus)
        assertEquals("Bad Request", result.handshakeResponse)
        assertEquals("http", result.actualProtocol)
    }

    @Test
    fun `test frame error message classification`() {
        val exception = RuntimeException("Invalid frame payload")
        val context = WebSocketContext(
            frameType = "text",
            frameSize = 1024,
            maxFrameSize = 512,
            frameOpcode = 1
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is WebSocketFrameException)
        assertEquals("text", (result as WebSocketFrameException).frameType)
        assertEquals(1024, result.frameSize)
        assertEquals(512, result.maxFrameSize)
        assertEquals(1, result.frameOpcode)
    }

    @Test
    fun `test buffer error message classification`() {
        val exception = RuntimeException("Buffer overflow detected")
        val context = WebSocketContext(
            bufferOperation = "write",
            bufferSize = 2048,
            maxBufferSize = 1024,
            availableMemory = 512
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is WebSocketBufferException)
        assertEquals("write", (result as WebSocketBufferException).operation)
        assertEquals(2048, result.bufferSize)
        assertEquals(1024, result.maxBufferSize)
        assertEquals(512, result.availableMemory)
    }

    @Test
    fun `test compression error message classification`() {
        val exception = RuntimeException("Compression failed")
        val context = WebSocketContext(
            compressionOperation = "compress",
            compressionMethod = "deflate",
            originalSize = 1000,
            compressedSize = 800
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is WebSocketCompressionException)
        assertEquals("compress", (result as WebSocketCompressionException).operation)
        assertEquals("deflate", result.compressionMethod)
        assertEquals(1000, result.originalSize)
        assertEquals(800, result.compressedSize)
    }

    @Test
    fun `test send error message classification`() {
        val exception = RuntimeException("Failed to send message")
        val context = WebSocketContext(
            messageType = "text",
            messageSize = 256,
            queueSize = 10,
            currentState = "connected"
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is WebSocketSendException)
        assertEquals("text", (result as WebSocketSendException).messageType)
        assertEquals(256, result.messageSize)
        assertEquals(10, result.queueSize)
        assertEquals("connected", result.connectionState)
    }

    @Test
    fun `test close error message classification`() {
        val exception = RuntimeException("Failed to close connection")
        val context = WebSocketContext(
            closeCode = 1000,
            closeReason = "Normal closure",
            timeoutMs = 3000,
            forceClosed = true
        )
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is WebSocketCloseException)
        assertEquals(1000, (result as WebSocketCloseException).closeCode)
        assertEquals("Normal closure", result.closeReason)
        assertEquals(3000, result.timeoutMs)
        assertTrue(result.forceClosed)
    }

    @Test
    fun `test null exception classification`() {
        val context = WebSocketContext(host = "localhost", port = 3000)
        
        val result = classifier.classifyException(null, context)
        
        assertTrue(result is ConnectionException)
        assertEquals("Unknown WebSocket error", result.message)
        assertEquals("WEBSOCKET_UNKNOWN_ERROR", result.errorCode)
    }

    @Test
    fun `test generic exception classification`() {
        val exception = RuntimeException("Some unknown error")
        val context = WebSocketContext(host = "localhost", port = 3000, currentState = "connected")
        
        val result = classifier.classifyException(exception, context)
        
        assertTrue(result is ConnectionException)
        assertEquals("WebSocket error: Some unknown error", result.message)
        assertEquals("WEBSOCKET_GENERIC_ERROR", result.errorCode)
    }

    @Test
    fun `test statistics tracking`() {
        val context = WebSocketContext()
        
        // Classify several exceptions
        classifier.classifyException(SSLHandshakeException("SSL error"), context)
        classifier.classifyException(SocketTimeoutException("Timeout"), context)
        classifier.classifyException(ConnectException("Refused"), context)
        classifier.classifyException(RuntimeException("Unknown"), context)
        classifier.classifyException(null, context)
        
        val stats = classifier.getStatistics()
        
        assertTrue(stats.contains("Total classifications: 5"))
        assertTrue(stats.contains("Handshake failures: 1"))
        assertTrue(stats.contains("Timeout failures: 1"))
        assertTrue(stats.contains("Connection refused: 1"))
        assertTrue(stats.contains("Unknown failures: 2"))
    }

    @Test
    fun `test statistics reset`() {
        val context = WebSocketContext()
        
        // Classify some exceptions
        classifier.classifyException(SSLHandshakeException("SSL error"), context)
        classifier.classifyException(SocketTimeoutException("Timeout"), context)
        
        // Reset statistics
        classifier.resetStatistics()
        
        val stats = classifier.getStatistics()
        assertTrue(stats.contains("Total classifications: 0"))
        assertTrue(stats.contains("Handshake failures: 0"))
        assertTrue(stats.contains("Timeout failures: 0"))
    }
}

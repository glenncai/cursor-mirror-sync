package com.glenncai.cursormirrorsync.core.managers

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import com.glenncai.cursormirrorsync.config.ConnectionFileReader
import com.glenncai.cursormirrorsync.config.Settings
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.interfaces.ConnectionManager
import com.glenncai.cursormirrorsync.core.models.ConnectionInfo
import com.glenncai.cursormirrorsync.core.models.EditorState
import com.glenncai.cursormirrorsync.core.exceptions.*
import com.glenncai.cursormirrorsync.core.validators.ConfigurationValidator

/**
 * Implementation of ConnectionManager interface.
 * Manages WebSocket connections for synchronization with VSCode.
 * Handles connection lifecycle, reconnection logic, and message sending.
 */
class ConnectionManagerImpl(
    private val project: Project
) : ConnectionManager {

    private val log: Logger = Logger.getInstance(ConnectionManagerImpl::class.java)
    private val gson = Gson()
    private val connectionFileReader = ConnectionFileReader()

    // WebSocket connection state
    private var webSocket: WebSocketClient? = null
    private val isConnected = AtomicBoolean(false)
    private val isReconnecting = AtomicBoolean(false)
    private val autoReconnect = AtomicBoolean(true)

    // Smart reconnection strategy
    private val reconnectionStrategy = ReconnectionStrategy()
    private var currentConnectionInfo: ConnectionInfo? = null
    private var lastReconnectSignal: Long? = null
    private var connectionStartTime: Long = 0

    // Message batch processor for optimized sending
    private var messageBatchProcessor: MessageBatchProcessor? = null

    // WebSocket exception classifier for intelligent error handling
    private val exceptionClassifier = WebSocketExceptionClassifier()

    // Connection listener
    private var connectionListener: ConnectionManager.ConnectionListener? = null

    init {
        log.info("Initializing ConnectionManager for project: ${project.name}")
    }

    override fun connect(connectionInfo: ConnectionInfo) {
        if (!autoReconnect.get()) {
            isReconnecting.set(false)
            return
        }

        if (isReconnecting.get()) {
            return
        }

        isReconnecting.set(true)
        connectionListener?.onReconnecting()

        cleanup()

        currentConnectionInfo = connectionInfo
        val port = connectionInfo.port
        connectionStartTime = System.currentTimeMillis()

        try {
            webSocket = object : WebSocketClient(URI("${Constants.WEBSOCKET_PROTOCOL}://${Constants.LOCALHOST}:${port}${Constants.WEBSOCKET_PATH}")) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    val connectionDuration = System.currentTimeMillis() - connectionStartTime
                    log.info("Successfully connected to VSCode on port $port (took ${connectionDuration}ms)")
                    isConnected.set(true)
                    isReconnecting.set(false)

                    // Initialize message batch processor
                    initializeBatchProcessor()

                    // Record successful connection
                    reconnectionStrategy.recordConnectionAttempt(true, connectionDuration)
                    connectionListener?.onConnected()
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        connectionListener?.onMessage(it)
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    log.info("WebSocket connection closed. Code: $code, Reason: $reason, Remote: $remote")
                    isConnected.set(false)
                    connectionListener?.onDisconnected()

                    // Record failed connection if this was unexpected
                    if (autoReconnect.get()) {
                        val connectionDuration = System.currentTimeMillis() - connectionStartTime
                        reconnectionStrategy.recordConnectionAttempt(false, connectionDuration)

                        scheduleSmartReconnect()
                    } else {
                        isReconnecting.set(false)
                    }
                }

                override fun onError(ex: Exception?) {
                    // Create context for intelligent exception classification
                    val context = WebSocketContext(
                        host = "localhost",
                        port = port,
                        retryCount = 0, // Will be updated by reconnection strategy
                        timeoutMs = Constants.CONNECTION_TIMEOUT,
                        connectionDuration = System.currentTimeMillis() - connectionStartTime,
                        currentState = when {
                            isConnected.get() -> "connected"
                            isReconnecting.get() -> "reconnecting"
                            else -> "disconnected"
                        },
                        operation = "connect",
                        connectionFileExists = checkConnectionFileExists(),
                        projectPath = project.basePath
                    )

                    // Use intelligent exception classifier
                    val connectionException = exceptionClassifier.classifyException(ex, context)

                    // Log based on severity level to reduce noise
                    logConnectionException(connectionException)

                    isConnected.set(false)
                    connectionListener?.onError(connectionException)

                    if (autoReconnect.get()) {
                        val connectionDuration = System.currentTimeMillis() - connectionStartTime
                        reconnectionStrategy.recordConnectionAttempt(false, connectionDuration, connectionException)

                        scheduleSmartReconnect()
                    } else {
                        isReconnecting.set(false)
                    }
                }
            }

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    webSocket?.setConnectionLostTimeout(Constants.CONNECTION_TIMEOUT.toInt())
                    val connectResult = webSocket?.connectBlocking(Constants.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                    if (connectResult != true) {
                        log.warn("Failed to connect to VSCode on port ${connectionInfo.port}")

                        // If this was from auto-discovery, the port might be stale
                        // Clear the current connection info to force fallback to manual port
                        if (currentConnectionInfo == connectionInfo) {
                            currentConnectionInfo = null
                        }

                        if (autoReconnect.get()) {
                            val connectionDuration = System.currentTimeMillis() - connectionStartTime
                            reconnectionStrategy.recordConnectionAttempt(false, connectionDuration)
                            scheduleSmartReconnect()
                        } else {
                            isReconnecting.set(false)
                        }
                    }
                } catch (e: Exception) {
                    // Create context for connection attempt exception
                    val context = WebSocketContext(
                        host = "localhost",
                        port = connectionInfo.port,
                        retryCount = 0, // Will be updated by reconnection strategy
                        timeoutMs = Constants.CONNECTION_TIMEOUT,
                        connectionDuration = System.currentTimeMillis() - connectionStartTime,
                        currentState = "connecting",
                        operation = "connectBlocking",
                        connectionFileExists = checkConnectionFileExists(),
                        projectPath = project.basePath
                    )

                    val connectionException = exceptionClassifier.classifyException(e, context)

                    // Log based on severity level to reduce noise
                    logConnectionException(connectionException)
                    if (autoReconnect.get()) {
                        val connectionDuration = System.currentTimeMillis() - connectionStartTime
                        reconnectionStrategy.recordConnectionAttempt(false, connectionDuration, connectionException)
                        scheduleSmartReconnect()
                    } else {
                        isReconnecting.set(false)
                    }
                }
            }

        } catch (e: Exception) {
            // Create context for WebSocket creation exception
            val context = WebSocketContext(
                host = "localhost",
                port = connectionInfo.port,
                operation = "createWebSocket",
                currentState = "initializing",
                connectionFileExists = checkConnectionFileExists(),
                projectPath = project.basePath
            )

            val connectionException = exceptionClassifier.classifyException(e, context)

            // Log based on severity level to reduce noise
            logConnectionException(connectionException)
            isReconnecting.set(false)
        }
    }

    override fun disconnect() {
        autoReconnect.set(false)
        cleanup()
        isReconnecting.set(false)
    }

    /**
     * Initializes the message batch processor
     */
    private fun initializeBatchProcessor() {
        messageBatchProcessor?.dispose() // Clean up any existing processor
        messageBatchProcessor = MessageBatchProcessor { message ->
            sendMessageDirect(message)
        }
        log.debug("Message batch processor initialized")
    }

    override fun sendEditorState(state: EditorState): Boolean {
        val message = gson.toJson(state)
        return messageBatchProcessor?.queueEditorState(message) ?: sendMessageDirect(message)
    }

    override fun isConnected(): Boolean = isConnected.get()

    override fun setConnectionListener(listener: ConnectionManager.ConnectionListener) {
        this.connectionListener = listener
    }

    /**
     * Additional methods for enhanced connection management
     */
    override fun isReconnecting(): Boolean = isReconnecting.get()

    override fun isAutoReconnectEnabled(): Boolean = autoReconnect.get()

    override fun setAutoReconnect(enabled: Boolean) {
        autoReconnect.set(enabled)
        if (!enabled) {
            cleanup()
            isReconnecting.set(false)
        }
    }

    override fun forceReconnect() {
        reconnectionStrategy.reset()
        currentConnectionInfo?.let { connect(it) }
    }

    override fun restart() {
        cleanup()
        isReconnecting.set(false)
        if (autoReconnect.get()) {
            currentConnectionInfo?.let { connect(it) }
        }
    }

    override fun getCurrentConnectionInfo(): ConnectionInfo? = currentConnectionInfo

    /**
     * Attempts to establish a connection using connection discovery logic
     */
    fun attemptConnection() {
        // Discover connection information
        val connectionInfo = discoverConnection()
        if (connectionInfo == null) {
            log.error("No valid connection configuration found")
            return
        }

        currentConnectionInfo = connectionInfo
        connect(connectionInfo)
    }

    /**
     * Discovers connection information using multiple strategies
     */
    private fun discoverConnection(): ConnectionInfo? {
        val settings = Settings.getInstance(project)

        // Strategy 1: Try to read from .cursor-mirror-sync.json file if auto connect is enabled
        if (settings.isAutoConnectEnabled) {
            val connectionInfo = connectionFileReader.readConnectionFile(project.basePath)
            if (connectionInfo != null && connectionInfo.isValid()) {
                log.info("Found valid connection file with port ${connectionInfo.port}")

                val newReconnectSignal = connectionInfo.reconnectSignal
                if (newReconnectSignal != null && newReconnectSignal != lastReconnectSignal) {
                    lastReconnectSignal = newReconnectSignal

                    if (isConnected()) {
                        disconnect()
                        currentConnectionInfo = null
                    }
                }

                return connectionInfo
            }
        }

        // Strategy 2: Try to use the last known good connection if available
        currentConnectionInfo?.let { lastConnection ->
            if (isConnected()) {
                return lastConnection
            } else {
                currentConnectionInfo = null
            }
        }

        // Strategy 3: Fall back to manual port configuration
        val manualPort = settings.manualPort
        if (ConfigurationValidator.isValidPort(manualPort)) {
            return ConnectionInfo(
                port = manualPort,
                projectName = project.name,
                projectPath = project.basePath,
                createdAt = java.time.Instant.now().toString()
            )
        }

        log.warn("No valid connection configuration found for project: ${project.name}")
        return null
    }

    /**
     * Sends a raw message through the WebSocket connection with batching support
     */
    override fun sendMessage(message: String): Boolean {
        return messageBatchProcessor?.queueMessage(message) ?: sendMessageDirect(message)
    }

    /**
     * Sends a message immediately without batching
     */
    fun sendMessageImmediately(message: String): Boolean {
        return messageBatchProcessor?.sendImmediately(message) ?: sendMessageDirect(message)
    }

    /**
     * Sends a configuration sync message with high priority
     */
    fun sendConfigSync(message: String): Boolean {
        return messageBatchProcessor?.queueConfigSync(message) ?: sendMessageDirect(message)
    }

    /**
     * Direct message sending without batch processing (internal use)
     */
    private fun sendMessageDirect(message: String): Boolean {
        return webSocket?.let { client ->
            if (client.isOpen) {
                try {
                    client.send(message)
                    true
                } catch (e: Exception) {
                    // Create context for send exception
                    val context = WebSocketContext(
                        host = "localhost",
                        port = currentConnectionInfo?.port ?: 0,
                        operation = "send",
                        messageType = "text",
                        messageSize = message.length.toLong(),
                        currentState = if (client.isOpen) "connected" else "disconnected"
                    )

                    val sendException = exceptionClassifier.classifyException(e, context)
                    log.error("Failed to send WebSocket message: ${sendException.getFormattedMessage()}", sendException)
                    false
                }
            } else {
                false
            }
        } ?: false
    }

    /**
     * Schedules a smart reconnection attempt using intelligent strategy
     */
    private fun scheduleSmartReconnect() {
        if (!autoReconnect.get()) {
            return
        }

        val decision = reconnectionStrategy.shouldAttemptReconnection()

        when (decision) {
            ReconnectionStrategy.ReconnectionDecision.PROCEED -> {
                val delay = reconnectionStrategy.calculateReconnectionDelay()
                log.debug("Scheduling smart reconnection in ${delay}ms")

                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        Thread.sleep(delay)
                        if (autoReconnect.get() && !isConnected()) {
                            // Check if we can still attempt connection before reconnecting
                            if (canAttemptReconnection()) {
                                currentConnectionInfo?.let { connect(it) }
                            } else {
                                log.info("Skipping smart reconnection - VSCode not running or no valid connection info")
                                isReconnecting.set(false)
                            }
                        }
                    } catch (e: InterruptedException) {
                        log.warn("Smart reconnection delay interrupted: ${e.message}")
                    }
                }
            }

            ReconnectionStrategy.ReconnectionDecision.DELAY -> {
                val delay = Constants.MAX_RECONNECT_DELAY // Use max delay for forced delays
                log.debug("Delaying reconnection due to recent failures, waiting ${delay}ms")

                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        Thread.sleep(delay)
                        if (autoReconnect.get() && !isConnected()) {
                            scheduleSmartReconnect() // Retry decision making
                        }
                    } catch (e: InterruptedException) {
                        log.warn("Delayed reconnection interrupted: ${e.message}")
                    }
                }
            }

            ReconnectionStrategy.ReconnectionDecision.ABORT_TEMPORARY -> {
                log.info("Temporarily aborting reconnection due to network issues")
                isReconnecting.set(false)

                // Schedule a retry after a longer delay
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        Thread.sleep(Constants.MAX_RECONNECT_DELAY * 2) // Wait longer for network recovery
                        if (autoReconnect.get() && !isConnected()) {
                            // Check if we can still attempt connection before retrying
                            if (canAttemptReconnection()) {
                                scheduleSmartReconnect() // Retry decision making
                            } else {
                                log.info("Skipping network recovery retry - VSCode not running or no valid connection info")
                                isReconnecting.set(false)
                            }
                        }
                    } catch (e: InterruptedException) {
                        log.warn("Network recovery wait interrupted: ${e.message}")
                    }
                }
            }

            ReconnectionStrategy.ReconnectionDecision.ABORT_PERMANENT -> {
                log.warn("Permanently aborting reconnection due to too many failures")
                isReconnecting.set(false)
                autoReconnect.set(false)
            }
        }
    }

    /**
     * Cleans up WebSocket resources
     */
    private fun cleanup() {
        try {
            // Flush any pending messages before closing
            messageBatchProcessor?.flush()

            webSocket?.close()
        } catch (e: Exception) {
            log.warn("Error closing WebSocket: ${e.message}")
        } finally {
            webSocket = null
            isConnected.set(false)
        }
    }

    /**
     * Handles changes to the connection file
     * This method is called when the .cursor-mirror-sync.json file is modified
     */
    fun handleConnectionFileChange() {
        val newConnectionInfo = connectionFileReader.readConnectionFile(project.basePath)
        if (newConnectionInfo == null || !newConnectionInfo.isValid()) {
            return
        }

        val newReconnectSignal = newConnectionInfo.reconnectSignal
        if (newReconnectSignal != null && newReconnectSignal != lastReconnectSignal) {
            lastReconnectSignal = newReconnectSignal

            if (isConnected()) {
                currentConnectionInfo = null
                disconnect()

                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        Thread.sleep(500) // Wait for clean disconnection
                        attemptConnection()
                    } catch (e: InterruptedException) {
                        log.warn("Reconnection delay interrupted")
                    }
                }
            } else {
                attemptConnection()
            }
        }
    }

    /**
     * Gets connection information as a formatted string
     */
    fun getConnectionInfo(): String {
        val connectionInfo = currentConnectionInfo
        return if (connectionInfo != null) {
            buildString {
                appendLine("Connection Information:")
                appendLine("Port: ${connectionInfo.port}")
                appendLine("Project: ${connectionInfo.projectName}")
                appendLine("Path: ${connectionInfo.projectPath}")
                appendLine("Status: ${if (isConnected()) "Connected" else "Disconnected"}")
                if (connectionInfo.createdAt != null) {
                    appendLine("Created: ${connectionInfo.createdAt}")
                }
                appendLine()
                append(reconnectionStrategy.getDebugInfo())
                appendLine()
                messageBatchProcessor?.let { processor ->
                    append(processor.getStatistics())
                } ?: appendLine("Message batch processor: Not initialized")
            }
        } else {
            buildString {
                appendLine("No connection information available")
                appendLine()
                append(reconnectionStrategy.getDebugInfo())
                appendLine()
                messageBatchProcessor?.let { processor ->
                    append(processor.getStatistics())
                } ?: appendLine("Message batch processor: Not initialized")
            }
        }
    }

    /**
     * Handles deletion of the connection file
     * This method is called when the .cursor-mirror-sync.json file is deleted
     */
    fun handleConnectionFileDeleted() {
        log.info("Connection file deleted - stopping all reconnection attempts")

        // Stop auto-reconnect to prevent CONNECTION_REFUSED errors
        autoReconnect.set(false)

        // Clean up current connection
        cleanup()
        isReconnecting.set(false)

        // Clear connection info
        currentConnectionInfo = null
        lastReconnectSignal = null

        // Notify listener of disconnection
        connectionListener?.onDisconnected()
    }

    /**
     * Handles creation of the connection file
     * This method is called when the .cursor-mirror-sync.json file is created
     */
    fun handleConnectionFileCreated(connectionInfo: ConnectionInfo) {
        log.info("Connection file created - re-enabling auto-reconnect and attempting connection")

        // Re-enable auto-reconnect
        autoReconnect.set(true)

        // Reset reconnection strategy
        reconnectionStrategy.reset()

        // Update connection info and attempt connection
        currentConnectionInfo = connectionInfo
        connect(connectionInfo)
    }

    /**
     * Logs connection exception based on its severity level to reduce noise
     */
    private fun logConnectionException(connectionException: ConnectionException) {
        when (connectionException.severity) {
            SyncException.Severity.INFO -> log.info(connectionException.getFormattedMessage())
            SyncException.Severity.WARN -> log.warn(connectionException.getFormattedMessage())
            SyncException.Severity.ERROR, SyncException.Severity.FATAL ->
                log.error(connectionException.getFormattedMessage(), connectionException)
        }
    }

    /**
     * Checks if the connection file exists in the project directory
     */
    private fun checkConnectionFileExists(): Boolean {
        return try {
            val projectPath = project.basePath
            if (projectPath != null) {
                val connectionFile = java.io.File(projectPath, Constants.CONNECTION_FILE_NAME)
                connectionFile.exists()
            } else {
                false
            }
        } catch (e: Exception) {
            log.debug("Error checking connection file existence: ${e.message}")
            false
        }
    }

    /**
     * Checks if a reconnection attempt is likely to succeed
     * Returns true if either connection file exists or manual port is configured
     */
    private fun canAttemptReconnection(): Boolean {
        // Check if connection file exists
        val connectionFileExists = checkConnectionFileExists()
        if (connectionFileExists) {
            return true
        }

        // Check if manual port is configured using standard 3000-9999 range (matches VSCode)
        val settings = Settings.getInstance(project)
        val manualPort = settings.manualPort
        return ConfigurationValidator.isValidPort(manualPort)
    }

    /**
     * Gets detailed connection health information
     */
    fun getConnectionHealthInfo(): String {
        return buildString {
            appendLine("Connection Health Information:")
            appendLine("  WebSocket state: ${webSocket?.readyState ?: "None"}")
            appendLine("  Connection start time: ${if (connectionStartTime > 0) "${System.currentTimeMillis() - connectionStartTime}ms ago" else "Not started"}")
            appendLine("  Last reconnect signal: ${lastReconnectSignal ?: "None"}")
            appendLine("  Auto-reconnect enabled: ${autoReconnect.get()}")
            appendLine("  Currently reconnecting: ${isReconnecting.get()}")
            appendLine()
            append(reconnectionStrategy.getDebugInfo())
        }
    }

    /**
     * Manually triggers a connection health check
     */
    fun performHealthCheck(): Boolean {
        return if (isConnected()) {
            try {
                // Try to send a ping-like message to test connection health (immediate send)
                sendMessageImmediately("{\"type\":\"ping\",\"timestamp\":${System.currentTimeMillis()}}")
            } catch (e: Exception) {
                log.warn("Health check failed: ${e.message}")
                false
            }
        } else {
            false
        }
    }

    /**
     * Gets WebSocket exception classification statistics
     */
    fun getExceptionStatistics(): String {
        return exceptionClassifier.getStatistics()
    }

    /**
     * Resets WebSocket exception classification statistics
     */
    fun resetExceptionStatistics() {
        exceptionClassifier.resetStatistics()
    }

    /**
     * Disposes of all resources and cleans up connections
     */
    override fun dispose() {
        disconnect()

        // Dispose batch processor
        messageBatchProcessor?.dispose()
        messageBatchProcessor = null

        log.info("ConnectionManager disposed for project: ${project.name}")
    }
}
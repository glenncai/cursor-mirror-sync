package com.glenncai.cursormirrorsync.core

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.TimeUnit
import com.glenncai.cursormirrorsync.core.models.ConnectionInfo
import com.glenncai.cursormirrorsync.core.models.EditorState

/**
 * Manages WebSocket connections for synchronization with VSCode.
 * Handles connection lifecycle, reconnection logic, and message sending.
 */
class WebSocketManager(private val project: Project) {

    private val log: Logger = Logger.getInstance(WebSocketManager::class.java)
    private val gson = Gson()

    // WebSocket connection state
    private var webSocket: WebSocketClient? = null
    private var isConnected = false
    private var isReconnecting = false
    private var autoReconnect = true

    // Reconnection logic
    private var reconnectAttempts = Constants.INITIAL_RECONNECT_ATTEMPTS
    private var currentReconnectDelay = Constants.INITIAL_RECONNECT_DELAY
    private var currentConnectionInfo: ConnectionInfo? = null

    // Callback interfaces
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onReconnecting()
        fun onMessage(message: String)
        fun onError(exception: Exception?)
    }

    private var connectionListener: ConnectionListener? = null

    fun setConnectionListener(listener: ConnectionListener) {
        this.connectionListener = listener
    }

    fun isConnected(): Boolean = isConnected
    fun isReconnecting(): Boolean = isReconnecting
    fun isAutoReconnectEnabled(): Boolean = autoReconnect

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        if (!enabled) {
            cleanup()
            isReconnecting = false
        }
    }

    fun connect(connectionInfo: ConnectionInfo) {
        if (!autoReconnect) {
            isReconnecting = false
            return
        }


        if (isReconnecting) {
            return
        }

        isReconnecting = true
        connectionListener?.onReconnecting()


        cleanup()

        currentConnectionInfo = connectionInfo
        val port = connectionInfo.port

        try {

            webSocket = object : WebSocketClient(URI("${Constants.WEBSOCKET_PROTOCOL}://${Constants.LOCALHOST}:${port}${Constants.WEBSOCKET_PATH}")) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    log.info("Successfully connected to VSCode on port $port")
                    isConnected = true
                    isReconnecting = false

                    reconnectAttempts = Constants.INITIAL_RECONNECT_ATTEMPTS
                    currentReconnectDelay = Constants.INITIAL_RECONNECT_DELAY
                    connectionListener?.onConnected()
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        connectionListener?.onMessage(it)
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    log.info("WebSocket connection closed. Code: $code, Reason: $reason, Remote: $remote")
                    isConnected = false
                    connectionListener?.onDisconnected()
                    
                    if (autoReconnect && reconnectAttempts < Constants.MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect()
                    } else {
                        isReconnecting = false
                        if (reconnectAttempts >= Constants.MAX_RECONNECT_ATTEMPTS) {
                            log.warn("Max reconnection attempts reached. Stopping auto-reconnect.")
                        }
                    }
                }

                override fun onError(ex: Exception?) {
                    log.error("WebSocket error occurred", ex)
                    isConnected = false
                    connectionListener?.onError(ex)
                    
                    if (autoReconnect && reconnectAttempts < Constants.MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect()
                    } else {
                        isReconnecting = false
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

                        if (autoReconnect && reconnectAttempts < Constants.MAX_RECONNECT_ATTEMPTS) {
                            scheduleReconnect()
                        } else {
                            isReconnecting = false
                        }
                    }
                } catch (e: Exception) {
                    log.error("Exception during connection attempt", e)
                    if (autoReconnect && reconnectAttempts < Constants.MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect()
                    } else {
                        isReconnecting = false
                    }
                }
            }

        } catch (e: Exception) {
            log.error("Failed to create WebSocket connection", e)
            isReconnecting = false
        }
    }

    fun sendMessage(message: String): Boolean {
        return webSocket?.let { client ->
            if (client.isOpen) {
                try {
                    client.send(message)
                    true
                } catch (e: Exception) {
                    log.error("Failed to send WebSocket message", e)
                    false
                }
            } else {
                false
            }
        } ?: false
    }

    fun sendEditorState(state: EditorState): Boolean {
        return sendMessage(gson.toJson(state))
    }

    fun forceReconnect() {
        reconnectAttempts = Constants.INITIAL_RECONNECT_ATTEMPTS
        currentReconnectDelay = Constants.INITIAL_RECONNECT_DELAY
        currentConnectionInfo?.let { connect(it) }
    }

    fun disconnect() {
        autoReconnect = false
        cleanup()
        isReconnecting = false
    }

    fun restart() {
        cleanup()
        isReconnecting = false
        if (autoReconnect) {
            currentConnectionInfo?.let { connect(it) }
        }
    }

    private fun scheduleReconnect() {
        if (!autoReconnect) {
            return
        }

        reconnectAttempts++

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Thread.sleep(currentReconnectDelay)
                if (autoReconnect && !isConnected) {
                    currentConnectionInfo?.let { connect(it) }
                }
            } catch (e: InterruptedException) {
                log.warn("Reconnection delay interrupted: ${e.message}")
            }
        }

        // Exponential backoff with maximum delay
        currentReconnectDelay = (currentReconnectDelay * Constants.RECONNECT_DELAY_MULTIPLIER).toLong()
            .coerceAtMost(Constants.MAX_RECONNECT_DELAY)
    }

    private fun cleanup() {
        try {
            webSocket?.close()
        } catch (e: Exception) {
            log.warn("Error closing WebSocket: ${e.message}")
        } finally {
            webSocket = null
            isConnected = false
        }
    }

    fun getCurrentConnectionInfo(): ConnectionInfo? = currentConnectionInfo
}

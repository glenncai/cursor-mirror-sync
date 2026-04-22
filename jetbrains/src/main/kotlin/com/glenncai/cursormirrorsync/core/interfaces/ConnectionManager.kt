package com.glenncai.cursormirrorsync.core.interfaces

import com.glenncai.cursormirrorsync.core.models.ConnectionInfo
import com.glenncai.cursormirrorsync.core.exceptions.SyncException
import com.glenncai.cursormirrorsync.core.models.EditorState

/**
 * Interface for managing WebSocket connections with VSCode.
 * Handles connection lifecycle, reconnection logic, and message transmission.
 */
interface ConnectionManager {

    /**
     * Establishes a connection using the provided connection information.
     *
     * @param connectionInfo The connection details including port and project info
     */
    fun connect(connectionInfo: ConnectionInfo)

    /**
     * Disconnects from the current WebSocket connection.
     * This will stop auto-reconnection attempts.
     */
    fun disconnect()

    /**
     * Forces a reconnection attempt, resetting the reconnection state.
     * This is useful for manual reconnection triggers.
     */
    fun forceReconnect()

    /**
     * Restarts the connection manager, cleaning up current state.
     * This will attempt to reconnect if auto-reconnect is enabled.
     */
    fun restart()

    /**
     * Sends an editor state message to the connected VSCode instance.
     *
     * @param state The editor state to send
     * @return true if the message was sent successfully, false otherwise
     */
    fun sendEditorState(state: EditorState): Boolean

    /**
     * Sends a raw message to the connected VSCode instance.
     *
     * @param message The message to send
     * @return true if the message was sent successfully, false otherwise
     */
    fun sendMessage(message: String): Boolean

    /**
     * Checks if the connection is currently established.
     *
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean

    /**
     * Checks if the connection manager is currently attempting to reconnect.
     *
     * @return true if reconnecting, false otherwise
     */
    fun isReconnecting(): Boolean

    /**
     * Checks if auto-reconnection is enabled.
     *
     * @return true if auto-reconnect is enabled, false otherwise
     */
    fun isAutoReconnectEnabled(): Boolean

    /**
     * Sets the auto-reconnection behavior.
     *
     * @param enabled true to enable auto-reconnect, false to disable
     */
    fun setAutoReconnect(enabled: Boolean)

    /**
     * Gets the current connection information.
     *
     * @return The current connection info, or null if not connected
     */
    fun getCurrentConnectionInfo(): ConnectionInfo?

    /**
     * Sets the connection listener to receive connection events.
     *
     * @param listener The listener to receive connection events
     */
    fun setConnectionListener(listener: ConnectionListener)

    /**
     * Interface for receiving connection events.
     */
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onReconnecting()
        fun onMessage(message: String)
        fun onError(exception: SyncException?)
    }

    /**
     * Disposes of all resources and cleans up connections.
     * This method should be called when the manager is no longer needed.
     */
    fun dispose()
}
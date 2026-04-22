package com.glenncai.cursormirrorsync.core.interfaces

/**
 * Interface for the main synchronization service that coordinates between IDEs.
 * 
 * This interface defines the contract for the core synchronization service that orchestrates
 * cursor position and text selection synchronization between VSCode and JetBrains IDEs.
 * It provides a clean abstraction layer for connection management, status monitoring,
 * and resource lifecycle management.
 * 
 * The service acts as a coordinator between specialized components:
 * - EditorSyncManager: Handles editor state synchronization
 * - ConnectionManager: Manages WebSocket connections
 * - MessageHandler: Processes incoming messages
 * - FileWatcherService: Monitors connection file changes
 * 
 * @since 1.0.0
 */
interface ISyncService {

    // Connection State Management
    
    /**
     * Checks if the service is currently connected to the remote IDE.
     * 
     * @return true if connected to VSCode, false otherwise
     */
    fun isConnected(): Boolean

    /**
     * Checks if the service is currently attempting to reconnect.
     * 
     * @return true if reconnection is in progress, false otherwise
     */
    fun isReconnecting(): Boolean

    /**
     * Checks if automatic reconnection is enabled.
     * 
     * @return true if auto-reconnect is enabled, false otherwise
     */
    fun isAutoReconnectEnabled(): Boolean

    // Connection Control Operations
    
    /**
     * Toggles the automatic reconnection feature.
     * 
     * When enabled, the service will automatically attempt to reconnect
     * when the connection is lost. When disabled, manual intervention
     * is required to re-establish the connection.
     */
    fun toggleAutoReconnect()

    /**
     * Forces an immediate reconnection attempt.
     * 
     * This method resets the reconnection state and attempts to establish
     * a new connection immediately, regardless of the current connection status.
     */
    fun forceReconnect()

    /**
     * Disconnects from the remote IDE and stops auto-reconnection.
     * 
     * This method cleanly closes the current connection and disables
     * automatic reconnection attempts.
     */
    fun disconnect()

    /**
     * Restarts the connection by disconnecting and reconnecting.
     * 
     * This method performs a clean restart of the connection, which can
     * help resolve connection issues or apply configuration changes.
     */
    fun restartConnection()

    /**
     * Manually initiates a connection attempt.
     * 
     * This method enables auto-reconnect and attempts to establish a connection.
     * It's typically used when the user wants to manually start the synchronization.
     */
    fun manualConnect()

    /**
     * Manually disconnects from the remote IDE.
     * 
     * This method disconnects from the remote IDE but keeps the service
     * in a state where it can be reconnected later.
     */
    fun manualDisconnect()

    // Status and Information
    
    /**
     * Retrieves detailed connection information.
     * 
     * @return A string containing connection details such as port, status,
     *         and other relevant connection information
     */
    fun getConnectionInfo(): String

    // Resource Management
    
    /**
     * Disposes of all resources and cleans up the service.
     * 
     * This method should be called when the service is no longer needed.
     * It ensures proper cleanup of all resources including:
     * - WebSocket connections
     * - File watchers
     * - Editor listeners
     * - Status bar widgets
     * - Internal service components
     * 
     * After calling this method, the service should not be used anymore.
     */
    fun dispose()
}

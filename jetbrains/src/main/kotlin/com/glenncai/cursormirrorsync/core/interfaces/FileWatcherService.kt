package com.glenncai.cursormirrorsync.core.interfaces

import com.glenncai.cursormirrorsync.core.models.ConnectionInfo

/**
 * Interface for monitoring file system changes.
 * Specifically handles monitoring of .cursor-mirror-sync.json files for connection updates.
 */
interface FileWatcherService {

    /**
     * Starts monitoring the connection file for changes.
     * This will set up file system watchers for the .cursor-mirror-sync.json file.
     *
     * @param projectPath The project root path where the connection file is located
     */
    fun startWatching(projectPath: String)

    /**
     * Stops monitoring file changes and cleans up watchers.
     */
    fun stopWatching()

    /**
     * Checks if the file watcher is currently active.
     *
     * @return true if watching for file changes, false otherwise
     */
    fun isWatching(): Boolean

    /**
     * Manually triggers a check of the connection file.
     * This is useful for immediate updates without waiting for file system events.
     *
     * @param projectPath The project root path to check
     * @return The connection info if found and valid, null otherwise
     */
    fun checkConnectionFile(projectPath: String): ConnectionInfo?

    /**
     * Sets the file change listener to receive file update events.
     *
     * @param listener The listener to receive file change events
     */
    fun setFileChangeListener(listener: FileChangeListener)

    /**
     * Interface for receiving file change events.
     */
    interface FileChangeListener {
        /**
         * Called when the connection file has been modified.
         *
         * @param connectionInfo The new connection information, or null if invalid
         */
        fun onConnectionFileChanged(connectionInfo: ConnectionInfo?)

        /**
         * Called when the connection file has been deleted.
         * This indicates that VSCode has been closed or the file was manually removed.
         */
        fun onConnectionFileDeleted()

        /**
         * Called when a new connection file has been created.
         * This indicates that VSCode has been started and is ready for connections.
         *
         * @param connectionInfo The new connection information
         */
        fun onConnectionFileCreated(connectionInfo: ConnectionInfo?)

        /**
         * Called when there's an error reading or processing the connection file.
         *
         * @param error The error that occurred
         */
        fun onFileError(error: Exception)
    }

    /**
     * Disposes of all resources and cleans up file watchers.
     * This method should be called when the service is no longer needed.
     */
    fun dispose()
}
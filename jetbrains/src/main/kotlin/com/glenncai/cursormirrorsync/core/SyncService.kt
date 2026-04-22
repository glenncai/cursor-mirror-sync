package com.glenncai.cursormirrorsync.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.glenncai.cursormirrorsync.core.interfaces.ConnectionManager
import com.glenncai.cursormirrorsync.core.interfaces.EditorSyncManager
import com.glenncai.cursormirrorsync.core.interfaces.FileWatcherService
import com.glenncai.cursormirrorsync.core.interfaces.ISyncService
import com.glenncai.cursormirrorsync.core.interfaces.MessageHandler
import com.glenncai.cursormirrorsync.core.exceptions.SyncException
import com.glenncai.cursormirrorsync.core.managers.ConnectionManagerImpl
import com.glenncai.cursormirrorsync.core.managers.EditorSyncManagerImpl
import com.glenncai.cursormirrorsync.core.managers.FileWatcherServiceImpl
import com.glenncai.cursormirrorsync.core.managers.ListenerManager
import com.glenncai.cursormirrorsync.core.managers.MessageHandlerImpl
import com.glenncai.cursormirrorsync.core.models.ConfigSyncMessage
import com.glenncai.cursormirrorsync.core.models.EditorState
import com.glenncai.cursormirrorsync.ui.NotificationManager
import com.glenncai.cursormirrorsync.config.Settings
import com.glenncai.cursormirrorsync.core.validators.ConfigurationValidator

/**
 * Refactored SyncService as a coordinator that orchestrates specialized service components.
 * This service acts as the main entry point and coordinates between:
 * - EditorSyncManager: Handles editor state synchronization
 * - ConnectionManager: Manages WebSocket connections
 * - MessageHandler: Processes incoming messages
 * - FileWatcherService: Monitors connection file changes
 */
@Service(Service.Level.PROJECT)
class SyncService(private val project: Project) :
    ISyncService,
    ConnectionManager.ConnectionListener,
    MessageHandler.MessageListener,
    FileWatcherService.FileChangeListener {

    private val log: Logger = Logger.getInstance(SyncService::class.java)

    // Service components
    private val listenerManager: ListenerManager = ListenerManager(project)
    private val editorSyncManager: EditorSyncManager = EditorSyncManagerImpl(project, listenerManager)
    private val connectionManager: ConnectionManager = ConnectionManagerImpl(project)
    private val messageHandler: MessageHandler = MessageHandlerImpl(project)
    private val fileWatcherService: FileWatcherService = FileWatcherServiceImpl(project, listenerManager)
    private val notificationManager: NotificationManager = NotificationManager(project)

    init {
        log.info("Initializing SyncService for project: ${project.name}")

        // Setup service components
        setupServiceComponents()

        // Setup file editor listener through ListenerManager
        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    handleEditorChange(event.newEditor)
                }
            }
        )

        // Register the MessageBus connection with ListenerManager
        listenerManager.registerMessageBusConnection("fileEditorManager", messageBusConnection)

        // Start services
        startServices()
    }

    /**
     * Setup service components and their callbacks
     */
    private fun setupServiceComponents() {
        // Setup EditorSyncManager
        (editorSyncManager as EditorSyncManagerImpl).apply {
            setStateUpdateCallback { state -> connectionManager.sendEditorState(state) }
            setActivityCallback { /* Activity state management if needed */ }
        }

        // Setup ConnectionManager
        connectionManager.setConnectionListener(this)

        // Setup MessageHandler
        messageHandler.setMessageListener(this)
        (messageHandler as MessageHandlerImpl).apply {
            setStateUpdateCallback {
                // Trigger state update when config changes
                triggerCurrentEditorStateUpdate()
            }
        }

        // Setup FileWatcherService
        fileWatcherService.setFileChangeListener(this)
    }

    /**
     * Start all services
     */
    private fun startServices() {
        // Start connection attempt only if it's likely to succeed
        if (canAttemptConnection()) {
            (connectionManager as ConnectionManagerImpl).attemptConnection()
        } else {
            log.info("Skipping initial connection attempt - VSCode not running or no manual port configured")
        }

        // Start file watching
        project.basePath?.let { basePath ->
            fileWatcherService.startWatching(basePath)
        }
    }

    // ConnectionManager.ConnectionListener implementation
    override fun onConnected() {
        log.info("WebSocket connected successfully")
        notifySyncStateChanged()
        triggerCurrentEditorStateUpdate()
    }

    override fun onDisconnected() {
        log.info("WebSocket disconnected")
        notifySyncStateChanged()
    }

    override fun onReconnecting() {
        log.info("WebSocket reconnecting")
        notifySyncStateChanged()
    }

    override fun onMessage(message: String) {
        messageHandler.handleMessage(message)
    }

    override fun onError(exception: SyncException?) {
        log.error("WebSocket error: ${exception?.message}", exception)
    }

    // MessageHandler.MessageListener implementation
    override fun onEditorStateReceived(state: EditorState) {
        editorSyncManager.applyIncomingState(state)
    }

    override fun onConfigSyncReceived(configMessage: ConfigSyncMessage) {
        // Config sync is handled by MessageHandler internally
        log.debug("Config sync received: ${configMessage.enableSelectionSync}")
    }

    override fun onMessageError(message: String, error: Exception) {
        log.error("Message processing error: ${error.message}", error)
    }

    // FileWatcherService.FileChangeListener implementation
    override fun onConnectionFileChanged(connectionInfo: com.glenncai.cursormirrorsync.core.models.ConnectionInfo?) {
        if (connectionInfo != null) {
            log.info("Connection file changed, attempting reconnection")
            (connectionManager as ConnectionManagerImpl).handleConnectionFileChange()
        }
    }

    override fun onConnectionFileDeleted() {
        log.info("Connection file deleted - stopping reconnection attempts")
        // Stop auto-reconnect and update status
        (connectionManager as ConnectionManagerImpl).handleConnectionFileDeleted()
        notifySyncStateChanged()
    }

    override fun onConnectionFileCreated(connectionInfo: com.glenncai.cursormirrorsync.core.models.ConnectionInfo?) {
        if (connectionInfo != null) {
            log.info("Connection file created - attempting new connection")
            // Re-enable auto-reconnect and attempt connection
            (connectionManager as ConnectionManagerImpl).handleConnectionFileCreated(connectionInfo)
            notifySyncStateChanged()
        }
    }

    override fun onFileError(error: Exception) {
        log.error("File watcher error: ${error.message}", error)
    }

    /**
     * Triggers current editor state update
     */
    private fun triggerCurrentEditorStateUpdate() {
        ApplicationManager.getApplication().runReadAction {
            val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor
            val file = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            if (editor != null && file != null) {
                editorSyncManager.updateStateFromEditor(editor, file)
            }
        }
    }

    /**
     * Handles editor change events
     */
    private fun handleEditorChange(newEditor: com.intellij.openapi.fileEditor.FileEditor?) {
        if (newEditor is TextEditor) {
            val editor = newEditor.editor
            val file = newEditor.file
            if (file != null) {
                editorSyncManager.handleEditorChange(editor, file)
            }
        }
    }

    /**
     * Publishes a sync state change on the project message bus. Any UI
     * subscriber (e.g. the status bar widget) reacts to the event without
     * this service needing a direct reference to the UI component.
     */
    private fun notifySyncStateChanged() {
        if (project.isDisposed) return
        try {
            project.messageBus.syncPublisher(SyncStateTopic.TOPIC).onSyncStateChanged()
        } catch (e: Exception) {
            log.warn("Failed to publish sync state change: ${e.message}")
        }
    }

    // Public API methods for external access
    override fun isConnected(): Boolean = connectionManager.isConnected()
    override fun isReconnecting(): Boolean = (connectionManager as ConnectionManagerImpl).isReconnecting()
    override fun isAutoReconnectEnabled(): Boolean = (connectionManager as ConnectionManagerImpl).isAutoReconnectEnabled()

    override fun toggleAutoReconnect() {
        val connectionManagerImpl = connectionManager as ConnectionManagerImpl
        val newValue = !connectionManagerImpl.isAutoReconnectEnabled()
        connectionManagerImpl.setAutoReconnect(newValue)

        if (newValue && !connectionManager.isConnected()) {
            // Check if we can actually connect before attempting
            if (canAttemptConnection()) {
                connectionManagerImpl.attemptConnection()
            } else {
                showConnectionUnavailableNotification()
            }
        }

        notifySyncStateChanged()
    }

    override fun forceReconnect() {
        // Check if we can actually connect before attempting
        if (canAttemptConnection()) {
            (connectionManager as ConnectionManagerImpl).forceReconnect()
        } else {
            showConnectionUnavailableNotification()
        }
    }

    override fun disconnect() {
        connectionManager.disconnect()
        notifySyncStateChanged()
    }

    override fun getConnectionInfo(): String {
        return (connectionManager as ConnectionManagerImpl).getConnectionInfo()
    }

    override fun restartConnection() {
        (connectionManager as ConnectionManagerImpl).restart()
        notifySyncStateChanged()
    }

    override fun manualConnect() {
        (connectionManager as ConnectionManagerImpl).setAutoReconnect(true)

        // Check if we can actually connect before attempting
        if (canAttemptConnection()) {
            connectionManager.attemptConnection()
        } else {
            showConnectionUnavailableNotification()
        }

        notifySyncStateChanged()
    }

    override fun manualDisconnect() {
        connectionManager.disconnect()
        notifySyncStateChanged()
    }

    /**
     * Checks if a connection attempt is likely to succeed
     * Returns true if either connection file exists or manual port is configured
     */
    private fun canAttemptConnection(): Boolean {
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
     * Shows a user-friendly notification when connection is not available
     */
    private fun showConnectionUnavailableNotification() {
        notificationManager.showInfoNotification(
            "VSCode is not running. Please start VSCode to enable synchronization."
        )
    }

    /**
     * Clean up resources when the service is disposed
     */
    override fun dispose() {
        log.info("Disposing SyncService for project: ${project.name}")

        try {
            // Dispose ListenerManager first to ensure all listeners are properly deregistered.
            // StatusBarWidget lifecycle (and therefore its message-bus subscription) is
            // owned by the IntelliJ platform + StatusBarWidgetFactory, which tears itself
            // down via Disposer when the project closes.
            listenerManager.dispose()

            // Dispose core services
            editorSyncManager.dispose()
            connectionManager.disconnect()
            messageHandler.dispose()
            fileWatcherService.dispose()

            log.info("SyncService disposed successfully for project: ${project.name}")

        } catch (e: Exception) {
            log.error("Error disposing SyncService for project: ${project.name}", e)
        }
    }
}
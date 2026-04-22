package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import java.io.File
import com.glenncai.cursormirrorsync.core.Constants
import java.util.concurrent.atomic.AtomicBoolean
import com.glenncai.cursormirrorsync.config.ConnectionFileReader
import com.glenncai.cursormirrorsync.core.interfaces.FileWatcherService
import com.glenncai.cursormirrorsync.core.models.ConnectionInfo

/**
 * Implementation of FileWatcherService interface.
 * Monitors file system changes for .cursor-mirror-sync.json files.
 * Handles connection file updates and notifies listeners of changes.
 */
class FileWatcherServiceImpl(
    private val project: Project,
    private val listenerManager: ListenerManager? = null
) : FileWatcherService {

    private val log: Logger = Logger.getInstance(FileWatcherServiceImpl::class.java)
    private val connectionFileReader = ConnectionFileReader()

    // File watching state
    private var connectionFileWatcher: BulkFileListener? = null
    private val isWatching = AtomicBoolean(false)
    private var messageBusConnectionId: String? = null

    // File change listener
    private var fileChangeListener: FileWatcherService.FileChangeListener? = null

    // File processing delay to ensure write completion
    private val fileProcessingDelayMs = Constants.FILE_PROCESSING_DELAY_MS

    init {
        log.info("Initializing FileWatcherService for project: ${project.name}")
    }

    override fun startWatching(projectPath: String) {
        if (isWatching.get()) {
            log.debug("File watcher is already active")
            return
        }

        val connectionFilePath = File(projectPath, ".cursor-mirror-sync.json").absolutePath
        log.info("Setting up file watcher for: $connectionFilePath")

        try {
            connectionFileWatcher = object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    events.forEach { event ->
                        when (event) {
                            is VFileContentChangeEvent -> {
                                val filePath = event.file.path
                                if (filePath.endsWith(".cursor-mirror-sync.json")) {
                                    log.debug("Connection file changed: $filePath")
                                    // Delay slightly to ensure file write is complete
                                    ApplicationManager.getApplication().executeOnPooledThread {
                                        try {
                                            Thread.sleep(fileProcessingDelayMs)
                                            handleConnectionFileChange(projectPath)
                                        } catch (e: InterruptedException) {
                                            log.warn("File watcher delay interrupted: ${e.message}")
                                        } catch (e: Exception) {
                                            log.error("Error processing file change: ${e.message}", e)
                                            fileChangeListener?.onFileError(e)
                                        }
                                    }
                                }
                            }
                            is VFileDeleteEvent -> {
                                val filePath = event.file.path
                                if (filePath.endsWith(".cursor-mirror-sync.json")) {
                                    log.info("Connection file deleted: $filePath")
                                    ApplicationManager.getApplication().executeOnPooledThread {
                                        try {
                                            handleConnectionFileDeleted()
                                        } catch (e: Exception) {
                                            log.error("Error processing file deletion: ${e.message}", e)
                                            fileChangeListener?.onFileError(e)
                                        }
                                    }
                                }
                            }
                            is VFileCreateEvent -> {
                                val filePath = event.file?.path
                                if (filePath != null && filePath.endsWith(".cursor-mirror-sync.json")) {
                                    log.info("Connection file created: $filePath")
                                    // Delay slightly to ensure file write is complete
                                    ApplicationManager.getApplication().executeOnPooledThread {
                                        try {
                                            Thread.sleep(fileProcessingDelayMs)
                                            handleConnectionFileCreated(projectPath)
                                        } catch (e: InterruptedException) {
                                            log.warn("File watcher delay interrupted: ${e.message}")
                                        } catch (e: Exception) {
                                            log.error("Error processing file creation: ${e.message}", e)
                                            fileChangeListener?.onFileError(e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Subscribe to VFS changes through ListenerManager if available
            if (listenerManager != null) {
                try {
                    val messageBusConnection = project.messageBus.connect()
                    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, connectionFileWatcher!!)
                    messageBusConnectionId = listenerManager.registerMessageBusConnection("fileWatcher", messageBusConnection)
                } catch (e: Exception) {
                    log.warn("Failed to register MessageBus connection through ListenerManager, falling back to direct connection: ${e.message}")
                    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, connectionFileWatcher!!)
                }
            } else {
                project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, connectionFileWatcher!!)
            }

            isWatching.set(true)
            log.info("File watcher started successfully")

        } catch (e: Exception) {
            log.error("Failed to start file watcher: ${e.message}", e)
            fileChangeListener?.onFileError(e)
        }
    }

    override fun stopWatching() {
        if (!isWatching.get()) {
            log.debug("File watcher is not active")
            return
        }

        try {
            // Deregister MessageBus connection through ListenerManager if available
            messageBusConnectionId?.let { connectionId ->
                listenerManager?.deregisterMessageBusConnection(connectionId)
                messageBusConnectionId = null
            }

            connectionFileWatcher = null
            isWatching.set(false)
            log.info("File watcher stopped successfully")
        } catch (e: Exception) {
            log.error("Error stopping file watcher: ${e.message}", e)
        }
    }

    override fun isWatching(): Boolean = isWatching.get()

    override fun checkConnectionFile(projectPath: String): ConnectionInfo? {
        return try {
            val connectionInfo = connectionFileReader.readConnectionFile(projectPath)
            if (connectionInfo != null && connectionInfo.isValid()) {
                log.debug("Found valid connection file: port ${connectionInfo.port}")
                connectionInfo
            } else {
                log.debug("No valid connection file found or file is invalid")
                null
            }
        } catch (e: Exception) {
            log.error("Error reading connection file: ${e.message}", e)
            fileChangeListener?.onFileError(e)
            null
        }
    }

    override fun setFileChangeListener(listener: FileWatcherService.FileChangeListener) {
        this.fileChangeListener = listener
    }

    override fun dispose() {
        stopWatching()
        fileChangeListener = null
        log.info("FileWatcherService disposed for project: ${project.name}")
    }

    /**
     * Handles changes to the connection file
     * This method is called when the .cursor-mirror-sync.json file is modified
     */
    private fun handleConnectionFileChange(projectPath: String) {
        try {
            val newConnectionInfo = connectionFileReader.readConnectionFile(projectPath)

            if (newConnectionInfo == null || !newConnectionInfo.isValid()) {
                log.debug("Connection file is invalid or not found")
                fileChangeListener?.onConnectionFileChanged(null)
                return
            }

            log.info("Connection file changed: port ${newConnectionInfo.port}, project ${newConnectionInfo.projectName}")
            fileChangeListener?.onConnectionFileChanged(newConnectionInfo)

        } catch (e: Exception) {
            log.error("Error handling connection file change: ${e.message}", e)
            fileChangeListener?.onFileError(e)
        }
    }

    /**
     * Handles deletion of the connection file
     * This method is called when the .cursor-mirror-sync.json file is deleted
     */
    private fun handleConnectionFileDeleted() {
        try {
            log.info("Connection file deleted - VSCode likely closed")
            fileChangeListener?.onConnectionFileDeleted()
        } catch (e: Exception) {
            log.error("Error handling connection file deletion: ${e.message}", e)
            fileChangeListener?.onFileError(e)
        }
    }

    /**
     * Handles creation of the connection file
     * This method is called when the .cursor-mirror-sync.json file is created
     */
    private fun handleConnectionFileCreated(projectPath: String) {
        try {
            val newConnectionInfo = connectionFileReader.readConnectionFile(projectPath)

            if (newConnectionInfo == null || !newConnectionInfo.isValid()) {
                log.debug("Newly created connection file is invalid")
                fileChangeListener?.onConnectionFileCreated(null)
                return
            }

            log.info("Connection file created: port ${newConnectionInfo.port}, project ${newConnectionInfo.projectName}")
            fileChangeListener?.onConnectionFileCreated(newConnectionInfo)

        } catch (e: Exception) {
            log.error("Error handling connection file creation: ${e.message}", e)
            fileChangeListener?.onFileError(e)
        }
    }
}
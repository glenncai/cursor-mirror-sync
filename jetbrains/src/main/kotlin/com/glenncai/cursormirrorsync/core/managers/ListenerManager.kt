package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified listener management system that prevents listener leaks and provides
 * centralized lifecycle management for all types of listeners in the plugin.
 * 
 * This manager provides:
 * - Centralized listener registration and deregistration
 * - Memory leak prevention through WeakReference tracking
 * - Automatic cleanup of disposed listeners
 * - Listener usage monitoring and statistics
 * - Thread-safe operations for concurrent access
 */
class ListenerManager(
    private val project: Project
) : Disposable {
    
    companion object {
        private val log: Logger = Logger.getInstance(ListenerManager::class.java)
    }
    
    // Listener tracking with WeakReference to prevent memory leaks
    private val editorListeners = ConcurrentHashMap<Int, EditorListenerInfo>()
    private val messageBusConnections = ConcurrentHashMap<String, MessageBusConnection>()
    private val fileListeners = ConcurrentHashMap<String, Any>()
    private val customListeners = ConcurrentHashMap<String, ListenerInfo>()
    
    // Statistics tracking
    private val totalListenersRegistered = AtomicLong(0)
    private val totalListenersDeregistered = AtomicLong(0)
    private val memoryLeaksDetected = AtomicLong(0)
    
    @Volatile
    private var isDisposed = false
    
    init {
        log.debug("ListenerManager initialized for project: ${project.name}")
    }
    
    /**
     * Registers editor listeners (CaretListener and SelectionListener) for an editor
     */
    fun registerEditorListeners(
        editor: Editor,
        caretListener: CaretListener,
        selectionListener: SelectionListener
    ): String {
        if (isDisposed) {
            throw IllegalStateException("ListenerManager has been disposed")
        }
        
        val editorId = editor.hashCode()
        val listenerId = generateListenerId("editor", editorId.toString())
        
        try {
            // Register listeners with the editor
            editor.caretModel.addCaretListener(caretListener)
            editor.selectionModel.addSelectionListener(selectionListener)
            
            // Track the listeners
            val listenerInfo = EditorListenerInfo(
                editorRef = WeakReference(editor),
                caretListener = caretListener,
                selectionListener = selectionListener,
                registrationTime = System.currentTimeMillis()
            )
            
            editorListeners[editorId] = listenerInfo
            totalListenersRegistered.incrementAndGet()
            
            log.debug("Registered editor listeners for editor: $editorId")
            return listenerId
            
        } catch (e: Exception) {
            log.error("Error registering editor listeners for editor: $editorId", e)
            throw e
        }
    }
    
    /**
     * Deregisters editor listeners for an editor
     */
    fun deregisterEditorListeners(editor: Editor): Boolean {
        val editorId = editor.hashCode()
        val listenerInfo = editorListeners.remove(editorId)
        
        return if (listenerInfo != null) {
            try {
                editor.caretModel.removeCaretListener(listenerInfo.caretListener)
                editor.selectionModel.removeSelectionListener(listenerInfo.selectionListener)
                
                totalListenersDeregistered.incrementAndGet()
                log.debug("Deregistered editor listeners for editor: $editorId")
                true
                
            } catch (e: Exception) {
                log.error("Error deregistering editor listeners for editor: $editorId", e)
                false
            }
        } else {
            log.debug("No listeners found for editor: $editorId")
            false
        }
    }
    
    /**
     * Registers a MessageBus connection
     */
    fun registerMessageBusConnection(connectionId: String, connection: MessageBusConnection): String {
        if (isDisposed) {
            throw IllegalStateException("ListenerManager has been disposed")
        }
        
        messageBusConnections[connectionId] = connection
        totalListenersRegistered.incrementAndGet()
        
        log.debug("Registered MessageBus connection: $connectionId")
        return connectionId
    }
    
    /**
     * Deregisters a MessageBus connection
     */
    fun deregisterMessageBusConnection(connectionId: String): Boolean {
        val connection = messageBusConnections.remove(connectionId)
        
        return if (connection != null) {
            try {
                connection.disconnect()
                totalListenersDeregistered.incrementAndGet()
                log.debug("Deregistered MessageBus connection: $connectionId")
                true
                
            } catch (e: Exception) {
                log.error("Error deregistering MessageBus connection: $connectionId", e)
                false
            }
        } else {
            log.debug("No MessageBus connection found: $connectionId")
            false
        }
    }
    
    /**
     * Registers a file listener (BulkFileListener, etc.)
     */
    fun registerFileListener(listenerId: String, listener: Any): String {
        if (isDisposed) {
            throw IllegalStateException("ListenerManager has been disposed")
        }
        
        fileListeners[listenerId] = listener
        totalListenersRegistered.incrementAndGet()
        
        log.debug("Registered file listener: $listenerId")
        return listenerId
    }
    
    /**
     * Deregisters a file listener
     */
    fun deregisterFileListener(listenerId: String): Boolean {
        val listener = fileListeners.remove(listenerId)
        
        return if (listener != null) {
            totalListenersDeregistered.incrementAndGet()
            log.debug("Deregistered file listener: $listenerId")
            true
        } else {
            log.debug("No file listener found: $listenerId")
            false
        }
    }
    
    /**
     * Registers a custom listener with cleanup callback
     */
    fun registerCustomListener(
        listenerId: String,
        listener: Any,
        cleanupCallback: () -> Unit
    ): String {
        if (isDisposed) {
            throw IllegalStateException("ListenerManager has been disposed")
        }
        
        val listenerInfo = ListenerInfo(
            listener = listener,
            cleanupCallback = cleanupCallback,
            registrationTime = System.currentTimeMillis()
        )
        
        customListeners[listenerId] = listenerInfo
        totalListenersRegistered.incrementAndGet()
        
        log.debug("Registered custom listener: $listenerId")
        return listenerId
    }
    
    /**
     * Deregisters a custom listener
     */
    fun deregisterCustomListener(listenerId: String): Boolean {
        val listenerInfo = customListeners.remove(listenerId)
        
        return if (listenerInfo != null) {
            try {
                listenerInfo.cleanupCallback()
                totalListenersDeregistered.incrementAndGet()
                log.debug("Deregistered custom listener: $listenerId")
                true
                
            } catch (e: Exception) {
                log.error("Error deregistering custom listener: $listenerId", e)
                false
            }
        } else {
            log.debug("No custom listener found: $listenerId")
            false
        }
    }
    
    /**
     * Performs cleanup of invalid listeners and detects memory leaks
     */
    fun performCleanup(): Int {
        if (isDisposed) {
            return 0
        }
        
        var cleanedCount = 0
        
        // Clean up invalid editor listeners
        val invalidEditorListeners = editorListeners.entries.filter { (_, info) ->
            val editor = info.editorRef.get()
            editor == null || editor.isDisposed
        }
        
        invalidEditorListeners.forEach { (editorId, info) ->
            editorListeners.remove(editorId)
            
            // Try to clean up if editor is still available
            info.editorRef.get()?.let { editor ->
                try {
                    editor.caretModel.removeCaretListener(info.caretListener)
                    editor.selectionModel.removeSelectionListener(info.selectionListener)
                } catch (e: Exception) {
                    log.warn("Error cleaning up editor listeners during cleanup: ${e.message}")
                }
            }
            
            cleanedCount++
            memoryLeaksDetected.incrementAndGet()
        }
        
        if (cleanedCount > 0) {
            log.debug("Cleaned up $cleanedCount invalid editor listeners")
        }
        
        return cleanedCount
    }
    
    /**
     * Gets comprehensive statistics about listener management
     */
    fun getStatistics(): ListenerManagerStats {
        val currentEditorListeners = editorListeners.values.count { it.editorRef.get() != null }
        
        return ListenerManagerStats(
            totalRegistered = totalListenersRegistered.get(),
            totalDeregistered = totalListenersDeregistered.get(),
            currentEditorListeners = currentEditorListeners,
            currentMessageBusConnections = messageBusConnections.size,
            currentFileListeners = fileListeners.size,
            currentCustomListeners = customListeners.size,
            memoryLeaksDetected = memoryLeaksDetected.get()
        )
    }
    
    /**
     * Gets debug information about the manager state
     */
    fun getDebugInfo(): String {
        val stats = getStatistics()
        
        return buildString {
            appendLine("=== ListenerManager Debug Info ===")
            appendLine("  Project: ${project.name}")
            appendLine("  Disposed: $isDisposed")
            appendLine("  Total Registered: ${stats.totalRegistered}")
            appendLine("  Total Deregistered: ${stats.totalDeregistered}")
            appendLine("  Current Editor Listeners: ${stats.currentEditorListeners}")
            appendLine("  Current MessageBus Connections: ${stats.currentMessageBusConnections}")
            appendLine("  Current File Listeners: ${stats.currentFileListeners}")
            appendLine("  Current Custom Listeners: ${stats.currentCustomListeners}")
            appendLine("  Memory Leaks Detected: ${stats.memoryLeaksDetected}")
            
            // Add detailed listener information
            if (editorListeners.isNotEmpty()) {
                appendLine("  Editor Listeners:")
                editorListeners.entries.take(5).forEach { (editorId, info) ->
                    val editor = info.editorRef.get()
                    val status = if (editor == null) "[DISPOSED/GC'd]" else "[ACTIVE]"
                    val age = System.currentTimeMillis() - info.registrationTime
                    appendLine("    Editor $editorId: $status (${age}ms old)")
                }
            }
            
            if (messageBusConnections.isNotEmpty()) {
                appendLine("  MessageBus Connections:")
                messageBusConnections.keys.take(5).forEach { connectionId ->
                    appendLine("    $connectionId")
                }
            }
        }
    }
    
    /**
     * Generates a unique listener ID
     */
    private fun generateListenerId(type: String, identifier: String): String {
        return "$type-$identifier-${System.currentTimeMillis()}"
    }
    
    override fun dispose() {
        if (isDisposed) {
            log.debug("ListenerManager already disposed")
            return
        }
        
        log.debug("Disposing ListenerManager for project: ${project.name}")
        isDisposed = true
        
        try {
            // Deregister all editor listeners
            val editorIds = editorListeners.keys.toList()
            editorIds.forEach { editorId ->
                val info = editorListeners[editorId]
                info?.editorRef?.get()?.let { editor ->
                    deregisterEditorListeners(editor)
                }
            }
            
            // Disconnect all MessageBus connections
            val connectionIds = messageBusConnections.keys.toList()
            connectionIds.forEach { connectionId ->
                deregisterMessageBusConnection(connectionId)
            }
            
            // Clean up custom listeners
            val customListenerIds = customListeners.keys.toList()
            customListenerIds.forEach { listenerId ->
                deregisterCustomListener(listenerId)
            }
            
            // Clear all collections
            editorListeners.clear()
            messageBusConnections.clear()
            fileListeners.clear()
            customListeners.clear()
            
            val finalStats = getStatistics()
            log.info("ListenerManager disposed. Final stats: $finalStats")
            
        } catch (e: Exception) {
            log.error("Error disposing ListenerManager: ${e.message}", e)
        }
    }
}

/**
 * Data class to hold editor listener information
 */
data class EditorListenerInfo(
    val editorRef: WeakReference<Editor>,
    val caretListener: CaretListener,
    val selectionListener: SelectionListener,
    val registrationTime: Long
)

/**
 * Data class to hold custom listener information
 */
data class ListenerInfo(
    val listener: Any,
    val cleanupCallback: () -> Unit,
    val registrationTime: Long
)

/**
 * Statistics data class for listener management
 */
data class ListenerManagerStats(
    val totalRegistered: Long,
    val totalDeregistered: Long,
    val currentEditorListeners: Int,
    val currentMessageBusConnections: Int,
    val currentFileListeners: Int,
    val currentCustomListeners: Int,
    val memoryLeaksDetected: Long
)

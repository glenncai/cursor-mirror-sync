package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * Editor lifecycle information with weak reference to prevent memory leaks
 */
data class EditorLifecycleInfo(
    val editorRef: WeakReference<Editor>,
    val file: VirtualFile?,
    val registrationTime: Long = System.currentTimeMillis(),
    val lastAccessTime: Long = System.currentTimeMillis(),
    var accessCount: Int = 0,
    var isActive: Boolean = true
) {
    fun updateAccess() {
        accessCount++
    }
    
    fun isEditorValid(): Boolean {
        val editor = editorRef.get()
        return editor != null && !editor.isDisposed
    }
    
    fun getEditor(): Editor? = editorRef.get()
}

/**
 * Statistics for editor lifecycle management
 */
data class EditorLifecycleStats(
    val totalRegistered: Long,
    val totalCleaned: Long,
    val currentActive: Int,
    val currentPending: Int,
    val memoryLeaksDetected: Long,
    val cleanupOperations: Long,
    val averageEditorLifetime: Double,
    val maxConcurrentEditors: Int
)

/**
 * Manages editor lifecycle to prevent memory leaks in registeredEditors collection.
 * Provides automatic cleanup, weak references, and lifecycle monitoring.
 */
class EditorLifecycleManager(
    private val project: Project,
    private val executorService: ScheduledExecutorService
) : Disposable {
    
    private val log: Logger = Logger.getInstance(EditorLifecycleManager::class.java)
    
    // Weak reference collections to prevent memory leaks
    private val registeredEditors = ConcurrentHashMap<Int, EditorLifecycleInfo>() // Editor hashCode -> Info
    private val pendingEditors = ConcurrentHashMap<Int, EditorLifecycleInfo>() // Editor hashCode -> Info
    
    // Cleanup callbacks
    private val cleanupCallbacks = mutableListOf<(Editor) -> Unit>()
    
    // Statistics tracking
    private val totalRegistered = AtomicLong(0)
    private val totalCleaned = AtomicLong(0)
    private val memoryLeaksDetected = AtomicLong(0)
    private val cleanupOperations = AtomicLong(0)
    private val maxConcurrentEditors = AtomicInteger(0)
    
    // Configuration
    private val maxEditorLifetime = 3600000L // 1 hour
    private val cleanupInterval = 30000L // 30 seconds
    private val maxConcurrentEditorsLimit = 50
    private val inactivityThreshold = 300000L // 5 minutes
    
    // Scheduled tasks
    private var cleanupTask: ScheduledFuture<*>? = null
    private var memoryMonitorTask: ScheduledFuture<*>? = null
    
    // Editor factory listener for automatic cleanup
    private val editorFactoryListener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            // Track new editors automatically
            val editor = event.editor
            if (editor.project == project) {
                log.debug("Editor created: ${editor.hashCode()}")
            }
        }
        
        override fun editorReleased(event: EditorFactoryEvent) {
            // Clean up when editor is released
            val editor = event.editor
            if (editor.project == project) {
                log.debug("Editor released: ${editor.hashCode()}")
                cleanupEditor(editor)
            }
        }
    }
    
    init {
        log.info("Initializing EditorLifecycleManager for project: ${project.name}")
        
        // Register editor factory listener
        EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, this)
        
        // Start periodic cleanup tasks
        startCleanupTasks()
    }
    
    /**
     * Registers an editor for lifecycle management
     */
    fun registerEditor(editor: Editor, file: VirtualFile? = null): Boolean {
        if (editor.isDisposed) {
            log.warn("Attempted to register disposed editor")
            return false
        }
        
        val editorId = editor.hashCode()
        val lifecycleInfo = EditorLifecycleInfo(
            editorRef = WeakReference(editor),
            file = file ?: FileDocumentManager.getInstance().getFile(editor.document)
        )
        
        val wasNew = registeredEditors.put(editorId, lifecycleInfo) == null
        if (wasNew) {
            totalRegistered.incrementAndGet()
            updateMaxConcurrentEditors()
            log.debug("Registered editor: $editorId, file: ${lifecycleInfo.file?.name}")
        }
        
        return wasNew
    }
    
    /**
     * Moves an editor to pending state
     */
    fun moveEditorToPending(editor: Editor): Boolean {
        val editorId = editor.hashCode()
        val lifecycleInfo = registeredEditors.remove(editorId)
        
        return if (lifecycleInfo != null && lifecycleInfo.isEditorValid()) {
            lifecycleInfo.isActive = false
            pendingEditors[editorId] = lifecycleInfo
            log.debug("Moved editor to pending: $editorId")
            true
        } else {
            false
        }
    }
    
    /**
     * Activates a pending editor
     */
    fun activatePendingEditor(editor: Editor): Boolean {
        val editorId = editor.hashCode()
        val lifecycleInfo = pendingEditors.remove(editorId)
        
        return if (lifecycleInfo != null && lifecycleInfo.isEditorValid()) {
            lifecycleInfo.isActive = true
            lifecycleInfo.updateAccess()
            registeredEditors[editorId] = lifecycleInfo
            log.debug("Activated pending editor: $editorId")
            true
        } else {
            false
        }
    }
    
    /**
     * Cleans up a specific editor
     */
    fun cleanupEditor(editor: Editor) {
        val editorId = editor.hashCode()
        
        // Remove from both collections
        val registeredInfo = registeredEditors.remove(editorId)
        val pendingInfo = pendingEditors.remove(editorId)
        
        if (registeredInfo != null || pendingInfo != null) {
            // Notify cleanup callbacks
            cleanupCallbacks.forEach { callback ->
                try {
                    callback(editor)
                } catch (e: Exception) {
                    log.warn("Error in cleanup callback: ${e.message}")
                }
            }
            
            totalCleaned.incrementAndGet()
            cleanupOperations.incrementAndGet()
            log.debug("Cleaned up editor: $editorId")
        }
    }
    
    /**
     * Adds a cleanup callback to be called when an editor is cleaned up
     */
    fun addCleanupCallback(callback: (Editor) -> Unit) {
        cleanupCallbacks.add(callback)
    }
    
    /**
     * Removes a cleanup callback
     */
    fun removeCleanupCallback(callback: (Editor) -> Unit) {
        cleanupCallbacks.remove(callback)
    }
    
    /**
     * Gets all currently registered editors (valid ones only)
     */
    fun getRegisteredEditors(): List<Editor> {
        return registeredEditors.values.mapNotNull { info ->
            if (info.isEditorValid()) info.getEditor() else null
        }
    }
    
    /**
     * Gets all pending editors (valid ones only)
     */
    fun getPendingEditors(): List<Editor> {
        return pendingEditors.values.mapNotNull { info ->
            if (info.isEditorValid()) info.getEditor() else null
        }
    }
    
    /**
     * Checks if an editor is registered
     */
    fun isEditorRegistered(editor: Editor): Boolean {
        val editorId = editor.hashCode()
        return registeredEditors.containsKey(editorId) || pendingEditors.containsKey(editorId)
    }
    
    /**
     * Updates access time for an editor
     */
    fun updateEditorAccess(editor: Editor) {
        val editorId = editor.hashCode()
        registeredEditors[editorId]?.updateAccess()
        pendingEditors[editorId]?.updateAccess()
    }
    
    /**
     * Performs comprehensive cleanup of invalid editors
     */
    fun performCleanup(): Int {
        val currentTime = System.currentTimeMillis()
        var cleanedCount = 0
        
        // Clean up invalid registered editors
        val invalidRegistered = registeredEditors.entries.filter { (_, info) ->
            !info.isEditorValid() || 
            (currentTime - info.lastAccessTime > maxEditorLifetime) ||
            (currentTime - info.lastAccessTime > inactivityThreshold && !info.isActive)
        }
        
        invalidRegistered.forEach { (editorId, info) ->
            registeredEditors.remove(editorId)
            info.getEditor()?.let { editor ->
                cleanupCallbacks.forEach { callback ->
                    try {
                        callback(editor)
                    } catch (e: Exception) {
                        log.warn("Error in cleanup callback: ${e.message}")
                    }
                }
            }
            cleanedCount++
            
            if (!info.isEditorValid()) {
                memoryLeaksDetected.incrementAndGet()
            }
        }
        
        // Clean up invalid pending editors
        val invalidPending = pendingEditors.entries.filter { (_, info) ->
            !info.isEditorValid() || (currentTime - info.lastAccessTime > maxEditorLifetime)
        }
        
        invalidPending.forEach { (editorId, info) ->
            pendingEditors.remove(editorId)
            cleanedCount++
            
            if (!info.isEditorValid()) {
                memoryLeaksDetected.incrementAndGet()
            }
        }
        
        if (cleanedCount > 0) {
            totalCleaned.addAndGet(cleanedCount.toLong())
            cleanupOperations.incrementAndGet()
            log.debug("Cleaned up $cleanedCount invalid editors")
        }
        
        return cleanedCount
    }
    
    /**
     * Enforces maximum concurrent editors limit
     */
    fun enforceEditorLimit(): Int {
        val currentCount = registeredEditors.size
        if (currentCount <= maxConcurrentEditorsLimit) {
            return 0
        }
        
        val excessCount = currentCount - maxConcurrentEditorsLimit
        
        // Sort by last access time and remove least recently used
        val sortedEditors = registeredEditors.entries.sortedBy { it.value.lastAccessTime }
        val editorsToRemove = sortedEditors.take(excessCount)
        
        editorsToRemove.forEach { (editorId, info) ->
            registeredEditors.remove(editorId)
            info.getEditor()?.let { editor ->
                moveEditorToPending(editor)
            }
        }
        
        log.debug("Moved $excessCount editors to pending due to limit enforcement")
        return excessCount
    }
    
    /**
     * Gets lifecycle statistics
     */
    fun getStatistics(): EditorLifecycleStats {
        val currentActive = registeredEditors.size
        val currentPending = pendingEditors.size
        
        // Calculate average editor lifetime
        val currentTime = System.currentTimeMillis()
        val totalLifetime = registeredEditors.values.sumOf { currentTime - it.registrationTime } +
                           pendingEditors.values.sumOf { currentTime - it.registrationTime }
        val totalEditors = currentActive + currentPending
        val averageLifetime = if (totalEditors > 0) totalLifetime.toDouble() / totalEditors else 0.0
        
        return EditorLifecycleStats(
            totalRegistered = totalRegistered.get(),
            totalCleaned = totalCleaned.get(),
            currentActive = currentActive,
            currentPending = currentPending,
            memoryLeaksDetected = memoryLeaksDetected.get(),
            cleanupOperations = cleanupOperations.get(),
            averageEditorLifetime = averageLifetime,
            maxConcurrentEditors = maxConcurrentEditors.get()
        )
    }
    
    /**
     * Gets detailed debug information
     */
    fun getDebugInfo(): String {
        val stats = getStatistics()
        return buildString {
            appendLine("=== Editor Lifecycle Manager Debug Info ===")
            appendLine("Total registered: ${stats.totalRegistered}")
            appendLine("Total cleaned: ${stats.totalCleaned}")
            appendLine("Current active: ${stats.currentActive}")
            appendLine("Current pending: ${stats.currentPending}")
            appendLine("Memory leaks detected: ${stats.memoryLeaksDetected}")
            appendLine("Cleanup operations: ${stats.cleanupOperations}")
            appendLine("Average editor lifetime: ${String.format("%.2f", stats.averageEditorLifetime)}ms")
            appendLine("Max concurrent editors: ${stats.maxConcurrentEditors}")
            appendLine("Cleanup callbacks: ${cleanupCallbacks.size}")
        }
    }
    
    private fun startCleanupTasks() {
        // Periodic cleanup task
        cleanupTask = executorService.scheduleWithFixedDelay({
            try {
                performCleanup()
                enforceEditorLimit()
            } catch (e: Exception) {
                log.warn("Error in periodic cleanup: ${e.message}")
            }
        }, cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS)
        
        // Memory monitoring task
        memoryMonitorTask = executorService.scheduleWithFixedDelay({
            try {
                updateMaxConcurrentEditors()
                
                // Log statistics periodically
                if (cleanupOperations.get() % 10 == 0L) {
                    log.debug(getDebugInfo())
                }
            } catch (e: Exception) {
                log.warn("Error in memory monitoring: ${e.message}")
            }
        }, 60000L, 60000L, TimeUnit.MILLISECONDS) // Every minute
    }
    
    private fun updateMaxConcurrentEditors() {
        val currentCount = registeredEditors.size + pendingEditors.size
        val currentMax = maxConcurrentEditors.get()
        if (currentCount > currentMax) {
            maxConcurrentEditors.set(currentCount)
        }
    }
    
    override fun dispose() {
        log.info("Disposing EditorLifecycleManager for project: ${project.name}")
        
        // Cancel scheduled tasks
        cleanupTask?.cancel(false)
        memoryMonitorTask?.cancel(false)
        
        // Remove editor factory listener
        EditorFactory.getInstance().removeEditorFactoryListener(editorFactoryListener)
        
        // Clean up all editors
        val allEditors = getRegisteredEditors() + getPendingEditors()
        allEditors.forEach { editor ->
            cleanupEditor(editor)
        }
        
        // Clear collections
        registeredEditors.clear()
        pendingEditors.clear()
        cleanupCallbacks.clear()
        
        log.info("EditorLifecycleManager disposed. Final stats: ${getStatistics()}")
    }
}

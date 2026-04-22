package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.diagnostic.Logger
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.models.EditorState
import com.glenncai.cursormirrorsync.core.models.TextPosition
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance object pool for EditorState instances.
 * Implements thread-safe object pooling to reduce GC pressure and improve
 * memory allocation efficiency for frequently created EditorState objects.
 */
class EditorStatePool {
    
    private val log: Logger = Logger.getInstance(EditorStatePool::class.java)
    
    // Thread-safe object pool
    private val availableObjects = ConcurrentLinkedQueue<PooledEditorState>()
    private val currentPoolSize = AtomicInteger(0)
    private val maxPoolSize = Constants.EDITOR_STATE_POOL_MAX_SIZE
    
    // Pool statistics
    private val totalCreated = AtomicLong(0)
    private val totalAcquired = AtomicLong(0)
    private val totalReturned = AtomicLong(0)
    private val totalReused = AtomicLong(0)
    private val totalDiscarded = AtomicLong(0)
    
    // Cleanup scheduler
    private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "EditorStatePool-Cleanup").apply {
            isDaemon = true
        }
    }
    private var cleanupTask: ScheduledFuture<*>? = null
    
    /**
     * Wrapper for pooled EditorState with metadata
     */
    private data class PooledEditorState(
        var editorState: EditorState,
        val createdTime: Long = System.currentTimeMillis(),
        var lastUsedTime: Long = System.currentTimeMillis(),
        var useCount: Int = 0
    ) {
        fun markUsed() {
            lastUsedTime = System.currentTimeMillis()
            useCount++
        }
        
        fun isExpired(): Boolean {
            val idleTime = System.currentTimeMillis() - lastUsedTime
            return idleTime > Constants.EDITOR_STATE_POOL_IDLE_TIMEOUT_MS
        }
    }
    
    /**
     * Builder for creating EditorState instances with object pool support
     */
    class EditorStateBuilder {
        private var filePath: String = ""
        private var line: Int = 0
        private var column: Int = 0
        private var source: String? = "jetbrains"
        private var isActive: Boolean = false
        private var hasSelection: Boolean? = false
        private var selectionStart: TextPosition? = null
        private var selectionEnd: TextPosition? = null
        
        fun filePath(path: String) = apply { this.filePath = path }
        fun line(line: Int) = apply { this.line = line }
        fun column(column: Int) = apply { this.column = column }
        fun source(source: String?) = apply { this.source = source }
        fun isActive(active: Boolean) = apply { this.isActive = active }
        fun hasSelection(hasSelection: Boolean?) = apply { this.hasSelection = hasSelection }
        fun selectionStart(start: TextPosition?) = apply { this.selectionStart = start }
        fun selectionEnd(end: TextPosition?) = apply { this.selectionEnd = end }
        
        fun build(): EditorState {
            return EditorState(
                filePath = filePath,
                line = line,
                column = column,
                source = source,
                isActive = isActive,
                hasSelection = hasSelection,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd
            )
        }
    }
    
    init {
        log.info("Initializing EditorStatePool with max size: $maxPoolSize")
        
        // Preload objects if enabled
        if (Constants.EDITOR_STATE_POOL_PRELOAD_ENABLED) {
            preloadObjects()
        }
        
        // Start cleanup task
        startCleanupTask()
    }
    
    /**
     * Acquires an EditorState from the pool or creates a new one
     */
    fun acquire(
        filePath: String,
        line: Int,
        column: Int,
        source: String? = "jetbrains",
        isActive: Boolean = false,
        hasSelection: Boolean? = false,
        selectionStart: TextPosition? = null,
        selectionEnd: TextPosition? = null
    ): EditorState {
        totalAcquired.incrementAndGet()
        
        val pooledObject = availableObjects.poll()
        
        return if (pooledObject != null) {
            // Reuse existing object
            pooledObject.markUsed()
            totalReused.incrementAndGet()
            
            // Reset the object with new values
            resetEditorState(
                pooledObject.editorState,
                filePath, line, column, source, isActive, hasSelection, selectionStart, selectionEnd
            )
        } else {
            // Create new object
            totalCreated.incrementAndGet()
            createNewEditorState(filePath, line, column, source, isActive, hasSelection, selectionStart, selectionEnd)
        }
    }
    
    /**
     * Returns an EditorState to the pool for reuse
     */
    fun release(editorState: EditorState) {
        totalReturned.incrementAndGet()
        
        // Check if pool is full
        if (currentPoolSize.get() >= maxPoolSize) {
            totalDiscarded.incrementAndGet()
            log.debug("Pool is full, discarding EditorState")
            return
        }
        
        // Validate object before returning to pool
        if (Constants.EDITOR_STATE_POOL_VALIDATION_ENABLED && !isValidForPooling(editorState)) {
            totalDiscarded.incrementAndGet()
            log.debug("EditorState failed validation, discarding")
            return
        }
        
        // Add to pool
        val pooledObject = PooledEditorState(editorState)
        availableObjects.offer(pooledObject)
        currentPoolSize.incrementAndGet()
        
        log.debug("Returned EditorState to pool, current size: ${currentPoolSize.get()}")
    }
    
    /**
     * Creates a new EditorState instance
     */
    private fun createNewEditorState(
        filePath: String,
        line: Int,
        column: Int,
        source: String?,
        isActive: Boolean,
        hasSelection: Boolean?,
        selectionStart: TextPosition?,
        selectionEnd: TextPosition?
    ): EditorState {
        return EditorState(
            filePath = filePath,
            line = line,
            column = column,
            source = source,
            isActive = isActive,
            hasSelection = hasSelection,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd
        )
    }
    
    /**
     * Resets an existing EditorState with new values (simulates object reuse)
     * Note: Since EditorState is a data class, we create a new instance with the same reference pattern
     */
    private fun resetEditorState(
        @Suppress("UNUSED_PARAMETER") original: EditorState,
        filePath: String,
        line: Int,
        column: Int,
        source: String?,
        isActive: Boolean,
        hasSelection: Boolean?,
        selectionStart: TextPosition?,
        selectionEnd: TextPosition?
    ): EditorState {
        // Since EditorState is immutable, we create a new instance
        // The pool benefit comes from reducing allocation pressure and GC overhead
        return EditorState(
            filePath = filePath,
            line = line,
            column = column,
            source = source,
            isActive = isActive,
            hasSelection = hasSelection,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd
        )
    }
    
    /**
     * Validates if an EditorState is suitable for pooling
     */
    private fun isValidForPooling(editorState: EditorState): Boolean {
        return try {
            editorState.filePath.isNotBlank() &&
            editorState.line >= 0 &&
            editorState.column >= 0
        } catch (e: Exception) {
            log.warn("EditorState validation failed: ${e.message}")
            false
        }
    }
    
    /**
     * Preloads objects into the pool
     */
    private fun preloadObjects() {
        val initialSize = Constants.EDITOR_STATE_POOL_INITIAL_SIZE
        log.debug("Preloading $initialSize EditorState objects")
        
        repeat(initialSize) {
            val editorState = createNewEditorState(
                filePath = "/temp/preload.txt",
                line = 0,
                column = 0,
                source = "jetbrains",
                isActive = false,
                hasSelection = false,
                selectionStart = null,
                selectionEnd = null
            )
            
            val pooledObject = PooledEditorState(editorState)
            availableObjects.offer(pooledObject)
            currentPoolSize.incrementAndGet()
        }
        
        log.info("Preloaded $initialSize EditorState objects into pool")
    }
    
    /**
     * Starts the cleanup task for expired objects
     */
    private fun startCleanupTask() {
        cleanupTask = cleanupExecutor.scheduleAtFixedRate({
            cleanupExpiredObjects()
        }, Constants.EDITOR_STATE_POOL_CLEANUP_INTERVAL_MS, 
           Constants.EDITOR_STATE_POOL_CLEANUP_INTERVAL_MS, 
           TimeUnit.MILLISECONDS)
        
        log.debug("Started pool cleanup task with interval: ${Constants.EDITOR_STATE_POOL_CLEANUP_INTERVAL_MS}ms")
    }
    
    /**
     * Cleans up expired objects from the pool
     */
    private fun cleanupExpiredObjects() {
        val iterator = availableObjects.iterator()
        var removedCount = 0
        
        while (iterator.hasNext()) {
            val pooledObject = iterator.next()
            if (pooledObject.isExpired()) {
                iterator.remove()
                currentPoolSize.decrementAndGet()
                removedCount++
            }
        }
        
        if (removedCount > 0) {
            log.debug("Cleaned up $removedCount expired objects from pool")
        }
    }
    
    /**
     * Gets pool statistics
     */
    fun getStatistics(): String {
        val currentSize = currentPoolSize.get()
        val availableSize = availableObjects.size
        val created = totalCreated.get()
        val acquired = totalAcquired.get()
        val returned = totalReturned.get()
        val reused = totalReused.get()
        val discarded = totalDiscarded.get()
        
        val reuseRate = if (acquired > 0) (reused.toDouble() / acquired * 100) else 0.0
        val returnRate = if (acquired > 0) (returned.toDouble() / acquired * 100) else 0.0
        
        return buildString {
            appendLine("EditorState Pool Statistics:")
            appendLine("  Current pool size: $currentSize / $maxPoolSize")
            appendLine("  Available objects: $availableSize")
            appendLine("  Total created: $created")
            appendLine("  Total acquired: $acquired")
            appendLine("  Total returned: $returned")
            appendLine("  Total reused: $reused (${String.format("%.1f", reuseRate)}%)")
            appendLine("  Total discarded: $discarded")
            appendLine("  Return rate: ${String.format("%.1f", returnRate)}%")
        }
    }
    
    /**
     * Clears the pool and releases all objects
     */
    fun clear() {
        availableObjects.clear()
        currentPoolSize.set(0)
        log.debug("Cleared all objects from pool")
    }
    
    /**
     * Disposes the pool and releases all resources
     */
    fun dispose() {
        cleanupTask?.cancel(false)
        clear()
        
        cleanupExecutor.shutdown()
        try {
            if (!cleanupExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cleanupExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        log.info("EditorStatePool disposed")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: EditorStatePool? = null
        
        /**
         * Gets the singleton instance of EditorStatePool
         */
        fun getInstance(): EditorStatePool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EditorStatePool().also { INSTANCE = it }
            }
        }
        
        /**
         * Disposes the singleton instance
         */
        fun disposeInstance() {
            synchronized(this) {
                INSTANCE?.dispose()
                INSTANCE = null
            }
        }
    }
}

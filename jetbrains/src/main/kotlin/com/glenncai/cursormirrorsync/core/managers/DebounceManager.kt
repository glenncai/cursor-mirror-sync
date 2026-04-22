package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * High-performance debounce manager using ScheduledExecutorService.
 * Provides thread-safe debouncing capabilities with better resource management
 * compared to Timer-based implementations.
 * 
 * This manager supports multiple concurrent debounce operations identified by keys,
 * allowing different components to use independent debounce timers.
 */
class DebounceManager {
    
    private val log: Logger = Logger.getInstance(DebounceManager::class.java)
    
    // Thread-safe executor service for scheduling debounced tasks
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(
        2, // Small pool size for debounce operations
        { runnable ->
            Thread(runnable, "DebounceManager-Thread").apply {
                isDaemon = true // Daemon threads won't prevent JVM shutdown
            }
        }
    )
    
    // Thread-safe map to track active debounce tasks by key
    private val activeTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    
    // Flag to track if the manager has been disposed
    @Volatile
    private var isDisposed = false
    
    /**
     * Schedules a debounced action. If a previous action with the same key is pending,
     * it will be cancelled and replaced with the new action.
     * 
     * @param key Unique identifier for this debounce operation
     * @param delay Delay in milliseconds before executing the action
     * @param action The action to execute after the delay
     * @throws IllegalStateException if the manager has been disposed
     */
    fun debounce(key: String, delay: Long, action: () -> Unit) {
        if (isDisposed) {
            log.warn("Attempted to schedule debounce operation on disposed DebounceManager")
            return
        }
        
        try {
            // Cancel any existing task with the same key
            cancel(key)
            
            // Schedule the new task
            val future = executor.schedule({
                try {
                    // Remove from active tasks when executing
                    activeTasks.remove(key)
                    action()
                } catch (e: Exception) {
                    log.error("Error executing debounced action for key '$key': ${e.message}", e)
                }
            }, delay, TimeUnit.MILLISECONDS)
            
            // Store the future for potential cancellation
            activeTasks[key] = future
            
            log.debug("Scheduled debounced action for key '$key' with delay ${delay}ms")
            
        } catch (e: Exception) {
            log.error("Failed to schedule debounced action for key '$key': ${e.message}", e)
        }
    }
    
    /**
     * Cancels a pending debounced action identified by the given key.
     * 
     * @param key The key of the debounce operation to cancel
     * @return true if a task was cancelled, false if no task was found
     */
    fun cancel(key: String): Boolean {
        val future = activeTasks.remove(key)
        return if (future != null) {
            val cancelled = future.cancel(false) // Don't interrupt if already running
            if (cancelled) {
                log.debug("Cancelled debounced action for key '$key'")
            }
            cancelled
        } else {
            false
        }
    }
    
    /**
     * Cancels all pending debounced actions.
     * 
     * @return The number of tasks that were cancelled
     */
    fun cancelAll(): Int {
        val keys = activeTasks.keys.toList()
        var cancelledCount = 0
        
        keys.forEach { key ->
            if (cancel(key)) {
                cancelledCount++
            }
        }
        
        log.debug("Cancelled $cancelledCount debounced actions")
        return cancelledCount
    }
    
    /**
     * Checks if there are any pending debounced actions.
     * 
     * @return true if there are pending actions, false otherwise
     */
    fun hasPendingActions(): Boolean = activeTasks.isNotEmpty()
    
    /**
     * Gets the number of currently pending debounced actions.
     * 
     * @return The number of pending actions
     */
    fun getPendingActionCount(): Int = activeTasks.size
    
    /**
     * Checks if a specific key has a pending debounced action.
     * 
     * @param key The key to check
     * @return true if there's a pending action for the key, false otherwise
     */
    fun hasPendingAction(key: String): Boolean = activeTasks.containsKey(key)
    
    /**
     * Disposes of the DebounceManager and releases all resources.
     * After calling this method, the manager cannot be used anymore.
     * 
     * This method:
     * 1. Cancels all pending debounced actions
     * 2. Shuts down the executor service
     * 3. Waits for a brief period for tasks to complete
     */
    fun dispose() {
        if (isDisposed) {
            log.debug("DebounceManager already disposed")
            return
        }
        
        isDisposed = true
        
        try {
            // Cancel all pending tasks
            val cancelledCount = cancelAll()
            log.info("Disposing DebounceManager: cancelled $cancelledCount pending tasks")
            
            // Shutdown the executor
            executor.shutdown()
            
            // Wait for a brief period for tasks to complete
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                log.warn("DebounceManager executor did not terminate within 1 second, forcing shutdown")
                executor.shutdownNow()
                
                // Wait a bit more for forced shutdown
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    log.error("DebounceManager executor did not terminate after forced shutdown")
                }
            }
            
            log.info("DebounceManager disposed successfully")
            
        } catch (e: InterruptedException) {
            log.warn("Interrupted while disposing DebounceManager", e)
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            log.error("Error disposing DebounceManager: ${e.message}", e)
        }
    }
    
    /**
     * Schedules a periodic task that runs at fixed intervals.
     *
     * @param key Unique identifier for this periodic task
     * @param initialDelay Initial delay before first execution in milliseconds
     * @param period Period between successive executions in milliseconds
     * @param action The action to execute periodically
     * @throws IllegalStateException if the manager has been disposed
     */
    fun scheduleAtFixedRate(key: String, initialDelay: Long, period: Long, action: () -> Unit) {
        if (isDisposed) {
            log.warn("Attempted to schedule periodic task on disposed DebounceManager")
            return
        }

        try {
            // Cancel any existing task with the same key
            cancel(key)

            // Schedule the periodic task
            val future = executor.scheduleAtFixedRate({
                try {
                    action()
                } catch (e: Exception) {
                    log.error("Error executing periodic action for key '$key': ${e.message}", e)
                }
            }, initialDelay, period, TimeUnit.MILLISECONDS)

            // Store the future for potential cancellation
            activeTasks[key] = future

            log.debug("Scheduled periodic task for key '$key' with initial delay ${initialDelay}ms and period ${period}ms")

        } catch (e: Exception) {
            log.error("Failed to schedule periodic task for key '$key': ${e.message}", e)
        }
    }

    /**
     * Gets debug information about the current state of the DebounceManager.
     *
     * @return A string containing debug information
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("DebounceManager Debug Info:")
            appendLine("  Disposed: $isDisposed")
            appendLine("  Executor shutdown: ${executor.isShutdown}")
            appendLine("  Executor terminated: ${executor.isTerminated}")
            appendLine("  Active tasks: ${activeTasks.size}")
            if (activeTasks.isNotEmpty()) {
                appendLine("  Task keys: ${activeTasks.keys.joinToString(", ")}")
            }
        }
    }
}

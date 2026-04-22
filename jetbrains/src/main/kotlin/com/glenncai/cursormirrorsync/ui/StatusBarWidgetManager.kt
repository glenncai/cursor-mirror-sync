package com.glenncai.cursormirrorsync.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.SyncService
import com.glenncai.cursormirrorsync.core.managers.DebounceManager
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages StatusBarWidget lifecycle and prevents Timer/resource leaks.
 * 
 * This manager provides:
 * - Centralized widget lifecycle management
 * - Memory leak prevention through WeakReference tracking
 * - Automatic cleanup of disposed widgets
 * - Resource usage monitoring and statistics
 * - Timer leak detection and prevention
 */
class StatusBarWidgetManager(
    private val project: Project
) : Disposable {
    
    companion object {
        private val log: Logger = Logger.getInstance(StatusBarWidgetManager::class.java)
    }
    
    // WeakReference tracking to prevent memory leaks
    private val managedWidgets = ConcurrentHashMap<String, WeakReference<StatusBarWidget>>()
    private val debounceManager = DebounceManager()
    
    // Statistics tracking
    private val totalWidgetsCreated = AtomicLong(0)
    private val totalWidgetsDisposed = AtomicLong(0)
    private val memoryLeaksDetected = AtomicLong(0)
    
    @Volatile
    private var isDisposed = false
    
    init {
        startPeriodicCleanup()
        log.debug("StatusBarWidgetManager initialized for project: ${project.name}")
    }
    
    /**
     * Creates and registers a new StatusBarWidget with lifecycle management
     */
    fun createWidget(syncService: SyncService): StatusBarWidget {
        if (isDisposed) {
            throw IllegalStateException("StatusBarWidgetManager has been disposed")
        }
        
        val widget = StatusBarWidget(project, syncService)
        val widgetId = generateWidgetId()
        
        // Register with weak reference to prevent memory leaks
        managedWidgets[widgetId] = WeakReference(widget)
        totalWidgetsCreated.incrementAndGet()
        
        log.debug("Created StatusBarWidget with ID: $widgetId for project: ${project.name}")
        return widget
    }
    
    /**
     * Manually disposes a specific widget
     */
    fun disposeWidget(widget: StatusBarWidget) {
        try {
            widget.dispose()
            removeWidgetFromTracking(widget)
            totalWidgetsDisposed.incrementAndGet()
            
            log.debug("Manually disposed StatusBarWidget for project: ${project.name}")
        } catch (e: Exception) {
            log.error("Error manually disposing StatusBarWidget: ${e.message}", e)
        }
    }
    
    /**
     * Gets the current active widget for the project
     */
    fun getCurrentWidget(): StatusBarWidget? {
        val windowManager = WindowManager.getInstance()
        val statusBar = windowManager.getStatusBar(project)
        
        return statusBar?.getWidget(Constants.PLUGIN_ID) as? StatusBarWidget
    }
    
    /**
     * Performs cleanup of disposed widgets and detects memory leaks
     */
    fun performCleanup(): Int {
        if (isDisposed) {
            return 0
        }
        
        var cleanedCount = 0
        val toRemove = mutableListOf<String>()
        
        managedWidgets.entries.forEach { (id, weakRef) ->
            val widget = weakRef.get()
            if (widget == null) {
                // Widget was garbage collected - this is expected
                toRemove.add(id)
                cleanedCount++
            }
        }
        
        // Remove cleaned references
        toRemove.forEach { id ->
            managedWidgets.remove(id)
        }
        
        if (cleanedCount > 0) {
            log.debug("Cleaned up $cleanedCount disposed StatusBarWidget references")
        }
        
        return cleanedCount
    }
    
    /**
     * Detects potential memory leaks in StatusBarWidget management
     */
    fun detectMemoryLeaks(): Int {
        var leakCount = 0
        
        managedWidgets.values.forEach { weakRef ->
            val widget = weakRef.get()
            if (widget != null) {
                // Check if widget should have been disposed but wasn't
                try {
                    // If the project is disposed but widget is still alive, it's a potential leak
                    if (project.isDisposed) {
                        log.warn("Potential memory leak detected: StatusBarWidget still alive after project disposal")
                        leakCount++
                        memoryLeaksDetected.incrementAndGet()
                    }
                } catch (e: Exception) {
                    log.warn("Error checking widget state during leak detection: ${e.message}")
                }
            }
        }
        
        return leakCount
    }
    
    /**
     * Gets comprehensive statistics about widget management
     */
    fun getStatistics(): WidgetManagerStats {
        val currentActive = managedWidgets.values.count { it.get() != null }
        
        return WidgetManagerStats(
            totalCreated = totalWidgetsCreated.get(),
            totalDisposed = totalWidgetsDisposed.get(),
            currentActive = currentActive,
            memoryLeaksDetected = memoryLeaksDetected.get(),
            pendingCleanupTasks = debounceManager.getPendingActionCount()
        )
    }
    
    /**
     * Gets debug information about the manager state
     */
    fun getDebugInfo(): String {
        val stats = getStatistics()
        
        return buildString {
            appendLine("=== StatusBarWidgetManager Debug Info ===")
            appendLine("  Project: ${project.name}")
            appendLine("  Disposed: $isDisposed")
            appendLine("  Total Created: ${stats.totalCreated}")
            appendLine("  Total Disposed: ${stats.totalDisposed}")
            appendLine("  Current Active: ${stats.currentActive}")
            appendLine("  Memory Leaks Detected: ${stats.memoryLeaksDetected}")
            appendLine("  Pending Cleanup Tasks: ${stats.pendingCleanupTasks}")
            appendLine("  Managed Widgets: ${managedWidgets.size}")
            
            // Add individual widget debug info
            managedWidgets.entries.forEach { (id, weakRef) ->
                val widget = weakRef.get()
                if (widget != null) {
                    appendLine("  Widget $id:")
                    widget.getDebugInfo().lines().forEach { line ->
                        if (line.isNotBlank()) {
                            appendLine("    $line")
                        }
                    }
                } else {
                    appendLine("  Widget $id: [DISPOSED/GC'd]")
                }
            }
        }
    }
    
    /**
     * Starts periodic cleanup tasks
     */
    private fun startPeriodicCleanup() {
        // Cleanup task every 30 seconds
        debounceManager.scheduleAtFixedRate(
            "widget-cleanup",
            30000L, // Initial delay
            30000L  // Period
        ) {
            performCleanup()
            detectMemoryLeaks()
        }
        
        // Memory monitoring task every 2 minutes
        debounceManager.scheduleAtFixedRate(
            "memory-monitoring",
            120000L, // Initial delay
            120000L  // Period
        ) {
            val stats = getStatistics()
            if (stats.memoryLeaksDetected > 0) {
                log.warn("Memory leaks detected in StatusBarWidget management: ${stats.memoryLeaksDetected}")
            }
            
            // Log statistics periodically
            log.debug("StatusBarWidget statistics: $stats")
        }
    }
    
    /**
     * Generates a unique widget ID
     */
    private fun generateWidgetId(): String {
        return "widget-${System.currentTimeMillis()}-${totalWidgetsCreated.get()}"
    }
    
    /**
     * Removes a widget from tracking
     */
    private fun removeWidgetFromTracking(widget: StatusBarWidget) {
        val toRemove = managedWidgets.entries.find { (_, weakRef) ->
            weakRef.get() === widget
        }?.key
        
        toRemove?.let { id ->
            managedWidgets.remove(id)
            log.debug("Removed widget $id from tracking")
        }
    }
    
    override fun dispose() {
        if (isDisposed) {
            log.debug("StatusBarWidgetManager already disposed")
            return
        }
        
        log.debug("Disposing StatusBarWidgetManager for project: ${project.name}")
        isDisposed = true
        
        try {
            // Dispose all managed widgets
            val widgets = managedWidgets.values.mapNotNull { it.get() }
            widgets.forEach { widget ->
                try {
                    widget.dispose()
                } catch (e: Exception) {
                    log.warn("Error disposing widget during manager disposal: ${e.message}")
                }
            }
            
            // Clear tracking
            managedWidgets.clear()
            
            // Dispose debounce manager
            debounceManager.dispose()
            
            val finalStats = getStatistics()
            log.info("StatusBarWidgetManager disposed. Final stats: $finalStats")
            
        } catch (e: Exception) {
            log.error("Error disposing StatusBarWidgetManager: ${e.message}", e)
        }
    }
}

/**
 * Statistics data class for StatusBarWidget management
 */
data class WidgetManagerStats(
    val totalCreated: Long,
    val totalDisposed: Long,
    val currentActive: Int,
    val memoryLeaksDetected: Long,
    val pendingCleanupTasks: Int
)

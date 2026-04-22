package com.glenncai.cursormirrorsync.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.glenncai.cursormirrorsync.core.SyncService
import com.glenncai.cursormirrorsync.core.Constants
import java.util.concurrent.ConcurrentHashMap

class StatusBarWidgetFactory : StatusBarWidgetFactory {
    companion object {
        private val log: Logger = Logger.getInstance(StatusBarWidgetFactory::class.java)

        // Track widget managers per project to prevent memory leaks.
        // Keyed by Project.locationHash so that multiple windows opened against
        // the same project name do not collide. Each entry is bound to the
        // project's lifecycle via Disposer.register(project, ...) and will be
        // removed automatically from this map when the project closes.
        private val widgetManagers = ConcurrentHashMap<String, StatusBarWidgetManager>()

        /**
         * Releases the widget manager associated with the given project, if any.
         * Exposed as a static helper so callers don't instantiate the factory
         * just to clean up state. Normally not needed because the manager is
         * auto-disposed through Disposer.register(project, ...); callers may
         * still invoke this for deterministic cleanup in tests.
         */
        @JvmStatic
        fun disposeWidgetManager(project: Project) {
            val key = project.locationHash
            val manager = widgetManagers.remove(key)
            if (manager != null) {
                manager.dispose()
                log.debug("Disposed StatusBarWidgetManager for project: ${project.name}")
            }
        }
    }

    override fun getId() = Constants.PLUGIN_ID

    override fun getDisplayName() = Constants.PLUGIN_NAME

    override fun isAvailable(project: Project) = !project.isDisposed

    override fun createWidget(project: Project): CustomStatusBarWidget {
        try {
            val syncService = project.service<SyncService>()

            val widgetManager = getOrCreateManager(project)
            val widget = widgetManager.createWidget(syncService)

            log.debug("Created StatusBarWidget for project: ${project.name}")
            return widget

        } catch (e: Exception) {
            log.error("Error creating StatusBarWidget for project: ${project.name}", e)
            // Fallback to direct creation if manager fails
            val syncService = project.service<SyncService>()
            return StatusBarWidget(project, syncService)
        }
    }

    /**
     * Looks up (or lazily creates) the widget manager for a project. The
     * manager is registered with the project's Disposer so it is cleaned up
     * automatically when the project closes, and the static map entry is
     * removed at the same time.
     */
    private fun getOrCreateManager(project: Project): StatusBarWidgetManager {
        val key = project.locationHash
        return widgetManagers.computeIfAbsent(key) {
            val manager = StatusBarWidgetManager(project)
            Disposer.register(project, Disposable {
                val removed = widgetManagers.remove(key)
                removed?.dispose()
            })
            log.debug("Created StatusBarWidgetManager for project: ${project.name}")
            manager
        }
    }

    /**
     * Gets debug information about all widget managers
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== StatusBarWidgetFactory Debug Info ===")
            appendLine("  Active Widget Managers: ${widgetManagers.size}")

            widgetManagers.entries.forEach { (key, manager) ->
                appendLine("  Project Key: $key")
                manager.getDebugInfo().lines().forEach { line ->
                    if (line.isNotBlank()) {
                        appendLine("    $line")
                    }
                }
            }
        }
    }
}

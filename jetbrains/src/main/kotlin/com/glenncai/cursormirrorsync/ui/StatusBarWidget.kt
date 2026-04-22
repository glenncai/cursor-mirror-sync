package com.glenncai.cursormirrorsync.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.icons.AllIcons
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.MessageBusConnection
import com.glenncai.cursormirrorsync.core.SyncService
import com.glenncai.cursormirrorsync.core.SyncStateListener
import com.glenncai.cursormirrorsync.core.SyncStateTopic
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.managers.DebounceManager

class StatusBarWidget(
    private val project: Project,
    private val syncService: SyncService) : CustomStatusBarWidget {
    companion object {
        private val SPINNER_ICONS = arrayOf(
            AllIcons.Process.Step_1,
            AllIcons.Process.Step_2,
            AllIcons.Process.Step_3,
            AllIcons.Process.Step_4,
            AllIcons.Process.Step_5,
            AllIcons.Process.Step_6,
            AllIcons.Process.Step_7,
            AllIcons.Process.Step_8
        )
    }

    // Instance variable to prevent state sharing between widgets
    private var spinnerIndex = 0
    private val debounceManager = DebounceManager()
    private var log: Logger = Logger.getInstance(StatusBarWidget::class.java)

    // Track disposal state to prevent double disposal
    @Volatile
    private var isDisposed = false

    // MessageBus subscription for sync state updates (replaces direct SyncService-held widget reference)
    private val messageBusConnection: MessageBusConnection = project.messageBus.connect()

    private val component = JLabel().apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        text = Constants.PLUGIN_NAME

        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.CENTER
        iconTextGap = Constants.UI_ICON_TEXT_GAP
        verticalTextPosition = SwingConstants.CENTER
        horizontalTextPosition = SwingConstants.RIGHT
    }

    init {

        // Subscribe to sync state changes via MessageBus so the core service
        // does not need to hold a direct reference to this widget.
        messageBusConnection.subscribe(SyncStateTopic.TOPIC, SyncStateListener { updateUI() })

        // Schedule spinner animation using DebounceManager
        debounceManager.scheduleAtFixedRate(
            "spinner-animation",
            0,
            Constants.SPINNER_UPDATE_INTERVAL
        ) {
            if (!isDisposed && syncService.isReconnecting() && syncService.isAutoReconnectEnabled()) {
                spinnerIndex = (spinnerIndex + 1) % SPINNER_ICONS.size
                ApplicationManager.getApplication().invokeLater {
                    if (!isDisposed) {
                        updateUI()
                    }
                }
            }
        }

        component.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
                    showContextMenu(e)
                } else {
                    syncService.toggleAutoReconnect()
                }
            }
        })
        updateUI()
    }

    private fun showContextMenu(e: MouseEvent) {
        val popup = JPopupMenu()
        

        val infoItem = JMenuItem("Connection Info")
        infoItem.addActionListener {
            val info = syncService.getConnectionInfo()
            JOptionPane.showMessageDialog(
                component,
                info,
                "Connection Information",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
        popup.add(infoItem)
        
        popup.addSeparator()
        

        if (syncService.isConnected()) {
            val disconnectItem = JMenuItem("Disconnect")
            disconnectItem.addActionListener {
                syncService.disconnect()
            }
            popup.add(disconnectItem)
        } else {
            val connectItem = JMenuItem("Connect")
            connectItem.addActionListener {
                syncService.forceReconnect()
            }
            popup.add(connectItem)
        }
        
        popup.show(component, e.x, e.y)
    }

    /**
     * Immediate UI update without queuing on EDT
     */
    fun updateUIImmediate() {
        if (isDisposed) {
            log.debug("Skipping UI update for disposed StatusBarWidget")
            return
        }

        try {
            val isConnected = syncService.isConnected()
            val isReconnecting = syncService.isReconnecting()
            val isAutoReconnectEnabled = syncService.isAutoReconnectEnabled()

            val icon = when {
                isConnected -> AllIcons.General.InspectionsOK
                isReconnecting && isAutoReconnectEnabled -> SPINNER_ICONS[spinnerIndex]
                else -> AllIcons.General.Error
            }

            component.icon = icon
            component.text = Constants.PLUGIN_NAME
            component.toolTipText = when {
                isConnected -> Constants.TooltipMessages.CONNECTED
                isReconnecting -> Constants.TooltipMessages.CONNECTING
                else -> Constants.TooltipMessages.DISCONNECTED
            }

            component.repaint()

        } catch (e: Exception) {
            log.error("Error updating status bar widget UI immediately: ${e.message}", e)
        }
    }

    fun updateUI() {
        if (isDisposed) {
            log.debug("Skipping UI update for disposed StatusBarWidget")
            return
        }

        if (ApplicationManager.getApplication().isDispatchThread) {
            updateUIImmediate()
        } else {
            ApplicationManager.getApplication().invokeLater {
                if (!isDisposed) {
                    updateUIImmediate()
                }
            }
        }
    }

    override fun ID() = Constants.PLUGIN_ID

    override fun getComponent(): JComponent = component

    override fun install(statusBar: StatusBar) {
        updateUI()
    }

    override fun dispose() {
        if (isDisposed) {
            log.debug("StatusBarWidget already disposed")
            return
        }

        log.debug("Disposing StatusBarWidget for project: ${project.name}")
        isDisposed = true

        try {
            // Stop all scheduled tasks first
            debounceManager.dispose()

            // Disconnect from the sync state MessageBus topic
            try {
                messageBusConnection.disconnect()
            } catch (e: Exception) {
                log.warn("Error disconnecting MessageBus connection: ${e.message}")
            }

            // Clear component references
            component.removeAll()
            component.icon = null
            component.text = null
            component.toolTipText = null

            // Remove mouse listeners to prevent memory leaks
            component.mouseListeners.forEach { listener ->
                component.removeMouseListener(listener)
            }

            log.debug("StatusBarWidget disposed successfully for project: ${project.name}")

        } catch (e: Exception) {
            log.error("Error disposing StatusBarWidget: ${e.message}", e)
        }
    }

    /**
     * Gets debug information about the widget state
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== StatusBarWidget Debug Info ===")
            appendLine("  Project: ${project.name}")
            appendLine("  Disposed: $isDisposed")
            appendLine("  Spinner Index: $spinnerIndex")
            appendLine("  Component Valid: ${component.isValid}")
            appendLine("  Pending Actions: ${debounceManager.getPendingActionCount()}")
            appendLine("  SyncService Connected: ${syncService.isConnected()}")
            appendLine("  SyncService Reconnecting: ${syncService.isReconnecting()}")
        }
    }
}

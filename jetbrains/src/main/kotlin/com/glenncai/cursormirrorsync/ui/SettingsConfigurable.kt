package com.glenncai.cursormirrorsync.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.icons.AllIcons
import javax.swing.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import com.glenncai.cursormirrorsync.config.Settings
import com.glenncai.cursormirrorsync.core.Constants

class SettingsConfigurable(private val project: Project) : Configurable {
    private val log: Logger = Logger.getInstance(SettingsConfigurable::class.java)
    private var autoConnectCheckBox: JCheckBox? = null
    private var portTextField: JTextField? = null
    private var settings: Settings = Settings.getInstance(project)

    override fun getDisplayName(): String = Constants.PLUGIN_NAME

    override fun createComponent(): JComponent {
        // Initialize UI components
        portTextField = createPortTextField()
        autoConnectCheckBox = createAutoConnectCheckBox()

        // Create and setup main panel
        val panel = createMainPanel()
        setupMainLayout(panel)

        // Reset to current settings and return
        reset()
        return panel
    }

    /**
     * Creates the port input text field with validation logic.
     */
    private fun createPortTextField(): JTextField {
        return JTextField(Constants.PORT_INPUT_LENGTH).apply {
            text = settings.manualPort.toString()

            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyTyped(e: java.awt.event.KeyEvent) {
                    val char = e.keyChar
                    val currentText = text ?: ""

                    // Allow only digits, backspace, and delete
                    if (!char.isDigit() && char != '\b' && char != java.awt.event.KeyEvent.VK_DELETE.toChar()) {
                        e.consume()
                        return
                    }

                    // Limit input length to PORT_INPUT_LENGTH
                    if (char.isDigit() && currentText.length >= Constants.PORT_INPUT_LENGTH) {
                        e.consume()
                        return
                    }
                }
            })

            toolTipText = Constants.TooltipMessages.PORT_HELP
        }
    }

    /**
     * Creates the auto connect checkbox.
     */
    private fun createAutoConnectCheckBox(): JCheckBox {
        return JCheckBox("Enable auto port discovery from .cursor-mirror-sync.json")
    }

    /**
     * Creates the main panel with basic layout configuration.
     */
    private fun createMainPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
    }

    /**
     * Creates the port configuration panel with label, help icon, and text field.
     */
    private fun createPortPanel(): JPanel {
        val portPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        portPanel.alignmentX = Component.LEFT_ALIGNMENT
        portPanel.add(JLabel("Manual Port"))

        val helpIcon = createHelpIcon()
        portPanel.add(Box.createHorizontalStrut(Constants.UI_HORIZONTAL_STRUT_SMALL))
        portPanel.add(helpIcon)
        portPanel.add(Box.createHorizontalStrut(Constants.UI_HORIZONTAL_STRUT_MEDIUM))
        portPanel.add(portTextField)

        return portPanel
    }

    /**
     * Creates the help icon with click event handler.
     */
    private fun createHelpIcon(): JLabel {
        return JLabel(AllIcons.General.ContextHelp).apply {
            toolTipText = Constants.TooltipMessages.PORT_HELP_DETAILED
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    JOptionPane.showMessageDialog(
                        parent,
                        "Manual Port Configuration:\n\n" +
                                "• Used as fallback when auto discovery fails\n" +
                                "• Must be exactly 4 digits (${Constants.MIN_PORT}-${Constants.MAX_PORT})\n" +
                                "• Should match the port range used by VSCode extension\n" +
                                "• Default: ${Constants.DEFAULT_PORT}",
                        "Manual Port Help",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            })
        }
    }

    /**
     * Creates the explanation label with configuration details.
     */
    private fun createExplanationLabel(): JLabel {
        return JLabel("<html><small>" +
            "Auto discovery will read port from ${Constants.CONNECTION_FILE_NAME} file.<br/>" +
            "Manual port is used as fallback when auto discovery fails.<br/>" +
            "<b>Port range: ${Constants.MIN_PORT}-${Constants.MAX_PORT}</b> (matches VSCode extension default range)" +
            "</small></html>").apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }

    /**
     * Sets up the main panel layout by adding all components in the correct order.
     */
    private fun setupMainLayout(panel: JPanel) {
        // Add description label
        val descriptionLabel = JLabel("Configure connection settings for synchronization with VSCode.")
        descriptionLabel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(descriptionLabel)
        panel.add(Box.createVerticalStrut(Constants.UI_VERTICAL_STRUT_MEDIUM))

        // Add auto connect checkbox
        autoConnectCheckBox?.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(autoConnectCheckBox)
        panel.add(Box.createVerticalStrut(Constants.UI_VERTICAL_STRUT_SMALL))

        // Add port configuration panel
        panel.add(createPortPanel())

        // Add explanation label
        panel.add(Box.createVerticalStrut(Constants.UI_VERTICAL_STRUT_SMALL))
        panel.add(createExplanationLabel())
    }

    override fun isModified(): Boolean {
        return try {
            val currentPortText = portTextField?.text?.trim() ?: ""
            val currentPort = if (currentPortText.isNotEmpty()) currentPortText.toInt() else 3000
            val portChanged = currentPort != settings.manualPort
            val autoConnectChanged = autoConnectCheckBox?.isSelected != settings.isAutoConnectEnabled
            portChanged || autoConnectChanged
        } catch (e: NumberFormatException) {
            log.warn("Invalid port number format in settings: ${e.message}")
            true
        }
    }

    override fun apply() {
        try {
            val portText = portTextField?.text?.trim() ?: ""
            val port = if (portText.isNotEmpty() && portText.length == Constants.PORT_INPUT_LENGTH) {
                val parsedPort = portText.toInt()
                if (parsedPort in Constants.MIN_PORT..Constants.MAX_PORT) parsedPort else Constants.DEFAULT_PORT
            } else {
                Constants.DEFAULT_PORT
            }

            settings.manualPort = port
            settings.isAutoConnectEnabled = autoConnectCheckBox?.isSelected ?: Constants.DEFAULT_AUTO_CONNECT_ENABLED


            portTextField?.text = port.toString()
        } catch (e: NumberFormatException) {

            log.warn("Failed to parse port number, resetting to default: ${e.message}")
            settings.manualPort = Constants.DEFAULT_PORT
            portTextField?.text = Constants.DEFAULT_PORT.toString()
        }
    }

    override fun reset() {
        autoConnectCheckBox?.isSelected = settings.isAutoConnectEnabled
        portTextField?.text = settings.manualPort.toString()
    }
}

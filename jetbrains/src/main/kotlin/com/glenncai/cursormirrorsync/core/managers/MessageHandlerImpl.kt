package com.glenncai.cursormirrorsync.core.managers

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.glenncai.cursormirrorsync.core.interfaces.MessageHandler
import com.glenncai.cursormirrorsync.core.models.ConfigSyncMessage
import com.glenncai.cursormirrorsync.core.models.EditorState
import com.glenncai.cursormirrorsync.core.exceptions.*
import com.glenncai.cursormirrorsync.ui.NotificationManager

/**
 * Implementation of MessageHandler interface.
 * Handles WebSocket message processing, parsing, and routing.
 * Manages message validation and listener notifications.
 */
class MessageHandlerImpl(
    private val project: Project
) : MessageHandler {

    private val log: Logger = Logger.getInstance(MessageHandlerImpl::class.java)
    private val gson = Gson()
    private val notificationManager = NotificationManager(project)

    // Message listener
    private var messageListener: MessageHandler.MessageListener? = null

    // Configuration state
    private var enableSelectionSync: Boolean = true

    // Callback for state updates
    private var stateUpdateCallback: (() -> Unit)? = null

    init {
        log.info("Initializing MessageHandler for project: ${project.name}")
    }

    /**
     * Sets the callback for state updates
     */
    fun setStateUpdateCallback(callback: () -> Unit) {
        stateUpdateCallback = callback
    }

    /**
     * Sets the selection sync enabled state
     */
    fun setSelectionSyncEnabled(enabled: Boolean) {
        enableSelectionSync = enabled
    }

    /**
     * Gets the current selection sync enabled state
     */
    fun isSelectionSyncEnabled(): Boolean = enableSelectionSync

    override fun handleMessage(message: String) {
        try {
            if (!validateMessage(message)) {
                val validationException = MessageValidationException(
                    message = "Invalid message format",
                    validationErrors = listOf("Message format validation failed")
                )
                log.warn(validationException.getFormattedMessage())
                messageListener?.onMessageError(message, validationException)
                return
            }

            val messageType = getMessageType(message)
            when (messageType) {
                MessageHandler.MessageType.CONFIG_SYNC -> {
                    val configMessage = parseConfigSyncMessage(message)
                    if (configMessage != null) {
                        handleConfigSync(configMessage)
                        messageListener?.onConfigSyncReceived(configMessage)
                    } else {
                        val parsingException = MessageParsingException(
                            message = "Failed to parse ConfigSyncMessage",
                            rawMessage = message,
                            expectedFormat = "ConfigSyncMessage"
                        )
                        messageListener?.onMessageError(message, parsingException)
                    }
                }
                MessageHandler.MessageType.EDITOR_STATE -> {
                    val state = parseEditorState(message)
                    if (state != null) {
                        // Only handle incoming state if it's not from JetBrains
                        if (state.source != "jetbrains") {
                            handleEditorState(state)
                            messageListener?.onEditorStateReceived(state)
                        }
                    } else {
                        val parsingException = MessageParsingException(
                            message = "Failed to parse EditorState",
                            rawMessage = message,
                            expectedFormat = "EditorState"
                        )
                        messageListener?.onMessageError(message, parsingException)
                    }
                }
                MessageHandler.MessageType.UNKNOWN -> {
                    val unknownTypeException = UnknownMessageTypeException(
                        message = "Unknown message type received",
                        rawMessage = message,
                        supportedTypes = listOf("CONFIG_SYNC", "EDITOR_STATE")
                    )
                    log.warn(unknownTypeException.getFormattedMessage())
                    messageListener?.onMessageError(message, unknownTypeException)
                }
            }
        } catch (e: Exception) {
            val messageException = when (e) {
                is JsonSyntaxException -> MessageParsingException(
                    message = "JSON parsing error: ${e.message}",
                    cause = e,
                    rawMessage = message,
                    expectedFormat = "JSON"
                )
                is MessageException -> e
                else -> MessageException(
                    message = "Error processing message: ${e.message}",
                    cause = e,
                    messageInfo = mapOf("messageLength" to message.length)
                )
            }
            log.error(messageException.getFormattedMessage(), messageException)
            messageListener?.onMessageError(message, messageException)
        }
    }

    override fun handleEditorState(state: EditorState) {
        // Only handle incoming state if the other IDE is active
        if (!state.isActive) {
            return
        }

        log.debug("Handling incoming editor state: ${state.filePath} at (${state.line}, ${state.column})")
        // The actual editor state application will be handled by EditorSyncManager
        // This method serves as a validation and routing point
    }

    override fun handleConfigSync(configMessage: ConfigSyncMessage) {
        val oldValue = enableSelectionSync
        enableSelectionSync = configMessage.enableSelectionSync

        log.info("Config sync received: enableSelectionSync = ${configMessage.enableSelectionSync}")

        if (oldValue != enableSelectionSync) {
            notificationManager.showSelectionSyncNotification(enableSelectionSync)

            // Trigger state update callback to refresh current editor state
            stateUpdateCallback?.invoke()

            ApplicationManager.getApplication().runReadAction {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                if (editor != null && file != null) {
                    // Trigger state update through callback
                    stateUpdateCallback?.invoke()
                }
            }
        }
    }

    override fun validateMessage(message: String): Boolean {
        if (message.isBlank()) {
            return false
        }

        try {
            // Basic JSON validation - try to parse as a generic JSON object
            gson.fromJson(message, Any::class.java)
            return true
        } catch (e: JsonSyntaxException) {
            val parsingException = MessageParsingException(
                message = "Invalid JSON message: ${e.message}",
                cause = e,
                rawMessage = message,
                expectedFormat = "JSON"
            )
            log.warn(parsingException.getFormattedMessage())
            return false
        } catch (e: Exception) {
            log.warn("Message validation error: ${e.message}")
            return false
        }
    }

    override fun parseEditorState(message: String): EditorState? {
        return try {
            gson.fromJson(message, EditorState::class.java)
        } catch (e: JsonSyntaxException) {
            log.error("Failed to parse EditorState: ${e.message}")
            null
        } catch (e: Exception) {
            log.error("Unexpected error parsing EditorState: ${e.message}")
            null
        }
    }

    override fun parseConfigSyncMessage(message: String): ConfigSyncMessage? {
        return try {
            gson.fromJson(message, ConfigSyncMessage::class.java)
        } catch (e: JsonSyntaxException) {
            log.error("Failed to parse ConfigSyncMessage: ${e.message}")
            null
        } catch (e: Exception) {
            log.error("Unexpected error parsing ConfigSyncMessage: ${e.message}")
            null
        }
    }

    override fun getMessageType(message: String): MessageHandler.MessageType {
        return when {
            message.contains("\"type\":\"configSync\"") || message.contains("\"_priority\":\"HIGH\"") -> {
                MessageHandler.MessageType.CONFIG_SYNC
            }
            // Check for EditorState characteristics
            message.contains("\"filePath\"") && (message.contains("\"line\"") || message.contains("\"column\"")) -> {
                MessageHandler.MessageType.EDITOR_STATE
            }
            else -> {
                MessageHandler.MessageType.UNKNOWN
            }
        }
    }

    override fun setMessageListener(listener: MessageHandler.MessageListener) {
        this.messageListener = listener
    }

    override fun dispose() {
        messageListener = null
        stateUpdateCallback = null
        log.info("MessageHandler disposed for project: ${project.name}")
    }
}
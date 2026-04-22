package com.glenncai.cursormirrorsync.core.interfaces

import com.glenncai.cursormirrorsync.core.models.ConfigSyncMessage
import com.glenncai.cursormirrorsync.core.models.EditorState

/**
 * Interface for handling WebSocket message processing.
 * Manages message parsing, validation, and routing to appropriate handlers.
 */
interface MessageHandler {

    /**
     * Processes an incoming WebSocket message.
     * This method determines the message type and routes it to the appropriate handler.
     *
     * @param message The raw message string received from WebSocket
     */
    fun handleMessage(message: String)

    /**
     * Handles incoming editor state messages from the remote IDE.
     *
     * @param state The parsed editor state from the remote IDE
     */
    fun handleEditorState(state: EditorState)

    /**
     * Handles incoming configuration synchronization messages.
     *
     * @param configMessage The parsed configuration sync message
     */
    fun handleConfigSync(configMessage: ConfigSyncMessage)

    /**
     * Validates a message before processing.
     * This helps ensure message integrity and prevents processing of malformed data.
     *
     * @param message The raw message to validate
     * @return true if the message is valid, false otherwise
     */
    fun validateMessage(message: String): Boolean

    /**
     * Parses a raw message string into an EditorState object.
     *
     * @param message The raw message string
     * @return The parsed EditorState, or null if parsing fails
     */
    fun parseEditorState(message: String): EditorState?

    /**
     * Parses a raw message string into a ConfigSyncMessage object.
     *
     * @param message The raw message string
     * @return The parsed ConfigSyncMessage, or null if parsing fails
     */
    fun parseConfigSyncMessage(message: String): ConfigSyncMessage?

    /**
     * Determines the type of message based on its content.
     *
     * @param message The raw message string
     * @return The message type identifier
     */
    fun getMessageType(message: String): MessageType

    /**
     * Sets the message listener to receive processed messages.
     *
     * @param listener The listener to receive message events
     */
    fun setMessageListener(listener: MessageListener)

    /**
     * Enumeration of supported message types.
     */
    enum class MessageType {
        EDITOR_STATE,
        CONFIG_SYNC,
        UNKNOWN
    }

    /**
     * Interface for receiving processed message events.
     */
    interface MessageListener {
        fun onEditorStateReceived(state: EditorState)
        fun onConfigSyncReceived(configMessage: ConfigSyncMessage)
        fun onMessageError(message: String, error: Exception)
    }

    /**
     * Disposes of all resources and cleans up message processing.
     * This method should be called when the handler is no longer needed.
     */
    fun dispose()
}
package com.glenncai.cursormirrorsync.core.models

import com.glenncai.cursormirrorsync.core.Constants
import com.google.gson.annotations.SerializedName

/**
 * Represents a position in a text editor with line and column coordinates.
 * Both line and column are zero-based indices.
 */
data class TextPosition(
    val line: Int,
    val column: Int
) {
    init {
        require(line >= 0) { "Line must be non-negative, got: $line" }
        require(column >= 0) { "Column must be non-negative, got: $column" }
    }
}

/**
 * Represents the complete state of an editor including cursor position,
 * file path, selection state, and activity status.
 */
data class EditorState(
    val filePath: String,
    val line: Int,
    val column: Int,
    @SerializedName("sourceIde")
    val source: String? = "jetbrains",
    val isActive: Boolean = false,
    val hasSelection: Boolean? = false,
    val selectionStart: TextPosition? = null,
    val selectionEnd: TextPosition? = null
) {
    init {
        require(filePath.isNotBlank()) { "File path cannot be blank" }
        require(line >= 0) { "Line must be non-negative, got: $line" }
        require(column >= 0) { "Column must be non-negative, got: $column" }

        // Validate selection consistency
        if (hasSelection == true) {
            require(selectionStart != null && selectionEnd != null) {
                "Selection start and end must be provided when hasSelection is true"
            }
        }
    }

    /**
     * Returns the cursor position as a TextPosition.
     */
    val cursorPosition: TextPosition
        get() = TextPosition(line, column)

    /**
     * Checks if this editor state represents a valid selection.
     */
    val hasValidSelection: Boolean
        get() = hasSelection == true && selectionStart != null && selectionEnd != null
}

/**
 * Represents a configuration synchronization message between IDEs.
 * Used to sync settings like selection synchronization preferences.
 */
data class ConfigSyncMessage(
    val type: String,
    val enableSelectionSync: Boolean,
    val sourceIde: String
) {
    init {
        require(type.isNotBlank()) { "Type cannot be blank" }
        require(sourceIde.isNotBlank()) { "Source IDE cannot be blank" }
    }

    companion object {
        const val TYPE_CONFIG_SYNC = Constants.MESSAGE_TYPE_CONFIG_SYNC

        /**
         * Creates a configuration sync message for selection sync settings.
         */
        fun createSelectionSyncMessage(enabled: Boolean, sourceIde: String): ConfigSyncMessage {
            return ConfigSyncMessage(
                type = TYPE_CONFIG_SYNC,
                enableSelectionSync = enabled,
                sourceIde = sourceIde
            )
        }
    }
}

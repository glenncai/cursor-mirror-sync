package com.glenncai.cursormirrorsync.core.interfaces

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.glenncai.cursormirrorsync.core.models.EditorState

/**
 * Interface for managing editor synchronization between IDEs.
 * Handles cursor position synchronization and text selection mirroring.
 */
interface EditorSyncManager {

    /**
     * Handles editor change events when user switches between files or editors.
     *
     * @param editor The new active editor, or null if no editor is active
     * @param file The virtual file associated with the editor, or null if no file
     */
    fun handleEditorChange(editor: Editor?, file: VirtualFile?)

    /**
     * Updates the current editor state based on user interactions.
     * This includes cursor position changes and text selection modifications.
     *
     * @param editor The editor instance where changes occurred
     * @param file The virtual file associated with the editor
     */
    fun updateStateFromEditor(editor: Editor, file: VirtualFile)

    /**
     * Applies incoming editor state from remote IDE to the local editor.
     * This method handles cursor positioning and text selection synchronization.
     *
     * @param state The editor state received from the remote IDE
     */
    fun applyIncomingState(state: EditorState)

    /**
     * Sets up necessary listeners for an editor to track cursor and selection changes.
     * This method should be called when a new editor becomes active.
     *
     * @param editor The editor to set up listeners for
     * @param file The virtual file associated with the editor
     */
    fun setupEditorListeners(editor: Editor, file: VirtualFile)

    /**
     * Removes listeners from an editor when it's no longer needed.
     * This helps prevent memory leaks and unnecessary event processing.
     *
     * @param editor The editor to remove listeners from
     */
    fun removeEditorListeners(editor: Editor)

    /**
     * Checks if the manager is currently processing an external update.
     * This helps prevent feedback loops during synchronization.
     *
     * @return true if processing external update, false otherwise
     */
    fun isProcessingExternalUpdate(): Boolean

    /**
     * Sets the external update processing state.
     * This should be called when starting/ending external update processing.
     *
     * @param processing true when starting external update, false when ending
     */
    fun setProcessingExternalUpdate(processing: Boolean)

    /**
     * Disposes of all resources and cleans up listeners.
     * This method should be called when the manager is no longer needed.
     */
    fun dispose()
}
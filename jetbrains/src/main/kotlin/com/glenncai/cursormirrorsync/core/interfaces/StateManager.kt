package com.glenncai.cursormirrorsync.core.interfaces

import com.glenncai.cursormirrorsync.core.models.EditorState

/**
 * Interface for managing application state and synchronization logic.
 * Handles state validation, comparison, and update decisions.
 */
interface StateManager {

    /**
     * Validates an editor state before processing or sending.
     *
     * @param state The editor state to validate
     * @return true if the state is valid, false otherwise
     */
    fun validateState(state: EditorState): Boolean

    /**
     * Compares two editor states to determine if an update is significant.
     * This helps reduce unnecessary synchronization messages.
     *
     * @param currentState The current editor state
     * @param newState The new editor state to compare
     * @return true if the change is significant enough to sync, false otherwise
     */
    fun isSignificantStateChange(currentState: EditorState?, newState: EditorState): Boolean

    /**
     * Updates the last sent editor state.
     * This is used for comparison in future state changes.
     *
     * @param state The editor state that was successfully sent
     */
    fun updateLastSentState(state: EditorState)

    /**
     * Gets the last sent editor state.
     *
     * @return The last editor state that was sent, or null if none
     */
    fun getLastSentState(): EditorState?

    /**
     * Checks if the IDE is currently active.
     * Active state determines whether to send synchronization updates.
     *
     * @return true if the IDE is active, false otherwise
     */
    fun isActive(): Boolean

    /**
     * Sets the active state of the IDE.
     *
     * @param active true to mark as active, false for inactive
     */
    fun setActive(active: Boolean)

    /**
     * Checks if selection synchronization is currently enabled.
     *
     * @return true if selection sync is enabled, false otherwise
     */
    fun isSelectionSyncEnabled(): Boolean

    /**
     * Sets the selection synchronization state.
     *
     * @param enabled true to enable selection sync, false to disable
     */
    fun setSelectionSyncEnabled(enabled: Boolean)

    /**
     * Gets the current connection information summary.
     *
     * @return A string containing connection details for display
     */
    fun getConnectionInfo(): String

    /**
     * Resets all state tracking to initial values.
     * This is useful when starting fresh or after errors.
     */
    fun resetState()

    /**
     * Disposes of all resources and cleans up state.
     * This method should be called when the manager is no longer needed.
     */
    fun dispose()
}
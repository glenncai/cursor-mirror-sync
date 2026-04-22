package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.interfaces.EditorSyncManager
import com.glenncai.cursormirrorsync.core.models.EditorState
import com.glenncai.cursormirrorsync.core.models.TextPosition
import com.glenncai.cursormirrorsync.core.exceptions.*

/**
 * Implementation of EditorSyncManager interface.
 * Handles editor synchronization between IDEs including cursor position and text selection.
 */
class EditorSyncManagerImpl(
    private val project: Project,
    private val listenerManager: ListenerManager? = null
) : EditorSyncManager {

    private val log: Logger = Logger.getInstance(EditorSyncManagerImpl::class.java)

    // Editor state tracking
    private var isProcessingExternalUpdate = false
    private var isActive = false
    private var lastUpdateTime = 0L
    private var enableSelectionSync: Boolean = true

    // Debounce timing constants
    private val selectionDebounceMs = Constants.UPDATE_DEBOUNCE_MS

    // Editor management with memory leak prevention
    private val registeredEditors = ConcurrentHashMap<Editor, EditorListeners>()
    private val activeEditors = ConcurrentHashMap<Editor, Long>() // Editor -> last activity timestamp
    private val pendingEditors = ConcurrentHashMap<Editor, VirtualFile>() // Editors waiting for activation

    // Debounce manager for selection changes
    private val debounceManager = DebounceManager()

    // State comparator for optimized state comparison
    private val stateComparator = StateComparator()

    // Object pool for EditorState instances
    private val editorStatePool = EditorStatePool.getInstance()

    // Editor lifecycle manager for memory leak prevention
    private val lifecycleExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val editorLifecycleManager = EditorLifecycleManager(project, lifecycleExecutor)

    // Configuration for lazy loading
    private val editorInactivityThreshold = 30000L // 30 seconds
    private val maxActiveEditors = 10 // Maximum number of editors to keep active simultaneously

    // Callback for state updates
    private var stateUpdateCallback: ((EditorState) -> Unit)? = null

    // Callback for activity state changes
    private var activityCallback: (() -> Unit)? = null

    // Current state tracking for comparison
    private val currentStates = ConcurrentHashMap<String, EditorState>()

    /**
     * Data class to hold editor listeners for cleanup
     */
    private data class EditorListeners(
        val caretListener: CaretListener,
        val selectionListener: SelectionListener,
        val registrationTime: Long = System.currentTimeMillis()
    )

    /**
     * Enum to represent editor activity states
     */
    private enum class EditorActivityState {
        INACTIVE,    // Editor is not being used
        PENDING,     // Editor is waiting for activation
        ACTIVE       // Editor has active listeners
    }

    init {
        log.info("Initializing EditorSyncManager for project: ${project.name}")
        isActive = true

        // Setup editor lifecycle management
        setupEditorLifecycleManagement()

        // Start periodic cleanup task for inactive editors
        startInactiveEditorCleanupTask()
    }

    /**
     * Sets the callback for state updates
     */
    fun setStateUpdateCallback(callback: (EditorState) -> Unit) {
        stateUpdateCallback = callback
    }

    /**
     * Sets the callback for activity state changes
     */
    fun setActivityCallback(callback: () -> Unit) {
        activityCallback = callback
    }

    /**
     * Sets the selection sync enabled state
     */
    fun setSelectionSyncEnabled(enabled: Boolean) {
        enableSelectionSync = enabled
    }

    override fun handleEditorChange(editor: Editor?, file: VirtualFile?) {
        if (editor != null && file != null && !isProcessingExternalUpdate) {
            // Ensure we're marked as active when user interacts with editor
            ensureActiveState()

            // Check if this editor is pending activation and activate it immediately
            if (pendingEditors.containsKey(editor)) {
                activateEditorListeners(editor, file)
                log.debug("Activated pending editor due to user interaction: ${file.name}")
            }

            updateStateFromEditor(editor, file)
            setupEditorListeners(editor, file)
        }
    }

    override fun updateStateFromEditor(editor: Editor, file: VirtualFile) {
        val selectionModel = editor.selectionModel
        val hasSelection = selectionModel.hasSelection()

        // Simplified debouncing - unified timing approach
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < selectionDebounceMs) {
            return
        }
        lastUpdateTime = currentTime

        // Simplified cursor position calculation - always use actual caret position
        val caretPosition = editor.caretModel.logicalPosition
        val cursorLine = caretPosition.line
        val cursorColumn = caretPosition.column

        // Simplified selection position calculation
        val (vscodeSelectionStart, vscodeSelectionEnd) = if (enableSelectionSync && hasSelection) {
            calculateVSCodeSelectionPositions(editor, selectionModel)
        } else {
            Pair(null, null)
        }

        val newState = editorStatePool.acquire(
            filePath = file.path,
            line = cursorLine,
            column = cursorColumn,
            isActive = isActive,
            hasSelection = if (enableSelectionSync) hasSelection else false,
            selectionStart = vscodeSelectionStart,
            selectionEnd = vscodeSelectionEnd
        )

        // Validate state before comparison
        if (!validateSelectionState(newState)) {
            return
        }

        // Use state comparator to determine if update is needed
        val currentState = currentStates[file.path]
        val shouldUpdate = if (currentState != null) {
            val comparisonResult = stateComparator.compareStates(currentState, newState)
            log.debug("State comparison for ${file.name}: ${comparisonResult.reason}")
            comparisonResult.shouldUpdate
        } else {
            // First state for this file, always update
            true
        }

        if (shouldUpdate) {
            // Release the old state back to the pool if it exists
            val oldState = currentStates.put(file.path, newState)
            oldState?.let { editorStatePool.release(it) }

            logSelectionSync(editor, newState)
            stateUpdateCallback?.invoke(newState)
        } else {
            // Release the new state since we're not using it
            editorStatePool.release(newState)
            log.debug("Skipping state update for ${file.name} - no significant changes")
        }
    }

    override fun applyIncomingState(state: EditorState) {
        // Early return if the other IDE is not active
        if (!state.isActive) {
            return
        }

        // Early return if state is not significantly different
        val currentState = currentStates[state.filePath]
        if (currentState != null) {
            val comparisonResult = stateComparator.compareStates(currentState, state)
            if (!comparisonResult.shouldUpdate) {
                log.debug("Skipping incoming state application for ${state.filePath} - ${comparisonResult.reason}")
                return
            }
        }

        isProcessingExternalUpdate = true
        try {
            ApplicationManager.getApplication().invokeLater {
                applyStateToEditor(state)
            }
        } finally {
            isProcessingExternalUpdate = false
        }
    }

    /**
     * Applies the incoming state to the appropriate editor
     */
    private fun applyStateToEditor(state: EditorState) {
        try {
            val file = File(state.filePath)
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)

            // Early return if virtual file not found
            if (virtualFile == null) {
                log.warn("Virtual file not found for path: ${state.filePath}")
                return
            }

            val fileEditorManager = FileEditorManager.getInstance(project)
            val existingEditor = fileEditorManager.selectedEditors.firstOrNull { it.file == virtualFile } as? TextEditor
            val editor = existingEditor ?: fileEditorManager.openFile(virtualFile, false).firstOrNull() as? TextEditor

            // Early return if editor not available
            if (editor == null) {
                log.warn("Could not open or find editor for file: ${state.filePath}")
                return
            }

            applyStateToTextEditor(editor, state, virtualFile, fileEditorManager)
        } catch (e: Exception) {
            val stateException = StateSynchronizationException(
                message = "Error handling incoming state: ${e.message}",
                cause = e,
                sourceIde = state.source ?: "unknown",
                targetIde = "jetbrains",
                syncDirection = "incoming"
            )
            log.error(stateException.getFormattedMessage(), stateException)
        }
    }

    /**
     * Applies state to a specific text editor
     */
    private fun applyStateToTextEditor(textEditor: TextEditor, state: EditorState, virtualFile: VirtualFile, fileEditorManager: FileEditorManager) {
        // Ensure the file is opened and focused
        fileEditorManager.openFile(virtualFile, true) // true = focus the editor

        ApplicationManager.getApplication().runWriteAction {
            // Temporarily disable listeners to prevent feedback loops
            val wasHandlingUpdate = isProcessingExternalUpdate
            isProcessingExternalUpdate = true

            try {
                if (shouldApplySelection(state)) {
                    applySelectionState(textEditor, state)
                } else {
                    applyCursorState(textEditor, state)
                }
            } catch (e: Exception) {
                val stateException = StateApplicationException(
                    message = "Error applying editor state changes: ${e.message}",
                    cause = e,
                    filePath = state.filePath,
                    targetLine = state.line,
                    targetColumn = state.column,
                    hasSelection = state.hasSelection ?: false
                )
                log.error(stateException.getFormattedMessage(), stateException)
            } finally {
                // Restore the original state
                isProcessingExternalUpdate = wasHandlingUpdate
            }
        }

        // Focus the editor after the write action to ensure active caret for immediate typing
        focusEditorAfterStateApplication(textEditor, state)
    }

    /**
     * Determines if selection should be applied based on state and settings
     */
    private fun shouldApplySelection(state: EditorState): Boolean {
        return enableSelectionSync &&
               state.hasSelection == true &&
               state.selectionStart != null &&
               state.selectionEnd != null
    }

    /**
     * Applies selection state to the editor
     */
    private fun applySelectionState(textEditor: TextEditor, state: EditorState) {
        val startOffset = textEditor.editor.logicalPositionToOffset(
            LogicalPosition(state.selectionStart!!.line, state.selectionStart.column)
        )
        val endOffset = textEditor.editor.logicalPositionToOffset(
            LogicalPosition(state.selectionEnd!!.line, state.selectionEnd.column)
        )

        // Set selection and caret position in one operation to minimize events
        textEditor.editor.selectionModel.setSelection(startOffset, endOffset)
        val caretPosition = LogicalPosition(state.line, state.column)
        textEditor.editor.caretModel.moveToLogicalPosition(caretPosition)

        // Scroll to show the selection
        val selectionRange = textEditor.editor.selectionModel
        if (selectionRange.hasSelection()) {
            val startPos = textEditor.editor.offsetToLogicalPosition(selectionRange.selectionStart)
            textEditor.editor.scrollingModel.scrollTo(startPos, ScrollType.MAKE_VISIBLE)
        }
    }

    /**
     * Applies cursor position state to the editor
     */
    private fun applyCursorState(textEditor: TextEditor, state: EditorState) {
        val position = LogicalPosition(state.line, state.column)

        // Clear selection first, then move caret
        textEditor.editor.selectionModel.removeSelection()
        textEditor.editor.caretModel.moveToLogicalPosition(position)

        // Only scroll if the caret is not visible
        val visibleArea = textEditor.editor.scrollingModel.visibleArea
        val targetPoint = textEditor.editor.logicalPositionToXY(position)
        if (!visibleArea.contains(targetPoint)) {
            textEditor.editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
    }

    /**
     * Focuses the editor after state application
     */
    private fun focusEditorAfterStateApplication(textEditor: TextEditor, state: EditorState) {
        ApplicationManager.getApplication().invokeLater {
            try {
                // Request focus on the editor component to make caret active
                textEditor.editor.contentComponent.requestFocusInWindow()

                // Ensure the editor is properly focused and ready for typing
                val ideFrame = WindowManager.getInstance().getIdeFrame(project)
                ideFrame?.let { frame ->
                    if (frame.component.isDisplayable) {
                        textEditor.editor.contentComponent.requestFocus()
                    }
                }
            } catch (e: Exception) {
                val focusException = EditorFocusException(
                    message = "Failed to focus editor after cursor sync: ${e.message}",
                    cause = e,
                    operation = "focus",
                    filePath = state.filePath
                )
                log.warn(focusException.getFormattedMessage(), focusException)
            }
        }
    }

    override fun setupEditorListeners(editor: Editor, file: VirtualFile) {
        // Check if editor should be activated immediately or deferred
        if (shouldActivateEditorImmediately(editor)) {
            activateEditorListeners(editor, file)
        } else {
            // Add to pending editors for lazy activation
            pendingEditors[editor] = file
            log.debug("Editor added to pending activation queue: ${file.name}")
        }
    }

    /**
     * Determines if an editor should be activated immediately based on current state
     */
    private fun shouldActivateEditorImmediately(editor: Editor): Boolean {
        // Always activate if we have few active editors
        if (activeEditors.size < maxActiveEditors / 2) {
            return true
        }

        // Activate if this editor is currently focused
        if (editor == FileEditorManager.getInstance(project).selectedTextEditor) {
            return true
        }

        // Activate if editor has recent activity
        val lastActivity = activeEditors[editor]
        if (lastActivity != null && (System.currentTimeMillis() - lastActivity) < editorInactivityThreshold) {
            return true
        }

        return false
    }

    /**
     * Actually creates and registers listeners for an editor
     */
    private fun activateEditorListeners(editor: Editor, file: VirtualFile) {
        // Only setup listeners if not already registered for this editor
        if (!registeredEditors.containsKey(editor)) {
            // Register with lifecycle manager first
            editorLifecycleManager.registerEditor(editor, file)

            val caretListener = object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    if (!isProcessingExternalUpdate) {
                        // Mark editor as active and ensure we're marked as active
                        markEditorActive(editor)
                        editorLifecycleManager.updateEditorAccess(editor)
                        ensureActiveState()
                        updateStateFromEditor(editor, file)
                    }
                }
            }

            val selectionListener = object : SelectionListener {
                override fun selectionChanged(event: SelectionEvent) {
                    if (!isProcessingExternalUpdate) {
                        // Mark editor as active and ensure we're marked as active
                        markEditorActive(editor)
                        editorLifecycleManager.updateEditorAccess(editor)
                        ensureActiveState()
                        // Use debounced update similar to VSCode's debouncedHandleSelection
                        debouncedUpdateStateFromEditor(editor, file)
                    }
                }
            }

            // Register listeners through ListenerManager if available, otherwise directly
            if (listenerManager != null) {
                try {
                    listenerManager.registerEditorListeners(editor, caretListener, selectionListener)
                } catch (e: Exception) {
                    log.warn("Failed to register listeners through ListenerManager, falling back to direct registration: ${e.message}")
                    editor.caretModel.addCaretListener(caretListener)
                    editor.selectionModel.addSelectionListener(selectionListener)
                }
            } else {
                editor.caretModel.addCaretListener(caretListener)
                editor.selectionModel.addSelectionListener(selectionListener)
            }

            // Store listeners for cleanup
            registeredEditors[editor] = EditorListeners(caretListener, selectionListener)

            // Mark as active and remove from pending
            markEditorActive(editor)
            pendingEditors.remove(editor)

            log.debug("Editor listeners activated for: ${file.name}")

            // Check if we need to deactivate some editors to stay within limits
            enforceActiveEditorLimit()
        }
    }

    /**
     * Marks an editor as active and updates its activity timestamp
     */
    private fun markEditorActive(editor: Editor) {
        activeEditors[editor] = System.currentTimeMillis()
    }

    /**
     * Gets the current activity state of an editor
     */
    private fun getEditorActivityState(editor: Editor): EditorActivityState {
        return when {
            registeredEditors.containsKey(editor) -> EditorActivityState.ACTIVE
            pendingEditors.containsKey(editor) -> EditorActivityState.PENDING
            else -> EditorActivityState.INACTIVE
        }
    }

    /**
     * Enforces the maximum number of active editors by deactivating least recently used ones
     */
    private fun enforceActiveEditorLimit() {
        if (activeEditors.size > maxActiveEditors) {
            // Find least recently used editors to deactivate
            val sortedByActivity = activeEditors.entries.sortedBy { it.value }
            val editorsToDeactivate = sortedByActivity.take(activeEditors.size - maxActiveEditors)

            editorsToDeactivate.forEach { (editor, _) ->
                deactivateEditorListeners(editor)
                log.debug("Deactivated editor due to limit: ${editor.document.text.take(50)}...")
            }
        }
    }

    /**
     * Deactivates listeners for an editor but keeps it in pending state for potential reactivation
     */
    private fun deactivateEditorListeners(editor: Editor) {
        registeredEditors[editor]?.let { listeners ->
            try {
                editor.caretModel.removeCaretListener(listeners.caretListener)
                editor.selectionModel.removeSelectionListener(listeners.selectionListener)

                // Move to pending state in lifecycle manager
                editorLifecycleManager.moveEditorToPending(editor)

                // Move to pending state instead of completely removing
                val file = FileDocumentManager.getInstance().getFile(editor.document)
                if (file != null) {
                    pendingEditors[editor] = file
                }

                registeredEditors.remove(editor)
                activeEditors.remove(editor)

                log.debug("Editor listeners deactivated and moved to pending")
            } catch (e: Exception) {
                log.warn("Error deactivating editor listeners: ${e.message}")
            }
        }
    }

    override fun removeEditorListeners(editor: Editor) {
        registeredEditors[editor]?.let { listeners ->
            try {
                // Remove listeners through ListenerManager if available, otherwise directly
                if (listenerManager != null) {
                    try {
                        listenerManager.deregisterEditorListeners(editor)
                    } catch (e: Exception) {
                        log.warn("Failed to deregister listeners through ListenerManager, falling back to direct removal: ${e.message}")
                        editor.caretModel.removeCaretListener(listeners.caretListener)
                        editor.selectionModel.removeSelectionListener(listeners.selectionListener)
                    }
                } else {
                    editor.caretModel.removeCaretListener(listeners.caretListener)
                    editor.selectionModel.removeSelectionListener(listeners.selectionListener)
                }
            } catch (e: Exception) {
                val listenerException = EditorListenerException(
                    message = "Error removing editor listeners: ${e.message}",
                    cause = e,
                    operation = "remove",
                    listenerType = "caret,selection",
                    editorId = editor.hashCode().toString()
                )
                log.warn(listenerException.getFormattedMessage(), listenerException)
            }
        }

        // Clean up from all collections
        cleanupEditorFromCollections(editor)

        // Clean up from lifecycle manager
        editorLifecycleManager.cleanupEditor(editor)
    }

    /**
     * Cleans up an editor from all internal collections
     */
    private fun cleanupEditorFromCollections(editor: Editor) {
        registeredEditors.remove(editor)
        activeEditors.remove(editor)
        pendingEditors.remove(editor)

        // Clean up any current state for this editor
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        file?.let { vFile ->
            val oldState = currentStates.remove(vFile.path)
            oldState?.let { editorStatePool.release(it) }
        }

        log.debug("Cleaned up editor from all collections: ${editor.hashCode()}")
    }

    override fun isProcessingExternalUpdate(): Boolean = isProcessingExternalUpdate

    override fun setProcessingExternalUpdate(processing: Boolean) {
        isProcessingExternalUpdate = processing
    }

    /**
     * Sets up editor lifecycle management with memory leak prevention
     */
    private fun setupEditorLifecycleManagement() {
        // Add cleanup callback to lifecycle manager
        editorLifecycleManager.addCleanupCallback { editor ->
            cleanupEditorFromCollections(editor)
        }

        log.debug("Editor lifecycle management setup completed")
    }

    /**
     * Starts the periodic cleanup task for inactive editors
     */
    private fun startInactiveEditorCleanupTask() {
        debounceManager.scheduleAtFixedRate(
            "editor-cleanup",
            editorInactivityThreshold, // Initial delay
            editorInactivityThreshold / 2 // Run every 15 seconds
        ) {
            cleanupInactiveEditors()
            // Also trigger lifecycle manager cleanup
            editorLifecycleManager.performCleanup()
        }
    }

    /**
     * Cleans up editors that have been inactive for too long
     */
    private fun cleanupInactiveEditors() {
        val currentTime = System.currentTimeMillis()
        val editorsToDeactivate = mutableListOf<Editor>()
        val editorsToCleanup = mutableListOf<Editor>()

        // Find editors that have been inactive for too long
        activeEditors.entries.forEach { (editor, lastActivity) ->
            if (currentTime - lastActivity > editorInactivityThreshold) {
                // Check if editor is disposed - if so, clean it up completely
                if (editor.isDisposed) {
                    editorsToCleanup.add(editor)
                    return@forEach
                }

                // Check if editor is currently focused - if so, skip deactivation
                if (editor == FileEditorManager.getInstance(project).selectedTextEditor) {
                    return@forEach
                }

                editorsToDeactivate.add(editor)
            }
        }

        // Clean up disposed editors completely
        editorsToCleanup.forEach { editor ->
            cleanupEditorFromCollections(editor)
            log.debug("Cleaned up disposed editor")
        }

        // Deactivate inactive editors
        editorsToDeactivate.forEach { editor ->
            deactivateEditorListeners(editor)
            log.debug("Cleaned up inactive editor")
        }

        // Also check for pending editors that might need activation
        checkPendingEditorsForActivation()

        // Enforce editor limits using lifecycle manager
        editorLifecycleManager.enforceEditorLimit()
    }

    /**
     * Checks if any pending editors should be activated based on current focus
     */
    private fun checkPendingEditorsForActivation() {
        val currentEditor = FileEditorManager.getInstance(project).selectedTextEditor
        if (currentEditor != null && pendingEditors.containsKey(currentEditor)) {
            val file = pendingEditors[currentEditor]
            if (file != null) {
                activateEditorListeners(currentEditor, file)
                log.debug("Activated pending editor due to focus: ${file.name}")
            }
        }
    }

    /**
     * Public method to manually activate a pending editor (can be called when user interacts with editor)
     */
    fun activatePendingEditor(editor: Editor) {
        val file = pendingEditors[editor]
        if (file != null) {
            // Activate in lifecycle manager first
            editorLifecycleManager.activatePendingEditor(editor)
            activateEditorListeners(editor, file)
        }
    }

    /**
     * Gets debug information about editor management state
     */
    fun getEditorManagementDebugInfo(): String {
        return buildString {
            appendLine("=== Editor Management Debug Info ===")
            appendLine("  Active editors: ${activeEditors.size}")
            appendLine("  Pending editors: ${pendingEditors.size}")
            appendLine("  Registered editors: ${registeredEditors.size}")
            appendLine("  Max active editors: $maxActiveEditors")
            appendLine("  Inactivity threshold: ${editorInactivityThreshold}ms")

            if (activeEditors.isNotEmpty()) {
                appendLine("  Active editor details:")
                activeEditors.entries.sortedByDescending { it.value }.take(5).forEach { (editor, timestamp) ->
                    val file = FileDocumentManager.getInstance().getFile(editor.document)
                    val fileName = file?.name ?: "Unknown"
                    val age = System.currentTimeMillis() - timestamp
                    val isDisposed = editor.isDisposed
                    appendLine("    $fileName (${age}ms ago, disposed: $isDisposed)")
                }
            }

            appendLine()
            appendLine(editorLifecycleManager.getDebugInfo())
        }
    }

    override fun dispose() {
        log.info("Disposing EditorSyncManager for project: ${project.name}")

        // Get final statistics before cleanup
        val finalStats = editorLifecycleManager.getStatistics()
        log.info("Final editor lifecycle stats: $finalStats")

        // Remove all editor listeners
        registeredEditors.keys.toList().forEach { editor ->
            removeEditorListeners(editor)
        }
        registeredEditors.clear()
        activeEditors.clear()
        pendingEditors.clear()

        // Release all current states back to the pool
        currentStates.values.forEach { state ->
            editorStatePool.release(state)
        }
        currentStates.clear()

        // Dispose managers
        debounceManager.dispose()
        stateComparator.dispose()

        // Dispose lifecycle manager
        editorLifecycleManager.dispose()

        // Shutdown lifecycle executor
        lifecycleExecutor.shutdown()
        try {
            if (!lifecycleExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                lifecycleExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            lifecycleExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        log.info("EditorSyncManager disposed for project: ${project.name}")
    }

    /**
     * Gets comprehensive debug information including state comparison statistics
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("EditorSyncManager Debug Information:")
            appendLine("  Project: ${project.name}")
            appendLine("  Active: $isActive")
            appendLine("  Selection sync enabled: $enableSelectionSync")
            appendLine("  Registered editors: ${registeredEditors.size}")
            appendLine("  Active editors: ${activeEditors.size}")
            appendLine("  Pending editors: ${pendingEditors.size}")
            appendLine("  Current states tracked: ${currentStates.size}")
            appendLine()
            append(getEditorManagementDebugInfo())
            appendLine()
            append(stateComparator.getStatistics())
            appendLine()
            append(editorStatePool.getStatistics())
        }
    }

    /**
     * Debounced state update method, similar to VSCode's debouncedHandleSelection.
     * Prevents rapid-fire updates during fast selection changes.
     * Uses DebounceManager for better performance and resource management.
     */
    private fun debouncedUpdateStateFromEditor(editor: Editor, file: VirtualFile) {
        // Use debounce manager with a unique key for this editor
        val debounceKey = "editor-${editor.hashCode()}"

        debounceManager.debounce(debounceKey, selectionDebounceMs) {
            ApplicationManager.getApplication().runReadAction {
                updateStateFromEditor(editor, file)
            }
        }
    }

    /**
     * Simple activity state management.
     * For now, we assume JetBrains is active when user interacts with editors.
     * This is a pragmatic approach that ensures bidirectional sync works.
     */
    private fun ensureActiveState() {
        if (!isActive) {
            isActive = true
            activityCallback?.invoke()
        }
    }

    /**
     * Simplified VSCode selection position calculation, aligned with VSCode's implementation.
     * Uses direct text order (selection.start and selection.end) without complex direction detection.
     * This matches VSCode's simple and reliable approach.
     */
    private fun calculateVSCodeSelectionPositions(
        editor: Editor,
        selectionModel: SelectionModel
    ): Pair<TextPosition?, TextPosition?> {
        // Get the text-based start and end positions (similar to VSCode's selection.start and selection.end)
        val textStartPos = editor.offsetToLogicalPosition(selectionModel.selectionStart)
        val textEndPos = editor.offsetToLogicalPosition(selectionModel.selectionEnd)

        // Use direct text order without complex direction calculation
        // This aligns with VSCode's selection.start and selection.end behavior
        return Pair(
            TextPosition(textStartPos.line, textStartPos.column),
            TextPosition(textEndPos.line, textEndPos.column)
        )
    }

    /**
     * Validates selection state consistency to prevent sync errors.
     * Optimized version with early returns and minimal logging.
     */
    private fun validateSelectionState(state: EditorState): Boolean {
        // Fast path: no selection to validate
        if (state.hasSelection != true) {
            return true
        }

        val start = state.selectionStart
        val end = state.selectionEnd

        // Null check with single condition
        if (start == null || end == null) {
            if (log.isDebugEnabled) {
                log.debug("Selection state inconsistent: hasSelection=true but start/end is null for ${state.filePath}")
            }
            return false
        }

        // Combined position validation
        if (start.line < 0 || start.column < 0 || end.line < 0 || end.column < 0) {
            if (log.isDebugEnabled) {
                log.debug("Invalid selection positions for ${state.filePath}: start=$start, end=$end")
            }
            return false
        }

        // Empty selection check
        if (start.line == end.line && start.column == end.column) {
            if (log.isDebugEnabled) {
                log.debug("Empty selection detected for ${state.filePath}: start=$start, end=$end")
            }
            return false
        }

        return true
    }

    /**
     * Logs detailed selection sync information for debugging purposes.
     * Helps diagnose sync issues and verify the fix effectiveness.
     */
    private fun logSelectionSync(editor: Editor, state: EditorState) {
        if (log.isDebugEnabled && state.hasSelection == true) {
            val caretOffset = editor.caretModel.offset
            val selectionStart = editor.selectionModel.selectionStart
            val selectionEnd = editor.selectionModel.selectionEnd

            log.debug("Selection sync - File: ${state.filePath}")
            log.debug("  Caret offset: $caretOffset, Selection range: [$selectionStart, $selectionEnd]")
            log.debug("  VSCode format - Start: ${state.selectionStart}, End: ${state.selectionEnd}")
            log.debug("  Cursor position: (${state.line}, ${state.column})")
        }
    }
}
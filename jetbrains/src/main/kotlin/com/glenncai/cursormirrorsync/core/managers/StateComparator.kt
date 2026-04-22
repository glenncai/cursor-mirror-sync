package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.diagnostic.Logger
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.models.EditorState
import com.glenncai.cursormirrorsync.core.models.TextPosition
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance state comparison engine for editor state synchronization.
 * Implements intelligent comparison strategies to minimize unnecessary updates
 * while maintaining synchronization accuracy.
 */
class StateComparator {
    
    private val log: Logger = Logger.getInstance(StateComparator::class.java)
    
    // State caching for performance optimization
    private val stateCache = ConcurrentHashMap<String, CachedState>()
    private val stateHistory = ConcurrentHashMap<String, MutableList<StateSnapshot>>()
    
    // Performance statistics
    private val comparisonCount = AtomicLong(0)
    private val cacheHitCount = AtomicLong(0)
    private val significantChangeCount = AtomicLong(0)
    private val duplicateStateCount = AtomicLong(0)
    
    /**
     * Cached state with metadata
     */
    private data class CachedState(
        val state: EditorState,
        val timestamp: Long = System.currentTimeMillis(),
        val hash: Int = state.hashCode()
    )
    
    /**
     * State snapshot for history tracking
     */
    private data class StateSnapshot(
        val hash: Int,
        val timestamp: Long,
        val cursorPosition: TextPosition,
        val hasSelection: Boolean,
        val filePath: String
    )
    
    /**
     * Comparison result with detailed information
     */
    data class ComparisonResult(
        val hasSignificantChange: Boolean,
        val changedFields: Set<StateField>,
        val changeScore: Double,
        val shouldUpdate: Boolean,
        val reason: String
    )
    
    /**
     * State field enumeration for change tracking
     */
    enum class StateField(val weight: Double) {
        FILE_PATH(1.0),
        CURSOR_POSITION(0.8),
        SELECTION_STATE(0.6),
        SELECTION_POSITIONS(0.4),
        ACTIVITY_STATE(0.2)
    }
    
    /**
     * Comparison strategy enumeration
     */
    enum class ComparisonStrategy {
        FAST,      // Quick hash-based comparison
        DETAILED,  // Field-by-field comparison
        SMART      // Adaptive strategy based on context
    }
    
    /**
     * Compares two editor states and determines if an update is needed
     */
    fun compareStates(
        currentState: EditorState,
        newState: EditorState,
        strategy: ComparisonStrategy = ComparisonStrategy.SMART
    ): ComparisonResult {
        val startTime = System.currentTimeMillis()
        comparisonCount.incrementAndGet()
        
        try {
            // Check for duplicate states first
            if (isDuplicateState(newState)) {
                duplicateStateCount.incrementAndGet()
                return ComparisonResult(
                    hasSignificantChange = false,
                    changedFields = emptySet(),
                    changeScore = 0.0,
                    shouldUpdate = false,
                    reason = "Duplicate state detected"
                )
            }
            
            // Use appropriate comparison strategy
            val result = when (strategy) {
                ComparisonStrategy.FAST -> performFastComparison(currentState, newState)
                ComparisonStrategy.DETAILED -> performDetailedComparison(currentState, newState)
                ComparisonStrategy.SMART -> performSmartComparison(currentState, newState)
            }
            
            // Update cache and history
            if (result.shouldUpdate) {
                updateStateCache(newState)
                updateStateHistory(newState)
                significantChangeCount.incrementAndGet()
            }
            
            val duration = System.currentTimeMillis() - startTime
            if (duration > Constants.STATE_COMPARISON_TIMEOUT_MS) {
                log.warn("State comparison took ${duration}ms, exceeding timeout threshold")
            }
            
            return result
            
        } catch (e: Exception) {
            log.error("Error during state comparison: ${e.message}", e)
            // Fallback to simple comparison
            return ComparisonResult(
                hasSignificantChange = true,
                changedFields = setOf(StateField.CURSOR_POSITION),
                changeScore = 1.0,
                shouldUpdate = true,
                reason = "Comparison error, defaulting to update"
            )
        }
    }
    
    /**
     * Fast hash-based comparison for quick filtering
     */
    private fun performFastComparison(currentState: EditorState, newState: EditorState): ComparisonResult {
        val currentHash = calculateStateHash(currentState)
        val newHash = calculateStateHash(newState)
        
        val hasChange = currentHash != newHash
        
        return ComparisonResult(
            hasSignificantChange = hasChange,
            changedFields = if (hasChange) setOf(StateField.CURSOR_POSITION) else emptySet(),
            changeScore = if (hasChange) 1.0 else 0.0,
            shouldUpdate = hasChange,
            reason = if (hasChange) "Hash difference detected" else "No hash difference"
        )
    }
    
    /**
     * Detailed field-by-field comparison
     */
    private fun performDetailedComparison(currentState: EditorState, newState: EditorState): ComparisonResult {
        val changedFields = mutableSetOf<StateField>()
        var changeScore = 0.0
        
        // File path comparison
        if (currentState.filePath != newState.filePath) {
            changedFields.add(StateField.FILE_PATH)
            changeScore += StateField.FILE_PATH.weight
        }
        
        // Cursor position comparison with threshold
        if (isCursorPositionChanged(currentState, newState)) {
            changedFields.add(StateField.CURSOR_POSITION)
            changeScore += StateField.CURSOR_POSITION.weight
        }
        
        // Activity state comparison
        if (currentState.isActive != newState.isActive) {
            changedFields.add(StateField.ACTIVITY_STATE)
            changeScore += StateField.ACTIVITY_STATE.weight
        }
        
        // Selection state comparison
        if (currentState.hasSelection != newState.hasSelection) {
            changedFields.add(StateField.SELECTION_STATE)
            changeScore += StateField.SELECTION_STATE.weight
        }
        
        // Selection positions comparison
        if (isSelectionPositionChanged(currentState, newState)) {
            changedFields.add(StateField.SELECTION_POSITIONS)
            changeScore += StateField.SELECTION_POSITIONS.weight
        }
        
        val hasSignificantChange = changedFields.isNotEmpty()
        val shouldUpdate = hasSignificantChange && changeScore >= 0.1 // Minimum change threshold
        
        return ComparisonResult(
            hasSignificantChange = hasSignificantChange,
            changedFields = changedFields,
            changeScore = changeScore,
            shouldUpdate = shouldUpdate,
            reason = if (shouldUpdate) "Significant changes: ${changedFields.joinToString()}" else "No significant changes"
        )
    }
    
    /**
     * Smart adaptive comparison strategy
     */
    private fun performSmartComparison(currentState: EditorState, newState: EditorState): ComparisonResult {
        // Start with fast comparison
        val fastResult = performFastComparison(currentState, newState)
        
        // If fast comparison shows no change, we're done
        if (!fastResult.hasSignificantChange) {
            return fastResult
        }
        
        // If fast comparison shows change, do detailed comparison
        return performDetailedComparison(currentState, newState)
    }
    
    /**
     * Checks if cursor position has changed significantly
     */
    private fun isCursorPositionChanged(currentState: EditorState, newState: EditorState): Boolean {
        val lineDiff = kotlin.math.abs(currentState.line - newState.line)
        val columnDiff = kotlin.math.abs(currentState.column - newState.column)
        
        return lineDiff >= Constants.STATE_CHANGE_THRESHOLD_LINES ||
               columnDiff >= Constants.STATE_CHANGE_THRESHOLD_COLUMNS
    }
    
    /**
     * Checks if selection positions have changed significantly
     */
    private fun isSelectionPositionChanged(currentState: EditorState, newState: EditorState): Boolean {
        val currentStart = currentState.selectionStart
        val currentEnd = currentState.selectionEnd
        val newStart = newState.selectionStart
        val newEnd = newState.selectionEnd
        
        // If selection existence differs, it's a change
        if ((currentStart == null) != (newStart == null) || (currentEnd == null) != (newEnd == null)) {
            return true
        }
        
        // If both have selections, compare positions
        if (currentStart != null && newStart != null && currentEnd != null && newEnd != null) {
            return !arePositionsEqual(currentStart, newStart) || !arePositionsEqual(currentEnd, newEnd)
        }
        
        return false
    }
    
    /**
     * Compares two text positions for equality with threshold
     */
    private fun arePositionsEqual(pos1: TextPosition, pos2: TextPosition): Boolean {
        val lineDiff = kotlin.math.abs(pos1.line - pos2.line)
        val columnDiff = kotlin.math.abs(pos1.column - pos2.column)
        
        return lineDiff < Constants.POSITION_CHANGE_SENSITIVITY &&
               columnDiff < Constants.POSITION_CHANGE_SENSITIVITY
    }
    
    /**
     * Calculates a hash for state comparison
     */
    private fun calculateStateHash(state: EditorState): Int {
        var result = state.filePath.hashCode()
        result = 31 * result + state.line
        result = 31 * result + state.column
        result = 31 * result + state.isActive.hashCode()
        result = 31 * result + (state.hasSelection?.hashCode() ?: 0)
        result = 31 * result + (state.selectionStart?.hashCode() ?: 0)
        result = 31 * result + (state.selectionEnd?.hashCode() ?: 0)
        return result
    }
    
    /**
     * Checks if the state is a duplicate of recent states
     */
    private fun isDuplicateState(state: EditorState): Boolean {
        val key = state.filePath
        val history = stateHistory[key] ?: return false
        
        val stateHash = calculateStateHash(state)
        return history.any { it.hash == stateHash }
    }
    
    /**
     * Updates the state cache
     */
    private fun updateStateCache(state: EditorState) {
        val key = state.filePath
        stateCache[key] = CachedState(state)
        
        // Cleanup old cache entries
        if (stateCache.size > Constants.STATE_CACHE_SIZE) {
            val oldestKey = stateCache.entries.minByOrNull { it.value.timestamp }?.key
            oldestKey?.let { stateCache.remove(it) }
        }
    }
    
    /**
     * Updates the state history for duplicate detection
     */
    private fun updateStateHistory(state: EditorState) {
        val key = state.filePath
        val history = stateHistory.getOrPut(key) { mutableListOf() }
        
        val snapshot = StateSnapshot(
            hash = calculateStateHash(state),
            timestamp = System.currentTimeMillis(),
            cursorPosition = TextPosition(state.line, state.column),
            hasSelection = state.hasSelection ?: false,
            filePath = state.filePath
        )
        
        history.add(snapshot)
        
        // Keep only recent history
        if (history.size > Constants.STATE_HISTORY_SIZE) {
            history.removeAt(0)
        }
    }
    
    /**
     * Gets cached state for a file
     */
    fun getCachedState(filePath: String): EditorState? {
        val cached = stateCache[filePath]
        return if (cached != null) {
            cacheHitCount.incrementAndGet()
            cached.state
        } else {
            null
        }
    }
    
    /**
     * Clears cache for a specific file
     */
    fun clearCache(filePath: String) {
        stateCache.remove(filePath)
        stateHistory.remove(filePath)
    }
    
    /**
     * Clears all cached data
     */
    fun clearAllCache() {
        stateCache.clear()
        stateHistory.clear()
    }
    
    /**
     * Gets performance statistics
     */
    fun getStatistics(): String {
        val totalComparisons = comparisonCount.get()
        val cacheHits = cacheHitCount.get()
        val significantChanges = significantChangeCount.get()
        val duplicates = duplicateStateCount.get()
        
        val cacheHitRate = if (totalComparisons > 0) (cacheHits.toDouble() / totalComparisons * 100) else 0.0
        val changeRate = if (totalComparisons > 0) (significantChanges.toDouble() / totalComparisons * 100) else 0.0
        val duplicateRate = if (totalComparisons > 0) (duplicates.toDouble() / totalComparisons * 100) else 0.0
        
        return buildString {
            appendLine("State Comparator Statistics:")
            appendLine("  Total comparisons: $totalComparisons")
            appendLine("  Cache hits: $cacheHits (${String.format("%.1f", cacheHitRate)}%)")
            appendLine("  Significant changes: $significantChanges (${String.format("%.1f", changeRate)}%)")
            appendLine("  Duplicate states: $duplicates (${String.format("%.1f", duplicateRate)}%)")
            appendLine("  Cached states: ${stateCache.size}")
            appendLine("  History entries: ${stateHistory.values.sumOf { it.size }}")
        }
    }
    
    /**
     * Disposes the state comparator and cleans up resources
     */
    fun dispose() {
        clearAllCache()
        log.info("StateComparator disposed")
    }
}

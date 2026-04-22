package com.glenncai.cursormirrorsync.core.managers

import com.glenncai.cursormirrorsync.core.models.EditorState
import com.glenncai.cursormirrorsync.core.models.TextPosition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

class StateComparatorTest {
    
    private lateinit var stateComparator: StateComparator
    
    @BeforeEach
    fun setUp() {
        stateComparator = StateComparator()
    }
    
    @AfterEach
    fun tearDown() {
        stateComparator.dispose()
    }
    
    @Test
    fun `test identical states should not trigger update`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        val state2 = state1.copy()
        
        val result = stateComparator.compareStates(state1, state2)
        
        assertFalse(result.hasSignificantChange, "Identical states should not have significant changes")
        assertFalse(result.shouldUpdate, "Identical states should not trigger update")
        assertEquals(0.0, result.changeScore, "Change score should be 0 for identical states")
    }
    
    @Test
    fun `test cursor position change should trigger update`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        val state2 = state1.copy(line = 15, column = 10)
        
        val result = stateComparator.compareStates(state1, state2)
        
        assertTrue(result.hasSignificantChange, "Cursor position change should be significant")
        assertTrue(result.shouldUpdate, "Cursor position change should trigger update")
        assertTrue(result.changedFields.contains(StateComparator.StateField.CURSOR_POSITION))
    }
    
    @Test
    fun `test file path change should trigger update`() {
        val state1 = EditorState(
            filePath = "/test/file1.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        val state2 = state1.copy(filePath = "/test/file2.txt")
        
        val result = stateComparator.compareStates(state1, state2)
        
        assertTrue(result.hasSignificantChange, "File path change should be significant")
        assertTrue(result.shouldUpdate, "File path change should trigger update")
        assertTrue(result.changedFields.contains(StateComparator.StateField.FILE_PATH))
    }
    
    @Test
    fun `test selection state change should trigger update`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true,
            hasSelection = false
        )
        
        val state2 = state1.copy(
            hasSelection = true,
            selectionStart = TextPosition(10, 5),
            selectionEnd = TextPosition(10, 15)
        )
        
        val result = stateComparator.compareStates(state1, state2)
        
        assertTrue(result.hasSignificantChange, "Selection state change should be significant")
        assertTrue(result.shouldUpdate, "Selection state change should trigger update")
        assertTrue(result.changedFields.contains(StateComparator.StateField.SELECTION_STATE))
    }
    
    @Test
    fun `test selection position change should trigger update`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true,
            hasSelection = true,
            selectionStart = TextPosition(10, 5),
            selectionEnd = TextPosition(10, 15)
        )
        
        val state2 = state1.copy(
            selectionStart = TextPosition(10, 6),
            selectionEnd = TextPosition(10, 16)
        )
        
        val result = stateComparator.compareStates(state1, state2)
        
        assertTrue(result.hasSignificantChange, "Selection position change should be significant")
        assertTrue(result.shouldUpdate, "Selection position change should trigger update")
        assertTrue(result.changedFields.contains(StateComparator.StateField.SELECTION_POSITIONS))
    }
    
    @Test
    fun `test activity state change should trigger update`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = false
        )
        
        val state2 = state1.copy(isActive = true)
        
        val result = stateComparator.compareStates(state1, state2)
        
        assertTrue(result.hasSignificantChange, "Activity state change should be significant")
        assertTrue(result.shouldUpdate, "Activity state change should trigger update")
        assertTrue(result.changedFields.contains(StateComparator.StateField.ACTIVITY_STATE))
    }
    
    @Test
    fun `test fast comparison strategy`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        val state2 = state1.copy(line = 15)
        
        val result = stateComparator.compareStates(
            state1, 
            state2, 
            StateComparator.ComparisonStrategy.FAST
        )
        
        assertTrue(result.hasSignificantChange, "Fast comparison should detect changes")
        assertTrue(result.shouldUpdate, "Fast comparison should trigger update")
    }
    
    @Test
    fun `test detailed comparison strategy`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        val state2 = state1.copy(line = 15, isActive = false)
        
        val result = stateComparator.compareStates(
            state1, 
            state2, 
            StateComparator.ComparisonStrategy.DETAILED
        )
        
        assertTrue(result.hasSignificantChange, "Detailed comparison should detect changes")
        assertTrue(result.shouldUpdate, "Detailed comparison should trigger update")
        assertTrue(result.changedFields.size >= 2, "Should detect multiple changed fields")
    }
    
    @Test
    fun `test duplicate state detection`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )

        val state2 = state1.copy()

        // First comparison should not update (identical states)
        val result1 = stateComparator.compareStates(state1, state2)
        assertFalse(result1.shouldUpdate, "Identical states should not trigger update")

        // Create a different state and compare
        val state3 = state1.copy(line = 15)
        val result2 = stateComparator.compareStates(state1, state3)
        assertTrue(result2.shouldUpdate, "Different states should trigger update")

        // Now compare the same different state again - should detect as duplicate
        val result3 = stateComparator.compareStates(state1, state3)
        assertFalse(result3.shouldUpdate, "Duplicate state should not trigger update")
        assertEquals("Duplicate state detected", result3.reason)
    }
    
    @Test
    fun `test state caching functionality`() {
        val state = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        // Cache should be empty initially
        assertNull(stateComparator.getCachedState("/test/file.txt"))
        
        // Compare states to populate cache
        stateComparator.compareStates(state, state.copy(line = 15))
        
        // Cache should now contain the state
        assertNotNull(stateComparator.getCachedState("/test/file.txt"))
    }
    
    @Test
    fun `test cache clearing functionality`() {
        val state = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        // Populate cache
        stateComparator.compareStates(state, state.copy(line = 15))
        assertNotNull(stateComparator.getCachedState("/test/file.txt"))
        
        // Clear specific cache
        stateComparator.clearCache("/test/file.txt")
        assertNull(stateComparator.getCachedState("/test/file.txt"))
    }
    
    @Test
    fun `test statistics tracking`() {
        val state1 = EditorState(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        val state2 = state1.copy(line = 15)
        
        // Perform some comparisons
        stateComparator.compareStates(state1, state2)
        stateComparator.compareStates(state1, state1) // Duplicate
        
        val statistics = stateComparator.getStatistics()
        
        assertNotNull(statistics, "Statistics should not be null")
        assertTrue(statistics.contains("Total comparisons"), "Should contain comparison count")
        assertTrue(statistics.contains("Significant changes"), "Should contain change count")
        assertTrue(statistics.contains("Duplicate states"), "Should contain duplicate count")
    }
    
    @Test
    fun `test multiple field changes with weighted scoring`() {
        val state1 = EditorState(
            filePath = "/test/file1.txt",
            line = 10,
            column = 5,
            isActive = false,
            hasSelection = false
        )
        
        val state2 = EditorState(
            filePath = "/test/file2.txt",
            line = 15,
            column = 10,
            isActive = true,
            hasSelection = true,
            selectionStart = TextPosition(15, 10),
            selectionEnd = TextPosition(15, 20)
        )
        
        val result = stateComparator.compareStates(state1, state2)
        
        assertTrue(result.hasSignificantChange, "Multiple changes should be significant")
        assertTrue(result.shouldUpdate, "Multiple changes should trigger update")
        assertTrue(result.changeScore > 1.0, "Change score should reflect multiple changes")
        assertTrue(result.changedFields.size >= 4, "Should detect multiple changed fields")
    }
    
    @Test
    fun `test comparison performance with many states`() {
        val baseState = EditorState(
            filePath = "/test/file.txt",
            line = 0,
            column = 0,
            isActive = true
        )
        
        val startTime = System.currentTimeMillis()
        
        // Perform many comparisons
        repeat(1000) { index ->
            val newState = baseState.copy(line = index, column = index)
            stateComparator.compareStates(baseState, newState)
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Should complete within reasonable time (adjust threshold as needed)
        assertTrue(duration < 1000, "1000 comparisons should complete within 1 second, took ${duration}ms")
        
        val statistics = stateComparator.getStatistics()
        assertTrue(statistics.contains("1000"), "Should show 1000 comparisons in statistics")
    }
}

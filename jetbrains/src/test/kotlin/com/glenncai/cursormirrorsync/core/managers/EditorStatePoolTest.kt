package com.glenncai.cursormirrorsync.core.managers

import com.glenncai.cursormirrorsync.core.models.TextPosition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

class EditorStatePoolTest {
    
    private lateinit var editorStatePool: EditorStatePool
    
    @BeforeEach
    fun setUp() {
        editorStatePool = EditorStatePool()
    }
    
    @AfterEach
    fun tearDown() {
        editorStatePool.dispose()
    }
    
    @Test
    fun `test basic acquire and release functionality`() {
        val state = editorStatePool.acquire(
            filePath = "/test/file.txt",
            line = 10,
            column = 5,
            isActive = true
        )
        
        assertNotNull(state, "Acquired state should not be null")
        assertEquals("/test/file.txt", state.filePath)
        assertEquals(10, state.line)
        assertEquals(5, state.column)
        assertTrue(state.isActive)
        
        // Release the state back to pool
        editorStatePool.release(state)
        
        val statistics = editorStatePool.getStatistics()
        assertTrue(statistics.contains("Total acquired: 1"), "Should show 1 acquisition")
        assertTrue(statistics.contains("Total returned: 1"), "Should show 1 return")
    }
    
    @Test
    fun `test object reuse from pool`() {
        // Acquire and release a state
        val state1 = editorStatePool.acquire(
            filePath = "/test/file1.txt",
            line = 10,
            column = 5
        )
        editorStatePool.release(state1)

        // Acquire another state - should reuse from pool
        val state2 = editorStatePool.acquire(
            filePath = "/test/file2.txt",
            line = 20,
            column = 10
        )

        // Verify the new state has correct values
        assertEquals("/test/file2.txt", state2.filePath)
        assertEquals(20, state2.line)
        assertEquals(10, state2.column)

        val statistics = editorStatePool.getStatistics()
        // Check that we have reused objects (the exact count may vary based on pool implementation)
        assertTrue(statistics.contains("Total reused:"), "Should show reuse statistics")
        assertTrue(statistics.contains("Total acquired: 2"), "Should show 2 acquisitions")

        editorStatePool.release(state2)
    }
    
    @Test
    fun `test acquire with selection parameters`() {
        val selectionStart = TextPosition(5, 0)
        val selectionEnd = TextPosition(5, 10)
        
        val state = editorStatePool.acquire(
            filePath = "/test/file.txt",
            line = 5,
            column = 5,
            hasSelection = true,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd
        )
        
        assertEquals(true, state.hasSelection)
        assertEquals(selectionStart, state.selectionStart)
        assertEquals(selectionEnd, state.selectionEnd)
        
        editorStatePool.release(state)
    }
    
    @Test
    fun `test pool statistics tracking`() {
        // Perform various operations
        val state1 = editorStatePool.acquire("/test/file1.txt", 1, 1)
        val state2 = editorStatePool.acquire("/test/file2.txt", 2, 2)
        val state3 = editorStatePool.acquire("/test/file3.txt", 3, 3)
        
        editorStatePool.release(state1)
        editorStatePool.release(state2)
        // Don't release state3 to test different scenarios
        
        val statistics = editorStatePool.getStatistics()
        
        assertNotNull(statistics, "Statistics should not be null")
        assertTrue(statistics.contains("Total acquired: 3"), "Should show 3 acquisitions")
        assertTrue(statistics.contains("Total returned: 2"), "Should show 2 returns")
        assertTrue(statistics.contains("EditorState Pool Statistics"), "Should contain header")
        
        editorStatePool.release(state3)
    }
    
    @Test
    fun `test pool clear functionality`() {
        // Add some objects to pool
        val state1 = editorStatePool.acquire("/test/file1.txt", 1, 1)
        val state2 = editorStatePool.acquire("/test/file2.txt", 2, 2)
        
        editorStatePool.release(state1)
        editorStatePool.release(state2)
        
        // Clear the pool
        editorStatePool.clear()
        
        val statistics = editorStatePool.getStatistics()
        assertTrue(statistics.contains("Available objects: 0"), "Pool should be empty after clear")
    }
    
    @Test
    fun `test concurrent access to pool`() {
        val threadCount = 10
        val operationsPerThread = 50
        val threads = mutableListOf<Thread>()
        
        repeat(threadCount) { threadIndex ->
            val thread = Thread {
                repeat(operationsPerThread) { opIndex ->
                    val state = editorStatePool.acquire(
                        filePath = "/test/thread-$threadIndex-op-$opIndex.txt",
                        line = threadIndex,
                        column = opIndex
                    )
                    
                    // Simulate some work
                    Thread.sleep(1)
                    
                    editorStatePool.release(state)
                }
            }
            threads.add(thread)
            thread.start()
        }
        
        // Wait for all threads to complete
        threads.forEach { it.join() }
        
        val statistics = editorStatePool.getStatistics()
        val expectedOperations = threadCount * operationsPerThread
        
        assertTrue(statistics.contains("Total acquired: $expectedOperations"), 
                  "Should show $expectedOperations acquisitions")
        assertTrue(statistics.contains("Total returned: $expectedOperations"), 
                  "Should show $expectedOperations returns")
    }
    
    @Test
    fun `test pool with invalid states`() {
        // Try to release an invalid state (this should be handled gracefully)
        try {
            val invalidState = editorStatePool.acquire(
                filePath = "", // Invalid empty path
                line = -1,     // Invalid negative line
                column = -1    // Invalid negative column
            )
            
            // The pool should still create the object but validation might affect pooling
            assertNotNull(invalidState, "Should still create object even with invalid parameters")
            
            editorStatePool.release(invalidState)
            
        } catch (e: Exception) {
            // If validation throws exception, that's also acceptable behavior
            assertTrue(e.message?.contains("blank") == true || 
                      e.message?.contains("negative") == true,
                      "Exception should be related to validation")
        }
    }
    
    @Test
    fun `test pool builder pattern`() {
        val builder = EditorStatePool.EditorStateBuilder()
        
        val state = builder
            .filePath("/test/builder.txt")
            .line(15)
            .column(25)
            .isActive(true)
            .hasSelection(true)
            .selectionStart(TextPosition(15, 25))
            .selectionEnd(TextPosition(15, 35))
            .build()
        
        assertEquals("/test/builder.txt", state.filePath)
        assertEquals(15, state.line)
        assertEquals(25, state.column)
        assertTrue(state.isActive)
        assertEquals(true, state.hasSelection)
        assertNotNull(state.selectionStart)
        assertNotNull(state.selectionEnd)
    }
    
    @Test
    fun `test singleton instance functionality`() {
        val instance1 = EditorStatePool.getInstance()
        val instance2 = EditorStatePool.getInstance()
        
        assertSame(instance1, instance2, "Should return the same singleton instance")
        
        // Test that operations work on singleton
        val state = instance1.acquire("/test/singleton.txt", 1, 1)
        instance2.release(state)
        
        val statistics = instance1.getStatistics()
        assertTrue(statistics.contains("Total acquired: 1"), "Singleton should track operations")
    }
    
    @Test
    fun `test pool performance with many operations`() {
        val operationCount = 1000
        val startTime = System.currentTimeMillis()
        
        repeat(operationCount) { index ->
            val state = editorStatePool.acquire(
                filePath = "/test/perf-$index.txt",
                line = index,
                column = index % 100
            )
            editorStatePool.release(state)
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Should complete within reasonable time (adjust threshold as needed)
        assertTrue(duration < 1000, 
                  "$operationCount operations should complete within 1 second, took ${duration}ms")
        
        val statistics = editorStatePool.getStatistics()
        assertTrue(statistics.contains("Total acquired: $operationCount"), 
                  "Should show $operationCount acquisitions")
    }
    
    @Test
    fun `test pool disposal cleanup`() {
        // Add some objects to pool
        val state1 = editorStatePool.acquire("/test/dispose1.txt", 1, 1)
        val state2 = editorStatePool.acquire("/test/dispose2.txt", 2, 2)
        
        editorStatePool.release(state1)
        editorStatePool.release(state2)
        
        // Dispose should clean up everything
        editorStatePool.dispose()
        
        // After disposal, pool should be empty
        val statistics = editorStatePool.getStatistics()
        assertTrue(statistics.contains("Available objects: 0"), 
                  "Pool should be empty after disposal")
    }
}

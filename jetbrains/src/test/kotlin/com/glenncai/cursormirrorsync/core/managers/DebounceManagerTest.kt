package com.glenncai.cursormirrorsync.core.managers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebounceManagerTest {
    
    private lateinit var debounceManager: DebounceManager
    
    @BeforeEach
    fun setUp() {
        debounceManager = DebounceManager()
    }
    
    @AfterEach
    fun tearDown() {
        debounceManager.dispose()
    }
    
    @Test
    fun `test basic debounce functionality`() {
        val counter = AtomicInteger(0)
        val latch = CountDownLatch(1)
        
        // Schedule multiple debounce operations with the same key
        debounceManager.debounce("test-key", 100) {
            counter.incrementAndGet()
            latch.countDown()
        }
        
        debounceManager.debounce("test-key", 100) {
            counter.incrementAndGet()
            latch.countDown()
        }
        
        debounceManager.debounce("test-key", 100) {
            counter.incrementAndGet()
            latch.countDown()
        }
        
        // Wait for the debounced action to execute
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Debounced action should execute")
        
        // Only the last action should have executed
        assertEquals(1, counter.get(), "Only one debounced action should execute")
    }
    
    @Test
    fun `test multiple keys work independently`() {
        val counter1 = AtomicInteger(0)
        val counter2 = AtomicInteger(0)
        val latch = CountDownLatch(2)
        
        debounceManager.debounce("key1", 100) {
            counter1.incrementAndGet()
            latch.countDown()
        }
        
        debounceManager.debounce("key2", 100) {
            counter2.incrementAndGet()
            latch.countDown()
        }
        
        // Wait for both actions to execute
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Both debounced actions should execute")
        
        assertEquals(1, counter1.get(), "First key action should execute")
        assertEquals(1, counter2.get(), "Second key action should execute")
    }
    
    @Test
    fun `test cancel functionality`() {
        val counter = AtomicInteger(0)
        
        debounceManager.debounce("test-key", 200) {
            counter.incrementAndGet()
        }
        
        // Cancel the task before it executes
        val cancelled = debounceManager.cancel("test-key")
        assertTrue(cancelled, "Task should be cancelled")
        
        // Wait longer than the debounce delay
        Thread.sleep(300)
        
        assertEquals(0, counter.get(), "Cancelled action should not execute")
    }
    
    @Test
    fun `test cancelAll functionality`() {
        val counter = AtomicInteger(0)
        
        debounceManager.debounce("key1", 200) { counter.incrementAndGet() }
        debounceManager.debounce("key2", 200) { counter.incrementAndGet() }
        debounceManager.debounce("key3", 200) { counter.incrementAndGet() }
        
        assertEquals(3, debounceManager.getPendingActionCount(), "Should have 3 pending actions")
        
        val cancelledCount = debounceManager.cancelAll()
        assertEquals(3, cancelledCount, "Should cancel 3 actions")
        assertEquals(0, debounceManager.getPendingActionCount(), "Should have no pending actions")
        
        // Wait longer than the debounce delay
        Thread.sleep(300)
        
        assertEquals(0, counter.get(), "No cancelled actions should execute")
    }
    
    @Test
    fun `test hasPendingAction functionality`() {
        assertFalse(debounceManager.hasPendingAction("test-key"), "Should not have pending action initially")
        
        debounceManager.debounce("test-key", 200) { /* no-op */ }
        
        assertTrue(debounceManager.hasPendingAction("test-key"), "Should have pending action after scheduling")
        
        debounceManager.cancel("test-key")
        
        assertFalse(debounceManager.hasPendingAction("test-key"), "Should not have pending action after cancellation")
    }
    
    @Test
    fun `test periodic task functionality`() {
        val counter = AtomicInteger(0)
        val latch = CountDownLatch(3) // Wait for 3 executions
        
        debounceManager.scheduleAtFixedRate("periodic-task", 50, 100) {
            counter.incrementAndGet()
            latch.countDown()
        }
        
        // Wait for multiple executions
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS), "Periodic task should execute multiple times")
        
        // Should have executed at least 3 times
        assertTrue(counter.get() >= 3, "Periodic task should execute at least 3 times, got ${counter.get()}")
    }
    
    @Test
    fun `test dispose prevents new operations`() {
        debounceManager.dispose()
        
        val counter = AtomicInteger(0)
        
        // Should not throw exception but should not execute
        debounceManager.debounce("test-key", 100) {
            counter.incrementAndGet()
        }
        
        Thread.sleep(200)
        
        assertEquals(0, counter.get(), "Disposed manager should not execute new tasks")
    }
    
    @Test
    fun `test error handling in debounced action`() {
        val latch = CountDownLatch(1)
        
        // Schedule an action that throws an exception
        debounceManager.debounce("error-key", 100) {
            latch.countDown()
            throw RuntimeException("Test exception")
        }
        
        // The action should still execute (and the exception should be caught internally)
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Action should execute despite throwing exception")
    }
}

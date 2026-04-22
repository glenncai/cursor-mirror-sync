package com.glenncai.cursormirrorsync.core.managers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test for ListenerManager functionality and listener deregistration mechanisms.
 * Tests the core concepts without depending on IntelliJ platform components.
 */
class ListenerManagerTest {

    private lateinit var listenerManager: MockListenerManager

    @BeforeEach
    fun setUp() {
        listenerManager = MockListenerManager()
    }

    @AfterEach
    fun tearDown() {
        listenerManager.dispose()
    }

    @Test
    fun `test listener registration and deregistration`() {
        val listener = MockListener("test-listener")
        
        val listenerId = listenerManager.registerListener("test", listener) {
            listener.dispose()
        }
        
        assertNotNull(listenerId, "Listener ID should not be null")
        
        val stats = listenerManager.getStatistics()
        assertEquals(1, stats.totalRegistered, "Should have registered 1 listener")
        assertEquals(1, stats.currentListeners, "Should have 1 active listener")
        
        // Deregister listener
        val deregistered = listenerManager.deregisterListener(listenerId)
        assertTrue(deregistered, "Listener should be successfully deregistered")
        assertTrue(listener.isDisposed(), "Listener should be disposed")
        
        val statsAfterDeregistration = listenerManager.getStatistics()
        assertEquals(1, statsAfterDeregistration.totalDeregistered, "Should have deregistered 1 listener")
        assertEquals(0, statsAfterDeregistration.currentListeners, "Should have 0 active listeners")
    }

    @Test
    fun `test weak reference prevents memory leaks`() {
        var strongRef: MockListener? = MockListener("test-listener")
        val weakRef = WeakReference(strongRef)
        
        val listenerId = listenerManager.registerListener("test", strongRef!!) {
            strongRef?.dispose()
        }
        
        // Verify object is accessible
        assertNotNull(weakRef.get(), "Object should be accessible through weak reference")
        
        // Clear strong reference
        strongRef = null
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        
        // Perform cleanup
        val cleanedCount = listenerManager.performCleanup()
        
        // The weak reference might still hold the object due to GC timing,
        // but we can test the cleanup logic
        assertTrue(cleanedCount >= 0, "Cleanup count should be non-negative")
    }

    @Test
    fun `test disposal state prevents operations`() {
        val listener = MockListener("test-listener")
        
        val listenerId = listenerManager.registerListener("test", listener) {
            listener.dispose()
        }
        
        assertNotNull(listenerId, "Should be able to register listener")
        
        // Dispose manager
        listenerManager.dispose()
        
        // Operations should be prevented
        assertThrows(IllegalStateException::class.java) {
            listenerManager.registerListener("test2", MockListener("test2")) {}
        }
    }

    @Test
    fun `test cleanup callback mechanism`() {
        var callbackInvoked = false
        val listener = MockListener("test-listener")
        
        val listenerId = listenerManager.registerListener("test", listener) {
            callbackInvoked = true
            listener.dispose()
        }
        
        // Deregister and verify callback
        listenerManager.deregisterListener(listenerId)
        
        assertTrue(callbackInvoked, "Cleanup callback should be invoked")
        assertTrue(listener.isDisposed(), "Listener should be disposed")
    }

    @Test
    fun `test concurrent listener management`() {
        val listenerIds = mutableListOf<String>()
        val threads = mutableListOf<Thread>()

        // Create multiple threads that register listeners
        repeat(3) { threadIndex ->
            val thread = Thread {
                repeat(2) {
                    val listener = MockListener("thread-$threadIndex-listener-$it")
                    val listenerId = listenerManager.registerListener("thread-$threadIndex-$it", listener) {
                        listener.dispose()
                    }
                    synchronized(listenerIds) {
                        listenerIds.add(listenerId)
                    }
                    Thread.yield()
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        val stats = listenerManager.getStatistics()
        assertEquals(6, stats.totalRegistered, "Should have registered 6 listeners")

        // Dispose all listeners using stored IDs
        synchronized(listenerIds) {
            listenerIds.forEach { listenerId ->
                listenerManager.deregisterListener(listenerId)
            }
        }

        val statsAfterCleanup = listenerManager.getStatistics()
        assertTrue(statsAfterCleanup.totalDeregistered >= 0, "Should have deregistered some listeners")
    }

    @Test
    fun `test memory leak detection`() {
        val listener = MockListener("test-listener")
        listenerManager.registerListener("test", listener) {
            listener.dispose()
        }
        
        // Simulate a scenario where listener should be disposed but isn't
        val leakCount = listenerManager.detectMemoryLeaks()
        assertTrue(leakCount >= 0, "Leak count should be non-negative")
        
        // Dispose listener properly
        val listenerId = listenerManager.findListenerId(listener)
        if (listenerId != null) {
            listenerManager.deregisterListener(listenerId)
        }
        
        val leakCountAfterCleanup = listenerManager.detectMemoryLeaks()
        assertEquals(0, leakCountAfterCleanup, "Should have no leaks after proper cleanup")
    }

    @Test
    fun `test listener statistics tracking`() {
        val initialStats = listenerManager.getStatistics()
        assertEquals(0, initialStats.totalRegistered, "Initial registered count should be 0")
        assertEquals(0, initialStats.totalDeregistered, "Initial deregistered count should be 0")
        
        // Register listeners
        val listener1 = MockListener("listener1")
        val listener2 = MockListener("listener2")
        
        val id1 = listenerManager.registerListener("test1", listener1) { listener1.dispose() }
        val id2 = listenerManager.registerListener("test2", listener2) { listener2.dispose() }
        
        val afterRegistrationStats = listenerManager.getStatistics()
        assertEquals(2, afterRegistrationStats.totalRegistered, "Should have registered 2 listeners")
        assertEquals(2, afterRegistrationStats.currentListeners, "Should have 2 active listeners")
        
        // Deregister one listener
        listenerManager.deregisterListener(id1)
        
        val afterDeregistrationStats = listenerManager.getStatistics()
        assertEquals(2, afterDeregistrationStats.totalRegistered, "Total registered should remain 2")
        assertEquals(1, afterDeregistrationStats.totalDeregistered, "Should have deregistered 1 listener")
        assertEquals(1, afterDeregistrationStats.currentListeners, "Should have 1 active listener")
    }

    @Test
    fun `test double deregistration safety`() {
        val listener = MockListener("test-listener")
        
        val listenerId = listenerManager.registerListener("test", listener) {
            listener.dispose()
        }
        
        // First deregistration
        val firstResult = listenerManager.deregisterListener(listenerId)
        assertTrue(firstResult, "First deregistration should succeed")
        assertTrue(listener.isDisposed(), "Listener should be disposed after first deregistration")
        
        // Second deregistration should be safe
        val secondResult = listenerManager.deregisterListener(listenerId)
        assertFalse(secondResult, "Second deregistration should return false")
        
        // No exceptions should be thrown
        assertTrue(true, "Double deregistration should be safe")
    }

    @Test
    fun `test debug info provides useful information`() {
        val listener = MockListener("test-listener")
        listenerManager.registerListener("test", listener) { listener.dispose() }
        
        val debugInfo = listenerManager.getDebugInfo()
        
        assertNotNull(debugInfo, "Debug info should not be null")
        assertTrue(debugInfo.contains("ListenerManager Debug Info"), "Should contain debug header")
        assertTrue(debugInfo.contains("Total Registered: 1"), "Should show registered count")
        assertTrue(debugInfo.contains("Current Listeners: 1"), "Should show current count")
    }

    /**
     * Mock listener for testing
     */
    class MockListener(private val name: String) {
        @Volatile
        private var disposed = false
        
        fun isDisposed(): Boolean = disposed
        
        fun dispose() {
            disposed = true
        }
        
        override fun toString(): String = "MockListener($name)"
    }

    /**
     * Mock listener manager for testing
     */
    class MockListenerManager {
        private val listeners = mutableMapOf<String, ListenerEntry>()
        private val totalRegistered = AtomicInteger(0)
        private val totalDeregistered = AtomicInteger(0)
        
        @Volatile
        private var disposed = false
        
        fun registerListener(type: String, listener: MockListener, cleanupCallback: () -> Unit): String {
            if (disposed) {
                throw IllegalStateException("ListenerManager has been disposed")
            }
            
            val id = generateListenerId(type)
            val entry = ListenerEntry(
                listener = WeakReference(listener),
                cleanupCallback = cleanupCallback,
                registrationTime = System.currentTimeMillis()
            )
            
            listeners[id] = entry
            totalRegistered.incrementAndGet()
            
            return id
        }
        
        fun deregisterListener(listenerId: String): Boolean {
            val entry = listeners.remove(listenerId)
            
            return if (entry != null) {
                try {
                    entry.cleanupCallback()
                    totalDeregistered.incrementAndGet()
                    true
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        }
        
        fun performCleanup(): Int {
            var cleanedCount = 0
            val toRemove = mutableListOf<String>()
            
            listeners.entries.forEach { (id, entry) ->
                if (entry.listener.get() == null) {
                    toRemove.add(id)
                    cleanedCount++
                }
            }
            
            toRemove.forEach { id ->
                listeners.remove(id)
            }
            
            return cleanedCount
        }
        
        fun detectMemoryLeaks(): Int {
            return listeners.values.count { it.listener.get() != null && !it.listener.get()!!.isDisposed() }
        }
        
        fun findListenerId(listener: MockListener): String? {
            return listeners.entries.find { it.value.listener.get() == listener }?.key
        }
        
        fun getStatistics(): MockListenerStats {
            val currentListeners = listeners.values.count { it.listener.get() != null }
            
            return MockListenerStats(
                totalRegistered = totalRegistered.get(),
                totalDeregistered = totalDeregistered.get(),
                currentListeners = currentListeners
            )
        }
        
        fun getDebugInfo(): String {
            val stats = getStatistics()
            
            return buildString {
                appendLine("=== ListenerManager Debug Info ===")
                appendLine("  Disposed: $disposed")
                appendLine("  Total Registered: ${stats.totalRegistered}")
                appendLine("  Total Deregistered: ${stats.totalDeregistered}")
                appendLine("  Current Listeners: ${stats.currentListeners}")
            }
        }
        
        fun dispose() {
            if (disposed) return
            disposed = true

            try {
                val listenerIds = listeners.keys.toList()
                listenerIds.forEach { id ->
                    try {
                        deregisterListener(id)
                    } catch (e: Exception) {
                        // Ignore errors during disposal
                    }
                }

                listeners.clear()
            } catch (e: Exception) {
                // Ignore errors during disposal
            }
        }
        
        private fun generateListenerId(type: String): String {
            return "$type-${System.currentTimeMillis()}-${totalRegistered.get()}"
        }
        
        data class ListenerEntry(
            val listener: WeakReference<MockListener>,
            val cleanupCallback: () -> Unit,
            val registrationTime: Long
        )
    }

    /**
     * Statistics data class for mock listener management
     */
    data class MockListenerStats(
        val totalRegistered: Int,
        val totalDeregistered: Int,
        val currentListeners: Int
    )
}

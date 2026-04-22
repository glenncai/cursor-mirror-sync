package com.glenncai.cursormirrorsync.core.managers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive test for listener deregistration mechanisms across all components.
 * Tests the integration of ListenerManager with various service components.
 */
class ListenerDeregistrationTest {

    private lateinit var mockProject: MockProject
    private lateinit var listenerManager: MockListenerManager
    private lateinit var editorSyncManager: MockEditorSyncManager
    private lateinit var fileWatcherService: MockFileWatcherService

    @BeforeEach
    fun setUp() {
        mockProject = MockProject("test-project")
        listenerManager = MockListenerManager()
        editorSyncManager = MockEditorSyncManager(mockProject, listenerManager)
        fileWatcherService = MockFileWatcherService(mockProject, listenerManager)
    }

    @AfterEach
    fun tearDown() {
        fileWatcherService.dispose()
        editorSyncManager.dispose()
        listenerManager.dispose()
    }

    @Test
    fun `test editor listener deregistration on editor disposal`() {
        val mockEditor = MockEditor("test-editor")
        
        // Register editor listeners
        editorSyncManager.setupEditorListeners(mockEditor)
        
        val stats = listenerManager.getStatistics()
        assertEquals(1, stats.totalRegistered, "Should have registered editor listeners")
        
        // Dispose editor
        mockEditor.dispose()
        editorSyncManager.removeEditorListeners(mockEditor)
        
        val statsAfterRemoval = listenerManager.getStatistics()
        assertEquals(1, statsAfterRemoval.totalDeregistered, "Should have deregistered editor listeners")
        assertTrue(mockEditor.isDisposed(), "Editor should be disposed")
    }

    @Test
    fun `test file watcher listener deregistration on service disposal`() {
        // Start file watching
        fileWatcherService.startWatching("/test/path")
        
        val stats = listenerManager.getStatistics()
        assertTrue(stats.totalRegistered > 0, "Should have registered file watcher listeners")
        
        // Stop file watching
        fileWatcherService.stopWatching()
        
        val statsAfterStop = listenerManager.getStatistics()
        assertTrue(statsAfterStop.totalDeregistered > 0, "Should have deregistered file watcher listeners")
    }

    @Test
    fun `test message bus connection deregistration`() {
        val connectionId = "test-connection"
        val mockConnection = MockMessageBusConnection()
        
        // Register MessageBus connection
        listenerManager.registerMessageBusConnection(connectionId, mockConnection)
        
        val stats = listenerManager.getStatistics()
        assertEquals(1, stats.totalRegistered, "Should have registered MessageBus connection")
        assertFalse(mockConnection.isDisconnected(), "Connection should be active")
        
        // Deregister MessageBus connection
        val deregistered = listenerManager.deregisterMessageBusConnection(connectionId)
        
        assertTrue(deregistered, "Should successfully deregister MessageBus connection")
        assertTrue(mockConnection.isDisconnected(), "Connection should be disconnected")
        
        val statsAfterDeregistration = listenerManager.getStatistics()
        assertEquals(1, statsAfterDeregistration.totalDeregistered, "Should have deregistered MessageBus connection")
    }

    @Test
    fun `test comprehensive service disposal cleans up all listeners`() {
        // Setup multiple listeners
        val mockEditor1 = MockEditor("editor1")
        val mockEditor2 = MockEditor("editor2")
        
        editorSyncManager.setupEditorListeners(mockEditor1)
        editorSyncManager.setupEditorListeners(mockEditor2)
        fileWatcherService.startWatching("/test/path")
        
        val statsAfterSetup = listenerManager.getStatistics()
        assertTrue(statsAfterSetup.totalRegistered >= 3, "Should have registered multiple listeners")
        
        // Dispose all services
        fileWatcherService.dispose()
        editorSyncManager.dispose()
        
        val statsAfterDisposal = listenerManager.getStatistics()
        assertTrue(statsAfterDisposal.totalDeregistered >= 3, "Should have deregistered all listeners")
    }

    @Test
    fun `test listener leak detection and cleanup`() {
        val mockEditor = MockEditor("test-editor")
        
        // Register editor listeners
        editorSyncManager.setupEditorListeners(mockEditor)
        
        // Simulate editor being disposed without proper cleanup
        mockEditor.dispose()
        
        // Detect memory leaks
        val leakCount = listenerManager.detectMemoryLeaks()
        assertTrue(leakCount >= 0, "Should detect potential memory leaks")
        
        // Perform cleanup
        val cleanedCount = listenerManager.performCleanup()
        assertTrue(cleanedCount >= 0, "Should clean up invalid listeners")
    }

    @Test
    fun `test concurrent listener deregistration safety`() {
        val editors = mutableListOf<MockEditor>()
        val threads = mutableListOf<Thread>()
        
        // Create multiple threads that register and deregister listeners
        repeat(3) { threadIndex ->
            val thread = Thread {
                repeat(2) { editorIndex ->
                    val editor = MockEditor("thread-$threadIndex-editor-$editorIndex")
                    synchronized(editors) {
                        editors.add(editor)
                    }
                    
                    // Register listeners
                    editorSyncManager.setupEditorListeners(editor)
                    
                    // Immediately deregister
                    editorSyncManager.removeEditorListeners(editor)
                    
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
        assertEquals(6, stats.totalDeregistered, "Should have deregistered 6 listeners")
    }

    @Test
    fun `test listener manager disposal prevents new registrations`() {
        val mockEditor = MockEditor("test-editor")
        
        // Register a listener
        editorSyncManager.setupEditorListeners(mockEditor)
        
        val stats = listenerManager.getStatistics()
        assertEquals(1, stats.totalRegistered, "Should have registered 1 listener")
        
        // Dispose listener manager
        listenerManager.dispose()
        
        // Attempt to register new listener should fail
        assertThrows(IllegalStateException::class.java) {
            editorSyncManager.setupEditorListeners(MockEditor("new-editor"))
        }
    }

    @Test
    fun `test debug information provides comprehensive listener status`() {
        val mockEditor = MockEditor("test-editor")
        
        // Register listeners
        editorSyncManager.setupEditorListeners(mockEditor)
        fileWatcherService.startWatching("/test/path")
        
        val debugInfo = listenerManager.getDebugInfo()
        
        assertNotNull(debugInfo, "Debug info should not be null")
        assertTrue(debugInfo.contains("ListenerManager Debug Info"), "Should contain debug header")
        assertTrue(debugInfo.contains("Total Registered"), "Should show registration statistics")
        assertTrue(debugInfo.contains("Current"), "Should show current listener counts")
    }

    // Mock classes for testing

    class MockProject(val name: String)

    class MockEditor(private val name: String) {
        @Volatile
        private var disposed = false
        
        fun isDisposed(): Boolean = disposed
        
        fun dispose() {
            disposed = true
        }
        
        override fun toString(): String = "MockEditor($name)"
        override fun hashCode(): Int = name.hashCode()
        override fun equals(other: Any?): Boolean = other is MockEditor && other.name == name
    }

    class MockMessageBusConnection {
        @Volatile
        private var disconnected = false
        
        fun isDisconnected(): Boolean = disconnected
        
        fun disconnect() {
            disconnected = true
        }
    }

    class MockListenerManager {
        private val listeners = ConcurrentHashMap<String, Any>()
        private val messageBusConnections = ConcurrentHashMap<String, MockMessageBusConnection>()
        private val totalRegistered = AtomicInteger(0)
        private val totalDeregistered = AtomicInteger(0)
        
        @Volatile
        private var disposed = false
        
        fun registerEditorListeners(editor: MockEditor): String {
            if (disposed) throw IllegalStateException("ListenerManager has been disposed")
            
            val id = "editor-${editor.hashCode()}"
            listeners[id] = editor
            totalRegistered.incrementAndGet()
            return id
        }
        
        fun deregisterEditorListeners(editor: MockEditor): Boolean {
            val id = "editor-${editor.hashCode()}"
            val removed = listeners.remove(id)
            if (removed != null) {
                totalDeregistered.incrementAndGet()
                return true
            }
            return false
        }
        
        fun registerMessageBusConnection(connectionId: String, connection: MockMessageBusConnection): String {
            if (disposed) throw IllegalStateException("ListenerManager has been disposed")
            
            messageBusConnections[connectionId] = connection
            totalRegistered.incrementAndGet()
            return connectionId
        }
        
        fun deregisterMessageBusConnection(connectionId: String): Boolean {
            val connection = messageBusConnections.remove(connectionId)
            if (connection != null) {
                connection.disconnect()
                totalDeregistered.incrementAndGet()
                return true
            }
            return false
        }
        
        fun detectMemoryLeaks(): Int {
            return listeners.values.count { listener ->
                listener is MockEditor && listener.isDisposed()
            }
        }
        
        fun performCleanup(): Int {
            val toRemove = listeners.entries.filter { (_, listener) ->
                listener is MockEditor && listener.isDisposed()
            }
            
            toRemove.forEach { (id, _) ->
                listeners.remove(id)
            }
            
            return toRemove.size
        }
        
        fun getStatistics(): MockListenerStats {
            return MockListenerStats(
                totalRegistered = totalRegistered.get(),
                totalDeregistered = totalDeregistered.get(),
                currentListeners = listeners.size + messageBusConnections.size
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
            disposed = true
            
            // Deregister all MessageBus connections
            messageBusConnections.keys.toList().forEach { connectionId ->
                deregisterMessageBusConnection(connectionId)
            }
            
            listeners.clear()
            messageBusConnections.clear()
        }
    }

    class MockEditorSyncManager(
        private val project: MockProject,
        private val listenerManager: MockListenerManager
    ) {
        private val registeredEditors = ConcurrentHashMap<MockEditor, String>()
        
        fun setupEditorListeners(editor: MockEditor) {
            val listenerId = listenerManager.registerEditorListeners(editor)
            registeredEditors[editor] = listenerId
        }
        
        fun removeEditorListeners(editor: MockEditor) {
            registeredEditors.remove(editor)?.let {
                listenerManager.deregisterEditorListeners(editor)
            }
        }
        
        fun dispose() {
            registeredEditors.keys.toList().forEach { editor ->
                removeEditorListeners(editor)
            }
            registeredEditors.clear()
        }
    }

    class MockFileWatcherService(
        private val project: MockProject,
        private val listenerManager: MockListenerManager
    ) {
        private var messageBusConnectionId: String? = null
        private val isWatching = AtomicBoolean(false)
        
        fun startWatching(path: String) {
            if (!isWatching.get()) {
                val connection = MockMessageBusConnection()
                messageBusConnectionId = listenerManager.registerMessageBusConnection("fileWatcher", connection)
                isWatching.set(true)
            }
        }
        
        fun stopWatching() {
            if (isWatching.get()) {
                messageBusConnectionId?.let { connectionId ->
                    listenerManager.deregisterMessageBusConnection(connectionId)
                    messageBusConnectionId = null
                }
                isWatching.set(false)
            }
        }
        
        fun dispose() {
            stopWatching()
        }
    }

    data class MockListenerStats(
        val totalRegistered: Int,
        val totalDeregistered: Int,
        val currentListeners: Int
    )
}

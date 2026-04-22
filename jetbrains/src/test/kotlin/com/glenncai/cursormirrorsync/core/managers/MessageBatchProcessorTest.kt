package com.glenncai.cursormirrorsync.core.managers

import com.glenncai.cursormirrorsync.core.Constants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MessageBatchProcessorTest {
    
    private lateinit var messageBatchProcessor: MessageBatchProcessor
    private val sentMessages = mutableListOf<String>()
    private val sendCallCount = AtomicInteger(0)
    private val lastSentMessage = AtomicReference<String>()
    
    @BeforeEach
    fun setUp() {
        sentMessages.clear()
        sendCallCount.set(0)
        lastSentMessage.set(null)
        
        messageBatchProcessor = MessageBatchProcessor { message ->
            sentMessages.add(message)
            sendCallCount.incrementAndGet()
            lastSentMessage.set(message)
            true
        }
    }
    
    @AfterEach
    fun tearDown() {
        messageBatchProcessor.dispose()
    }
    
    @Test
    fun `test single message queuing and sending`() {
        val testMessage = "test message"
        
        val result = messageBatchProcessor.queueMessage(testMessage)
        assertTrue(result, "Message should be queued successfully")
        
        // Wait for processing
        Thread.sleep(Constants.BATCH_TIME_WINDOW_MS + 50)
        
        assertEquals(1, sendCallCount.get(), "Should have sent exactly one message")
        assertTrue(sentMessages.isNotEmpty(), "Should have sent messages")
    }
    
    @Test
    fun `test editor state message queuing`() {
        val editorStateMessage = "{\"type\":\"editorState\",\"filePath\":\"/test.txt\"}"
        
        val result = messageBatchProcessor.queueEditorState(editorStateMessage)
        assertTrue(result, "Editor state message should be queued successfully")
        
        // Wait for processing
        Thread.sleep(Constants.BATCH_TIME_WINDOW_MS + 50)
        
        assertTrue(sendCallCount.get() > 0, "Should have sent at least one message")
    }
    
    @Test
    fun `test config sync message with high priority`() {
        val configMessage = "{\"type\":\"configSync\",\"enableSelectionSync\":true}"
        
        val result = messageBatchProcessor.queueConfigSync(configMessage)
        assertTrue(result, "Config sync message should be queued successfully")
        
        // Wait for processing
        Thread.sleep(Constants.BATCH_TIME_WINDOW_MS + 50)
        
        assertTrue(sendCallCount.get() > 0, "Should have sent at least one message")
    }
    
    @Test
    fun `test urgent message immediate sending`() {
        val urgentMessage = "urgent test message"
        
        val result = messageBatchProcessor.queueMessage(
            urgentMessage, 
            MessageBatchProcessor.MessagePriority.URGENT
        )
        assertTrue(result, "Urgent message should be queued successfully")
        
        // Wait for immediate processing
        Thread.sleep(Constants.URGENT_MESSAGE_DELAY_MS + 50)
        
        assertTrue(sendCallCount.get() > 0, "Should have sent urgent message quickly")
    }
    
    @Test
    fun `test immediate sending without batching`() {
        val immediateMessage = "immediate test message"
        
        val result = messageBatchProcessor.sendImmediately(immediateMessage)
        assertTrue(result, "Message should be sent immediately")
        
        assertEquals(1, sendCallCount.get(), "Should have sent exactly one message immediately")
        assertEquals(immediateMessage, lastSentMessage.get(), "Should have sent the correct message")
    }
    
    @Test
    fun `test batch size threshold triggering`() {
        // Queue messages up to the batch threshold
        repeat(Constants.BATCH_SIZE_THRESHOLD) { index ->
            messageBatchProcessor.queueMessage("message $index")
        }

        // Wait for batch processing
        Thread.sleep(100)

        assertTrue(sendCallCount.get() > 0, "Should have sent messages when batch threshold reached")
    }
    
    @Test
    fun `test message statistics tracking`() {
        // Send some messages
        messageBatchProcessor.queueMessage("test message 1")
        messageBatchProcessor.queueMessage("test message 2")
        messageBatchProcessor.sendImmediately("immediate message")
        
        // Wait for processing
        Thread.sleep(Constants.BATCH_TIME_WINDOW_MS + 100)
        
        val statistics = messageBatchProcessor.getStatistics()
        assertNotNull(statistics, "Statistics should not be null")
        assertTrue(statistics.contains("Total messages sent"), "Statistics should contain message count")
        assertTrue(statistics.contains("Total bytes processed"), "Statistics should contain byte count")
    }
    
    @Test
    fun `test flush functionality`() {
        // Queue some messages
        messageBatchProcessor.queueMessage("message 1")
        messageBatchProcessor.queueMessage("message 2")
        
        // Flush immediately
        messageBatchProcessor.flush()
        
        assertTrue(sendCallCount.get() > 0, "Should have sent messages after flush")
    }
    
    @Test
    fun `test queue capacity limit`() {
        var successCount = 0
        var failureCount = 0
        
        // Try to queue more messages than capacity
        repeat(Constants.BATCH_QUEUE_CAPACITY + 10) { index ->
            val result = messageBatchProcessor.queueMessage("message $index")
            if (result) {
                successCount++
            } else {
                failureCount++
            }
        }
        
        assertTrue(successCount <= Constants.BATCH_QUEUE_CAPACITY, 
                  "Should not queue more than capacity")
        assertTrue(failureCount > 0, "Should reject messages when at capacity")
    }
    
    @Test
    fun `test message type classification`() {
        // Test different message types
        messageBatchProcessor.queueMessage(
            "editor state", 
            MessageBatchProcessor.MessagePriority.NORMAL,
            MessageBatchProcessor.MessageType.EDITOR_STATE
        )
        
        messageBatchProcessor.queueMessage(
            "config sync", 
            MessageBatchProcessor.MessagePriority.HIGH,
            MessageBatchProcessor.MessageType.CONFIG_SYNC
        )
        
        messageBatchProcessor.queueMessage(
            "ping", 
            MessageBatchProcessor.MessagePriority.URGENT,
            MessageBatchProcessor.MessageType.PING
        )
        
        // Wait for processing
        Thread.sleep(Constants.BATCH_TIME_WINDOW_MS + 100)
        
        assertTrue(sendCallCount.get() > 0, "Should have processed different message types")
    }
    
    @Test
    fun `test concurrent message queuing`() {
        val threadCount = 10
        val messagesPerThread = 5
        val latch = CountDownLatch(threadCount)
        
        repeat(threadCount) { threadIndex ->
            Thread {
                try {
                    repeat(messagesPerThread) { messageIndex ->
                        messageBatchProcessor.queueMessage("thread-$threadIndex-message-$messageIndex")
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete")
        
        // Wait for processing
        Thread.sleep(Constants.BATCH_TIME_WINDOW_MS + 200)
        
        assertTrue(sendCallCount.get() > 0, "Should have processed concurrent messages")
    }
    
    @Test
    fun `test processor disposal`() {
        messageBatchProcessor.queueMessage("test message")
        
        // Dispose should flush pending messages
        messageBatchProcessor.dispose()
        
        assertTrue(sendCallCount.get() > 0, "Should have flushed messages on disposal")
        
        // Further operations should not crash
        val statistics = messageBatchProcessor.getStatistics()
        assertNotNull(statistics, "Should still provide statistics after disposal")
    }
    
    @Test
    fun `test error handling in send function`() {
        // Create processor with failing send function
        val failingProcessor = MessageBatchProcessor { _ ->
            false // Always fail
        }
        
        try {
            val result = failingProcessor.sendImmediately("test message")
            assertFalse(result, "Should return false when send function fails")
        } finally {
            failingProcessor.dispose()
        }
    }
}

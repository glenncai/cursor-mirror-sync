package com.glenncai.cursormirrorsync.core.managers

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.glenncai.cursormirrorsync.core.Constants
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance message batch processor for WebSocket communications.
 * Implements intelligent batching strategies to reduce network overhead while
 * maintaining message delivery guarantees and real-time requirements.
 */
class MessageBatchProcessor(
    private val sendFunction: (String) -> Boolean
) {
    
    private val log: Logger = Logger.getInstance(MessageBatchProcessor::class.java)
    private val gson = Gson()
    
    // Thread-safe executor for batch processing
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "MessageBatchProcessor-Thread").apply {
            isDaemon = true
        }
    }
    
    // Message queue and batch state
    private val messageQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private val isProcessing = AtomicBoolean(false)
    private val lastFlushTime = AtomicLong(System.currentTimeMillis())
    private var scheduledFlush: ScheduledFuture<*>? = null
    
    // Statistics
    private val totalMessagesSent = AtomicLong(0)
    private val totalBatchesSent = AtomicLong(0)
    private val totalBytesProcessed = AtomicLong(0)
    
    /**
     * Represents a queued message with metadata
     */
    private data class QueuedMessage(
        val content: String,
        val priority: MessagePriority,
        val timestamp: Long = System.currentTimeMillis(),
        val messageType: MessageType,
        val sizeBytes: Int = content.toByteArray().size
    )
    
    /**
     * Message priority levels
     */
    enum class MessagePriority {
        LOW,      // Regular editor state updates
        NORMAL,   // Standard messages
        HIGH,     // Configuration sync messages
        URGENT    // Critical messages that need immediate sending
    }
    
    /**
     * Message type classification
     */
    enum class MessageType {
        EDITOR_STATE,
        CONFIG_SYNC,
        PING,
        CUSTOM
    }
    
    /**
     * Batch message container for efficient serialization
     */
    private data class BatchMessage(
        val type: String = "batch",
        val messages: List<String>,
        val timestamp: Long = System.currentTimeMillis(),
        val count: Int = messages.size
    )
    
    /**
     * Queues a message for batch processing
     */
    fun queueMessage(
        message: String, 
        priority: MessagePriority = MessagePriority.NORMAL,
        messageType: MessageType = MessageType.CUSTOM
    ): Boolean {
        if (messageQueue.size >= Constants.BATCH_QUEUE_CAPACITY) {
            log.warn("Message queue at capacity, dropping message")
            return false
        }
        
        val queuedMessage = QueuedMessage(message, priority, messageType = messageType)
        messageQueue.offer(queuedMessage)
        
        // Handle urgent messages immediately
        if (priority == MessagePriority.URGENT) {
            scheduleImmediateFlush()
        } else {
            scheduleFlushIfNeeded()
        }
        
        return true
    }
    
    /**
     * Queues an editor state message with optimized handling
     */
    fun queueEditorState(message: String): Boolean {
        return queueMessage(message, MessagePriority.NORMAL, MessageType.EDITOR_STATE)
    }
    
    /**
     * Queues a configuration sync message with high priority
     */
    fun queueConfigSync(message: String): Boolean {
        return queueMessage(message, MessagePriority.HIGH, MessageType.CONFIG_SYNC)
    }
    
    /**
     * Sends a message immediately without batching (for urgent messages)
     */
    fun sendImmediately(message: String): Boolean {
        log.debug("Sending urgent message immediately")
        totalMessagesSent.incrementAndGet()
        totalBytesProcessed.addAndGet(message.toByteArray().size.toLong())
        return sendFunction(message)
    }
    
    /**
     * Schedules a flush if conditions are met
     */
    private fun scheduleFlushIfNeeded() {
        if (shouldFlushImmediately()) {
            scheduleImmediateFlush()
        } else if (scheduledFlush == null || scheduledFlush?.isDone == true) {
            scheduleDelayedFlush()
        }
    }
    
    /**
     * Determines if messages should be flushed immediately
     */
    private fun shouldFlushImmediately(): Boolean {
        val queueSize = messageQueue.size
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFlush = currentTime - lastFlushTime.get()
        
        return when {
            queueSize >= Constants.BATCH_SIZE_THRESHOLD -> {
                log.debug("Flushing due to batch size threshold: $queueSize messages")
                true
            }
            calculateQueueSizeBytes() >= Constants.BATCH_MAX_SIZE_BYTES -> {
                log.debug("Flushing due to size threshold: ${calculateQueueSizeBytes()} bytes")
                true
            }
            timeSinceLastFlush >= Constants.BATCH_FLUSH_INTERVAL_MS -> {
                log.debug("Flushing due to time threshold: ${timeSinceLastFlush}ms")
                true
            }
            hasHighPriorityMessages() -> {
                log.debug("Flushing due to high priority messages")
                true
            }
            else -> false
        }
    }
    
    /**
     * Schedules an immediate flush
     */
    private fun scheduleImmediateFlush() {
        scheduledFlush?.cancel(false)
        scheduledFlush = executor.schedule({
            flushMessages()
        }, Constants.URGENT_MESSAGE_DELAY_MS, TimeUnit.MILLISECONDS)
    }
    
    /**
     * Schedules a delayed flush
     */
    private fun scheduleDelayedFlush() {
        scheduledFlush = executor.schedule({
            flushMessages()
        }, Constants.BATCH_TIME_WINDOW_MS, TimeUnit.MILLISECONDS)
    }
    
    /**
     * Flushes all queued messages
     */
    private fun flushMessages() {
        if (!isProcessing.compareAndSet(false, true)) {
            return // Already processing
        }
        
        try {
            val messagesToSend = mutableListOf<QueuedMessage>()
            
            // Collect messages from queue
            while (messagesToSend.size < Constants.BATCH_SIZE_THRESHOLD && messageQueue.isNotEmpty()) {
                val message = messageQueue.poll()
                if (message != null) {
                    messagesToSend.add(message)
                }
            }
            
            if (messagesToSend.isEmpty()) {
                return
            }
            
            // Group messages by type and priority for optimal batching
            val groupedMessages = groupMessagesByStrategy(messagesToSend)
            
            // Send each group
            groupedMessages.forEach { (strategy, messages) ->
                sendMessageGroup(strategy, messages)
            }
            
            lastFlushTime.set(System.currentTimeMillis())
            
        } catch (e: Exception) {
            log.error("Error flushing messages: ${e.message}", e)
        } finally {
            isProcessing.set(false)
            
            // Schedule next flush if there are still messages
            if (messageQueue.isNotEmpty()) {
                scheduleFlushIfNeeded()
            }
        }
    }
    
    /**
     * Groups messages by optimal sending strategy
     */
    private fun groupMessagesByStrategy(messages: List<QueuedMessage>): Map<SendStrategy, List<QueuedMessage>> {
        val grouped = mutableMapOf<SendStrategy, MutableList<QueuedMessage>>()
        
        messages.forEach { message ->
            val strategy = when {
                message.priority == MessagePriority.URGENT -> SendStrategy.INDIVIDUAL
                message.messageType == MessageType.CONFIG_SYNC -> SendStrategy.INDIVIDUAL
                message.messageType == MessageType.PING -> SendStrategy.INDIVIDUAL
                else -> SendStrategy.BATCH
            }
            
            grouped.getOrPut(strategy) { mutableListOf() }.add(message)
        }
        
        return grouped
    }
    
    /**
     * Sends a group of messages using the specified strategy
     */
    private fun sendMessageGroup(strategy: SendStrategy, messages: List<QueuedMessage>) {
        when (strategy) {
            SendStrategy.INDIVIDUAL -> {
                messages.forEach { message ->
                    sendIndividualMessage(message)
                }
            }
            SendStrategy.BATCH -> {
                if (messages.size == 1) {
                    sendIndividualMessage(messages[0])
                } else {
                    sendBatchedMessages(messages)
                }
            }
        }
    }
    
    /**
     * Sends an individual message
     */
    private fun sendIndividualMessage(message: QueuedMessage) {
        val success = sendFunction(message.content)
        if (success) {
            totalMessagesSent.incrementAndGet()
            totalBytesProcessed.addAndGet(message.sizeBytes.toLong())
            log.debug("Sent individual message: ${message.messageType}")
        } else {
            log.warn("Failed to send individual message: ${message.messageType}")
        }
    }
    
    /**
     * Sends multiple messages as a batch
     */
    private fun sendBatchedMessages(messages: List<QueuedMessage>) {
        val messageContents = messages.map { it.content }
        val batchMessage = BatchMessage(messages = messageContents)
        val batchJson = gson.toJson(batchMessage)
        
        val success = sendFunction(batchJson)
        if (success) {
            totalBatchesSent.incrementAndGet()
            totalMessagesSent.addAndGet(messages.size.toLong())
            totalBytesProcessed.addAndGet(batchJson.toByteArray().size.toLong())
            log.debug("Sent batch of ${messages.size} messages")
        } else {
            log.warn("Failed to send batch of ${messages.size} messages")
            // Fallback: try sending individually
            messages.forEach { sendIndividualMessage(it) }
        }
    }
    
    /**
     * Calculates total size of queued messages in bytes
     */
    private fun calculateQueueSizeBytes(): Int {
        return messageQueue.sumOf { it.sizeBytes }
    }
    
    /**
     * Checks if there are high priority messages in the queue
     */
    private fun hasHighPriorityMessages(): Boolean {
        return messageQueue.any { it.priority == MessagePriority.HIGH || it.priority == MessagePriority.URGENT }
    }
    
    /**
     * Send strategy enumeration
     */
    private enum class SendStrategy {
        INDIVIDUAL,  // Send each message separately
        BATCH       // Combine messages into a batch
    }
    
    /**
     * Forces immediate flush of all pending messages
     */
    fun flush() {
        scheduledFlush?.cancel(false)
        flushMessages()
    }
    
    /**
     * Gets processing statistics
     */
    fun getStatistics(): String {
        return buildString {
            appendLine("Message Batch Processor Statistics:")
            appendLine("  Total messages sent: ${totalMessagesSent.get()}")
            appendLine("  Total batches sent: ${totalBatchesSent.get()}")
            appendLine("  Total bytes processed: ${totalBytesProcessed.get()}")
            appendLine("  Current queue size: ${messageQueue.size}")
            appendLine("  Queue size (bytes): ${calculateQueueSizeBytes()}")
            appendLine("  Is processing: ${isProcessing.get()}")
            appendLine("  Last flush: ${System.currentTimeMillis() - lastFlushTime.get()}ms ago")
        }
    }
    
    /**
     * Disposes the batch processor and releases resources
     */
    fun dispose() {
        scheduledFlush?.cancel(false)
        flush() // Send any remaining messages
        executor.shutdown()
        
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        messageQueue.clear()
        log.info("MessageBatchProcessor disposed")
    }
}

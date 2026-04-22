package com.glenncai.cursormirrorsync.core.managers

import com.intellij.openapi.diagnostic.Logger
import com.glenncai.cursormirrorsync.core.Constants
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Smart reconnection strategy that implements intelligent reconnection logic
 * with network state detection, connection quality assessment, and adaptive backoff.
 */
class ReconnectionStrategy {
    
    private val log: Logger = Logger.getInstance(ReconnectionStrategy::class.java)
    
    // Connection attempt tracking
    private val attemptCount = AtomicInteger(0)
    private val currentDelay = AtomicLong(Constants.INITIAL_RECONNECT_DELAY)
    private val lastConnectionTime = AtomicLong(0)
    private val lastSuccessfulConnection = AtomicLong(0)
    private val isNetworkAvailable = AtomicBoolean(true)
    
    // Connection quality tracking
    private val connectionHistory = ConcurrentLinkedQueue<ConnectionAttempt>()
    private val maxHistorySize = Constants.CONNECTION_QUALITY_WINDOW
    
    // Health monitoring
    private var lastHealthCheck = 0L
    
    /**
     * Data class to track connection attempts
     */
    private data class ConnectionAttempt(
        val timestamp: Long,
        val success: Boolean,
        val duration: Long = 0L,
        val errorType: ErrorType = ErrorType.UNKNOWN
    )
    
    /**
     * Enum for categorizing connection errors
     */
    enum class ErrorType {
        NETWORK_UNREACHABLE,
        CONNECTION_REFUSED,
        TIMEOUT,
        AUTHENTICATION_FAILED,
        PROTOCOL_ERROR,
        UNKNOWN
    }
    
    /**
     * Enum for reconnection decision
     */
    enum class ReconnectionDecision {
        PROCEED,           // Continue with reconnection
        DELAY,            // Delay reconnection
        ABORT_TEMPORARY,  // Temporarily abort (network issues)
        ABORT_PERMANENT   // Permanently abort (too many failures)
    }
    
    /**
     * Determines if a reconnection attempt should be made
     */
    fun shouldAttemptReconnection(): ReconnectionDecision {
        // Check if we've exceeded maximum attempts
        if (attemptCount.get() >= Constants.MAX_RECONNECT_ATTEMPTS) {
            log.warn("Maximum reconnection attempts (${Constants.MAX_RECONNECT_ATTEMPTS}) reached")
            return ReconnectionDecision.ABORT_PERMANENT
        }
        
        // Check network availability
        if (!checkNetworkAvailability()) {
            log.debug("Network unavailable, delaying reconnection")
            return ReconnectionDecision.ABORT_TEMPORARY
        }
        
        // Check connection quality
        val successRate = calculateConnectionSuccessRate()
        if (successRate < Constants.MIN_CONNECTION_SUCCESS_RATE && attemptCount.get() > 3) {
            log.warn("Connection success rate too low: $successRate, temporarily aborting")
            return ReconnectionDecision.ABORT_TEMPORARY
        }
        
        // Check if we should delay based on recent failures
        if (shouldDelayBasedOnRecentFailures()) {
            return ReconnectionDecision.DELAY
        }
        
        return ReconnectionDecision.PROCEED
    }
    
    /**
     * Calculates the appropriate delay for the next reconnection attempt
     */
    fun calculateReconnectionDelay(): Long {
        val attempts = attemptCount.get()
        
        // Use fast reconnect for initial attempts
        if (attempts < Constants.FAST_RECONNECT_THRESHOLD) {
            return Constants.FAST_RECONNECT_DELAY
        }
        
        // Check if we should reset backoff due to previous stable connection
        if (shouldResetBackoff()) {
            currentDelay.set(Constants.INITIAL_RECONNECT_DELAY)
            log.debug("Resetting reconnection backoff due to previous stable connection")
        }
        
        val delay = currentDelay.get()
        
        // Apply exponential backoff for subsequent attempts
        val nextDelay = (delay * Constants.RECONNECT_DELAY_MULTIPLIER).toLong()
            .coerceAtMost(Constants.MAX_RECONNECT_DELAY)
        currentDelay.set(nextDelay)
        
        log.debug("Calculated reconnection delay: ${delay}ms (attempt ${attempts + 1})")
        return delay
    }
    
    /**
     * Records a connection attempt result
     */
    fun recordConnectionAttempt(success: Boolean, duration: Long = 0L, error: Exception? = null) {
        val currentTime = System.currentTimeMillis()
        val errorType = categorizeError(error)
        
        val attempt = ConnectionAttempt(currentTime, success, duration, errorType)
        connectionHistory.offer(attempt)
        
        // Maintain history size
        while (connectionHistory.size > maxHistorySize) {
            connectionHistory.poll()
        }
        
        if (success) {
            lastSuccessfulConnection.set(currentTime)
            // Reset attempt count on successful connection
            attemptCount.set(0)
            currentDelay.set(Constants.INITIAL_RECONNECT_DELAY)
            log.info("Connection successful, resetting reconnection strategy")
        } else {
            attemptCount.incrementAndGet()
            log.debug("Connection failed (attempt ${attemptCount.get()}), error type: $errorType")
        }
        
        lastConnectionTime.set(currentTime)
    }
    
    /**
     * Checks if the network is available
     */
    private fun checkNetworkAvailability(): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Cache network check for a short period to avoid excessive checks
        if (currentTime - lastHealthCheck < Constants.HEALTH_CHECK_INTERVAL) {
            return isNetworkAvailable.get()
        }
        
        lastHealthCheck = currentTime
        
        try {
            // Try to connect to localhost (where VSCode should be running)
            Socket().use { socket ->
                socket.connect(
                    java.net.InetSocketAddress(Constants.LOCALHOST, 80),
                    Constants.NETWORK_CHECK_TIMEOUT.toInt()
                )
            }
            isNetworkAvailable.set(true)
            return true
        } catch (e: Exception) {
            // Try alternative network check
            try {
                InetAddress.getByName(Constants.LOCALHOST).isReachable(Constants.NETWORK_CHECK_TIMEOUT.toInt())
                isNetworkAvailable.set(true)
                return true
            } catch (e2: Exception) {
                log.debug("Network availability check failed: ${e2.message}")
                isNetworkAvailable.set(false)
                return false
            }
        }
    }
    
    /**
     * Calculates the connection success rate based on recent history
     */
    private fun calculateConnectionSuccessRate(): Double {
        if (connectionHistory.isEmpty()) {
            return 1.0 // Assume success if no history
        }
        
        val successCount = connectionHistory.count { it.success }
        return successCount.toDouble() / connectionHistory.size
    }
    
    /**
     * Determines if reconnection should be delayed based on recent failures
     */
    private fun shouldDelayBasedOnRecentFailures(): Boolean {
        val currentTime = System.currentTimeMillis()
        val recentFailures = connectionHistory
            .filter { !it.success && currentTime - it.timestamp < 30000 } // Last 30 seconds
            .size
        
        return recentFailures >= 3 // Delay if 3+ failures in last 30 seconds
    }
    
    /**
     * Determines if backoff should be reset due to previous stable connection
     */
    private fun shouldResetBackoff(): Boolean {
        val lastSuccess = lastSuccessfulConnection.get()
        if (lastSuccess == 0L) return false
        
        val timeSinceLastSuccess = System.currentTimeMillis() - lastSuccess
        return timeSinceLastSuccess > Constants.BACKOFF_RESET_THRESHOLD
    }
    
    /**
     * Categorizes connection errors for better decision making
     */
    private fun categorizeError(error: Exception?): ErrorType {
        return when {
            error == null -> ErrorType.UNKNOWN
            error.message?.contains("Connection refused") == true -> ErrorType.CONNECTION_REFUSED
            error.message?.contains("timeout") == true -> ErrorType.TIMEOUT
            error.message?.contains("Network is unreachable") == true -> ErrorType.NETWORK_UNREACHABLE
            error.message?.contains("Authentication") == true -> ErrorType.AUTHENTICATION_FAILED
            error.message?.contains("protocol") == true -> ErrorType.PROTOCOL_ERROR
            else -> ErrorType.UNKNOWN
        }
    }
    
    /**
     * Gets debug information about the reconnection strategy state
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("Reconnection Strategy Debug Info:")
            appendLine("  Attempt count: ${attemptCount.get()}")
            appendLine("  Current delay: ${currentDelay.get()}ms")
            appendLine("  Network available: ${isNetworkAvailable.get()}")
            appendLine("  Connection success rate: ${String.format("%.2f", calculateConnectionSuccessRate())}")
            appendLine("  History size: ${connectionHistory.size}")
            appendLine("  Last successful connection: ${if (lastSuccessfulConnection.get() > 0) "${System.currentTimeMillis() - lastSuccessfulConnection.get()}ms ago" else "Never"}")
        }
    }
    
    /**
     * Resets the reconnection strategy state
     */
    fun reset() {
        attemptCount.set(0)
        currentDelay.set(Constants.INITIAL_RECONNECT_DELAY)
        connectionHistory.clear()
        lastConnectionTime.set(0)
        lastSuccessfulConnection.set(0)
        isNetworkAvailable.set(true)
        log.debug("Reconnection strategy reset")
    }
}

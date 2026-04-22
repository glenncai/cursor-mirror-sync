package com.glenncai.cursormirrorsync.core.managers

import com.glenncai.cursormirrorsync.core.Constants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

class ReconnectionStrategyTest {
    
    private lateinit var reconnectionStrategy: ReconnectionStrategy
    
    @BeforeEach
    fun setUp() {
        reconnectionStrategy = ReconnectionStrategy()
    }
    
    @AfterEach
    fun tearDown() {
        reconnectionStrategy.reset()
    }
    
    @Test
    fun `test initial state allows reconnection`() {
        val decision = reconnectionStrategy.shouldAttemptReconnection()
        assertEquals(ReconnectionStrategy.ReconnectionDecision.PROCEED, decision)
    }
    
    @Test
    fun `test successful connection resets attempt count`() {
        // Record some failed attempts
        reconnectionStrategy.recordConnectionAttempt(false, 1000)
        reconnectionStrategy.recordConnectionAttempt(false, 1000)
        
        // Record successful connection
        reconnectionStrategy.recordConnectionAttempt(true, 500)
        
        // Should allow reconnection again
        val decision = reconnectionStrategy.shouldAttemptReconnection()
        assertEquals(ReconnectionStrategy.ReconnectionDecision.PROCEED, decision)
    }
    
    @Test
    fun `test maximum attempts reached aborts permanently`() {
        // Record maximum number of failed attempts
        repeat(Constants.MAX_RECONNECT_ATTEMPTS) {
            reconnectionStrategy.recordConnectionAttempt(false, 1000)
        }
        
        val decision = reconnectionStrategy.shouldAttemptReconnection()
        assertEquals(ReconnectionStrategy.ReconnectionDecision.ABORT_PERMANENT, decision)
    }
    
    @Test
    fun `test fast reconnect delay for initial attempts`() {
        // First few attempts should use fast reconnect delay
        repeat(Constants.FAST_RECONNECT_THRESHOLD - 1) {
            reconnectionStrategy.recordConnectionAttempt(false, 1000)
            val delay = reconnectionStrategy.calculateReconnectionDelay()
            assertEquals(Constants.FAST_RECONNECT_DELAY, delay)
        }
    }
    
    @Test
    fun `test exponential backoff after fast reconnect threshold`() {
        // Record enough failures to exceed fast reconnect threshold
        repeat(Constants.FAST_RECONNECT_THRESHOLD) {
            reconnectionStrategy.recordConnectionAttempt(false, 1000)
        }
        
        // Next delay should be larger than fast reconnect delay
        val delay = reconnectionStrategy.calculateReconnectionDelay()
        assertTrue(delay > Constants.FAST_RECONNECT_DELAY)
    }
    
    @Test
    fun `test connection quality tracking`() {
        // Record mixed success/failure pattern
        reconnectionStrategy.recordConnectionAttempt(true, 500)
        reconnectionStrategy.recordConnectionAttempt(false, 1000)
        reconnectionStrategy.recordConnectionAttempt(true, 600)
        reconnectionStrategy.recordConnectionAttempt(false, 1000)
        
        // Should still allow reconnection with mixed results
        val decision = reconnectionStrategy.shouldAttemptReconnection()
        assertEquals(ReconnectionStrategy.ReconnectionDecision.PROCEED, decision)
    }
    
    @Test
    fun `test debug info provides useful information`() {
        reconnectionStrategy.recordConnectionAttempt(false, 1000)
        reconnectionStrategy.recordConnectionAttempt(true, 500)
        
        val debugInfo = reconnectionStrategy.getDebugInfo()
        
        assertNotNull(debugInfo)
        assertTrue(debugInfo.contains("Reconnection Strategy Debug Info"))
        assertTrue(debugInfo.contains("Attempt count"))
        assertTrue(debugInfo.contains("Current delay"))
        assertTrue(debugInfo.contains("Network available"))
        assertTrue(debugInfo.contains("Connection success rate"))
    }
    
    @Test
    fun `test reset clears all state`() {
        // Record some attempts
        reconnectionStrategy.recordConnectionAttempt(false, 1000)
        reconnectionStrategy.recordConnectionAttempt(false, 1000)
        
        // Reset should clear everything
        reconnectionStrategy.reset()
        
        // Should be back to initial state
        val decision = reconnectionStrategy.shouldAttemptReconnection()
        assertEquals(ReconnectionStrategy.ReconnectionDecision.PROCEED, decision)
        
        val delay = reconnectionStrategy.calculateReconnectionDelay()
        assertEquals(Constants.FAST_RECONNECT_DELAY, delay)
    }
    
    @Test
    fun `test error categorization affects strategy`() {
        // Test different types of errors
        val networkError = RuntimeException("Network is unreachable")
        val connectionRefused = RuntimeException("Connection refused")
        val timeoutError = RuntimeException("Connection timeout")
        
        reconnectionStrategy.recordConnectionAttempt(false, 1000, networkError)
        reconnectionStrategy.recordConnectionAttempt(false, 1000, connectionRefused)
        reconnectionStrategy.recordConnectionAttempt(false, 1000, timeoutError)
        
        // Strategy should still work with categorized errors
        val decision = reconnectionStrategy.shouldAttemptReconnection()
        assertNotNull(decision)
    }
    
    @Test
    fun `test delay calculation respects maximum`() {
        // Record many failed attempts to trigger maximum delay
        repeat(20) {
            reconnectionStrategy.recordConnectionAttempt(false, 1000)
            val delay = reconnectionStrategy.calculateReconnectionDelay()
            assertTrue(delay <= Constants.MAX_RECONNECT_DELAY, "Delay $delay exceeds maximum ${Constants.MAX_RECONNECT_DELAY}")
        }
    }
    
    @Test
    fun `test connection duration tracking`() {
        // Test that connection duration is properly tracked
        val shortDuration = 100L
        val longDuration = 5000L
        
        reconnectionStrategy.recordConnectionAttempt(true, shortDuration)
        reconnectionStrategy.recordConnectionAttempt(true, longDuration)
        
        val debugInfo = reconnectionStrategy.getDebugInfo()
        assertNotNull(debugInfo)
        assertTrue(debugInfo.contains("Last successful connection"))
    }
    
    @Test
    fun `test strategy handles rapid successive calls`() {
        // Test that strategy can handle rapid successive calls without issues
        repeat(100) {
            val decision = reconnectionStrategy.shouldAttemptReconnection()
            assertNotNull(decision)
            
            val delay = reconnectionStrategy.calculateReconnectionDelay()
            assertTrue(delay >= 0)
        }
    }
}

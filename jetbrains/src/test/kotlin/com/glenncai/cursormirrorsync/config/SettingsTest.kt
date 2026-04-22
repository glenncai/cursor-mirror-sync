package com.glenncai.cursormirrorsync.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import com.glenncai.cursormirrorsync.core.Constants

class SettingsTest {
    
    private lateinit var settings: Settings
    
    @BeforeEach
    fun setUp() {
        settings = Settings()
    }
    
    @Test
    fun `test valid port range 3000-9999`() {
        // Test valid ports within the 4-digit range
        val validPorts = listOf(Constants.MIN_PORT, Constants.MIN_PORT + 1, 5000, 8080, Constants.MAX_PORT)
        
        validPorts.forEach { port ->
            settings.manualPort = port
            assertEquals(port, settings.manualPort, "Port $port should be accepted")
        }
    }
    
    @Test
    fun `test port below minimum gets corrected to 3000`() {
        val invalidLowPorts = listOf(0, 100, Constants.MIN_PORT - 1)

        invalidLowPorts.forEach { port ->
            settings.manualPort = port
            assertEquals(Constants.DEFAULT_PORT, settings.manualPort, "Port $port should be corrected to ${Constants.DEFAULT_PORT}")
        }
    }

    @Test
    fun `test port above maximum gets corrected to 9999`() {
        val invalidHighPorts = listOf(Constants.MAX_PORT + 1, 65535, 99999)

        invalidHighPorts.forEach { port ->
            settings.manualPort = port
            assertEquals(Constants.MAX_PORT, settings.manualPort, "Port $port should be corrected to ${Constants.MAX_PORT}")
        }
    }

    @Test
    fun `test boundary values`() {
        // Test exact boundary values
        settings.manualPort = Constants.MIN_PORT - 1
        assertEquals(Constants.DEFAULT_PORT, settings.manualPort, "Port ${Constants.MIN_PORT - 1} should be corrected to ${Constants.DEFAULT_PORT}")

        settings.manualPort = Constants.ABSOLUTE_MIN_PORT
        assertEquals(Constants.DEFAULT_PORT, settings.manualPort, "Port ${Constants.ABSOLUTE_MIN_PORT} should be corrected to ${Constants.DEFAULT_PORT} (below recommended minimum)")

        settings.manualPort = Constants.MIN_PORT
        assertEquals(Constants.MIN_PORT, settings.manualPort, "Port ${Constants.MIN_PORT} should be accepted")

        settings.manualPort = Constants.MAX_PORT
        assertEquals(Constants.MAX_PORT, settings.manualPort, "Port ${Constants.MAX_PORT} should be accepted")

        settings.manualPort = Constants.MAX_PORT + 1
        assertEquals(Constants.MAX_PORT, settings.manualPort, "Port ${Constants.MAX_PORT + 1} should be corrected to ${Constants.MAX_PORT}")
    }

    @Test
    fun `test negative port values`() {
        val negativePorts = listOf(-1, -100, -9999)

        negativePorts.forEach { port ->
            settings.manualPort = port
            assertEquals(Constants.DEFAULT_PORT, settings.manualPort, "Negative port $port should be corrected to ${Constants.DEFAULT_PORT}")
        }
    }

    @Test
    fun `test default port value`() {
        // Test that default port is within valid range
        val defaultPort = settings.manualPort
        assertTrue(defaultPort in Constants.MIN_PORT..Constants.MAX_PORT, "Default port should be within ${Constants.MIN_PORT}-${Constants.MAX_PORT} range")
        assertEquals(Constants.DEFAULT_PORT, defaultPort, "Default port should be ${Constants.DEFAULT_PORT}")
    }

    @Test
    fun `test auto connect enabled by default`() {
        assertTrue(settings.isAutoConnectEnabled, "Auto connect should be enabled by default")
    }

    @Test
    fun `test auto connect toggle`() {
        settings.isAutoConnectEnabled = false
        assertFalse(settings.isAutoConnectEnabled, "Auto connect should be disabled")

        settings.isAutoConnectEnabled = true
        assertTrue(settings.isAutoConnectEnabled, "Auto connect should be enabled")
    }
    
    @Test
    fun `test state persistence structure`() {
        // Test that the state structure is correct
        val state = settings.getState()
        assertNotNull(state, "State should not be null")
        assertTrue(state.autoConnectEnabled, "Default auto connect should be enabled")
        assertEquals(Constants.DEFAULT_PORT, state.manualPort, "Default manual port should be ${Constants.DEFAULT_PORT}")
        
        // Test state loading
        val newState = Settings.State(autoConnectEnabled = false, manualPort = 5000)
        settings.loadState(newState)
        
        assertFalse(settings.isAutoConnectEnabled, "Auto connect should be disabled after loading state")
        assertEquals(5000, settings.manualPort, "Manual port should be 5000 after loading state")
    }

    @Test
    fun `test port validation with edge cases`() {
        // Test with very large numbers
        settings.manualPort = Int.MAX_VALUE
        assertEquals(Constants.MAX_PORT, settings.manualPort, "Very large port should be corrected to ${Constants.MAX_PORT}")

        // Test with very small numbers
        settings.manualPort = Int.MIN_VALUE
        assertEquals(Constants.DEFAULT_PORT, settings.manualPort, "Very small port should be corrected to ${Constants.DEFAULT_PORT}")
    }
}

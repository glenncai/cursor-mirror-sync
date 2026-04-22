package com.glenncai.cursormirrorsync.core.validators

import com.glenncai.cursormirrorsync.core.Constants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConfigurationValidatorTest {

    @Test
    fun `test port validation with different contexts`() {
        // Test settings context (unified port range validation)
        val settingsErrors = ConfigurationValidator.validatePort(500, "settings")
        assertTrue(settingsErrors.isNotEmpty(), "Port 500 should be invalid for settings context")

        val validSettingsPort = ConfigurationValidator.validatePort(3000, "settings")
        assertTrue(validSettingsPort.isEmpty(), "Port 3000 should be valid for settings context")

        // Test connection context (unified port range validation)
        val connectionErrors = ConfigurationValidator.validatePort(2000, "connection")
        assertTrue(connectionErrors.isNotEmpty(), "Port 2000 should be invalid for connection context")

        val validConnectionPort = ConfigurationValidator.validatePort(5000, "connection")
        assertTrue(validConnectionPort.isEmpty(), "Port 5000 should be valid for connection context")

        // Test config-file context (unified port range validation)
        val configFileErrors = ConfigurationValidator.validatePort(500, "config-file")
        assertTrue(configFileErrors.isNotEmpty(), "Port 500 should be invalid for config-file context")

        val validConfigFilePort = ConfigurationValidator.validatePort(8080, "config-file")
        assertTrue(validConfigFilePort.isEmpty(), "Port 8080 should be valid for config-file context")
    }

    @Test
    fun `test port range is consistent across contexts`() {
        // Regression: previously the three contexts disagreed on the valid range,
        // so a port could pass config-file validation but be rejected by isValidPort.
        // Ensure every context now shares the same MIN_PORT..MAX_PORT window.
        val contexts = listOf("settings", "connection", "config-file", "general")
        val invalidPorts = listOf(0, 1023, 1024, Constants.MIN_PORT - 1, Constants.MAX_PORT + 1, 65535)
        val validPorts = listOf(Constants.MIN_PORT, Constants.DEFAULT_PORT, 5000, 8080, Constants.MAX_PORT)

        contexts.forEach { ctx ->
            invalidPorts.forEach { port ->
                assertTrue(
                    ConfigurationValidator.validatePort(port, ctx).isNotEmpty(),
                    "Port $port must be invalid in context '$ctx'"
                )
            }
            validPorts.forEach { port ->
                assertTrue(
                    ConfigurationValidator.validatePort(port, ctx).isEmpty(),
                    "Port $port must be valid in context '$ctx'"
                )
            }
        }
    }

    @Test
    fun `test validate and correct port stays in lock-step with validatePort`() {
        // Any port accepted by validatePort must be returned unchanged by
        // validateAndCorrectPort; any rejected port must be corrected to a
        // value that passes validation.
        val portsToCheck = listOf(
            -1, 0, 100, 1023, 1024, Constants.MIN_PORT - 1,
            Constants.MIN_PORT, Constants.DEFAULT_PORT, 5000, 8080,
            Constants.MAX_PORT, Constants.MAX_PORT + 1, 65535, Int.MAX_VALUE
        )
        listOf("settings", "connection", "config-file", "general").forEach { ctx ->
            portsToCheck.forEach { port ->
                val errors = ConfigurationValidator.validatePort(port, ctx)
                val corrected = ConfigurationValidator.validateAndCorrectPort(port, ctx)
                if (errors.isEmpty()) {
                    assertEquals(port, corrected, "Valid port $port should not be corrected in context '$ctx'")
                }
                assertTrue(
                    ConfigurationValidator.validatePort(corrected, ctx).isEmpty(),
                    "Corrected port $corrected (from $port) must be valid in context '$ctx'"
                )
            }
        }
    }

    @Test
    fun `test port correction with different contexts`() {
        // Test settings context correction
        val correctedSettingsPort = ConfigurationValidator.validateAndCorrectPort(500, "settings")
        assertEquals(Constants.DEFAULT_PORT, correctedSettingsPort, "Low port should be corrected to default for settings")
        
        val correctedHighSettingsPort = ConfigurationValidator.validateAndCorrectPort(15000, "settings")
        assertEquals(Constants.MAX_PORT, correctedHighSettingsPort, "High port should be corrected to max for settings")

        // Test general context correction
        val correctedGeneralPort = ConfigurationValidator.validateAndCorrectPort(2000, "general")
        assertEquals(Constants.DEFAULT_PORT, correctedGeneralPort, "Low port should be corrected to default for general")
        
        val correctedHighGeneralPort = ConfigurationValidator.validateAndCorrectPort(15000, "general")
        assertEquals(Constants.MAX_PORT, correctedHighGeneralPort, "High port should be corrected to max for general")
    }

    @Test
    fun `test project name validation`() {
        // Test null project name
        val nullErrors = ConfigurationValidator.validateProjectName(null, allowNull = false)
        assertTrue(nullErrors.isNotEmpty(), "Null project name should be invalid when not allowed")
        
        val nullAllowedErrors = ConfigurationValidator.validateProjectName(null, allowNull = true)
        assertTrue(nullAllowedErrors.isEmpty(), "Null project name should be valid when allowed")

        // Test blank project name
        val blankErrors = ConfigurationValidator.validateProjectName("", allowNull = false)
        assertTrue(blankErrors.isNotEmpty(), "Blank project name should be invalid")
        
        val whitespaceErrors = ConfigurationValidator.validateProjectName("   ", allowNull = false)
        assertTrue(whitespaceErrors.isNotEmpty(), "Whitespace-only project name should be invalid")

        // Test valid project name
        val validErrors = ConfigurationValidator.validateProjectName("test-project", allowNull = false)
        assertTrue(validErrors.isEmpty(), "Valid project name should pass validation")
    }

    @Test
    fun `test project path validation`() {
        // Test null project path
        val nullErrors = ConfigurationValidator.validateProjectPath(null, allowNull = false)
        assertTrue(nullErrors.isNotEmpty(), "Null project path should be invalid when not allowed")
        
        val nullAllowedErrors = ConfigurationValidator.validateProjectPath(null, allowNull = true)
        assertTrue(nullAllowedErrors.isEmpty(), "Null project path should be valid when allowed")

        // Test blank project path
        val blankErrors = ConfigurationValidator.validateProjectPath("", allowNull = false)
        assertTrue(blankErrors.isNotEmpty(), "Blank project path should be invalid")

        // Test valid project path
        val validErrors = ConfigurationValidator.validateProjectPath("/path/to/project", allowNull = false)
        assertTrue(validErrors.isEmpty(), "Valid project path should pass validation")
    }

    @Test
    fun `test status validation`() {
        // Test valid statuses
        val nullStatusErrors = ConfigurationValidator.validateStatus(null)
        assertTrue(nullStatusErrors.isEmpty(), "Null status should be valid")
        
        val activeStatusErrors = ConfigurationValidator.validateStatus(Constants.STATUS_ACTIVE)
        assertTrue(activeStatusErrors.isEmpty(), "Active status should be valid")
        
        val inactiveStatusErrors = ConfigurationValidator.validateStatus(Constants.STATUS_INACTIVE)
        assertTrue(inactiveStatusErrors.isEmpty(), "Inactive status should be valid")

        // Test invalid status
        val invalidStatusErrors = ConfigurationValidator.validateStatus("invalid")
        assertTrue(invalidStatusErrors.isNotEmpty(), "Invalid status should fail validation")
    }

    @Test
    fun `test complete configuration validation`() {
        // Test valid configuration
        val validErrors = ConfigurationValidator.validateConfiguration(
            port = 5000,
            projectName = "test-project",
            projectPath = "/path/to/project",
            status = Constants.STATUS_ACTIVE,
            context = "connection"
        )
        assertTrue(validErrors.isEmpty(), "Valid configuration should pass validation")

        // Test invalid configuration
        val invalidErrors = ConfigurationValidator.validateConfiguration(
            port = 500,
            projectName = "",
            projectPath = null,
            status = "invalid",
            context = "connection"
        )
        assertTrue(invalidErrors.isNotEmpty(), "Invalid configuration should fail validation")
        assertTrue(invalidErrors.size >= 4, "Should have multiple validation errors")
    }

    @Test
    fun `test port validation helper methods`() {
        // Test isValidPort
        assertTrue(ConfigurationValidator.isValidPort(5000), "Port 5000 should be valid")
        assertFalse(ConfigurationValidator.isValidPort(2000), "Port 2000 should be invalid")
        assertFalse(ConfigurationValidator.isValidPort(15000), "Port 15000 should be invalid")

        // Test isAbsoluteValidPort
        assertTrue(ConfigurationValidator.isAbsoluteValidPort(2000), "Port 2000 should be absolute valid")
        assertFalse(ConfigurationValidator.isAbsoluteValidPort(500), "Port 500 should be absolute invalid")
        assertFalse(ConfigurationValidator.isAbsoluteValidPort(15000), "Port 15000 should be absolute invalid")
    }

    @Test
    fun `test status validation helper method`() {
        assertTrue(ConfigurationValidator.isValidStatus(null), "Null status should be valid")
        assertTrue(ConfigurationValidator.isValidStatus(Constants.STATUS_ACTIVE), "Active status should be valid")
        assertTrue(ConfigurationValidator.isValidStatus(Constants.STATUS_INACTIVE), "Inactive status should be valid")
        assertFalse(ConfigurationValidator.isValidStatus("invalid"), "Invalid status should be invalid")
    }
}

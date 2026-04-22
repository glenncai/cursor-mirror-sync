package com.glenncai.cursormirrorsync.config

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.glenncai.cursormirrorsync.core.Constants
import com.glenncai.cursormirrorsync.core.validators.ConfigurationValidator

/**
 * Settings management for the Cursor Mirror Sync plugin.
 * Handles configuration persistence and validation.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "CursorMirrorSyncSettings",
    storages = [Storage("cursor-mirror-sync.xml")]
)
class Settings : PersistentStateComponent<Settings.State> {

    companion object {
        private val log = Logger.getInstance(Settings::class.java)

        fun getInstance(project: Project): Settings = project.service<Settings>()

        /**
         * Validates if a port number is within the acceptable range.
         */
        fun isValidPort(port: Int): Boolean = ConfigurationValidator.isValidPort(port)

        /**
         * Validates if a port number is within the absolute minimum range.
         */
        fun isAbsoluteValidPort(port: Int): Boolean = ConfigurationValidator.isAbsoluteValidPort(port)
    }

    data class State(
        var autoConnectEnabled: Boolean = Constants.DEFAULT_AUTO_CONNECT_ENABLED,
        var manualPort: Int = Constants.DEFAULT_PORT
    ) {
        /**
         * Validates the current state and returns any validation errors.
         */
        fun validate(): List<String> {
            return ConfigurationValidator.validatePort(manualPort, "settings")
        }

        /**
         * Returns a copy of this state with validated values.
         */
        fun validated(): State {
            return State(
                autoConnectEnabled = autoConnectEnabled,
                manualPort = validatePortStatic(manualPort)
            )
        }

        companion object {
            /**
             * Static method to validate port numbers.
             */
            fun validatePortStatic(port: Int): Int {
                return ConfigurationValidator.validateAndCorrectPort(port, "settings")
            }
        }
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        // Validate and correct the loaded state
        val validationErrors = state.validate()
        if (validationErrors.isNotEmpty()) {
            log.warn("Settings validation errors found: ${validationErrors.joinToString(", ")}")
            myState = state.validated()
            log.info("Settings corrected to valid values")
        } else {
            myState = state
        }
    }

    // Kotlin property syntax for auto connect
    var isAutoConnectEnabled: Boolean
        get() = myState.autoConnectEnabled
        set(value) {
            myState.autoConnectEnabled = value
            log.info("Auto connect enabled set to: $value")
        }

    // Kotlin property syntax for manual port with validation
    var manualPort: Int
        get() = myState.manualPort
        set(value) {
            val validatedPort = validatePort(value)
            if (validatedPort != value) {
                log.warn("Port $value was corrected to $validatedPort")
            }
            myState.manualPort = validatedPort
            log.info("Manual port set to: $validatedPort")
        }



    /**
     * Validates and corrects a port number to be within acceptable range.
     */
    private fun validatePort(port: Int): Int {
        val correctedPort = ConfigurationValidator.validateAndCorrectPort(port, "settings")

        if (correctedPort != port) {
            when {
                port < Constants.ABSOLUTE_MIN_PORT -> {
                    log.warn("Port $port is below absolute minimum (${Constants.ABSOLUTE_MIN_PORT}). Using ${Constants.DEFAULT_PORT} instead.")
                }
                port < Constants.MIN_PORT -> {
                    log.warn("Port $port is below recommended minimum (${Constants.MIN_PORT}). Using ${Constants.DEFAULT_PORT} instead.")
                }
                port > Constants.MAX_PORT -> {
                    log.warn("Port $port exceeds maximum (${Constants.MAX_PORT}). Using ${Constants.MAX_PORT} instead.")
                }
            }
        }

        return correctedPort
    }

    /**
     * Resets all settings to their default values.
     */
    fun resetToDefaults() {
        myState = State()
        log.info("Settings reset to defaults")
    }

    /**
     * Returns a summary of current settings.
     */
    fun getSettingsSummary(): String {
        return buildString {
            appendLine("Cursor Mirror Sync Settings:")
            appendLine("  Auto Connect Enabled: $isAutoConnectEnabled")
            appendLine("  Manual Port: $manualPort")
            appendLine("  Port Valid: ${isValidPort(manualPort)}")
        }
    }

    /**
     * Validates current settings and returns any issues.
     */
    fun validateCurrentSettings(): List<String> {
        return myState.validate()
    }
}

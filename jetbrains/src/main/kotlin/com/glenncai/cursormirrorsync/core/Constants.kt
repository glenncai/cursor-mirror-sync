package com.glenncai.cursormirrorsync.core

/**
 * Application-wide constants for the Cursor Mirror Sync plugin.
 * Centralizes all constant values to ensure consistency across the codebase.
 */
object Constants {
    
    // Plugin Information
    const val PLUGIN_NAME = "Cursor Mirror"
    const val PLUGIN_ID = "CursorMirrorSync"
    
    // WebSocket Configuration
    const val WEBSOCKET_PATH = "/jetbrains"
    const val WEBSOCKET_PROTOCOL = "ws"
    const val LOCALHOST = "localhost"
    
    // Connection Timeouts and Delays
    const val INITIAL_RECONNECT_DELAY = 1000L // 1 second
    const val MAX_RECONNECT_DELAY = 30000L // 30 seconds
    const val RECONNECT_DELAY_MULTIPLIER = 1.5
    const val MAX_RECONNECT_ATTEMPTS = 10
    const val CONNECTION_TIMEOUT = 5000L // 5 seconds

    // Smart Reconnection Strategy
    const val NETWORK_CHECK_TIMEOUT = 3000L // 3 seconds for network connectivity check
    const val CONNECTION_QUALITY_WINDOW = 10 // Number of recent connections to track for quality assessment
    const val MIN_CONNECTION_SUCCESS_RATE = 0.3 // Minimum 30% success rate to continue reconnecting
    const val FAST_RECONNECT_THRESHOLD = 3 // Use fast reconnect for first 3 attempts
    const val FAST_RECONNECT_DELAY = 500L // 500ms for fast reconnects
    const val HEALTH_CHECK_INTERVAL = 10000L // 10 seconds between health checks
    const val CONNECTION_STABILITY_THRESHOLD = 30000L // 30 seconds to consider connection stable
    const val BACKOFF_RESET_THRESHOLD = 60000L // 1 minute to reset backoff if connection was stable

    // Message Batch Processing
    const val BATCH_TIME_WINDOW_MS = 50L // 50ms time window for batching messages
    const val BATCH_SIZE_THRESHOLD = 5 // Maximum number of messages in a batch
    const val BATCH_MAX_SIZE_BYTES = 8192 // Maximum batch size in bytes (8KB)
    const val URGENT_MESSAGE_DELAY_MS = 10L // Maximum delay for urgent messages
    const val BATCH_QUEUE_CAPACITY = 100 // Maximum number of pending messages
    const val BATCH_FLUSH_INTERVAL_MS = 100L // Forced flush interval for pending messages

    // State Comparison Optimization
    const val STATE_CHANGE_THRESHOLD_LINES = 0 // Minimum line difference to trigger update
    const val STATE_CHANGE_THRESHOLD_COLUMNS = 0 // Minimum column difference to trigger update
    const val STATE_CACHE_SIZE = 50 // Maximum number of cached states per editor
    const val STATE_COMPARISON_TIMEOUT_MS = 5L // Maximum time for state comparison
    const val STATE_HISTORY_SIZE = 10 // Number of recent states to track for duplicate detection
    const val POSITION_CHANGE_SENSITIVITY = 1 // Minimum position change to consider significant

    // Object Pool Management
    const val EDITOR_STATE_POOL_INITIAL_SIZE = 10 // Initial pool size for EditorState objects
    const val EDITOR_STATE_POOL_MAX_SIZE = 50 // Maximum pool size for EditorState objects
    const val EDITOR_STATE_POOL_CLEANUP_INTERVAL_MS = 30000L // 30 seconds cleanup interval
    const val EDITOR_STATE_POOL_IDLE_TIMEOUT_MS = 60000L // 1 minute idle timeout for pooled objects
    const val EDITOR_STATE_POOL_PRELOAD_ENABLED = true // Whether to preload objects on pool initialization
    const val EDITOR_STATE_POOL_VALIDATION_ENABLED = true // Whether to validate objects before reuse
    
    // Port Configuration
    const val MIN_PORT = 3000
    const val MAX_PORT = 9999
    const val DEFAULT_PORT = 3000
    const val ABSOLUTE_MIN_PORT = 1000
    const val ABSOLUTE_MAX_PORT = 65535
    
    // UI Configuration
    const val SPINNER_UPDATE_INTERVAL = 120L // milliseconds for icon animation
    const val PORT_INPUT_LENGTH = 4
    const val UI_ICON_TEXT_GAP = 4 // Gap between icon and text in UI components
    const val UI_VERTICAL_STRUT_SMALL = 10 // Small vertical spacing in UI
    const val UI_VERTICAL_STRUT_MEDIUM = 15 // Medium vertical spacing in UI
    const val UI_HORIZONTAL_STRUT_SMALL = 5 // Small horizontal spacing in UI
    const val UI_HORIZONTAL_STRUT_MEDIUM = 10 // Medium horizontal spacing in UI
    
    // Notification Configuration
    const val NOTIFICATION_THROTTLE_MS = 10000L // 10 seconds
    const val INFO_NOTIFICATION_THROTTLE_MS = 5000L // 5 seconds for info notifications
    const val MESSAGE_HASH_CLEANUP_INTERVAL_MS = 60000L // 1 minute cleanup interval for message hashes
    const val NOTIFICATION_GROUP_ID = "Cursor Mirror Sync"
    const val CONFIG_NOTIFICATION_GROUP_ID = "Cursor Mirror Sync"

    // File Configuration
    const val CONNECTION_FILE_NAME = ".cursor-mirror-sync.json"
    const val SETTINGS_FILE_NAME = "cursor-mirror-sync.xml"
    
    // Update Intervals
    const val UPDATE_DEBOUNCE_MS = 50L
    const val DEFAULT_MAX_AGE_MINUTES = 5L

    // File Processing
    const val FILE_PROCESSING_DELAY_MS = 100L // Delay to ensure file write completion
    
    // Status Values
    const val STATUS_ACTIVE = "active"
    const val STATUS_INACTIVE = "inactive"
    
    // Message Types
    const val MESSAGE_TYPE_CONFIG_SYNC = "configSync"
    
    // Note: Icons are now handled by AllIcons in StatusBarWidget
    // Removed Unicode icon constants - using JetBrains built-in icons instead
    
    // Default Settings
    const val DEFAULT_AUTO_CONNECT_ENABLED = true
    const val DEFAULT_SELECTION_SYNC_ENABLED = true

    // Initialization Values
    const val INITIAL_RECONNECT_ATTEMPTS = 0
    const val INITIAL_NOTIFICATION_TIME = 0L
    
    // Validation Messages
    object ValidationMessages {
        const val PORT_OUT_OF_RANGE = "Port must be between $MIN_PORT and $MAX_PORT"
        const val PORT_BELOW_MINIMUM = "Port is below minimum ($MIN_PORT)"
        const val PORT_ABOVE_MAXIMUM = "Port exceeds maximum ($MAX_PORT)"
        const val PROJECT_NAME_BLANK = "Project name cannot be blank"
        const val PROJECT_PATH_BLANK = "Project path cannot be blank"
        const val INVALID_STATUS = "Status must be null, '$STATUS_ACTIVE', or '$STATUS_INACTIVE'"
    }
    
    // Log Messages
    object LogMessages {
        const val SERVICE_INITIALIZING = "Initializing SyncService for project"
        const val WEBSOCKET_CONNECTED = "Successfully connected to VSCode"
        const val WEBSOCKET_DISCONNECTED = "WebSocket disconnected"
        const val WEBSOCKET_RECONNECTING = "WebSocket reconnecting"
        const val AUTO_CONNECT_ENABLED = "Auto connect is enabled, trying to discover connection file"
        const val AUTO_CONNECT_DISABLED = "Auto connect is disabled, using manual port"
        const val CONNECTION_FILE_FOUND = "Found valid connection file with port"
        const val CONNECTION_FILE_NOT_FOUND = "No valid connection file found or connection info is invalid"
        const val MANUAL_PORT_FALLBACK = "Using manual port for project"
        const val FORCE_RECONNECT = "Force reconnect requested"
        const val MANUAL_DISCONNECT = "Manual disconnect requested"
        const val STATUS_WIDGET_REGISTERED = "Status widget registered with service"
        const val STATUS_WIDGET_UNREGISTERED = "Status widget unregistered from service"
    }
    
    // Tooltip Messages
    object TooltipMessages {
        const val CONNECTED = "Connected - Click to disconnect"
        const val CONNECTING = "Connecting..."
        const val DISCONNECTED = "Disconnected - Click to reconnect"
        const val TOGGLE_SYNC_ON = "Left click: turn sync on"
        const val TOGGLE_SYNC_OFF = "Left click: turn sync off"
        const val RIGHT_CLICK_OPTIONS = "Right click: more options"
        const val PORT_HELP = "Enter a 4-digit port number (3000-9999)"
        const val PORT_HELP_DETAILED = "This port is used as fallback when auto discovery fails. Must be 4 digits (3000-9999)."
    }
    
    // Error Messages
    object ErrorMessages {
        const val WEBSOCKET_ERROR = "WebSocket error occurred"
        const val CONNECTION_FAILED = "Failed to connect to VSCode"
        const val MESSAGE_SEND_FAILED = "Failed to send WebSocket message"
        const val CONNECTION_FILE_READ_ERROR = "Failed to read connection file"
        const val SETTINGS_VALIDATION_ERROR = "Settings validation errors found"
        const val MAX_RECONNECT_ATTEMPTS = "Max reconnection attempts reached"
    }
}

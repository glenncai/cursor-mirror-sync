export const Constants = {
  // Timing
  SELECTION_DEBOUNCE_MS: 30,
  PORT_REASSIGNMENT_DELAY_MS: 500,
  SERVER_CLOSE_TIMEOUT_MS: 2000,

  // Configuration Keys
  CONFIG_NAMESPACE: 'cursor-mirror-sync',
  CONFIG_AUTO_CONNECT: 'autoConnect',
  CONFIG_PORT_RANGE: 'portRange',
  CONFIG_ENABLE_SELECTION_SYNC: 'enableSelectionSync',

  // UI Text
  STATUS_BAR_TEXT: 'Cursor Mirror',
  STATUS_BAR_PRIORITY: 100,

  // WebSocket
  JETBRAINS_CLIENT_TYPE: 'jetbrains',

  // File Names
  CONNECTION_INFO_FILE: '.cursor-mirror-sync.json',

  // Default Values
  DEFAULT_PORT_RANGE: { min: 3000, max: 9999 },
  DEFAULT_AUTO_CONNECT: true,
  DEFAULT_SELECTION_SYNC: true,

  // Port Manager
  MAX_PORT_ATTEMPTS: 100,

  // Commands
  COMMAND_OPEN_CONNECTION_MANAGER: 'cursor-mirror-sync.openConnectionManager',

  // Status Icons
  ICON_SYNC_IGNORED: '$(sync-ignored)',
  ICON_CHECK: '$(check)',
  ICON_SYNC_SPIN: '$(sync~spin)',

  // Project Name Generation
  PROJECT_NAME_COUNTER_START: 1,

  // Template Placeholders
  TEMPLATE_CSS_URI: /\{\{cssUri}}/g,
  TEMPLATE_JS_URI: /\{\{jsUri}}/g,
  TEMPLATE_CSP_SOURCE: /\{\{cspSource}}/g,

  // Asset File Names
  ASSET_FOLDER: 'assets',
} as const;

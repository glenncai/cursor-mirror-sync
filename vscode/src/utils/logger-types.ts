/** Category tag used by the logger to classify an error source. */
export enum ErrorType {
  FILE_OPERATION = 'file_operation',
  NETWORK = 'network',
  WEBSOCKET = 'websocket',
  CONFIGURATION = 'configuration',
  TEMPLATE = 'template',
  CONNECTION = 'connection',
  GENERAL = 'general',
}

export enum LogLevel {
  ERROR = 'error',
  WARN = 'warn',
  INFO = 'info',
  DEBUG = 'debug',
}

/** Contextual metadata attached to a single log entry. */
export interface LogContext {
  /** Component name (e.g., 'ProjectConnection', 'WorkspaceSyncService'). */
  component?: string;
  /** Project name for project-specific logs. */
  projectName?: string;
  /** Error type for categorization. */
  errorType?: ErrorType;
  /** Human-readable description of the operation that produced the log. */
  operation?: string;
}

/** Per-call overrides controlling notification side effects of a log call. */
export interface LogOptions {
  /** Whether to surface a VSCode user notification alongside the log entry. */
  showUserNotification?: boolean;
  /** Notification level override; defaults to the log level. */
  notificationLevel?: 'error' | 'warn' | 'info';
  /** Custom user-facing message; overrides the auto-generated one. */
  userFriendlyMessage?: string;
}

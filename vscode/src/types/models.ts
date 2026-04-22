/**
 * Plain data models (DTOs) used across the extension.
 *
 * These types intentionally have no behavior and no dependencies on VSCode APIs
 * or the utility layer, so they can be consumed freely from any module without
 * creating inverted dependencies.
 */

/** User-facing configuration persisted under the `cursor-mirror-sync` namespace. */
export interface CursorMirrorConfiguration {
  autoConnect: boolean;
  portRange: {
    min: number;
    max: number;
  };
  enableSelectionSync: boolean;
}

/** Read-only snapshot of a project's connection for UI/webview consumption. */
export interface ProjectConnectionInfo {
  name: string;
  path: string;
  port: number;
  isConnected: boolean;
}

/** Zero-based line/column pair used for cursor and selection endpoints. */
export interface TextPosition {
  line: number;
  column: number;
}

/**
 * Cursor position, selection, and file state exchanged between IDEs on each
 * editor update.
 */
export interface SyncState {
  filePath: string;
  line: number;
  column: number;
  sourceIde: 'vscode' | 'jetbrains';
  isActive: boolean;

  hasSelection?: boolean;
  selectionStart?: TextPosition;
  selectionEnd?: TextPosition;
}

/** Result of scanning the workspace for a project folder. */
export interface DetectedProject {
  name: string;
  path: string;
}

/** Options forwarded to the underlying `ws` server. */
export interface WebSocketServerOptions {
  port: number;
  host?: string;
  perMessageDeflate?: boolean;
}

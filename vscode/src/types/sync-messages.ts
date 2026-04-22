import { ProjectConnectionInfo, SyncState } from './models';

/**
 * Wire-level message contracts shared between the VSCode host, the connected
 * JetBrains IDE, and the status-panel webview.
 */

/** Configuration sync payload exchanged between VSCode and JetBrains IDEs. */
export interface ConfigSyncMessage {
  type: 'configSync';
  enableSelectionSync: boolean;
  sourceIde: 'vscode' | 'jetbrains';
}

/**
 * Minimal envelope for inbound messages. JetBrains emits editor states without
 * a `type` field but tags config-sync payloads with `type: 'configSync'`, so
 * routing relies on the optional discriminator before narrowing to a concrete
 * payload type.
 */
export interface InboundMessageEnvelope {
  type?: string;
  [key: string]: unknown;
}

/** Discriminated union of inbound messages recognized by the WebSocket server. */
export type InboundMessage = ConfigSyncMessage | SyncState;

/**
 * Shape guard for editor-state payloads coming from JetBrains. Protects
 * downstream consumers from crashes such as `vscode.Uri.file(undefined)` when
 * the remote sends an unexpected payload shape.
 */
export function isSyncStatePayload(value: InboundMessageEnvelope): value is SyncState & InboundMessageEnvelope {
  return (
    typeof value.filePath === 'string' &&
    value.filePath.length > 0 &&
    typeof value.line === 'number' &&
    typeof value.column === 'number'
  );
}

/** Shape guard for config-sync payloads. */
export function isConfigSyncPayload(
  value: InboundMessageEnvelope
): value is ConfigSyncMessage & InboundMessageEnvelope {
  return value.type === 'configSync';
}

/** Message exchanged between the status-panel webview and the extension host. */
export interface WebViewMessage {
  command: string;
  name?: string;
  data?: ProjectConnectionInfo[];
  error?: string;
  enabled?: boolean;
}

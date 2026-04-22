import * as vscode from 'vscode';
import { ProjectConnectionInfo, SyncState } from './models';
import { ConfigSyncMessage } from './sync-messages';

/**
 * Collaborator contracts used to keep concrete classes decoupled.
 *
 * Only interfaces live here; data shapes belong in `models.ts` and wire
 * payloads in `sync-messages.ts`.
 */

export interface IDisposable {
  dispose(): void;
}

export interface IProjectConnection extends IDisposable {
  readonly projectName: string;
  readonly projectPath: string;
  readonly port: number;
  readonly isConnected: boolean;
  isActive: boolean;
  currentState: SyncState | null;

  startServer(): Promise<boolean>;
  updateState(state: SyncState): void;
  sendConfigUpdate(configMessage: ConfigSyncMessage): void;
}

export interface IPortManager {
  generateRandomPort(): number;
  releasePort(port: number): void;
  reservePort(port: number): void;
  isPortAvailable(port: number): boolean;
  getUsedPorts(): number[];
  clearAllPorts(): void;
}

/** Manages the connection status webview lifecycle and rendering. */
export interface IStatusPanel extends IDisposable {
  openConnectionStatusPanel(): void;
  updatePanelData(): void;
  isPanelOpen(): boolean;
  generateConnectionStatusHTML(webview: vscode.Webview): string;
}

/** Drives auto-connection and config broadcasting across workspace projects. */
export interface IWorkspaceSyncService extends IDisposable {
  readonly connections: Map<string, IProjectConnection>;

  addProject(projectName: string, projectPath: string): Promise<boolean>;
  removeProject(projectName: string): boolean;
  connectAllWorkspaceProjects(): Promise<number>;
  reassignPort(projectName: string): Promise<boolean>;
  getConnectionsData(): ProjectConnectionInfo[];
  findProjectForFile(filePath: string): IProjectConnection | null;
  broadcastConfigUpdate(configMessage: ConfigSyncMessage): void;
  setStatusPanel(statusPanel: IStatusPanel): void;
  toggleSelectionSync(): Promise<boolean>;
}

import { IProjectConnection } from '../../types/contracts';
import { SyncState } from '../../types/models';
import { ConfigSyncMessage } from '../../types/sync-messages';
import { ConfigManager } from '../config-manager';
import { Logger } from '../../utils/logger';
import { ConnectionFileManager } from './connection-file-manager';
import { EditorSyncApplier } from './editor-sync-applier';
import { WebSocketServer } from './websocket-server';

/**
 * Thin coordinator for a single project's JetBrains <-> VSCode sync link.
 *
 * Delegates WebSocket transport to {@link WebSocketServer}, connection-file
 * persistence to {@link ConnectionFileManager}, and incoming-state editor
 * application to {@link EditorSyncApplier}. Its sole responsibilities are
 * wiring those collaborators together, tracking project-level state
 * ({@link isActive}, {@link currentState}), and guarding against re-emit
 * loops via {@link isHandlingExternalUpdate}.
 */
export class ProjectConnection implements IProjectConnection {
  public readonly projectName: string;
  public readonly projectPath: string;
  public readonly port: number;
  public isActive: boolean = false;
  public currentState: SyncState | null = null;

  private readonly wsServer: WebSocketServer;
  private readonly fileManager: ConnectionFileManager;
  private readonly editorApplier: EditorSyncApplier;
  private readonly onConnectionStateChanged?: () => void;

  private isHandlingExternalUpdate: boolean = false;

  constructor(projectName: string, projectPath: string, port: number, onConnectionStateChanged?: () => void) {
    this.projectName = projectName;
    this.projectPath = projectPath;
    this.port = port;
    this.onConnectionStateChanged = onConnectionStateChanged;

    this.fileManager = new ConnectionFileManager(projectName, projectPath, port);
    this.editorApplier = new EditorSyncApplier(projectName);
    this.wsServer = new WebSocketServer(projectName, port, {
      onListening: () => {
        this.fileManager.writeConnectionInfo();
      },
      onClientConnected: () => {
        this.wsServer.sendInitialConfiguration(
          ConfigManager.getEnableSelectionSync(),
          this.currentState,
          this.isActive
        );
        Logger.info(`JetBrains IDE connected to ${this.projectName}, isActive: ${this.isActive}`, 'ProjectConnection');
        this.notifyConnectionStateChange();
      },
      onClientDisconnected: () => {
        this.notifyConnectionStateChange();
      },
      onIncomingState: (state) => {
        this.handleIncomingState(state);
      },
    });

    this.fileManager.setupWatcher();
  }

  public get isConnected(): boolean {
    return this.wsServer.isConnected;
  }

  public async startServer(): Promise<boolean> {
    await this.fileManager.ensureValidConnectionFile();
    return this.wsServer.start();
  }

  public async handleIncomingState(state: SyncState): Promise<void> {
    try {
      this.isHandlingExternalUpdate = true;
      await this.editorApplier.apply(state);
    } finally {
      this.isHandlingExternalUpdate = false;
    }
  }

  public updateState(state: SyncState): void {
    this.currentState = state;

    if (!this.isHandlingExternalUpdate && this.isActive) {
      this.wsServer.sendState(state);
    }
  }

  /** Forwards a config update to the connected JetBrains IDE. */
  public sendConfigUpdate(configMessage: ConfigSyncMessage): void {
    this.wsServer.sendConfigUpdate(configMessage);
  }

  public dispose(): void {
    Logger.info(`Disposing connection for ${this.projectName}`, 'ProjectConnection');

    this.wsServer.stop();
    this.fileManager.dispose();
    this.fileManager.removeConnectionInfo();
  }

  private notifyConnectionStateChange(): void {
    if (this.onConnectionStateChanged) {
      this.onConnectionStateChanged();
    }
  }
}

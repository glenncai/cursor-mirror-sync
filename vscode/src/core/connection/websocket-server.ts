import * as WebSocket from 'ws';
import { IncomingMessage } from 'http';
import { SyncState, WebSocketServerOptions } from '../../types/models';
import {
  ConfigSyncMessage,
  InboundMessageEnvelope,
  isConfigSyncPayload,
  isSyncStatePayload,
} from '../../types/sync-messages';
import { Constants } from '../constants';
import { Logger, ErrorType } from '../../utils/logger';
import { ErrorReporter } from '../../utils/error-reporter';
import { NotificationService } from '../../utils/notification-service';

export interface WebSocketServerCallbacks {
  onListening: () => void;
  onClientConnected: () => void;
  onClientDisconnected: () => void;
  onIncomingState: (state: SyncState) => void;
}

/**
 * Owns the per-project WebSocket server lifecycle: bind, accept the JetBrains
 * client, handle incoming messages, push outgoing state / config updates,
 * and tear down on dispose. Higher-level orchestration (file sync, editor
 * application) is delegated to the caller via {@link WebSocketServerCallbacks}.
 */
export class WebSocketServer {
  private wss: WebSocket.Server | null = null;
  private jetbrainsClient: WebSocket | null = null;
  private _isConnected: boolean = false;
  private pendingConfigUpdate: (ConfigSyncMessage & { _priority: string; _timestamp: number }) | null = null;

  constructor(
    private readonly projectName: string,
    private readonly port: number,
    private readonly callbacks: WebSocketServerCallbacks
  ) {}

  public get isConnected(): boolean {
    return this._isConnected;
  }

  public async start(): Promise<boolean> {
    await this.forceClose();

    try {
      const serverOptions: WebSocketServerOptions = {
        port: this.port,
        perMessageDeflate: false,
      };
      this.wss = new WebSocket.Server(serverOptions);
      this.setupServerEventHandlers();
      return true;
    } catch (error: unknown) {
      ErrorReporter.reportNetworkError(error, this.projectName, 'starting server');
      return false;
    }
  }

  public sendInitialConfiguration(
    enableSelectionSync: boolean,
    currentState: SyncState | null,
    isActive: boolean
  ): void {
    const configMessage: ConfigSyncMessage = {
      type: 'configSync',
      enableSelectionSync,
      sourceIde: 'vscode',
    };

    try {
      if (this.pendingConfigUpdate) {
        const pendingConfig = this.pendingConfigUpdate;
        this.pendingConfigUpdate = null;
        this.jetbrainsClient?.send(JSON.stringify(pendingConfig));
      } else {
        this.jetbrainsClient?.send(
          JSON.stringify({
            ...configMessage,
            _priority: 'HIGH',
            _timestamp: Date.now(),
          })
        );
      }
    } catch (error: unknown) {
      ErrorReporter.reportNetworkError(error, this.projectName, 'sending config on connection');
    }

    if (isActive && currentState) {
      try {
        this.jetbrainsClient?.send(JSON.stringify(currentState));
      } catch (error: unknown) {
        ErrorReporter.reportNetworkError(error, this.projectName, 'sending current state on connection');
      }
    }
  }

  public sendState(state: SyncState): void {
    if (this.jetbrainsClient?.readyState !== WebSocket.OPEN) {
      return;
    }
    try {
      this.jetbrainsClient.send(JSON.stringify(state));
    } catch (error: unknown) {
      ErrorReporter.reportNetworkError(error, this.projectName, 'sending state');
    }
  }

  public sendConfigUpdate(configMessage: ConfigSyncMessage): void {
    const priorityConfigMessage = {
      ...configMessage,
      _priority: 'HIGH',
      _timestamp: Date.now(),
    };

    if (!this.jetbrainsClient || this.jetbrainsClient.readyState !== WebSocket.OPEN) {
      this.pendingConfigUpdate = priorityConfigMessage;
      return;
    }

    try {
      this.jetbrainsClient.send(JSON.stringify(priorityConfigMessage));
      this.pendingConfigUpdate = null;
    } catch (error: unknown) {
      ErrorReporter.reportNetworkError(error, this.projectName, 'sending config update');
      this.pendingConfigUpdate = priorityConfigMessage;
    }
  }

  private setupServerEventHandlers(): void {
    if (!this.wss) {
      return;
    }
    this.wss.on('connection', this.handleClientConnection.bind(this));
    this.wss.on('listening', this.handleServerListening.bind(this));
    this.wss.on('error', this.handleServerError.bind(this));
  }

  private handleClientConnection(ws: WebSocket, request: IncomingMessage): void {
    const clientType = request.url?.slice(1);

    if (clientType !== Constants.JETBRAINS_CLIENT_TYPE) {
      ws.close();
      return;
    }

    if (this.jetbrainsClient) {
      this.jetbrainsClient.close();
    }

    this.jetbrainsClient = ws;
    this._isConnected = true;

    Logger.info(`JetBrains IDE connected to ${this.projectName}`, 'ProjectConnection');
    NotificationService.show(`JetBrains IDE connected to ${this.projectName}`, 'info');

    ws.on('message', this.handleClientMessage.bind(this));
    ws.on('close', () => this.handleClientDisconnection(ws));
    ws.on('error', this.handleClientError.bind(this));

    this.callbacks.onClientConnected();
  }

  private handleClientMessage(data: WebSocket.Data): void {
    let envelope: InboundMessageEnvelope;
    try {
      const parsed = JSON.parse(data.toString());
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        Logger.warn(`Ignoring non-object message from JetBrains for ${this.projectName}`, 'ProjectConnection');
        return;
      }
      envelope = parsed as InboundMessageEnvelope;
    } catch (error: unknown) {
      ErrorReporter.report(error, {
        component: 'ProjectConnection',
        errorType: ErrorType.WEBSOCKET,
        operation: 'Error parsing message',
      });
      return;
    }

    if (isConfigSyncPayload(envelope)) {
      // JetBrains does not push configSync back to VSCode today; the branch is
      // reserved so a future bidirectional handshake cannot accidentally route
      // through onIncomingState.
      Logger.info(
        `Received configSync from JetBrains for ${this.projectName} (currently ignored)`,
        'ProjectConnection'
      );
      return;
    }

    if (isSyncStatePayload(envelope)) {
      this.callbacks.onIncomingState(envelope);
      return;
    }

    Logger.warn(
      `Ignoring unrecognized message shape from JetBrains for ${this.projectName} (type=${String(envelope.type ?? 'none')})`,
      'ProjectConnection'
    );
  }

  private handleClientDisconnection(ws: WebSocket): void {
    if (this.jetbrainsClient === ws) {
      this.jetbrainsClient = null;
      this._isConnected = false;
      Logger.info(`JetBrains IDE disconnected from ${this.projectName}`, 'ProjectConnection');
      NotificationService.show(`JetBrains IDE disconnected from ${this.projectName}`, 'warn');
      this.callbacks.onClientDisconnected();
    }
  }

  private handleClientError(error: Error): void {
    Logger.error('WebSocket error', error, 'ProjectConnection');
    this._isConnected = false;
  }

  private handleServerListening(): void {
    Logger.info(`WebSocket server listening for ${this.projectName} on port ${this.port}`, 'ProjectConnection');
    this.callbacks.onListening();
  }

  private handleServerError(error: Error): void {
    Logger.error(`WebSocket server error for ${this.projectName}`, error, 'ProjectConnection');
    NotificationService.show(`Failed to start server for ${this.projectName}`, 'error');
  }

  public stop(): void {
    Logger.info(`Stopping server for ${this.projectName}`, 'ProjectConnection');

    if (this.wss) {
      this.wss.close();
      this.wss = null;
    }
    this._isConnected = false;
    this.jetbrainsClient = null;
  }

  /**
   * Closes the server and all open client connections, resolving once the
   * underlying `ws.Server` has fully shut down (or the close timeout elapses).
   * Called before starting a new server to avoid port-in-use errors.
   */
  public async forceClose(): Promise<void> {
    if (!this.wss) {
      return;
    }

    Logger.info(`Force closing server for ${this.projectName} to ensure clean disconnection`, 'ProjectConnection');

    return new Promise<void>((resolve) => {
      const server = this.wss;
      if (!server) {
        resolve();
        return;
      }

      if (this.jetbrainsClient) {
        try {
          this.jetbrainsClient.close();
          Logger.info(`Forced close of JetBrains client connection for ${this.projectName}`, 'ProjectConnection');
        } catch {
          Logger.warn('Error closing JetBrains client', {
            component: 'ProjectConnection',
            errorType: ErrorType.CONNECTION,
            projectName: this.projectName,
          });
        }
        this.jetbrainsClient = null;
      }

      server.clients.forEach((client) => {
        try {
          client.close();
        } catch {
          Logger.warn('Error closing client connection', {
            component: 'ProjectConnection',
            errorType: ErrorType.CONNECTION,
            projectName: this.projectName,
          });
        }
      });

      let timeoutId: NodeJS.Timeout | null = null;

      const cleanup = () => {
        if (timeoutId) {
          clearTimeout(timeoutId);
          timeoutId = null;
        }
        this.wss = null;
        this._isConnected = false;
        resolve();
      };

      server.close(() => {
        Logger.info(`Server fully closed for ${this.projectName}`, 'ProjectConnection');
        cleanup();
      });

      timeoutId = setTimeout(() => {
        if (this.wss) {
          Logger.warn(`Server close timeout for ${this.projectName}, forcing cleanup`, 'ProjectConnection');
          cleanup();
        }
      }, Constants.SERVER_CLOSE_TIMEOUT_MS);
    });
  }
}

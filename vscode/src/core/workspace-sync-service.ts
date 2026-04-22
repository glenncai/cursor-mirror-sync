import * as vscode from 'vscode';
import { PortManager } from './connection/port-manager';
import { IWorkspaceSyncService, IProjectConnection, IStatusPanel } from '../types/contracts';
import { ProjectConnectionInfo } from '../types/models';
import { ConfigSyncMessage } from '../types/sync-messages';
import { Constants } from './constants';
import { ConfigManager } from './config-manager';
import { PathUtils } from '../utils/path-utils';
import { ConnectionFactory } from './connection/connection-factory';
import { Logger } from '../utils/logger';
import { NotificationService } from '../utils/notification-service';
import { StatusBarWidget } from '../ui/status-bar-widget';
import { EditorListener } from './sync/editor-listener';
import { WorkspaceScanner } from './workspace-scanner';

export class WorkspaceSyncService implements IWorkspaceSyncService {
  public readonly connections: Map<string, IProjectConnection>;
  private readonly portManager: PortManager;
  private readonly statusBarWidget: StatusBarWidget;
  private readonly editorListener: EditorListener;
  private readonly scanner: WorkspaceScanner;
  private readonly disposables: vscode.Disposable[] = [];
  private isActive: boolean = false;
  private statusPanel: IStatusPanel | null = null;

  private readonly projectPathCache = new Map<string, string>();

  constructor() {
    this.connections = new Map<string, IProjectConnection>();
    this.portManager = new PortManager();
    this.statusBarWidget = new StatusBarWidget();
    this.scanner = new WorkspaceScanner((name) => this.connections.has(name));
    this.editorListener = new EditorListener({
      isHandlingExternalUpdate: () => false,
      isWindowActive: () => this.isActive,
      findProjectForFile: (filePath) => this.findProjectForFile(filePath),
    });

    this.setupWindowListeners();
    this.setupWorkspaceListeners();
    this.setupConfigurationListener();
    this.isActive = vscode.window.state.focused;
  }

  public setStatusPanel(statusPanel: IStatusPanel): void {
    this.statusPanel = statusPanel;
  }

  private refreshStatus(): void {
    const connectedCount = Array.from(this.connections.values()).filter((conn) => conn.isConnected).length;
    const totalCount = this.connections.size;
    this.statusBarWidget.update(connectedCount, totalCount);

    if (this.statusPanel && this.statusPanel.isPanelOpen()) {
      this.statusPanel.updatePanelData();
    }
  }

  public async addProject(projectName: string, projectPath: string): Promise<boolean> {
    if (this.connections.has(projectName)) {
      NotificationService.show(`Project ${projectName} is already connected`, 'warn');
      return false;
    }

    const port = this.portManager.generateRandomPort();
    const connection = ConnectionFactory.createConnection(
      projectName,
      projectPath,
      port,
      () => this.refreshStatus(),
      this.isActive
    );

    const success = await connection.startServer();
    if (success) {
      this.connections.set(projectName, connection);
      this.refreshStatus();
      return true;
    } else {
      this.portManager.releasePort(port);
      NotificationService.show(`Failed to connect project ${projectName}`, 'error');
      return false;
    }
  }

  public removeProject(projectName: string): boolean {
    const connection = this.connections.get(projectName);
    if (connection) {
      this.portManager.releasePort(connection.port);
      this.projectPathCache.delete(connection.projectPath);
      connection.dispose();
      this.connections.delete(projectName);
      this.refreshStatus();
      return true;
    }
    return false;
  }

  public findProjectForFile(filePath: string): IProjectConnection | null {
    const normalizedFilePath = PathUtils.normalizePath(filePath);

    for (const [, connection] of this.connections) {
      let normalizedProjectPath = this.projectPathCache.get(connection.projectPath);
      if (!normalizedProjectPath) {
        normalizedProjectPath = PathUtils.normalizePath(connection.projectPath);
        this.projectPathCache.set(connection.projectPath, normalizedProjectPath);
      }

      if (PathUtils.isPathWithinProject(normalizedFilePath, normalizedProjectPath)) {
        return connection;
      }
    }
    return null;
  }

  private setupWindowListeners(): void {
    this.disposables.push(
      vscode.window.onDidChangeWindowState((e) => {
        this.isActive = e.focused;
        for (const connection of this.connections.values()) {
          connection.isActive = this.isActive;
        }
      })
    );
  }

  private setupWorkspaceListeners(): void {
    this.disposables.push(
      vscode.workspace.onDidChangeWorkspaceFolders((event) => {
        Logger.info('Workspace folders changed', { component: 'WorkspaceSyncService' });

        event.removed.forEach((folder) => {
          const projectName = this.scanner.generateProjectName(folder.name);
          if (this.connections.has(projectName)) {
            Logger.info(`Auto-disconnecting removed project: ${projectName}`, { component: 'WorkspaceSyncService' });
            this.removeProject(projectName);
          }
        });

        const autoConnect = ConfigManager.getAutoConnect();
        if (autoConnect) {
          event.added.forEach((folder) => {
            const projectName = this.scanner.generateProjectName(folder.name);
            this.addProject(projectName, folder.uri.fsPath);
          });
        }
      })
    );
  }

  private setupConfigurationListener(): void {
    this.disposables.push(
      vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration('cursor-mirror-sync.portRange')) {
          Logger.warn('Port range configuration changed. Restart may be required for existing connections.', {
            component: 'WorkspaceSyncService',
          });
          NotificationService.show(
            'Port range configuration changed. Existing connections will continue using their current ports. New connections will use the updated port range.',
            'warn'
          );
        }

        if (e.affectsConfiguration('cursor-mirror-sync.enableSelectionSync')) {
          const newValue = ConfigManager.getEnableSelectionSync();
          Logger.info(`Selection sync configuration changed to: ${newValue}`, {
            component: 'WorkspaceSyncService',
          });
          this.broadcastConfigUpdate({
            type: 'configSync',
            enableSelectionSync: newValue,
            sourceIde: 'vscode',
          });
        }
      })
    );
  }

  public async connectAllWorkspaceProjects(): Promise<number> {
    const detectedProjects = this.scanner.detectWorkspaceProjects();
    let connectedCount = 0;

    for (const project of detectedProjects) {
      if (!this.connections.has(project.name)) {
        const success = await this.addProject(project.name, project.path);
        if (success) {
          connectedCount++;
          const connection = this.connections.get(project.name);
          if (connection) {
            NotificationService.show(`Project '${project.name}' connected on port ${connection.port}`, 'info');
          }
        }
      }
    }

    return connectedCount;
  }

  public async reassignPort(projectName: string): Promise<boolean> {
    const connection = this.connections.get(projectName);
    if (!connection) {
      NotificationService.show(`Project ${projectName} not found`, 'warn');
      return false;
    }

    const oldPort = connection.port;

    const preservedCurrentState = connection.currentState;

    this.portManager.releasePort(oldPort);
    connection.dispose();

    const newPort = this.portManager.generateRandomPort();
    const newConnection = ConnectionFactory.createConnection(
      projectName,
      connection.projectPath,
      newPort,
      () => this.refreshStatus(),
      this.isActive
    );

    if (preservedCurrentState) {
      newConnection.currentState = preservedCurrentState;
    }

    const success = await newConnection.startServer();
    if (success) {
      this.connections.set(projectName, newConnection);
      this.refreshStatus();

      await new Promise((resolve) => setTimeout(resolve, Constants.PORT_REASSIGNMENT_DELAY_MS));

      NotificationService.show(`Project '${projectName}' port reassigned to ${newPort}`, 'info');
      return true;
    } else {
      this.portManager.releasePort(newPort);
      NotificationService.show(`Failed to reassign port for project ${projectName}`, 'error');
      return false;
    }
  }

  public async toggleSelectionSync(): Promise<boolean> {
    const config = vscode.workspace.getConfiguration('cursor-mirror-sync');
    const currentValue = config.get<boolean>('enableSelectionSync', true);
    const newValue = !currentValue;

    await config.update('enableSelectionSync', newValue, vscode.ConfigurationTarget.Global);

    this.broadcastConfigUpdate({
      type: 'configSync',
      enableSelectionSync: newValue,
      sourceIde: 'vscode',
    });

    const status = newValue ? 'enabled' : 'disabled';
    NotificationService.show(`Text selection synchronization ${status}`, 'info');

    return newValue;
  }

  public getConnectionsData(): ProjectConnectionInfo[] {
    return Array.from(this.connections.entries()).map(([name, connection]) => ({
      name,
      path: connection.projectPath,
      port: connection.port,
      isConnected: connection.isConnected,
    }));
  }

  public broadcastConfigUpdate(configMessage: ConfigSyncMessage): void {
    Logger.info(`Broadcasting config update to all connected JetBrains instances: ${JSON.stringify(configMessage)}`, {
      component: 'WorkspaceSyncService',
    });

    for (const [projectName, connection] of this.connections.entries()) {
      if (connection.isConnected) {
        try {
          connection.sendConfigUpdate(configMessage);
          Logger.info(`Config update sent to ${projectName}`, { component: 'WorkspaceSyncService' });
        } catch (error) {
          Logger.error(`Failed to send config update to ${projectName}`, error, { component: 'WorkspaceSyncService' });
        }
      } else {
        Logger.info(`Skipping config update for ${projectName} (not connected)`, { component: 'WorkspaceSyncService' });
      }
    }
  }

  public dispose(): void {
    this.editorListener.dispose();

    for (const connection of this.connections.values()) {
      this.portManager.releasePort(connection.port);
      connection.dispose();
    }
    this.connections.clear();
    this.projectPathCache.clear();
    PathUtils.clearCache();
    this.portManager.clearAllPorts();
    this.statusBarWidget.dispose();
    this.disposables.forEach((d) => d.dispose());
  }
}

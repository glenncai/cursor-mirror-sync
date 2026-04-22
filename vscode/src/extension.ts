import * as vscode from 'vscode';
import { WorkspaceSyncService } from './core/workspace-sync-service';
import { ConnectionStatusPanel } from './ui/status-panel';
import { ErrorType } from './utils/logger';
import { ConfigManager } from './core/config-manager';
import { Logger } from './utils/logger';
import { ErrorReporter } from './utils/error-reporter';

let workspaceSyncService: WorkspaceSyncService | null = null;
let statusPanel: ConnectionStatusPanel | null = null;

export function activate(context: vscode.ExtensionContext): void {
  Logger.initialize(context);
  Logger.info('Cursor Mirror Sync extension is now active!', { component: 'Extension' });

  workspaceSyncService = new WorkspaceSyncService();
  statusPanel = new ConnectionStatusPanel(workspaceSyncService, context);

  workspaceSyncService.setStatusPanel(statusPanel);

  const autoConnect = ConfigManager.getAutoConnect();
  if (autoConnect) {
    workspaceSyncService.connectAllWorkspaceProjects();
  }

  registerCommands(context);

  context.subscriptions.push({
    dispose: () => {
      workspaceSyncService?.dispose();
      statusPanel?.dispose();
    },
  });
}

function registerCommands(context: vscode.ExtensionContext): void {
  context.subscriptions.push(
    vscode.commands.registerCommand('cursor-mirror-sync.openConnectionManager', () => {
      statusPanel?.openConnectionStatusPanel();
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cursor-mirror-sync.toggleSelectionSync', async () => {
      try {
        await workspaceSyncService?.toggleSelectionSync();
      } catch (error) {
        ErrorReporter.report(
          error,
          {
            component: 'Extension',
            errorType: ErrorType.CONFIGURATION,
            operation: 'Failed to update selection sync setting',
          },
          { showUserNotification: true }
        );
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cursor-mirror-sync.showLogs', () => {
      Logger.showOutputChannel();
    })
  );
}

export function deactivate(): void {
  workspaceSyncService?.dispose();
}

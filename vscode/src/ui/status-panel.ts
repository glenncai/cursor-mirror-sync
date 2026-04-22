import * as vscode from 'vscode';
import { IStatusPanel, IWorkspaceSyncService } from '../types/contracts';
import { WebViewMessage } from '../types/sync-messages';
import { WebviewTemplateLoader } from './webview-template-loader';
import { Logger } from '../utils/logger';
import { NotificationService } from '../utils/notification-service';

export class ConnectionStatusPanel implements IStatusPanel {
  private readonly autoConnectionService: IWorkspaceSyncService;
  private readonly context: vscode.ExtensionContext;
  private currentPanel: vscode.WebviewPanel | null = null;

  constructor(autoConnectionService: IWorkspaceSyncService, context: vscode.ExtensionContext) {
    this.autoConnectionService = autoConnectionService;
    this.context = context;
  }

  public openConnectionStatusPanel(): void {
    if (this.currentPanel) {
      this.currentPanel.reveal(vscode.ViewColumn.One);
      this.refreshPanelData();
      return;
    }

    this.currentPanel = vscode.window.createWebviewPanel(
      'cursorMirrorConnectionStatus',
      'Cursor Mirror Sync Connection Status',
      vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
      }
    );

    this.currentPanel.onDidDispose(() => {
      this.currentPanel = null;
    });

    this.currentPanel.webview.html = this.generateConnectionStatusHTML(this.currentPanel.webview);
    this.setupPanelMessageHandling(this.currentPanel);
  }

  private refreshPanelData(): void {
    if (this.currentPanel) {
      try {
        const connectionsData = this.autoConnectionService.getConnectionsData();
        this.currentPanel.webview.postMessage({
          command: 'connectionsData',
          data: connectionsData,
        });
      } catch (error) {
        Logger.error('Failed to refresh panel data', error, { component: 'StatusPanel' });
      }
    }
  }

  private setupPanelMessageHandling(panel: vscode.WebviewPanel): void {
    panel.webview.onDidReceiveMessage(
      async (message: WebViewMessage) => {
        switch (message.command) {
          case 'getConnections': {
            const connectionsData = this.autoConnectionService.getConnectionsData();
            panel.webview.postMessage({
              command: 'connectionsData',
              data: connectionsData,
            });
            break;
          }
          case 'reassignPort':
            if (message.name) {
              const confirmed = await NotificationService.confirm(
                `Reassign port for project "${message.name}"? This will generate a new random port and restart the connection.`,
                'Reassign Port'
              );

              if (confirmed) {
                panel.webview.postMessage({
                  command: 'loading',
                });

                const success = await this.autoConnectionService.reassignPort(message.name);

                const updatedData = this.autoConnectionService.getConnectionsData();
                panel.webview.postMessage({
                  command: 'connectionsData',
                  data: updatedData,
                });

                if (success) {
                  Logger.info(`Port reassignment successful for ${message.name}, panel data updated`, {
                    component: 'StatusPanel',
                  });
                } else {
                  Logger.info(
                    `Port reassignment failed for ${message.name}, panel data updated to reflect current state`,
                    { component: 'StatusPanel' }
                  );
                }
              }
            }
            break;
          case 'toggleSelectionSync': {
            const newValue = await this.autoConnectionService.toggleSelectionSync();
            panel.webview.postMessage({
              command: 'selectionSyncToggled',
              enabled: newValue,
            });
            break;
          }
          case 'getSelectionSyncStatus': {
            const selectionSyncEnabled = vscode.workspace
              .getConfiguration('cursor-mirror-sync')
              .get<boolean>('enableSelectionSync', true);
            panel.webview.postMessage({
              command: 'selectionSyncStatus',
              enabled: selectionSyncEnabled,
            });
            break;
          }
        }
      },
      undefined,
      []
    );
  }

  /** Pushes fresh connection data to the webview when the panel is open. */
  public updatePanelData(): void {
    this.refreshPanelData();
  }

  public isPanelOpen(): boolean {
    return this.currentPanel !== null;
  }

  public dispose(): void {
    if (this.currentPanel) {
      this.currentPanel.dispose();
      this.currentPanel = null;
    }
  }

  public generateConnectionStatusHTML(webview: vscode.Webview): string {
    const templateLoader = new WebviewTemplateLoader(this.context, webview);

    const validation = templateLoader.validateTemplate('status-panel');
    if (!validation.isValid) {
      Logger.error(`Missing template files: ${validation.missingFiles.join(', ')}`, undefined, {
        component: 'StatusPanel',
      });
      return this.getFallbackHTML();
    }

    return templateLoader.loadTemplate('status-panel');
  }

  private getFallbackHTML(): string {
    return `
      <html lang="en">
      <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Cursor Mirror Sync Connection Status - Template Error</title>
          <style>
              body {
                  font-family: var(--vscode-font-family);
                  color: var(--vscode-errorForeground);
                  background-color: var(--vscode-editor-background);
                  padding: 20px;
                  margin: 0;
                  text-align: center;
              }
              .error-container {
                  max-width: 600px;
                  margin: 0 auto;
              }
              .error-title {
                  font-size: 24px;
                  margin-bottom: 20px;
              }
              .error-message {
                  background-color: var(--vscode-inputValidation-errorBackground);
                  border: 1px solid var(--vscode-inputValidation-errorBorder);
                  padding: 15px;
                  border-radius: 4px;
                  margin-bottom: 20px;
              }
          </style>
      </head>
      <body>
          <div class="error-container">
              <h1 class="error-title">Template Loading Error</h1>
              <div class="error-message">
                  The webview template files are missing or corrupted.
                  Please reinstall the extension.
              </div>
              <p>Extension developers: Check that the webview template files exist in src/ui/webview/</p>
          </div>
      </body>
      </html>
    `;
  }
}

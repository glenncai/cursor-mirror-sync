import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { Constants } from '../constants';
import { Logger, ErrorType } from '../../utils/logger';
import { ErrorReporter } from '../../utils/error-reporter';
import { NotificationService } from '../../utils/notification-service';

/**
 * Manages the persistent .cursor-mirror-sync.json file that advertises the
 * WebSocket port to JetBrains IDEs, plus the FileSystemWatcher that keeps
 * it in sync when users accidentally edit or delete it.
 */
export class ConnectionFileManager {
  private readonly filePath: string;
  private fileWatcher: vscode.FileSystemWatcher | null = null;

  constructor(
    private readonly projectName: string,
    private readonly projectPath: string,
    private readonly port: number
  ) {
    this.filePath = path.join(this.projectPath, Constants.CONNECTION_INFO_FILE);
  }

  public get connectionInfoFilePath(): string {
    return this.filePath;
  }

  public setupWatcher(): void {
    try {
      const pattern = new vscode.RelativePattern(this.projectPath, Constants.CONNECTION_INFO_FILE);
      this.fileWatcher = vscode.workspace.createFileSystemWatcher(pattern);

      this.fileWatcher.onDidChange(async () => {
        await this.checkFileIntegrity();
      });

      this.fileWatcher.onDidDelete(async () => {
        Logger.info(`Connection file deleted for ${this.projectName}, will recreate`, 'ProjectConnection');
        NotificationService.show(`Connection file for ${this.projectName} was deleted. Recreating...`, 'warn');

        try {
          await this.writeConnectionInfo();
          Logger.info(`Connection file successfully recreated for ${this.projectName}`, 'ProjectConnection');
          NotificationService.show(`Connection file for ${this.projectName} has been recreated.`, 'info');
        } catch (error: unknown) {
          ErrorReporter.report(
            error,
            {
              component: 'ProjectConnection',
              errorType: ErrorType.FILE_OPERATION,
              projectName: this.projectName,
              operation: 'Failed to recreate connection file',
            },
            { showUserNotification: true }
          );
          NotificationService.show(
            `Failed to recreate connection file for ${this.projectName}. Please restart the extension.`,
            'error'
          );
        }
      });

      Logger.info(`File watcher setup for ${this.projectName}: ${this.filePath}`, 'ProjectConnection');
    } catch (error: unknown) {
      ErrorReporter.report(error, {
        component: 'ProjectConnection',
        errorType: ErrorType.FILE_OPERATION,
        projectName: this.projectName,
        operation: 'Failed to setup file watcher',
      });
    }
  }

  public async writeConnectionInfo(): Promise<void> {
    try {
      const now = new Date();
      const localTimeString = now.toLocaleString('sv-SE', {
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      });
      const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

      let existingCreatedAt: string | null = null;
      try {
        if (fs.existsSync(this.filePath)) {
          const existingContent = await fs.promises.readFile(this.filePath, 'utf8');
          const existingInfo = JSON.parse(existingContent);
          existingCreatedAt = existingInfo.createdAt;
        }
      } catch {
        Logger.info('Could not read existing file, will use current time as createdAt', {
          component: 'ProjectConnection',
          errorType: ErrorType.FILE_OPERATION,
        });
      }

      const connectionInfo = {
        port: this.port,
        projectName: this.projectName,
        projectPath: this.projectPath,
        createdAt: existingCreatedAt || `${localTimeString} (${timeZone})`,
        lastModified: `${localTimeString} (${timeZone})`,
        _utcTime: now.toISOString(),
        _reconnectSignal: now.getTime(),
      };

      await fs.promises.writeFile(this.filePath, JSON.stringify(connectionInfo, null, 2), 'utf8');
      Logger.info(`Connection info written to ${this.filePath}`, { component: 'ProjectConnection' });
      Logger.info(
        `Created at: ${connectionInfo.createdAt} / Last modified: ${connectionInfo.lastModified} / ${now.toISOString()} (UTC)`,
        { component: 'ProjectConnection' }
      );
    } catch (error: unknown) {
      ErrorReporter.report(error, {
        component: 'ProjectConnection',
        errorType: ErrorType.FILE_OPERATION,
        projectName: this.projectName,
        operation: 'Failed to write connection info',
      });
    }
  }

  public async removeConnectionInfo(): Promise<void> {
    try {
      if (fs.existsSync(this.filePath)) {
        await fs.promises.unlink(this.filePath);
        Logger.info(`Connection info file removed: ${this.filePath}`, { component: 'ProjectConnection' });
      }
    } catch (error: unknown) {
      ErrorReporter.report(error, {
        component: 'ProjectConnection',
        errorType: ErrorType.FILE_OPERATION,
        projectName: this.projectName,
        operation: 'Failed to remove connection info',
      });
    }
  }

  public async ensureValidConnectionFile(): Promise<void> {
    try {
      if (!fs.existsSync(this.filePath)) {
        Logger.info(`Connection file does not exist, will create: ${this.filePath}`, 'ProjectConnection');
        return;
      }

      const fileContent = await fs.promises.readFile(this.filePath, 'utf8');
      const connectionInfo = JSON.parse(fileContent);

      if (!connectionInfo.port || !connectionInfo.projectName || !connectionInfo.projectPath) {
        Logger.info(`Connection file is invalid, will recreate: ${this.filePath}`, 'ProjectConnection');
        await this.removeConnectionInfo();
        return;
      }

      if (connectionInfo.port !== this.port) {
        return;
      }
    } catch (error) {
      Logger.info(
        `Error reading connection file, will recreate: ${error instanceof Error ? error.message : String(error)}`,
        'ProjectConnection'
      );
      await this.removeConnectionInfo();
    }
  }

  private async checkFileIntegrity(): Promise<void> {
    try {
      if (!fs.existsSync(this.filePath)) {
        return;
      }

      const fileContent = await fs.promises.readFile(this.filePath, 'utf8');
      const connectionInfo = JSON.parse(fileContent);

      const missingTimeFields = [];
      if (!connectionInfo.createdAt) {
        missingTimeFields.push('createdAt');
      }
      if (!connectionInfo.lastModified) {
        missingTimeFields.push('lastModified');
      }
      if (!connectionInfo._utcTime) {
        missingTimeFields.push('_utcTime');
      }

      if (missingTimeFields.length > 0) {
        Logger.warn(
          `Connection file for ${this.projectName} is missing time fields: ${missingTimeFields.join(', ')}`,
          'ProjectConnection'
        );
        Logger.info(`Regenerating file to restore missing fields...`, 'ProjectConnection');
        await this.writeConnectionInfo();
        NotificationService.show(
          `Connection file for ${this.projectName} was missing time fields and has been automatically restored.`,
          'info'
        );
        return;
      }

      if (
        connectionInfo.port !== this.port ||
        connectionInfo.projectName !== this.projectName ||
        connectionInfo.projectPath !== this.projectPath
      ) {
        Logger.warn(`Connection file for ${this.projectName} has incorrect values, will update`, 'ProjectConnection');
        await this.writeConnectionInfo();
      }
    } catch (error: unknown) {
      ErrorReporter.report(error, {
        component: 'ProjectConnection',
        errorType: ErrorType.FILE_OPERATION,
        projectName: this.projectName,
        operation: 'Error checking file integrity',
      });
      await this.writeConnectionInfo();
    }
  }

  public dispose(): void {
    if (this.fileWatcher) {
      this.fileWatcher.dispose();
      this.fileWatcher = null;
      Logger.info(`File watcher disposed for ${this.projectName}`, 'ProjectConnection');
    }
  }
}

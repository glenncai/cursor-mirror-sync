import * as vscode from 'vscode';
import { Constants } from '../core/constants';

/**
 * Owns the VSCode status bar item that surfaces aggregate connection status.
 * Mirrors the JetBrains `StatusBarWidget` by exposing a single
 * `update(connected, total)` API so the sync service stays free of
 * `vscode.window.createStatusBarItem` wiring.
 */
export class StatusBarWidget {
  private readonly item: vscode.StatusBarItem;

  constructor() {
    this.item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, Constants.STATUS_BAR_PRIORITY);
    this.item.command = Constants.COMMAND_OPEN_CONNECTION_MANAGER;
    this.update(0, 0);
    this.item.show();
  }

  public update(connectedCount: number, totalCount: number): void {
    let icon: string = Constants.ICON_SYNC_IGNORED;
    if (connectedCount > 0) {
      icon = Constants.ICON_CHECK;
    } else if (totalCount > 0) {
      icon = Constants.ICON_SYNC_SPIN;
    }

    this.item.text = `${icon} ${Constants.STATUS_BAR_TEXT}`;
    this.item.tooltip = `Cursor Mirror Sync: ${connectedCount} of ${totalCount} projects connected\nClick to view connection status`;
  }

  public dispose(): void {
    this.item.dispose();
  }
}

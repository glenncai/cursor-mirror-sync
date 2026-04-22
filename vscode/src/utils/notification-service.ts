import * as vscode from 'vscode';

/**
 * Thin wrapper around the VSCode notification APIs. Centralising these calls
 * keeps the notification concern out of {@link Logger} and lets callers (tests
 * or alternative UIs) substitute a different delivery strategy without
 * touching log code.
 */
export class NotificationService {
  /** Shows a user-facing notification at the given level. */
  static show(message: string, level: 'error' | 'warn' | 'info'): void {
    switch (level) {
      case 'error':
        vscode.window.showErrorMessage(message);
        break;
      case 'warn':
        vscode.window.showWarningMessage(message);
        break;
      case 'info':
        vscode.window.showInformationMessage(message);
        break;
    }
  }

  /**
   * Shows a modal confirmation dialog and resolves to `true` when the user
   * picks `confirmLabel`, `false` otherwise.
   */
  static async confirm(message: string, confirmLabel: string): Promise<boolean> {
    const result = await vscode.window.showInformationMessage(message, { modal: true }, confirmLabel);
    return result === confirmLabel;
  }
}

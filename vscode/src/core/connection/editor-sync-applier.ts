import * as vscode from 'vscode';
import { SyncState } from '../../types/models';
import { ConfigManager } from '../config-manager';
import { Logger, ErrorType } from '../../utils/logger';
import { ErrorReporter } from '../../utils/error-reporter';

/**
 * Applies incoming {@link SyncState} payloads from JetBrains to the local
 * VSCode editor: opens the target document, moves the cursor, and—when
 * enabled—mirrors the remote text selection.
 */
export class EditorSyncApplier {
  constructor(private readonly projectName: string) {}

  public async apply(state: SyncState): Promise<void> {
    if (!state.isActive) {
      Logger.info(`Ignoring update from inactive JetBrains for ${this.projectName}`, 'ProjectConnection');
      return;
    }

    try {
      const uri = vscode.Uri.file(state.filePath);
      const document = await vscode.workspace.openTextDocument(uri);
      const editor = await vscode.window.showTextDocument(document, { preview: false });

      const enableSelectionSync = ConfigManager.getEnableSelectionSync();

      if (enableSelectionSync && state.hasSelection && state.selectionStart && state.selectionEnd) {
        const cursorPosition = new vscode.Position(state.line, state.column);
        const startPosition = new vscode.Position(state.selectionStart.line, state.selectionStart.column);
        const endPosition = new vscode.Position(state.selectionEnd.line, state.selectionEnd.column);

        // Anchor at the endpoint opposite the active cursor so direction is preserved.
        let anchorPosition: vscode.Position;
        if (cursorPosition.isEqual(startPosition)) {
          anchorPosition = endPosition;
        } else if (cursorPosition.isEqual(endPosition)) {
          anchorPosition = startPosition;
        } else {
          anchorPosition = startPosition;
        }

        editor.selection = new vscode.Selection(anchorPosition, cursorPosition);

        const selectionRange = new vscode.Range(startPosition, endPosition);
        editor.revealRange(selectionRange, vscode.TextEditorRevealType.InCenter);
        Logger.info(
          `Applied selection sync from JetBrains for ${this.projectName} - cursor at (${state.line}, ${state.column})`,
          'ProjectConnection'
        );
      } else {
        const position = new vscode.Position(state.line, state.column);
        editor.selection = new vscode.Selection(position, position);
        editor.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenter);

        if (!enableSelectionSync && state.hasSelection) {
          Logger.info(
            `Skipped selection sync from JetBrains for ${this.projectName} (selection sync disabled)`,
            'ProjectConnection'
          );
        }
      }
    } catch (error: unknown) {
      ErrorReporter.report(error, {
        component: 'ProjectConnection',
        errorType: ErrorType.WEBSOCKET,
        projectName: this.projectName,
        operation: 'Error handling incoming state',
      });
    }
  }
}

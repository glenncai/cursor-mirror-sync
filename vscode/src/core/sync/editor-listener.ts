import * as vscode from 'vscode';
import { IDisposable, IProjectConnection } from '../../types/contracts';
import { SyncState } from '../../types/models';
import { Constants } from '../constants';
import { ConfigManager } from '../config-manager';

/**
 * Host contract the listener needs to translate editor events into
 * {@link SyncState} payloads routed to the correct project connection.
 */
export interface EditorListenerHost {
  isHandlingExternalUpdate(): boolean;
  isWindowActive(): boolean;
  findProjectForFile(filePath: string): IProjectConnection | null;
}

/**
 * Subscribes to VSCode editor activity (active-editor changes + selection
 * changes with debounce) and forwards the derived cursor / selection state
 * to the matching project connection via the {@link EditorListenerHost}.
 */
export class EditorListener implements IDisposable {
  private readonly disposables: vscode.Disposable[] = [];
  private selectionDebounceTimer: NodeJS.Timeout | null = null;

  constructor(private readonly host: EditorListenerHost) {
    this.disposables.push(
      vscode.window.onDidChangeActiveTextEditor((editor) => {
        if (editor && !this.host.isHandlingExternalUpdate()) {
          this.handleEditorChange(editor);
        }
      })
    );

    this.disposables.push(
      vscode.window.onDidChangeTextEditorSelection((event) => {
        if (!this.host.isHandlingExternalUpdate()) {
          this.debouncedHandleSelection(event.textEditor);
        }
      })
    );
  }

  private debouncedHandleSelection(editor: vscode.TextEditor): void {
    if (this.selectionDebounceTimer) {
      clearTimeout(this.selectionDebounceTimer);
    }

    this.selectionDebounceTimer = setTimeout(() => {
      this.handleEditorChange(editor);
      this.selectionDebounceTimer = null;
    }, Constants.SELECTION_DEBOUNCE_MS);
  }

  private handleEditorChange(editor: vscode.TextEditor): void {
    const document = editor.document;
    const position = editor.selection.active;
    const selection = editor.selection;
    const filePath = document.uri.fsPath;

    const enableSelectionSync = ConfigManager.getEnableSelectionSync();
    const isActive = this.host.isWindowActive();

    const projectConnection = this.host.findProjectForFile(filePath);
    if (projectConnection) {
      projectConnection.isActive = isActive;

      const state: SyncState = {
        filePath: filePath,
        line: position.line,
        column: position.character,
        sourceIde: 'vscode',
        isActive: isActive,
      };

      if (enableSelectionSync && !selection.isEmpty) {
        state.hasSelection = true;
        state.selectionStart = {
          line: selection.start.line,
          column: selection.start.character,
        };
        state.selectionEnd = {
          line: selection.end.line,
          column: selection.end.character,
        };
      } else {
        state.hasSelection = false;
        state.selectionStart = undefined;
        state.selectionEnd = undefined;
      }

      projectConnection.updateState(state);
    }
  }

  public dispose(): void {
    if (this.selectionDebounceTimer) {
      clearTimeout(this.selectionDebounceTimer);
      this.selectionDebounceTimer = null;
    }
    this.disposables.forEach((d) => d.dispose());
  }
}

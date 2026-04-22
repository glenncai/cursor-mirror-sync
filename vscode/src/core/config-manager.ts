import * as vscode from 'vscode';
import { Constants } from './constants';
import { CursorMirrorConfiguration } from '../types/models';

export class ConfigManager {
  static getConfiguration(): vscode.WorkspaceConfiguration {
    return vscode.workspace.getConfiguration(Constants.CONFIG_NAMESPACE);
  }

  static getAutoConnect(): boolean {
    return this.getConfiguration().get<boolean>(Constants.CONFIG_AUTO_CONNECT, Constants.DEFAULT_AUTO_CONNECT);
  }

  static getPortRange(): CursorMirrorConfiguration['portRange'] {
    return this.getConfiguration().get(Constants.CONFIG_PORT_RANGE, Constants.DEFAULT_PORT_RANGE);
  }

  static getEnableSelectionSync(): boolean {
    return this.getConfiguration().get<boolean>(
      Constants.CONFIG_ENABLE_SELECTION_SYNC,
      Constants.DEFAULT_SELECTION_SYNC
    );
  }
}

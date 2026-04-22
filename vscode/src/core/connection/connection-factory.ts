import * as vscode from 'vscode';
import { ProjectConnection } from './project-connection';

export class ConnectionFactory {
  /** Creates a new {@link ProjectConnection} seeded with the window-focused state. */
  static createConnection(
    projectName: string,
    projectPath: string,
    port: number,
    onStateChange: () => void,
    isActive: boolean = vscode.window.state.focused
  ): ProjectConnection {
    const connection = new ProjectConnection(projectName, projectPath, port, onStateChange);

    connection.isActive = isActive;

    return connection;
  }
}

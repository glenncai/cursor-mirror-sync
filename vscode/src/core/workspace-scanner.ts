import * as vscode from 'vscode';
import { DetectedProject } from '../types/models';
import { Constants } from './constants';
import { PathUtils } from '../utils/path-utils';

/**
 * Probes the current VSCode workspace for folders that qualify as projects
 * and mints collision-free project names. Extracted so the sync service
 * stays free of workspace-introspection details.
 */
export class WorkspaceScanner {
  constructor(private readonly isNameTaken: (name: string) => boolean) {}

  public detectWorkspaceProjects(): DetectedProject[] {
    const projects: DetectedProject[] = [];

    if (vscode.workspace.workspaceFolders) {
      for (const folder of vscode.workspace.workspaceFolders) {
        const projectName = this.generateProjectName(folder.name);
        projects.push({
          name: projectName,
          path: PathUtils.normalizePath(folder.uri.fsPath),
        });
      }
    }

    return projects;
  }

  public generateProjectName(folderName: string): string {
    const baseName = folderName;
    let counter = Constants.PROJECT_NAME_COUNTER_START;
    let projectName = baseName;

    while (this.isNameTaken(projectName)) {
      projectName = `${baseName}-${counter}`;
      counter++;
    }

    return projectName;
  }
}

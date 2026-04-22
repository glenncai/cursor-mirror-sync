import * as assert from 'assert';
import * as vscode from 'vscode';
import { PortManager } from '../core/connection/port-manager';
import { ProjectConnection } from '../core/connection/project-connection';
import { WorkspaceSyncService } from '../core/workspace-sync-service';

suite('Cursor Mirror Sync Extension Test Suite', () => {
  vscode.window.showInformationMessage('Start Cursor Mirror Sync tests.');

  test('PortManager should generate unique ports', () => {
    const portManager = new PortManager();
    const port1 = portManager.generateRandomPort();
    const port2 = portManager.generateRandomPort();

    assert.notStrictEqual(port1, port2, 'Generated ports should be unique');

    const config = vscode.workspace.getConfiguration('cursor-mirror-sync');
    const portRange = config.get('portRange', { min: 3000, max: 9999 });

    assert.ok(port1 >= portRange.min && port1 <= portRange.max, 'Port should be in configured range');
    assert.ok(port2 >= portRange.min && port2 <= portRange.max, 'Port should be in configured range');
  });

  test('PortManager should release and reuse ports', () => {
    const portManager = new PortManager();
    const port = portManager.generateRandomPort();

    assert.ok(!portManager.isPortAvailable(port), 'Port should be marked as used');

    portManager.releasePort(port);
    assert.ok(portManager.isPortAvailable(port), 'Port should be available after release');
  });

  test('ProjectConnection should initialize with correct properties', () => {
    const connection = new ProjectConnection('test-project', '/test/path', 3000);

    assert.strictEqual(connection.projectName, 'test-project');
    assert.strictEqual(connection.projectPath, '/test/path');
    assert.strictEqual(connection.port, 3000);
    assert.strictEqual(connection.isConnected, false);
    assert.strictEqual(connection.isActive, false);
    assert.strictEqual(connection.currentState, null);
  });

  test('WorkspaceSyncService should initialize with empty connections', () => {
    const service = new WorkspaceSyncService();

    assert.strictEqual(service.connections.size, 0);

    // Clean up
    service.dispose();
  });
});

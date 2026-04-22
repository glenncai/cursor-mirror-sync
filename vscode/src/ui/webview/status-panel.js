/**
 * Cursor Mirror Sync connection status panel webview script.
 * Handles webview interactions and UI updates.
 */

const vscode = acquireVsCodeApi();

const REFRESH_INTERVAL_MS = 30000;

/** In-memory UI state for the panel, with re-render on mutation. */
const AppState = {
  connections: [],
  isLoading: false,

  setConnections(connections) {
    this.connections = connections || [];
    this.render();
  },

  setLoading(loading) {
    this.isLoading = loading;
    this.render();
  },

  render() {
    updateConnectionsList(this.connections, this.isLoading);
  },
};

/** Posts a command message to the extension host. */
function sendMessage(command, data = {}) {
  vscode.postMessage({ command, ...data });
}

/** Requests that the extension reassign a new port to the given project. */
function reassignPort(projectName) {
  if (!projectName) {
    console.error('Project name is required for port reassignment');
    return;
  }

  sendMessage('reassignPort', { name: projectName });
}

/** Renders the connections list for the current state (loading/empty/filled). */
function updateConnectionsList(connections, isLoading = false) {
  const container = document.getElementById('connections-list');

  if (!container) {
    console.error('Connections list container not found');
    return;
  }

  if (isLoading) {
    container.innerHTML = `
      <div class="loading-state">
        <div class="loading-spinner"></div>
        <span>Loading connections...</span>
      </div>
    `;
    return;
  }

  if (!connections || connections.length === 0) {
    container.innerHTML = `
      <div class="empty-state">
        <p>No workspace folders detected. Open a folder or workspace to see connections.</p>
      </div>
    `;
    return;
  }

  container.innerHTML = connections
    .map(
      (conn, index) => `
    <div class="panel-item" data-project="${escapeHtml(conn.name)}">
      <div class="connection-info">
        <div class="connection-name">${escapeHtml(conn.name)}</div>
        <div class="connection-details">
          Path: ${escapeHtml(conn.path)}<br>
          Port: ${escapeHtml(conn.port.toString())}
        </div>
      </div>
      <div class="connection-status">
        <div class="status-indicator ${conn.isConnected ? 'status-connected' : 'status-disconnected'}"></div>
        <span class="status-text">${conn.isConnected ? 'Connected' : 'Disconnected'}</span>
        <button class="reassign-btn"
                data-project-name="${escapeHtml(conn.name)}"
                data-index="${index}"
                data-tooltip="Reassign Port"
                aria-label="Reassign port for ${escapeHtml(conn.name)}">
          <svg class="icon" viewBox="0 0 24 24" fill="none">
            <path d="M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c2.12 0 4.07.74 5.61 1.98" />
            <path d="M17 8l4-4-4-4" />
            <path d="M21 4h-7" />
          </svg>
          <span class="sr-only">Reassign Port</span>
        </button>
      </div>
    </div>
  `
    )
    .join('');

  attachEventListeners();
}

/** Wires click and keyboard activation for reassign-port buttons. */
function attachEventListeners() {
  const reassignButtons = document.querySelectorAll('.reassign-btn');

  reassignButtons.forEach((button) => {
    button.addEventListener('click', handleReassignClick);

    button.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handleReassignClick(e);
      }
    });
  });
}

/** Handles the reassign button activation by entering the in-progress UI state. */
function handleReassignClick(event) {
  // Use currentTarget so nested elements (e.g. the SVG icon) do not shadow the button.
  const button = event.currentTarget;
  const projectName = button.getAttribute('data-project-name');

  if (!projectName) {
    console.error('Project name not found on button');
    return;
  }

  button.disabled = true;
  button.innerHTML = `
    <svg class="icon" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
    <span class="sr-only">Reassigning...</span>
  `;

  // Marker used by restoreReassignButtons to revert after the response arrives.
  button.dataset.reassigning = 'true';

  reassignPort(projectName);
}

/** Reverts any buttons still marked `data-reassigning` back to the idle state. */
function restoreReassignButtons() {
  const reassigningButtons = document.querySelectorAll('button[data-reassigning="true"]');

  reassigningButtons.forEach((button) => {
    button.disabled = false;
    button.removeAttribute('data-reassigning');
    button.innerHTML = `
      <svg class="icon" viewBox="0 0 24 24" fill="none">
        <path d="M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c2.12 0 4.07.74 5.61 1.98" />
        <path d="M17 8l4-4-4-4" />
        <path d="M21 4h-7" />
      </svg>
      <span class="sr-only">Reassign Port</span>
    `;
  });
}

/** Escapes HTML-unsafe characters to prevent XSS when rendering user content. */
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/** Routes inbound messages from the extension host to the matching UI handler. */
function handleMessage(event) {
  const message = event.data;

  switch (message.command) {
    case 'connectionsData':
      AppState.setLoading(false);
      AppState.setConnections(message.data);
      restoreReassignButtons();
      break;

    case 'loading':
      AppState.setLoading(true);
      break;

    case 'error':
      AppState.setLoading(false);
      console.error('Extension error:', message.error);
      restoreReassignButtons();
      break;

    case 'selectionSyncStatus':
      updateSelectionSyncToggle(message.enabled);
      break;

    case 'selectionSyncToggled':
      updateSelectionSyncToggle(message.enabled);
      break;

    default:
      console.warn('Unknown message command:', message.command);
  }
}

/** Installs the message listener and kicks off the initial data fetch loop. */
function initialize() {
  window.addEventListener('message', handleMessage);

  setupSettingsControls();

  AppState.setLoading(true);
  sendMessage('getConnections');

  setInterval(() => {
    sendMessage('getConnections');
  }, REFRESH_INTERVAL_MS);
}

/** Binds the settings-panel controls and requests their initial state. */
function setupSettingsControls() {
  const selectionSyncToggle = document.getElementById('selection-sync-toggle');
  if (selectionSyncToggle) {
    selectionSyncToggle.addEventListener('change', () => {
      sendMessage('toggleSelectionSync');
    });
  }

  sendMessage('getSelectionSyncStatus');
}

/** Reflects the latest selection-sync value in the toggle UI. */
function updateSelectionSyncToggle(enabled) {
  const selectionSyncToggle = document.getElementById('selection-sync-toggle');
  if (selectionSyncToggle) {
    selectionSyncToggle.checked = enabled;
  }
}

/** Refreshes the connection data whenever the panel regains visibility. */
function handleVisibilityChange() {
  if (!document.hidden) {
    sendMessage('getConnections');
  }
}

document.addEventListener('visibilitychange', handleVisibilityChange);

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initialize);
} else {
  initialize();
}

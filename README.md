# Cursor Mirror Sync

**Stop losing your place when switching between VSCode and JetBrains IDEs**

## The Problem You Know Too Well

You know the drill.

Your AI assistant in VS Code brilliantly finds the exact line of code you need to change.
"Awesome!" you think, and switch back to your favorite JetBrains IDE (like IntelliJ or PyCharm).
And then... you're lost. "Wait, which file was that again? What line number?"
You just wasted precious seconds scrolling and searching like a human Ctrl+Shift+F / Cmd+Shift+F.

**This plugin ends that frustration.**

## The Solution

It does one simple thing: it instantly syncs your cursor from VS Code to JetBrains.

When you click on a line in VS Code, your cursor in JetBrains immediately jumps to the very same file and line. No thinking, no searching.

**Stop looking for your code. Start writing it.**

## ✨ Features

- 🔄 **Real-time cursor synchronization** - Click in VS Code, instantly jump to the same line in JetBrains
- 📝 **Text selection mirroring** - Select text in one IDE, see it highlighted in the other
- 🚀 **Auto-connect on startup** - Works automatically when you open your projects
- 🎯 **Multi-project support** - Work on multiple projects simultaneously with automatic port allocation
- ⚙️ **Smart port management** - Random port assignment prevents conflicts between projects
- 🔌 **Zero configuration** - Install and it just works

## 🚀 Quick Start

### 1. Install Both Plugins
- **VS Code**: The extension is not available in the marketplace. Please use `cursor-mirror-sync-1.0.0.vsix` directly.
- **JetBrains**: The extension is not available in the marketplace. Please use `cursor-mirror-sync-1.0.0.zip` directly.

### 2. Open Your Project(s)
Open the same project folder in both IDEs. Want to work on multiple projects? No problem! Each project pair gets its own connection automatically.

### 3. That's It!
The plugins will automatically connect and start syncing your cursor position. Multiple projects? Multiple connections. It just works.

## 🎯 Why This Plugin Stands Out

### Multiple Projects, Zero Conflicts
Unlike other sync tools that use fixed ports, our smart port allocation means you can work on as many projects as you want simultaneously. Whether you have multiple VS Code windows, multiple JetBrains IDEs, or any combination - each project pair gets its own dedicated connection. No setup, no conflicts, no headaches.

**Example workflows:**

**Scenario 1: Multiple VS Code windows**
- Project A: VS Code ↔ IntelliJ (auto-assigned port 3847)
- Project B: VS Code ↔ IntelliJ (auto-assigned port 5291)
- Project C: VS Code ↔ IntelliJ (auto-assigned port 7632)

**Scenario 2: Multiple JetBrains IDEs**
- Project A: VS Code ↔ IntelliJ (auto-assigned port 4521)
- Project B: VS Code ↔ PyCharm (auto-assigned port 6789)
- Project C: VS Code ↔ WebStorm (auto-assigned port 8234)

All running simultaneously, all perfectly synchronized, zero conflicts.

## ⚙️ Configuration

### VSCode Settings
Use the **"View Connection Status"** command in VSCode to access the settings panel with easy toggles for:
- Text selection synchronization
- Connection management
- Port reassignment

### JetBrains Settings
For advanced configuration, access `File > Settings > Tools > Cursor Mirror Sync`:

| Setting | Default | Description |
|---------|---------|-------------|
| Auto Connect | `true` | Enable automatic port discovery from .cursor-mirror-sync.json |
| Manual Port | `3000` | Fallback port when auto discovery fails (range: 3000-9999) |

## 🎯 Commands

- **View Connection Status** - Check if your IDEs are connected
- **Toggle Text Selection Sync** - Enable/disable selection mirroring

Access via Command Palette (`Ctrl+Shift+P` / `Cmd+Shift+P`) and search for "Cursor Mirror Sync"

## 🔧 Requirements

- **VS Code**: Version 1.80.0 or higher
- **JetBrains IDE**: Version 2023.3 or higher (IntelliJ IDEA, PyCharm, WebStorm, etc.)
- **Network**: Both IDEs must be on the same machine or network

## 🐛 Troubleshooting

### Connection Issues
1. Ensure both plugins are installed and enabled
2. Check that both IDEs have the same project open
3. Verify firewall isn't blocking the connection
4. Try restarting both IDEs

### Port Management
If you experience connection issues, you can manually reassign the port through the VSCode status panel. Click the "View Connection Status" command and use the "Reassign Port" button for the affected project.

## 🤝 Contributing

Found a bug or have a feature request? 

- **Issues**: [GitHub Issues](https://github.com/glenncai/cursor-mirror-sync/issues)
- **Source Code**: [GitHub Repository](https://github.com/glenncai/cursor-mirror-sync)

## 📄 License

This project is licensed under the GPL v3 License - see the LICENSE files in the respective subdirectories for details.

---

**Made with ❤️ for developers who use both VS Code and JetBrains IDEs**

*Stop context switching. Start coding.*

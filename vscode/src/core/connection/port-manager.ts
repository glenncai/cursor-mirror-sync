import { IPortManager } from '../../types/contracts';
import { Constants } from '../constants';
import { ConfigManager } from '../config-manager';

export class PortManager implements IPortManager {
  private readonly usedPorts: Set<number>;

  constructor() {
    this.usedPorts = new Set<number>();
  }

  generateRandomPort(): number {
    const portRange = ConfigManager.getPortRange();

    let attempts = 0;
    const maxAttempts = Constants.MAX_PORT_ATTEMPTS;

    while (attempts < maxAttempts) {
      const port = Math.floor(Math.random() * (portRange.max - portRange.min + 1)) + portRange.min;

      if (!this.usedPorts.has(port)) {
        this.usedPorts.add(port);
        return port;
      }

      attempts++;
    }

    for (let port = portRange.min; port <= portRange.max; port++) {
      if (!this.usedPorts.has(port)) {
        this.usedPorts.add(port);
        return port;
      }
    }

    throw new Error('No available ports in the configured range');
  }

  releasePort(port: number): void {
    this.usedPorts.delete(port);
  }

  reservePort(port: number): void {
    this.usedPorts.add(port);
  }

  isPortAvailable(port: number): boolean {
    return !this.usedPorts.has(port);
  }

  getUsedPorts(): number[] {
    return Array.from(this.usedPorts);
  }

  clearAllPorts(): void {
    this.usedPorts.clear();
  }
}

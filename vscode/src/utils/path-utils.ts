import * as path from 'path';

export class PathUtils {
  private static readonly pathCache = new Map<string, string>();
  private static readonly MAX_CACHE_SIZE = 1000;

  /**
   * Normalizes a file path for cross-platform comparison, backed by an LRU
   * cache of size {@link MAX_CACHE_SIZE}.
   */
  static normalizePath(filePath: string): string {
    const cached = this.pathCache.get(filePath);
    if (cached !== undefined) {
      // Re-insert to mark as most recently used.
      this.pathCache.delete(filePath);
      this.pathCache.set(filePath, cached);
      return cached;
    }

    const normalized = path.resolve(filePath).replace(/\\/g, '/');
    const result = process.platform === 'win32' ? normalized.toLowerCase() : normalized;

    if (this.pathCache.size >= this.MAX_CACHE_SIZE) {
      const firstKey = this.pathCache.keys().next().value;
      if (firstKey !== undefined) {
        this.pathCache.delete(firstKey);
      }
    }

    this.pathCache.set(filePath, result);
    return result;
  }

  /** Returns true when `filePath` lies inside `projectPath` (or equals it). */
  static isPathWithinProject(filePath: string, projectPath: string): boolean {
    const normalizedProjectPath = projectPath.endsWith('/') ? projectPath : projectPath + '/';
    const normalizedFilePath = filePath.endsWith('/') ? filePath : filePath + '/';

    return normalizedFilePath.startsWith(normalizedProjectPath) || filePath === projectPath;
  }

  static clearCache(): void {
    this.pathCache.clear();
  }

  static getCacheSize(): number {
    return this.pathCache.size;
  }
}

import { ErrorType, LogContext } from './logger-types';

/**
 * Translates technical errors into user-friendly messages and extracts textual
 * content from unknown error objects. Intentionally has no dependency on the
 * `vscode` API so the classification logic can be unit-tested in isolation.
 */
export class ErrorClassifier {
  /** Extracts a human-readable message from an arbitrary error value. */
  static extractErrorMessage(error: unknown): string {
    if (typeof error === 'string') {
      return error;
    }

    if (error instanceof Error) {
      return error.message;
    }

    if (error && typeof error === 'object') {
      const errorObj = error as Record<string, unknown>;
      if (typeof errorObj.message === 'string') {
        return errorObj.message;
      }
      if (typeof errorObj.error === 'string') {
        return errorObj.error;
      }
      if (typeof errorObj.description === 'string') {
        return errorObj.description;
      }
    }

    return String(error);
  }

  /** Builds a user-friendly error message from raw text, error, and context. */
  static createUserFriendlyMessage(message: string, error?: unknown, context?: LogContext): string {
    // Prefer a predefined friendly message when the caller tagged an error type.
    if (context?.errorType) {
      switch (context.errorType) {
        case ErrorType.NETWORK:
          return 'Network connection issue, please check your network settings or try again later';
        case ErrorType.FILE_OPERATION:
          return 'File operation failed, please check file permissions and path';
        case ErrorType.CONFIGURATION:
          return 'Configuration error, please check extension settings';
        case ErrorType.WEBSOCKET:
          return 'Connection interrupted, attempting to reconnect...';
        case ErrorType.TEMPLATE:
          return 'Template loading failed, please reload the extension or check installation';
        case ErrorType.CONNECTION:
          return 'IDE connection issue, please ensure JetBrains IDE is running';
        case ErrorType.GENERAL:
        default:
          break;
      }
    }

    // For specific operations, provide contextual messages
    if (context?.operation) {
      const operation = context.operation.toLowerCase();
      if (operation.includes('server') || operation.includes('start')) {
        return 'Service startup failed, please check if port is already in use';
      }
      if (operation.includes('file') || operation.includes('read') || operation.includes('write')) {
        return 'File operation failed, please check file permissions';
      }
      if (operation.includes('config') || operation.includes('setting')) {
        return 'Configuration update failed, please check settings format';
      }
      if (operation.includes('template')) {
        return 'Template processing failed, please reload the extension';
      }
    }

    // Analyze error object for common patterns
    if (error) {
      const errorStr = String(error).toLowerCase();
      if (errorStr.includes('enoent') || errorStr.includes('no such file')) {
        return 'File not found, please check the path settings';
      }
      if (errorStr.includes('eacces') || errorStr.includes('permission denied')) {
        return 'Permission denied, please check file permissions';
      }
      if (errorStr.includes('eaddrinuse') || errorStr.includes('address already in use')) {
        return 'Port already in use, please try a different port';
      }
      if (errorStr.includes('econnrefused') || errorStr.includes('connection refused')) {
        return 'Connection refused, please check if the target service is running';
      }
      if (errorStr.includes('timeout')) {
        return 'Operation timed out, please check network connection or try again later';
      }
    }

    // Fallback: clean up technical message for user display
    const cleanMessage = message
      .replace(/\[.*?\]/g, '') // Remove context tags like [Extension]
      .replace(/Error:/g, '')
      .replace(/at.*$/gm, '') // Remove stack trace lines
      .trim();

    return cleanMessage || 'Operation failed, please check the console for details';
  }
}

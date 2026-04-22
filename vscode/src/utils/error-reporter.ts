import { ErrorType, LogContext, LogOptions } from './logger-types';
import { ErrorClassifier } from './error-classifier';
import { Logger } from './logger';

/**
 * Coordinator for the "classify -> log -> notify" pipeline and the single
 * entry point for error-handling call sites. Mirrors the JetBrains plugin's
 * exception reporter/classifier design by keeping classification, logging, and
 * notification in three separate collaborators behind a unified facade.
 */
export class ErrorReporter {
  /**
   * Reports an arbitrary error with caller-supplied context. Extracts a
   * human-readable message and forwards it to {@link Logger.error}, which
   * handles console + OutputChannel IO and (when `options.showUserNotification`
   * is set) triggers a notification via {@link NotificationService}.
   */
  static report(error: unknown, context: LogContext, options?: LogOptions): void {
    const message = ErrorClassifier.extractErrorMessage(error);
    Logger.error(message, error, context, options);
  }

  /** Reports a network error with predefined context and a user notification. */
  static reportNetworkError(error: unknown, projectName: string, operation: string): void {
    const context: LogContext = {
      component: 'NetworkHandler',
      projectName,
      errorType: ErrorType.NETWORK,
      operation,
    };

    const options: LogOptions = {
      showUserNotification: true,
      userFriendlyMessage: `Network connection issue with ${projectName} project, please check network settings or try again later`,
    };

    this.report(error, context, options);
  }

  /** Reports a file operation error with predefined context and a user notification. */
  static reportFileError(error: unknown, filePath: string, operation: string): void {
    const context: LogContext = {
      component: 'FileHandler',
      errorType: ErrorType.FILE_OPERATION,
      operation: `${operation} (${filePath})`,
    };

    const options: LogOptions = {
      showUserNotification: true,
      userFriendlyMessage: 'File operation failed, please check file permissions and path',
    };

    this.report(error, context, options);
  }

  /** Reports a template-loading error with predefined context and a user notification. */
  static reportTemplateError(error: unknown, templateName: string): void {
    const context: LogContext = {
      component: 'TemplateHandler',
      errorType: ErrorType.TEMPLATE,
      operation: `Loading template '${templateName}'`,
    };

    const options: LogOptions = {
      showUserNotification: true,
      userFriendlyMessage: 'Template loading failed, please reload the extension or check installation',
    };

    this.report(error, context, options);
  }
}

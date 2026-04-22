import * as vscode from 'vscode';
import { ErrorType, LogContext, LogOptions } from './logger-types';
import { ErrorClassifier } from './error-classifier';
import { NotificationService } from './notification-service';

// Re-export shared types so existing `import { ... } from '../utils/logger'`
// call sites keep resolving without modification.
export { ErrorType, LogLevel } from './logger-types';
export type { LogContext, LogOptions } from './logger-types';

let isInitialized = false;

/**
 * Core logging component. Writes to the VSCode OutputChannel and the developer
 * console and formats the structured log prefix
 * (`[Component] [ErrorType] [Project] operation:`).
 *
 * Error classification lives in {@link ErrorClassifier}; user-facing
 * notifications are dispatched through {@link NotificationService}; the
 * coordinator that chains classify -> log -> notify is {@link ErrorReporter}.
 */
export class Logger {
  private static outputChannel: vscode.OutputChannel | null = null;

  /** Creates the OutputChannel. Must be called once during extension activation. */
  static initialize(context: vscode.ExtensionContext): void {
    if (isInitialized) {
      return;
    }

    this.outputChannel = vscode.window.createOutputChannel('Cursor Mirror Sync');
    context.subscriptions.push(this.outputChannel);
    isInitialized = true;

    this.info('Logger initialized with OutputChannel', { component: 'Logger' });
  }

  /** Reveals the OutputChannel panel to the user. */
  static showOutputChannel(): void {
    this.outputChannel?.show();
  }

  /** Logs an error with optional cause and context, and an optional notification. */
  static error(message: string, error?: unknown, context?: LogContext | string, options?: LogOptions): void {
    const logContext: LogContext | undefined = typeof context === 'string' ? { component: context } : context;
    const formattedMessage = this.formatMessage(message, logContext);

    if (error) {
      console.error(formattedMessage, error);
      if (error instanceof Error && error.stack) {
        console.error('Stack trace:', error.stack);
      }
    } else {
      console.error(formattedMessage);
    }

    if (this.outputChannel) {
      this.outputChannel.appendLine(`[ERROR] ${formattedMessage}`);
      if (error instanceof Error && error.stack) {
        this.outputChannel.appendLine(error.stack);
      } else if (error) {
        this.outputChannel.appendLine(String(error));
      }
    }

    if (options?.showUserNotification) {
      const notificationLevel = options.notificationLevel || 'error';
      const userMessage =
        options.userFriendlyMessage || ErrorClassifier.createUserFriendlyMessage(message, error, logContext);
      NotificationService.show(userMessage, notificationLevel);
    }
  }

  /** Logs a warning with optional context and an optional notification. */
  static warn(message: string, context?: LogContext | string, options?: LogOptions): void {
    const logContext: LogContext | undefined = typeof context === 'string' ? { component: context } : context;
    const formattedMessage = this.formatMessage(message, logContext);

    console.warn(formattedMessage);

    if (this.outputChannel) {
      this.outputChannel.appendLine(`[WARN] ${formattedMessage}`);
    }

    if (options?.showUserNotification) {
      const notificationLevel = options.notificationLevel || 'warn';
      const userMessage =
        options.userFriendlyMessage || ErrorClassifier.createUserFriendlyMessage(message, undefined, logContext);
      NotificationService.show(userMessage, notificationLevel);
    }
  }

  /** Logs an informational message with optional context and notification. */
  static info(message: string, context?: LogContext | string, options?: LogOptions): void {
    const logContext: LogContext | undefined = typeof context === 'string' ? { component: context } : context;
    const formattedMessage = this.formatMessage(message, logContext);

    console.log(formattedMessage);

    if (this.outputChannel) {
      this.outputChannel.appendLine(`[INFO] ${formattedMessage}`);
    }

    if (options?.showUserNotification) {
      const notificationLevel = options.notificationLevel || 'info';
      const userMessage =
        options.userFriendlyMessage || ErrorClassifier.createUserFriendlyMessage(message, undefined, logContext);
      NotificationService.show(userMessage, notificationLevel);
    }
  }

  /** Logs a debug message with optional context. */
  static debug(message: string, context?: LogContext | string): void {
    const logContext: LogContext | undefined = typeof context === 'string' ? { component: context } : context;
    const formattedMessage = this.formatMessage(message, logContext);

    console.debug(formattedMessage);

    if (this.outputChannel) {
      this.outputChannel.appendLine(`[DEBUG] ${formattedMessage}`);
    }
  }

  /** Logs a plain message without any context formatting. */
  static log(message: string): void {
    console.log(message);
  }

  /** Prefixes the message with component / error-type / project / operation segments. */
  private static formatMessage(message: string, context?: LogContext): string {
    let formattedMessage = message;

    if (context?.operation) {
      formattedMessage = `${context.operation}: ${formattedMessage}`;
    }

    if (context?.projectName) {
      formattedMessage = `[${context.projectName}] ${formattedMessage}`;
    }

    if (context?.errorType && context.errorType !== ErrorType.GENERAL) {
      formattedMessage = `[${context.errorType.toUpperCase()}] ${formattedMessage}`;
    }

    if (context?.component) {
      formattedMessage = `[${context.component}] ${formattedMessage}`;
    }

    return formattedMessage;
  }
}

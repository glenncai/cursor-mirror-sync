import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';

import { Constants } from '../core/constants';
import { Logger } from '../utils/logger';
import { ErrorReporter } from '../utils/error-reporter';
import { ErrorClassifier } from '../utils/error-classifier';

export class WebviewTemplateLoader {
  private readonly context: vscode.ExtensionContext;
  private readonly webview: vscode.Webview;

  constructor(context: vscode.ExtensionContext, webview: vscode.Webview) {
    this.context = context;
    this.webview = webview;
  }

  public loadTemplate(templateName: string): string {
    try {
      const templateDir = this.getTemplateDirectory();
      Logger.info(`Loading template '${templateName}' from directory: ${templateDir}`, {
        component: 'WebviewTemplateLoader',
      });
      const htmlPath = path.join(templateDir, `${templateName}.html`);
      const cssPath = path.join(templateDir, `${templateName}.css`);
      const jsPath = path.join(templateDir, `${templateName}.js`);

      let htmlContent = this.readFileSync(htmlPath);

      const cssUri = this.getWebviewUri(cssPath);
      const jsUri = this.getWebviewUri(jsPath);
      const cspSource = this.webview.cspSource;

      htmlContent = htmlContent
        .replace(Constants.TEMPLATE_CSS_URI, cssUri.toString())
        .replace(Constants.TEMPLATE_JS_URI, jsUri.toString())
        .replace(Constants.TEMPLATE_CSP_SOURCE, cspSource);

      return htmlContent;
    } catch (error: unknown) {
      ErrorReporter.reportTemplateError(error, templateName);
      return this.getErrorTemplate(error);
    }
  }

  private getWebviewUri(localPath: string): vscode.Uri {
    return this.webview.asWebviewUri(vscode.Uri.file(localPath));
  }

  private readFileSync(filePath: string): string {
    try {
      return fs.readFileSync(filePath, 'utf8');
    } catch (error: unknown) {
      const errorMessage = ErrorClassifier.extractErrorMessage(error);
      throw new Error(`Failed to read file '${filePath}': ${errorMessage}`);
    }
  }

  private async readFileAsync(filePath: string): Promise<string> {
    try {
      return await fs.promises.readFile(filePath, 'utf8');
    } catch (error: unknown) {
      const errorMessage = ErrorClassifier.extractErrorMessage(error);
      throw new Error(`Failed to read file '${filePath}': ${errorMessage}`);
    }
  }

  /** Asynchronous variant of {@link loadTemplate}. */
  public async loadTemplateAsync(templateName: string): Promise<string> {
    try {
      const templateDir = this.getTemplateDirectory();
      Logger.info(
        `Loading template '${templateName}' asynchronously from directory: ${templateDir}`,
        'WebviewTemplateLoader'
      );
      const htmlPath = path.join(templateDir, `${templateName}.html`);
      const cssPath = path.join(templateDir, `${templateName}.css`);
      const jsPath = path.join(templateDir, `${templateName}.js`);

      let htmlContent = await this.readFileAsync(htmlPath);

      const cssUri = this.getWebviewUri(cssPath);
      const jsUri = this.getWebviewUri(jsPath);
      const cspSource = this.webview.cspSource;

      htmlContent = htmlContent
        .replace(Constants.TEMPLATE_CSS_URI, cssUri.toString())
        .replace(Constants.TEMPLATE_JS_URI, jsUri.toString())
        .replace(Constants.TEMPLATE_CSP_SOURCE, cspSource);

      return htmlContent;
    } catch (error: unknown) {
      ErrorReporter.reportTemplateError(error, templateName);
      return this.getErrorTemplate(error);
    }
  }

  /** Produces a standalone HTML document describing the template failure. */
  private getErrorTemplate(error: unknown): string {
    return `
      <!DOCTYPE html>
      <html lang="en">
      <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Template Error</title>
          <style>
              body {
                  font-family: var(--vscode-font-family);
                  color: var(--vscode-errorForeground);
                  background-color: var(--vscode-editor-background);
                  padding: 20px;
                  margin: 0;
              }
              .error-container {
                  max-width: 600px;
                  margin: 0 auto;
                  text-align: center;
              }
              .error-title {
                  font-size: 24px;
                  margin-bottom: 20px;
              }
              .error-message {
                  background-color: var(--vscode-inputValidation-errorBackground);
                  border: 1px solid var(--vscode-inputValidation-errorBorder);
                  padding: 15px;
                  border-radius: 4px;
                  margin-bottom: 20px;
                  font-family: monospace;
                  text-align: left;
                  white-space: pre-wrap;
              }
          </style>
      </head>
      <body>
          <div class="error-container">
              <h1 class="error-title">Template Loading Error</h1>
              <div class="error-message">${this.escapeHtml(ErrorClassifier.extractErrorMessage(error))}</div>
              <p>Please check the extension logs for more details.</p>
          </div>
      </body>
      </html>
    `;
  }

  /** Escapes HTML-unsafe characters to prevent XSS in rendered error text. */
  private escapeHtml(text: string): string {
    const map: { [key: string]: string } = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;',
    };
    return text.replace(/[&<>"']/g, (m) => map[m]);
  }

  /** Resolves the template directory, preferring `dist` (packaged) over `src` (development). */
  private getTemplateDirectory(): string {
    const distDir = path.join(this.context.extensionPath, 'dist', 'src', 'ui', 'webview');
    const srcDir = path.join(this.context.extensionPath, 'src', 'ui', 'webview');

    if (fs.existsSync(distDir)) {
      return distDir;
    }

    return srcDir;
  }

  /** Reports whether every required template file (HTML, CSS, JS) is present. */
  public validateTemplate(templateName: string): { isValid: boolean; missingFiles: string[] } {
    const templateDir = this.getTemplateDirectory();
    const requiredFiles = [
      path.join(templateDir, `${templateName}.html`),
      path.join(templateDir, `${templateName}.css`),
      path.join(templateDir, `${templateName}.js`),
    ];

    const missingFiles: string[] = [];

    for (const filePath of requiredFiles) {
      try {
        fs.accessSync(filePath, fs.constants.F_OK);
      } catch {
        missingFiles.push(path.basename(filePath));
      }
    }

    return {
      isValid: missingFiles.length === 0,
      missingFiles,
    };
  }
}

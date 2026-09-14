import base from '../ember-integration/browser/playwright.config.mjs'
import { fileURLToPath } from 'node:url'
export default {
  ...base,
  testDir: '.', testMatch: 'browser.spec.mjs', workers: 1, timeout: 240000,
  outputDir: '../target/p28/browser-results',
  webServer: { ...base.webServer, command: 'node ../ember-integration/browser/server.mjs', cwd: fileURLToPath(new URL('.', import.meta.url)) },
  reporter: [['line'], ['json', { outputFile: '../target/p28/browser-report.json' }]],
}

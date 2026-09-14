import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './test',
  outputDir: '../target/ember-demo-results',
  fullyParallel: true,
  workers: 2,
  use: { baseURL: 'http://127.0.0.1:4201', viewport: { width: 1440, height: 1000 } },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: {
    command: 'node dev/server.mjs',
    url: 'http://127.0.0.1:4201',
    env: { EMBER_DEMO_PORT: '4201', EMBER_DEMO_FULL: '1' },
    reuseExistingServer: false,
  },
})

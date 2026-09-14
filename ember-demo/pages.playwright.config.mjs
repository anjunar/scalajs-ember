import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './test',
  outputDir: '../target/ember-demo-pages-results',
  fullyParallel: true,
  workers: 2,
  use: { baseURL: 'http://127.0.0.1:4202/scalajs-ember/', viewport: { width: 1440, height: 1000 } },
  projects: [{ name: 'pages-chromium', use: { browserName: 'chromium' } }],
  webServer: {
    command: 'node dev/pages-server.mjs',
    url: 'http://127.0.0.1:4202/scalajs-ember/',
    reuseExistingServer: false,
  },
})

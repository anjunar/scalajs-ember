import { defineConfig } from '@playwright/test'

// Drei Engines, weil §24 sie verlangt: Chromium, Firefox und WebKit unterscheiden sich genau
// dort, wo dieser Editor spaeter arbeitet -- Selection, Eingabe, Composition. Ein gruener Lauf
// in einer davon sagt darueber nichts.
export default defineConfig({
  testDir: './test',
  fullyParallel: true,
  workers: 3,
  outputDir: '../../target/ember-browser-results',
  use: { baseURL: 'http://127.0.0.1:4188' },
  projects: ['chromium', 'firefox', 'webkit'].map(browserName => ({
    name: browserName,
    use: { browserName },
  })),
  webServer: {
    command: 'node server.mjs',
    url: 'http://127.0.0.1:4188',
    reuseExistingServer: false,
  },
})

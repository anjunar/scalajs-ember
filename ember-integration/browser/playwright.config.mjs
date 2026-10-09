import { defineConfig } from '@playwright/test'

// Drei Engines, weil §24 sie verlangt: Chromium, Firefox und WebKit unterscheiden sich genau
// dort, wo dieser Editor spaeter arbeitet -- Selection, Eingabe, Composition. Ein gruener Lauf
// in einer davon sagt darueber nichts.
//
// Firefox laeuft voreingestellt gegen den von Playwright mitgelieferten Build. Das ist der
// kanonische Zielbrowser und der, den die CI verwendet.
//
// Auf manchen Windows-Staenden startet dieser Build nicht: er verlangt im Manifest die private
// Side-by-Side-Assembly `mozglue`, und Windows verweigert die Aufloesung mit
// "spawn UNKNOWN" -- noch vor dem ersten Test. Ein regulaer installierter Firefox derselben
// Version startet auf derselben Maschine einwandfrei, und die SxS-Mechanik ist damit
// nachweislich in Ordnung; das Problem liegt im mitgelieferten Build.
//
// Fuer diesen Fall gibt es den offiziell unterstuetzten Kanal `moz-firefox`, der den
// installierten Firefox ansteuert:
//
//   EMBER_FIREFOX_CHANNEL=moz-firefox npm run test:browser
//
// Er spricht WebDriver BiDi statt Juggler. Getestet wird dieselbe Engine, aber ueber einen
// anderen Steuerkanal -- deshalb ist er ausdruecklich nicht die Voreinstellung, sondern ein
// Ausweg fuer Arbeitsplaetze, an denen der mitgelieferte Build nicht startet.
const firefoxChannel = process.env.EMBER_FIREFOX_CHANNEL

export default defineConfig({
  testDir: './test',
  fullyParallel: true,
  workers: 3,
  outputDir: '../../target/ember-browser-results',
  use: { baseURL: 'http://127.0.0.1:4188' },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
    {
      name: 'firefox',
      use: {
        browserName: 'firefox',
        ...(firefoxChannel ? { channel: firefoxChannel } : {}),
      },
    },
    { name: 'webkit', use: { browserName: 'webkit' } },
  ],
  webServer: {
    command: 'node server.mjs',
    url: 'http://127.0.0.1:4188',
    reuseExistingServer: false,
  },
})

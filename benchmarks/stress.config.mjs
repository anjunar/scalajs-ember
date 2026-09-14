import base from './playwright.config.mjs'
export default {
  ...base, testMatch: 'large-move.spec.mjs', timeout: 240000,
  outputDir: '../target/p28/large-move-results',
  reporter: [['line'], ['json', { outputFile: '../target/p28/large-move-report.json' }]],
}

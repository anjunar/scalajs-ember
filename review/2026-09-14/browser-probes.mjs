// Run from the repository root after integration/fullLinkJS and npm install in
// ember-integration/browser: node review/2026-09-14/browser-probes.mjs
// Only tests the local fixture. It neither changes production sources nor uses a real IME.
// Exit 1 means at least one review regression remains; all four cases still run.
import { spawn } from 'node:child_process'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

const repository = process.cwd()
const harness = resolve(repository, 'ember-integration/browser')
const { chromium } = createRequire(resolve(harness, 'package.json'))('@playwright/test')
const url = 'http://127.0.0.1:4188'
const server = spawn(process.execPath, ['server.mjs'], {
  cwd: harness,
  windowsHide: true,
  stdio: ['ignore', 'pipe', 'pipe'],
})
let serverOutput = ''
server.stdout.on('data', value => { serverOutput += value.toString() })
server.stderr.on('data', value => { serverOutput += value.toString() })
let browser
const results = []
const record = (name, expected, actual, passed) => {
  const result = { name, passed, expected, actual }
  results.push(result)
  console.log(JSON.stringify(result, null, 2))
}

try {
  let ready = false
  for (let attempt = 0; attempt < 50; attempt++) {
    if (server.exitCode !== null) throw new Error(`Fixture server stopped: ${serverOutput}`)
    try { ready = (await fetch(url)).ok } catch {}
    if (ready) break
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  if (!ready) throw new Error(`Fixture server did not become ready: ${serverOutput}`)
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  page.on('pageerror', error => console.error('Page error:', error.message))
  const open = async () => {
    await page.goto(url)
    await page.waitForFunction(() => window.ready === true)
    await page.evaluate(() => window.editing.mount(document.getElementById('root')))
    await page.locator('#root').focus()
    await page.evaluate(() => {
      window.editing.setCaret('t0', 10)
      window.editing.clearOutcomes()
    })
  }

  // Real keyboard event: word deletion must preserve its inputType granularity.
  await open()
  await page.keyboard.press('Control+Backspace')
  const wordDelete = await page.evaluate(() => ({
    model: window.editing.text(), log: window.editing.outcomeLog(),
  }))
  // Native contenteditable retains trailing whitespace as NBSP.
  record('word-delete', 'Hallo\u00a0\nZweite Zeile', wordDelete,
    wordDelete.model === 'Hallo\u00a0\nZweite Zeile')

  // Real canceled typing first; then simulate a later noncancelable native edit.
  // This exercises the native-input protocol, not actual IME/device acceptance.
  await open()
  await page.keyboard.type('X')
  const nativeInput = await page.evaluate(() => {
    const host = document.getElementById('root')
    host.dispatchEvent(new InputEvent('beforeinput', {
      bubbles: true, cancelable: false, inputType: 'insertText', data: 'Y',
    }))
    const text = document.querySelector('[data-ember-node="t0"]').firstChild
    text.data += 'Y'
    getSelection().collapse(text, text.data.length)
    host.dispatchEvent(new InputEvent('input', {
      bubbles: true, inputType: 'insertText', data: 'Y',
    }))
    return { model: window.editing.text(), dom: text.data,
      log: window.editing.outcomeLog() }
  })
  record('stale-input-claim', 'Hallo WeltXY\nZweite Zeile', nativeInput,
    nativeInput.model === 'Hallo WeltXY\nZweite Zeile')

  // Composition in a native atom textarea belongs to that control.
  await open()
  await page.locator('[data-widget-input]').focus()
  const foreignComposition = await page.evaluate(() => {
    document.querySelector('[data-widget-input]').dispatchEvent(
      new CompositionEvent('compositionstart', { bubbles: true }))
    return { state: window.editing.state(), composition: window.editing.compositionLog(),
      independentEdit: window.editing.splice('t0', 0, 0, 'A') }
  })
  record('foreign-composition', { state: 'Ready', independentEdit: true },
    foreignComposition,
    foreignComposition.state === 'Ready' && foreignComposition.independentEdit)

  // Native/browser tooling may replace a Text host without changing its text.
  // Following typing must use a live host or refuse safely, never write a detached node.
  await open()
  await page.evaluate(() => {
    const original = document.querySelector('[data-ember-node="t0"]').firstChild
    const replacement = original.cloneNode(true)
    original.replaceWith(replacement)
    window.__reviewOriginalText = original
    getSelection().collapse(replacement, 5)
  })
  await page.waitForTimeout(30)
  await page.keyboard.type('X')
  const replacement = await page.evaluate(() => ({
    model: window.editing.text(),
    dom: document.querySelector('[data-ember-node="t0"]').textContent,
    detached: window.__reviewOriginalText.data,
    state: window.editing.state(), log: window.editing.outcomeLog(),
  }))
  record('replaced-text-host', { model: 'HalloX Welt\nZweite Zeile', dom: 'HalloX Welt' },
    replacement,
    replacement.model === 'HalloX Welt\nZweite Zeile' && replacement.dom === 'HalloX Welt')
} finally {
  if (browser) await browser.close()
  server.kill()
}

const failures = results.filter(result => !result.passed)
console.log(`${failures.length}/${results.length} review regressions reproduced`)
process.exitCode = failures.length > 0 ? 1 : 0

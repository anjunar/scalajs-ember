import { test, expect } from '../ember-integration/browser/node_modules/@playwright/test/index.mjs'

test('100001 nodes: moving the first of 50000 siblings to the end', async ({ page, browser, browserName }, testInfo) => {
  await page.goto('/')
  await page.waitForFunction(() => window.ready)
  const mount = await page.evaluate(() => window.bench.mount(document.getElementById('root'), 100000, 'paragraphs'))
  await testInfo.attach('mount', { body: JSON.stringify(mount), contentType: 'application/json' })
  await page.evaluate(() => window.bench.edits(1000))
  const move = await page.evaluate(() => {
    const root = document.querySelector('#root article')
    const first = root.firstElementChild
    const observer = new MutationObserver(() => {})
    observer.observe(root, { childList: true })
    const result = window.bench.move()
    const records = observer.takeRecords()
    observer.disconnect()
    return { ...result, firstIsLast: root.lastElementChild === first,
      added: records.reduce((n, r) => n + r.addedNodes.length, 0),
      removed: records.reduce((n, r) => n + r.removedNodes.length, 0) }
  })
  await testInfo.attach('move', { body: JSON.stringify({ browser: browserName, version: browser.version(), ...move }), contentType: 'application/json' })
  expect(move.identity).toBe(true)
  expect(move.mounts).toBe(0)
  expect(move.unmounts).toBe(0)
  expect(move.firstIsLast).toBe(true)
  expect(move.added).toBe(1)
  expect(move.removed).toBe(1)
  await page.evaluate(() => window.bench.dispose())
})

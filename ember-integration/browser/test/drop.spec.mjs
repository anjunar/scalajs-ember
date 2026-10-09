import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.clipboard.mount(document.getElementById('root')))
  await page.locator('#root').focus()
})
test.afterEach(async ({ page }) => page.evaluate(() => window.clipboard?.dispose()).catch(() => {}))

async function dropAt(page, { local = false, copy = false, change = false, cross = false, files = false, echo = false } = {}) {
  return page.evaluate(({ local, copy, change, cross, files, echo }) => {
    const source = document.getElementById('root')
    const data = new DataTransfer()
    if (local) source.dispatchEvent(new DragEvent('dragstart', { bubbles: true, cancelable: true, dataTransfer: data }))
    else data.setData('text/plain', 'EXTERNAL')
    if (files) data.items.add(new File(['fixture'], 'drop.png', { type: 'image/png' }))
    if (change) window.clipboard.edit('t0', 0, 'X')
    let target = source
    if (cross) {
      target = document.createElement('div')
      target.id = 'other'
      document.body.append(target)
      window.clipboard.mount(target, 'b')
      target.focus()
    }
    const run = target.querySelector('[data-ember-node="t2"]')
    const range = document.createRange()
    const text = document.createTreeWalker(run, NodeFilter.SHOW_TEXT).nextNode()
    range.setStart(text, text.length)
    range.collapse(true)
    const rect = range.getBoundingClientRect()
    const event = new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: data,
      clientX: rect.left, clientY: rect.top + rect.height / 2, ctrlKey: copy })
    target.dispatchEvent(event)
    if (echo) {
      const input = new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertFromDrop' })
      Object.defineProperty(input, 'dataTransfer', { value: data })
      target.dispatchEvent(input)
      source.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'deleteByDrag' }))
    }
    source.dispatchEvent(new DragEvent('dragend', { bubbles: true, dataTransfer: data }))
    return { text: window.clipboard.text(cross ? 'b' : 'a'), source: window.clipboard.text(),
      log: window.clipboard.log(cross ? 'b' : 'a'), prevented: event.defaultPrevented }
  }, { local, copy, change, cross, files, echo })
}

test('external text drops at the coordinates, not at the old selection', async ({ page }) => {
  await page.evaluate(() => window.clipboard.select('t0', 1, 't0', 1))
  const result = await dropAt(page)
  expect(result.prevented).toBe(true)
  expect(result.text, result.log).toBe('Hello world\nSecond lineEXTERNAL\n\uFFFC')
})

test('an internal partial range moves and is one undo step', async ({ page }) => {
  await page.evaluate(() => window.clipboard.select('t0', 0, 't0', 5))
  const result = await dropAt(page, { local: true, echo: true })
  expect(result.text, result.log).toBe(' world\nSecond lineHello\n\uFFFC')
  expect(await page.evaluate(() => window.clipboard.undo())).toBe(true)
  expect(await page.evaluate(() => window.clipboard.text())).toBe('Hello world\nSecond line\n\uFFFC')
})

test('a whole block move retains its DOM host and node IDs', async ({ page }) => {
  await page.evaluate(() => {
    window.clipboard.selectNode('p0')
    window.originalParagraph = document.querySelector('[data-ember-node="p0"]')
  })
  const result = await dropAt(page, { local: true })
  expect(result.text, result.log).toBe('Second line\nHello world\n\uFFFC')
  expect(await page.evaluate(() => document.querySelector('[data-ember-node="p0"]') === window.originalParagraph)).toBe(true)
  expect(await page.evaluate(() => window.clipboard.rootChildren())).toEqual(['p1', 'p0', 'p2'])
})

test('Control-drag copies without removing its source', async ({ page }) => {
  await page.evaluate(() => window.clipboard.select('t0', 0, 't0', 5))
  const result = await dropAt(page, { local: true, copy: true })
  expect(result.text).toBe('Hello world\nSecond lineHello\n\uFFFC')
})

test('a different editor receives a copy even when IDs and profiles match', async ({ page }) => {
  await page.evaluate(() => window.clipboard.select('t0', 0, 't0', 5))
  const result = await dropAt(page, { local: true, cross: true })
  expect(result.source).toBe('Hello world\nSecond line\n\uFFFC')
  expect(result.text).toBe('Hello world\nSecond lineHello\n\uFFFC')
})

test('a source changed during drag is never deleted by stale completion', async ({ page }) => {
  await page.evaluate(() => window.clipboard.select('t0', 0, 't0', 5))
  const result = await dropAt(page, { local: true, change: true })
  expect(result.text).toBe('XHello world\nSecond line\n\uFFFC')
  expect(result.log).toContain('geändert')
})

test('dropped files become exactly one media intent including an input echo', async ({ page }) => {
  const result = await dropAt(page, { files: true, echo: true })
  expect(result.text).toBe('Hello world\nSecond line\n\uFFFC')
  expect(await page.evaluate(() => window.clipboard.fileCount())).toBe(1)
})

test('readonly refuses drops and their input echo', async ({ page }) => {
  await page.evaluate(() => window.clipboard.readonly(true))
  const result = await dropAt(page, { echo: true })
  expect(result.prevented).toBe(true)
  expect(result.text).toBe('Hello world\nSecond line\n\uFFFC')
})

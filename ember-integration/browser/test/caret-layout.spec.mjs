import { test, expect } from '@playwright/test'

async function open(page) {
  await page.goto('/toolbar')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.toolbar.select(11, 11))
}

// Read the actual layout, not just the model offset. A collapsed trailing space and
// a zero-height empty paragraph both had correct offsets while the caret stayed behind.
const caret = page => page.evaluate(() => {
  const selection = getSelection()
  const range = selection.getRangeAt(0)
  let rect = range.getBoundingClientRect()
  const element = selection.focusNode.nodeType === Node.TEXT_NODE
    ? selection.focusNode.parentElement : selection.focusNode
  const block = element.closest('p, h1, h2, h3, h4, h5, h6, pre')
  // An empty Text has no glyph range. Its inline wrapper still exposes the
  // insertion line's box (also checked visually with the native caret shown).
  if (!rect.height && selection.focusNode.nodeType === Node.TEXT_NODE && !selection.focusNode.length) {
    rect = element.getBoundingClientRect()
  }
  // Engines do not all expose a collapsed element Range's rectangle. Its following
  // BR is the native line box for that insertion boundary.
  if (!rect.height && selection.focusNode.nodeType === Node.ELEMENT_NODE) {
    const next = selection.focusNode.childNodes[selection.focusOffset]
    if (next?.nodeName === 'BR') rect = next.getBoundingClientRect()
  }
  return { x: rect.x, y: rect.y, height: rect.height,
    blockHeight: block?.getBoundingClientRect().height,
    blockTop: block?.getBoundingClientRect().top,
    offset: selection.focusOffset, text: window.toolbar.text() }
})

test('trailing and repeated spaces move the visible caret immediately', async ({ page }) => {
  await open(page)
  let previous = await caret(page)
  for (let i = 0; i < 3; i++) {
    await page.keyboard.press('Space')
    const next = await caret(page)
    expect(next.text).toBe(`Hello world${' '.repeat(i + 1)}`)
    expect(next.x).toBeGreaterThan(previous.x + 1)
    expect(next.y).toBeCloseTo(previous.y, 0)
    previous = next
  }
})

test('Enter creates a visible caret line before any letter is typed', async ({ page }) => {
  await open(page)
  let previous = await caret(page)
  for (let i = 0; i < 3; i++) {
    await page.keyboard.press('Enter')
    const next = await caret(page)
    expect(next.blockHeight).toBeGreaterThan(10)
    expect(next.height).toBeGreaterThan(10)
    expect(next.y).toBeGreaterThan(previous.y + 10)
    expect(next.y).toBeGreaterThanOrEqual(next.blockTop)
    expect(next.text).toBe('Hello world')
    previous = next
  }
  await page.keyboard.press('Space')
  const space = await caret(page)
  expect(space.x).toBeGreaterThan(previous.x + 1)
  expect(space.y).toBeCloseTo(previous.y, 0)
  await page.keyboard.type('x')
  expect((await caret(page)).y).toBeCloseTo(space.y, 0)
})

test('Shift+Enter exposes the new line at the end of a paragraph', async ({ page }) => {
  await open(page)
  const before = await caret(page)
  await page.keyboard.press('Shift+Enter')
  const after = await caret(page)
  expect(after.height).toBeGreaterThan(10)
  expect(after.y).toBeGreaterThan(before.y + 10)
  await page.keyboard.type('x')
  expect((await caret(page)).y).toBeCloseTo(after.y, 0)
})

test('deleting the last letter and Undo restore the empty caret line', async ({ page }) => {
  await open(page)
  await page.keyboard.press('Enter')
  const empty = await caret(page)
  await page.keyboard.type('x')
  await expect(page.locator('[data-ember-caret]')).toHaveCount(0)
  await page.keyboard.press('Backspace')
  const deleted = await caret(page)
  expect(deleted.height).toBeGreaterThan(10)
  expect(deleted.y).toBeCloseTo(empty.y, 0)
  await expect(page.locator('[data-ember-caret]')).toHaveCount(1)
  await page.keyboard.press('ControlOrMeta+z')
  expect((await caret(page)).text).toBe('Hello worldx')
  await expect(page.locator('[data-ember-caret]')).toHaveCount(0)
  await page.keyboard.press('ControlOrMeta+Shift+z')
  expect((await caret(page)).height).toBeGreaterThan(10)
  await expect(page.locator('[data-ember-caret]')).toHaveCount(1)
})

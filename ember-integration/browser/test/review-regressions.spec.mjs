import { test, expect } from '@playwright/test'

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.editing.mount(document.getElementById('root')))
  await page.locator('#root').focus()
  await page.evaluate(() => window.editing.setCaret('t0', 10))
}

test.beforeEach(async ({ page }) => open(page))
test.afterEach(async ({ page }) => {
  await page.evaluate(() => window.editing?.dispose()).catch(() => {})
})

test('word deletion keeps the requested granularity', async ({ page }) => {
  await page.keyboard.press('Control+Backspace')
  // Native contenteditable preserves the newly trailing space as NBSP in all
  // three engines. The import keeps that DOM text instead of rewriting it.
  expect(await page.evaluate(() => window.editing.text())).toBe('Hallo\u00a0\nZweite Zeile')
})

for (const beforeInput of [true, false]) {
  test(`a canceled input cannot swallow later native text (beforeinput=${beforeInput})`, async ({ page }) => {
    await page.keyboard.type('X')
    const model = await page.evaluate((beforeInput) => {
      const host = document.getElementById('root')
      if (beforeInput) host.dispatchEvent(new InputEvent('beforeinput', {
        bubbles: true, cancelable: false, inputType: 'insertText', data: 'Y',
      }))
      const text = document.querySelector('[data-ember-node="t0"]').firstChild
      text.data += 'Y'
      getSelection().collapse(text, text.length)
      host.dispatchEvent(new InputEvent('input', {
        bubbles: true, inputType: 'insertText', data: 'Y',
      }))
      return window.editing.text()
    }, beforeInput)
    expect(model).toBe('Hallo WeltXY\nZweite Zeile')
  })
}

test('an atom textarea composition leaves the outer editor available', async ({ page }) => {
  await page.locator('[data-widget-input]').focus()
  const result = await page.evaluate(() => {
    document.querySelector('[data-widget-input]').dispatchEvent(
      new CompositionEvent('compositionstart', { bubbles: true }))
    return [window.editing.state(), window.editing.splice('t0', 0, 0, 'A')]
  })
  expect(result).toEqual(['Ready', true])
})

test('an atom compositionend cannot finish the outer composition', async ({ page }) => {
  const result = await page.evaluate(() => {
    document.getElementById('root').dispatchEvent(
      new CompositionEvent('compositionstart', { bubbles: true }))
    document.querySelector('[data-widget-input]').dispatchEvent(
      new CompositionEvent('compositionend', { bubbles: true }))
    return window.editing.state()
  })
  expect(result).toBe('Composing')
})

test('an identical replacement Text is automatically rebound before typing', async ({ page }) => {
  await page.evaluate(() => {
    const text = document.querySelector('[data-ember-node="t0"]').firstChild
    const clone = text.cloneNode(true)
    text.replaceWith(clone)
    window.oldText = text
    getSelection().collapse(clone, 5)
  })
  await page.keyboard.type('X')
  const result = await page.evaluate(() => ({
    model: window.editing.text(),
    dom: document.querySelector('[data-ember-node="t0"]').textContent,
    old: window.oldText.data,
  }))
  expect(result).toEqual({ model: 'HalloX Welt\nZweite Zeile', dom: 'HalloX Welt', old: 'Hallo Welt' })
})

test('a same-task projection never writes the detached former Text', async ({ page }) => {
  const result = await page.evaluate(() => {
    const text = document.querySelector('[data-ember-node="t0"]').firstChild
    text.replaceWith(text.cloneNode(true))
    const accepted = window.editing.splice('t0', 5, 0, 'X')
    return [accepted, document.querySelector('[data-ember-node="t0"]').textContent, text.data]
  })
  expect(result).toEqual([true, 'HalloX Welt', 'Hallo Welt'])
})

test('native input on a replacement Text is imported before rebinding', async ({ page }) => {
  const result = await page.evaluate(() => {
    const wrapper = document.querySelector('[data-ember-node="t0"]')
    const replacement = document.createTextNode('Hallo WeltY')
    wrapper.firstChild.replaceWith(replacement)
    getSelection().collapse(replacement, replacement.length)
    document.getElementById('root').dispatchEvent(new InputEvent('input', {
      bubbles: true, inputType: 'insertText', data: 'Y',
    }))
    return [window.editing.text(), wrapper.textContent, window.editing.state()]
  })
  expect(result).toEqual(['Hallo WeltY\nZweite Zeile', 'Hallo WeltY', 'Ready'])
})

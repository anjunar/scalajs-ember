import { test, expect } from '@playwright/test'
import { readFile } from 'node:fs/promises'
for (const name of ['text', 'markdown', 'standard']) {
  test(`${name} independently linked profile edits, serializes and disposes`, async ({ page }) => {
    await page.goto(`/profile?name=${name}`)
    await page.waitForFunction(() => window.ready)
    const registrations = await page.evaluate(() => [...window.profile.registrations()])
    if (name !== 'standard') expect(registrations).toEqual(['ember.rich-text'])
    else expect(registrations).toEqual(expect.arrayContaining(['ember.rich-text', 'ember.list', 'ember.link', 'ember.image', 'ember.code', 'ember.history']))
    await page.locator('#root').getByText('Hello', { exact: true }).click()
    await page.keyboard.press('Control+End')
    await page.waitForFunction(() => document.getSelection()?.focusOffset === 5)
    await page.keyboard.type('!')
    await page.getByRole('button', { name: 'Wert lesen' }).click()
    await expect(page.locator('#output')).toContainText('Hello!')
    await page.evaluate(() => window.profile.dispose())
    await expect(page.locator('#root')).toBeEmpty()
  })
}

test('controlled input uses the visible caret before queued selectionchange arrives', async ({ page }) => {
  await page.goto('/profile?name=text')
  await page.waitForFunction(() => window.ready)
  await page.locator('#root').focus()
  const result = await page.evaluate(() => {
    const host = document.getElementById('root')
    const text = host.querySelector('[data-ember-node="t"]').firstChild
    const selection = document.getSelection()
    selection.setPosition(text, 0)
    document.dispatchEvent(new Event('selectionchange'))
    // Both operations run in one task: the browser cannot deliver selectionchange between them.
    selection.setPosition(text, 5)
    host.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: '!' }))
    return { model: window.profile.value(), dom: host.textContent }
  })
  expect(result).toEqual({ model: 'Hello!', dom: 'Hello!' })
  await page.evaluate(() => window.profile.dispose())
})

test('operator trace starts explicitly, records real page input and downloads bounded evidence', async ({ page }) => {
  await page.goto('/toolbar?trace=1')
  await page.waitForFunction(() => window.ready)
  await page.getByRole('button', { name: 'Trace starten' }).click()
  await page.locator('#toolbar-editor').focus()
  await page.keyboard.press('End')
  await page.keyboard.type('abc')
  await page.getByRole('button', { name: 'Trace stoppen' }).click()
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Trace herunterladen' }).click()
  const download = await downloaded
  const trace = JSON.parse(await readFile(await download.path(), 'utf8'))
  expect(trace.truncated).toBe(false)
  expect(trace.userAgent).not.toBe('')
  // Controlled beforeinput may prevent the browser's later native input event.
  const inputs = trace.events.filter(event => event.type === 'beforeinput' && event.inputType === 'insertText')
  expect(inputs.length).toBeGreaterThan(0)
  expect(inputs.every(event => event.trusted)).toBe(true)
  expect(inputs.at(-1).modelText).toContain('abc')
  expect(inputs.at(-1).modelText).toBe(inputs.at(-1).domText)
  await page.evaluate(() => window.toolbar.dispose())
})

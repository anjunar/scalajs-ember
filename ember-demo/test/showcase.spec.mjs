import { test, expect } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const surface = page => page.getByRole('textbox', { name: 'Dokument bearbeiten' })
const action = (page, id) => page.locator(`[data-command="${id}"]`)

async function selectText(page, content) {
  await surface(page).evaluate((host, content) => {
    const walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT)
    let node
    while ((node = walker.nextNode())) {
      const index = node.textContent.indexOf(content)
      if (index < 0) continue
      host.focus()
      const range = document.createRange()
      range.setStart(node, index)
      range.setEnd(node, index + content.length)
      const selection = window.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)
      document.dispatchEvent(new Event('selectionchange'))
      return
    }
    throw new Error(`Visible text not found: ${content}`)
  }, content)
}

test('all examples render and navigation preserves edits and undo', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('./#blank')
  await surface(page).click()
  await page.keyboard.type('Meine neue Idee')
  await expect(surface(page)).toContainText('Meine neue Idee')
  await page.getByRole('link', { name: 'Notizen & Listen' }).click()
  await expect(surface(page).locator('ul ul')).toBeVisible()
  await page.getByRole('link', { name: 'Code & Medien' }).click()
  await expect(surface(page).locator('pre')).toContainText('println')
  await expect(surface(page).locator('img')).toHaveJSProperty('naturalWidth', 1000)
  await page.getByRole('link', { name: 'Leeres Dokument' }).click()
  await expect(surface(page)).toContainText('Meine neue Idee')
  await action(page, 'undo').click()
  await expect(surface(page)).not.toContainText('Meine neue Idee')
  await action(page, 'redo').click()
  await expect(surface(page)).toContainText('Meine neue Idee')
  expect(errors).toEqual([])
})

test('link dialog validates the URL, preserves selection and restores focus', async ({ page }) => {
  await page.goto('./')
  await selectText(page, 'Gute Texte')
  await action(page, 'link').click()
  const dialog = page.getByRole('dialog', { name: 'Link bearbeiten' })
  await dialog.getByLabel('Adresse', { exact: true }).fill('javascript:alert(1)')
  await dialog.getByRole('button', { name: 'Übernehmen' }).click()
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('alert')).not.toBeEmpty()
  await dialog.getByLabel('Adresse', { exact: true }).fill('https://example.com/editor')
  await dialog.getByRole('button', { name: 'Übernehmen' }).click()
  await expect(dialog).toBeHidden()
  await expect(surface(page).getByRole('link', { name: 'Gute Texte', exact: true })).toHaveAttribute('href', 'https://example.com/editor')
  await expect(surface(page)).toBeFocused()
  await action(page, 'undo').click()
  await expect(surface(page).getByRole('link', { name: 'Gute Texte', exact: true })).toHaveCount(0)
})

test('image dialog inserts a sized image and undo removes the whole insertion', async ({ page }) => {
  await page.goto('./#blank')
  await surface(page).click()
  await action(page, 'image').click()
  const dialog = page.getByRole('dialog', { name: 'Bild einfügen' })
  await dialog.getByLabel('Bildbeschreibung').fill('Abendlicht am Berg')
  await dialog.getByLabel('Breite').fill('320')
  await dialog.getByRole('button', { name: 'Übernehmen' }).click()
  await expect(surface(page).getByRole('img', { name: 'Abendlicht am Berg' })).toHaveAttribute('width', '320')
  await action(page, 'undo').click()
  await expect(surface(page).locator('img')).toHaveCount(0)
  await action(page, 'redo').click()
  await expect(surface(page).locator('img')).toHaveAttribute('width', '320')
  await page.getByRole('button', { name: 'Quellansicht' }).click()
  await page.getByRole('button', { name: 'Markdown herunterladen' }).click()
  await expect(page.locator('.demo-notice')).not.toBeEmpty()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'JSON herunterladen' }).click()
  const file = await download
  expect(file.suggestedFilename()).toBe('ember-blank.json')
  expect(await readFile(await file.path(), 'utf8')).toContain('Abendlicht am Berg')
})

test('formatting, read mode, source and Markdown export agree', async ({ page }) => {
  await page.goto('./')
  await selectText(page, 'Gute Texte')
  await action(page, 'bold').click()
  await expect(surface(page).locator('strong').filter({ hasText: 'Gute Texte' })).toBeVisible()
  await page.getByRole('button', { name: 'Quellansicht' }).click()
  await expect(page.locator('.inspector-code')).toContainText('**Gute Texte**')
  await page.getByRole('button', { name: 'Lesemodus', exact: true }).click()
  await expect(surface(page)).toHaveAttribute('contenteditable', 'false')
  await expect(action(page, 'bold')).toBeDisabled()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Markdown herunterladen' }).click()
  const file = await download
  expect(await readFile(await file.path(), 'utf8')).toContain('**Gute Texte**')
  await page.getByRole('button', { name: 'Bearbeiten', exact: true }).click()
  await expect(surface(page)).toHaveAttribute('contenteditable', 'true')
})

test('reset requires the concrete reset dialog and cancellation keeps the text', async ({ page }) => {
  await page.goto('./#blank')
  await surface(page).click()
  await page.keyboard.type('Behalten')
  await page.getByRole('button', { name: 'Beispiel zurücksetzen', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Beispiel zurücksetzen?' })
  await dialog.getByRole('button', { name: 'Abbrechen' }).click()
  await expect(surface(page)).toContainText('Behalten')
  await page.getByRole('button', { name: 'Beispiel zurücksetzen', exact: true }).click()
  await dialog.getByRole('button', { name: 'Übernehmen' }).click()
  await expect(surface(page)).not.toContainText('Behalten')
})

test('narrow screens retain navigation, toolbar access and theme control', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('./')
  await expect(surface(page)).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const theme = await page.locator('html').getAttribute('data-theme')
  await page.getByRole('button', { name: 'Hell / Dunkel' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', theme === 'dark' ? 'light' : 'dark')
  await page.getByRole('link', { name: 'Leeres Dokument' }).click()
  await surface(page).click()
  await action(page, 'image').click()
  await expect(page.getByRole('dialog', { name: 'Bild einfügen' })).toBeVisible()
  const bounds = await page.locator('.ui-window').boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390)
})

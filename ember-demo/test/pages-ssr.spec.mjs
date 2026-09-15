import { test, expect } from '@playwright/test'

test('Pages artifact contains the complete initial view before JavaScript runs', async ({ request }) => {
  const response = await request.get('./')
  expect(response.ok()).toBe(true)
  const html = await response.text()
  expect(html).toContain('data-rendering="ssr"')
  expect(html).toContain('class="demo-app"')
  expect(html).toContain('Raum für gute Gedanken.')
  expect(html).toContain('data-ember-node=')
})

test.describe('without JavaScript', () => {
  test.use({ javaScriptEnabled: false })

  test('keeps the server-rendered article readable', async ({ page }) => {
    await page.goto('./')
    await expect(page.locator('#root')).toHaveAttribute('data-rendering', 'ssr')
    await expect(page.getByRole('heading', { name: 'Raum für gute Gedanken.' })).toBeVisible()
    await expect(page.locator('.editor-surface')).toContainText('Gute Texte brauchen einen klaren Gedanken')
  })
})

test('hydrates the existing root and activates the editor afterwards', async ({ page }) => {
  await page.goto('./')
  await expect(page.locator('#root')).toHaveAttribute('data-rendering', 'hydrated')
  await expect(page.getByRole('textbox', { name: 'Dokument bearbeiten' })).toHaveAttribute(
    'contenteditable',
    'true'
  )
  await expect(page.locator('[data-command="bold"]')).toBeEnabled()
})

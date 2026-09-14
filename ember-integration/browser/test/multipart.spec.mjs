import { test, expect } from '@playwright/test'
import { fixturePng } from '../media-server.mjs'

test.use({ javaScriptEnabled: false })
const png = { name: 'image.png', mimeType: 'image/png', buffer: fixturePng }

test('No-JS multipart sends source and file through server-side core insertion', async ({ page, request }) => {
  await page.goto('/media/form')
  await expect(page.locator('textarea[name=body]')).toBeVisible()
  await page.locator('textarea[name=body]').fill('## Title\n\nOriginal source')
  await page.locator('[name=alt]').fill('My image')
  await page.locator('[name=title]').fill('Preserved title')
  await page.locator('[name=file]').setInputFiles(png)
  await page.locator('#media-save').click()
  await expect(page).toHaveURL(/\/media\/result\//)
  const value = await page.locator('#media-value').textContent()
  expect(value).toContain('## Title')
  expect(value).toContain('Original source')
  expect(value).toMatch(/!\[My image\]\(\/media\/assets\/[a-f0-9]{64}\.png\)/)
  expect(value).not.toMatch(/blob:|base64|data:/)
  await expect(page.locator('#received-title')).toHaveText('Preserved title')
  const src = await page.locator('#stored-image').getAttribute('src')
  expect(await (await request.get(src)).body()).toEqual(fixturePng)
})

test('No-JS validation returns exact source, alt and other values', async ({ page }) => {
  await page.goto('/media/form')
  const source = '\n<script>literal</script>\n\n**draft** & <text>\n'
  await page.locator('textarea[name=body]').fill(source)
  await page.locator('[name=alt]').fill('literal <alt>')
  await page.locator('[name=title]').fill('another & value')
  await page.locator('[name=file]').setInputFiles({ ...png, buffer: Buffer.from('invalid') })
  await page.locator('#media-save').click()
  await expect(page.locator('#media-error')).toContainText('Ungültiger PNG')
  await expect(page.locator('textarea[name=body]')).toHaveValue(source)
  await expect(page.locator('[name=alt]')).toHaveValue('literal <alt>')
  await expect(page.locator('[name=title]')).toHaveValue('another & value')
  expect(await page.locator('script').count()).toBe(0)
  expect(await page.locator('textarea[name=body]').count()).toBe(1)
})

test('server rejects a client-supplied DOM offset as insertion position', async ({ request }) => {
  const response = await request.post('/media/submit', { multipart: {
    body: 'keep this source', alt: '', title: 'keep title', position: 't:999999', file: png,
  } })
  expect(response.status()).toBe(422)
  expect(await response.text()).toContain('keep this source')
  expect(await response.text()).toContain('Ungültige Einfügeposition')
})

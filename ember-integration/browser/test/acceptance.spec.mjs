import { test, expect } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'

test('manual report exports operator observations and the actual linked build without granting acceptance', async ({ page }) => {
  await page.goto('/toolbar?trace=1')
  await page.waitForFunction(() => window.ready)
  await page.getByLabel('Testperson (Kürzel)').fill('automated-export-test')
  await page.getByLabel('Gerät, Windows- und Browser-Version').fill('Harness: no physical device claim')
  await page.getByLabel('Eingabemethode und Sprache').fill('Automated keyboard; no real IME')
  await page.getByLabel('Prüfschritt').selectOption('ime-confirm')
  await page.getByRole('button', { name: 'Trace starten' }).click()
  await page.locator('#toolbar-editor').focus()
  await page.keyboard.press('End')
  await page.keyboard.type('abc')
  await page.getByRole('button', { name: 'Trace stoppen' }).click()
  await page.getByLabel('Ergebnis', { exact: true }).selectOption('blocked')
  await page.getByLabel('Beobachtung / Abweichung').fill('Only export behavior is under test.')
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Trace herunterladen' }).click()
  const download = await downloaded
  const trace = JSON.parse(await readFile(await download.path(), 'utf8'))
  expect(download.suggestedFilename()).toContain('ember-device-ime-confirm-')
  expect(trace.operator).toEqual({ tester: 'automated-export-test', environment: 'Harness: no physical device claim',
    inputMethod: 'Automated keyboard; no real IME', caseId: 'ime-confirm', outcome: 'blocked', notes: 'Only export behavior is under test.' })
  expect(trace.reviewStatus).toBe('requires-human-review')
  expect(trace.build.revision).toMatch(/^[a-f0-9]{40}$/)
  // Compare bytes, without reading or searching generated JavaScript as source.
  const linkedHash = createHash('sha256').update(await readFile(new URL('../../../target/ember-browser-tests/main.js', import.meta.url))).digest('hex')
  expect(trace.build.integrationSha256).toBe(linkedHash)
  expect(trace.build.uiCore).toContain('com.anjunar:scalajs-ui-core_')
  expect(Date.parse(trace.stoppedAt)).toBeGreaterThanOrEqual(Date.parse(trace.startedAt))
  expect(trace.events.some(event => event.modelText?.includes('abc'))).toBe(true)
})

test('starting another manual run clears the prior verdict and observations', async ({ page }) => {
  await page.goto('/toolbar?trace=1')
  await page.waitForFunction(() => window.ready)
  await page.getByRole('button', { name: 'Trace starten' }).click()
  await page.getByRole('button', { name: 'Trace stoppen' }).click()
  await page.getByLabel('Ergebnis', { exact: true }).selectOption('passed')
  await page.getByLabel('Beobachtung / Abweichung').fill('Previous run only')
  await page.getByRole('button', { name: 'Trace starten' }).click()
  await expect(page.getByLabel('Ergebnis', { exact: true })).toHaveValue('open')
  await expect(page.getByLabel('Beobachtung / Abweichung')).toHaveValue('')
})

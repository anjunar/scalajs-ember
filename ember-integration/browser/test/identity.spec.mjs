import { test, expect } from '@playwright/test'

// Ein frischer Editor je Test. Die Fixture-App fuehrt Zustand in Modulvariablen -- zwischen
// zwei Tests darf davon nichts uebrig bleiben.
async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.fixtures.mount(document.getElementById('root')))
  await page.locator('#surface').focus()
}

const model = page => page.evaluate(() => window.fixtures.read())
const rendered = page => page.evaluate(() => window.fixtures.rendered())
const errorOf = page => page.evaluate(() => window.fixtures.error())

test.describe('Ember im echten Browser', () => {
  test('montiert eine leere, editierbare Flaeche', async ({ page }) => {
    await open(page)

    expect(await model(page)).toBe('')
    expect(await page.locator('#surface [data-block]').count()).toBe(1)
  })

  test('fuehrt echte Tastendruecke bis ins Modell durch', async ({ page }) => {
    // Der eigentliche Nachweis der Phase: DOM-Ereignis, Command, Transaktion, Commit,
    // Projektion -- in einem tatsaechlich gelinkten Bundle, nicht gegen einen Stub.
    await open(page)
    await page.keyboard.type('Hallo')

    expect(await model(page)).toBe('Hallo')
    expect(await rendered(page)).toBe('Hallo')
  })

  test('haelt Modell und Darstellung deckungsgleich', async ({ page }) => {
    await open(page)
    await page.keyboard.type('Hallo')
    await page.keyboard.press('Enter')
    await page.keyboard.type('Welt')

    expect(await model(page)).toBe('Hallo\nWelt')
    expect(await rendered(page)).toBe('Hallo\nWelt')
    expect(await page.locator('#surface [data-block]').count()).toBe(2)
  })

  test('fuehrt zwei Absaetze mit Backspace wieder zusammen', async ({ page }) => {
    await open(page)
    await page.keyboard.type('Hallo')
    await page.keyboard.press('Enter')
    await page.keyboard.type('Welt')
    for (let index = 0; index < 4; index += 1) await page.keyboard.press('Backspace')
    await page.keyboard.press('Backspace')

    expect(await model(page)).toBe('Hallo')
    expect(await page.locator('#surface [data-block]').count()).toBe(1)
  })

  test('entfernt ein Emoji als ein Zeichen', async ({ page }) => {
    // Dieselbe Zusicherung wie in `UnicodeBoundarySpec`, aber gegen eine echte Browser-Engine
    // und ueber die volle Kette. Ein Backspace, der hier eine UTF-16-Einheit entfernt, wuerde
    // einen halben Codepunkt hinterlassen.
    await open(page)
    await page.evaluate(() => window.fixtures.perform('insert', 'a😀'))
    await page.keyboard.press('Backspace')

    expect(await model(page)).toBe('a')
  })

  test('meldet keinen Fehler in den Error-Sink', async ({ page }) => {
    await open(page)
    await page.keyboard.type('Hallo Welt')
    await page.keyboard.press('Enter')
    await page.keyboard.press('Backspace')

    expect(await errorOf(page)).toBe('')
  })

  test('raeumt beim Dispose vollstaendig ab', async ({ page }) => {
    await open(page)
    await page.keyboard.type('Hallo')
    await page.evaluate(() => window.fixtures.dispose())

    expect(await page.evaluate(() => window.fixtures.isDisposed())).toBe(true)
    expect(await page.locator('#surface').count()).toBe(0)
    expect(await page.evaluate(() => window.fixtures.mountedBlocks())).toBe(0)
  })

  test('reagiert nach dem Dispose auf keine Taste mehr', async ({ page }) => {
    // Ein Listener, der den Dispose ueberlebt, faellt sonst erst dadurch auf, dass er auf einer
    // entsorgten Sitzung arbeitet.
    await open(page)
    await page.evaluate(() => window.fixtures.dispose())
    await page.locator('body').press('x')

    expect(await page.locator('[data-block]').count()).toBe(0)
  })
})

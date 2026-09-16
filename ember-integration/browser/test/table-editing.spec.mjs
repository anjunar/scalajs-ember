import { test, expect } from '@playwright/test'

// Tabellen im echten Browser (X01).
//
// Das Dokument der Fixture:
//   p0  "Vorher"
//   t   Tabelle mit Kopfzeile: A | B / 1 | 2
//   p1  "Nachher"

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.table.mount(document.getElementById('root')))
  await page.locator('#root').focus()
}

async function caret(page, run, offset) {
  await page.evaluate(([id, at]) => window.table.setCaret(id, at), [run, offset])
}

const table = (page) => page.evaluate(() => window.table.table())
const selection = (page) => page.evaluate(() => window.table.selection())

test.describe('Tabellen im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.table?.dispose()).catch(() => {})
  })

  test('rendert table, tbody, tr, th und td', async ({ page }) => {
    await open(page)

    expect(await page.locator('#root table > tbody > tr').count()).toBe(2)
    expect(await page.locator('#root th').count()).toBe(2)
    expect(await page.locator('#root td').count()).toBe(2)
  })

  test('tippt in eine Zelle', async ({ page }) => {
    await open(page)
    await caret(page, 'c10-t', 1)

    await page.keyboard.type('x')

    expect(await table(page)).toBe('*A|*B / 1x|2')
  })

  test('Enter macht einen zweiten Absatz in derselben Zelle', async ({ page }) => {
    await open(page)
    await caret(page, 'c10-t', 1)

    await page.keyboard.press('Enter')
    await page.keyboard.type('y')

    expect(await table(page)).toBe('*A|*B / 1¶y|2')
    expect(await page.locator('#root td').first().locator('p').count()).toBe(2)
  })

  test('Backspace am Zellanfang zieht nichts aus der Nachbarzelle', async ({ page }) => {
    await open(page)
    await caret(page, 'c11-t', 0)

    await page.keyboard.press('Backspace')

    expect(await table(page)).toBe('*A|*B / 1|2')
    expect(await page.locator('#root td').count()).toBe(2)
  })

  test('Tab springt in die naechste Zelle und haengt am Ende eine Zeile an', async ({ page }) => {
    await open(page)
    await caret(page, 'c00-t', 0)

    await page.keyboard.press('Tab')
    expect(await selection(page)).toBe('c01@1')

    await caret(page, 'c11-t', 1)
    await page.keyboard.press('Tab')
    expect(await table(page)).toBe('*A|*B / 1|2 / |')
    expect(await page.locator('#root table > tbody > tr').count()).toBe(3)

    await page.keyboard.type('neu')
    expect(await table(page)).toBe('*A|*B / 1|2 / neu|')
  })

  test('Escape und Tab verlassen den Editor auch aus einer Tabelle', async ({ page }) => {
    // §22: keine Tastaturfalle.
    await open(page)
    await caret(page, 'c00-t', 0)

    await page.keyboard.press('Escape')
    await page.keyboard.press('Tab')

    expect(
      await page.evaluate(() => document.getElementById('root').contains(document.activeElement))
    ).toBe(false)
  })

  test('eine Auswahl ueber Zellen wird ein Rechteck und Entf leert es', async ({ page }) => {
    await open(page)

    await page.evaluate(() => {
      const from = document.querySelector('[data-ember-node="c00-t"]').firstChild
      const to = document.querySelector('[data-ember-node="c11-t"]').firstChild
      const range = document.createRange()
      range.setStart(from, 0)
      range.setEnd(to, 1)
      const native = document.getSelection()
      native.removeAllRanges()
      native.addRange(range)
    })
    await expect.poll(() => selection(page)).toBe('cells(c00..c11)')

    // Die Auswahl ist sichtbar, ohne dass im Editor-DOM etwas geschrieben wurde.
    const background = await page.evaluate(
      () => getComputedStyle(document.querySelector('[data-ember-node="c01"]')).backgroundColor
    )
    expect(background).not.toBe('rgba(0, 0, 0, 0)')

    await page.keyboard.press('Delete')

    expect(await table(page)).toBe('*|* / |')
    expect(await page.locator('#root td').count()).toBe(2)
  })

  test('Strg+Z nimmt eine eingefuegte Zeile zurueck', async ({ page }) => {
    await open(page)
    await caret(page, 'c10-t', 0)
    await page.evaluate(() => window.table.insertRow(true))
    expect(await table(page)).toBe('*A|*B / 1|2 / |')

    await page.keyboard.press('Control+z')

    expect(await table(page)).toBe('*A|*B / 1|2')
    expect(await page.locator('#root table > tbody > tr').count()).toBe(2)
  })
})

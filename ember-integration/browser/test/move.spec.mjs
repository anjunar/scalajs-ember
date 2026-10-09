import { test, expect } from '@playwright/test'

// P08, Move-Seite. Wieder gilt: die Zyklus- und Kontextpruefungen sind im Nachbar-Repo
// abgedeckt. Hier geht es um DOM-Identitaet, um die Synchronitaet der logischen Kindliste mit
// dem DOM, und darum, dass eine abgewiesene Operation nichts hinterlaesst.

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.runtime.mount(document.getElementById('root')))
}

const idsIn = (page, section) =>
  page.evaluate(
    id => [...document.querySelectorAll(`#${id} > *`)].map(node => node.id),
    section
  )

test.describe('Runtime.move im echten Browser', () => {
  test('bewegt dasselbe Element, statt es neu zu bauen', async ({ page }) => {
    // §15.1: "Move erhaelt Node-Identitaet." Ein neu aufgebautes Element verlaere Fokus,
    // Selection und jeden daran haengenden Listener -- und ein Editor merkt das erst, wenn
    // der Cursor springt.
    await open(page)

    const same = await page.evaluate(() => {
      const before = document.querySelector('#movable')
      window.runtime.move('right', 0)
      return before === document.querySelector('#movable')
    })

    expect(same).toBe(true)
    expect(await idsIn(page, 'left')).toEqual([])
    expect(await idsIn(page, 'right')).toEqual(['movable'])
  })

  test('nimmt den Textknoten unveraendert mit', async ({ page }) => {
    await open(page)

    const same = await page.evaluate(() => {
      const before = document.querySelector('#movable').firstChild
      window.runtime.move('right', 0)
      return before === document.querySelector('#movable').firstChild
    })

    expect(same).toBe(true)
    expect(await page.locator('#right #movable').textContent()).toBe('A😀BC')
  })

  test('haelt einen daran haengenden Listener am Leben', async ({ page }) => {
    // Ein Listener ist der praktische Beleg dafuer, dass es wirklich dasselbe Element ist --
    // eine Neuerzeugung wuerde ihn stillschweigend verlieren.
    await open(page)

    const fired = await page.evaluate(() => {
      let count = 0
      document.querySelector('#movable').addEventListener('click', () => { count += 1 })
      window.runtime.move('right', 0)
      document.querySelector('#movable').click()
      return count
    })

    expect(fired).toBe(1)
  })

  test('hinterlaesst kein Duplikat im alten Parent', async ({ page }) => {
    await open(page)
    await page.evaluate(() => window.runtime.move('right', 0))

    expect(await page.locator('#movable').count()).toBe(1)
  })

  test('haelt die logische Kindliste mit dem DOM synchron', async ({ page }) => {
    // Der eigentliche Nachweis: `contentCursor` setzt anhand der Kindliste der Runtime an.
    // Waere sie nach dem Move nicht synchron, landete das neue Label an der falschen Stelle --
    // ohne Fehlermeldung, nur mit falscher Reihenfolge.
    await open(page)
    await page.evaluate(() => {
      window.runtime.move('right', 0)
      window.runtime.addLabel('right', 'danach', 'B')
    })

    expect(await idsIn(page, 'right')).toEqual(['movable', 'danach'])
  })

  test('setzt an der angegebenen Position ein', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.runtime.addLabel('right', 'erstes', 'A')
      window.runtime.addLabel('right', 'zweites', 'B')
      window.runtime.move('right', 1)
    })

    expect(await idsIn(page, 'right')).toEqual(['erstes', 'movable', 'zweites'])
  })

  test('sortiert innerhalb desselben Parents um', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.runtime.addLabel('left', 'zweites', 'B')
      window.runtime.move('left', 1)
    })

    expect(await idsIn(page, 'left')).toEqual(['zweites', 'movable'])
  })

  test('laesst spaetere Aenderungen im neuen Parent ankommen', async ({ page }) => {
    // §15.1: "reaktive Folgeaenderung im neuen Parent". Ein Move, nach dem der Knoten zwar
    // richtig steht, aber keine Updates mehr bekommt, waere schlimmer als gar keiner.
    await open(page)
    await page.evaluate(() => {
      window.runtime.move('right', 0)
      window.runtime.splice(0, 1, 'Z')
    })

    expect(await page.locator('#right #movable').textContent()).toBe('Z😀BC')
    expect(await page.evaluate(() => window.runtime.text())).toBe('Z😀BC')
  })

  test('entsorgt genau einmal', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.runtime.move('right', 0)
      window.runtime.dispose()
    })

    expect(await page.evaluate(() => window.runtime.disposals())).toBe(1)
    expect(await page.locator('#runtime-root').count()).toBe(0)
  })

  test.describe('abgewiesene Operationen', () => {
    // §15.1: Zyklen, Text- und virtuelle Wurzeln werden abgewiesen -- und zwar vor jeder
    // Mutation. Dass danach nichts kaputt ist, prueft jeweils der DOM-Vergleich.
    for (const [action, description] of [
      ['cycle', 'ein Abschnitt in eines seiner eigenen Kinder'],
      ['text', 'ein Textknoten statt einer Elementkomponente'],
      ['root', 'eine montierte Wurzel'],
      ['index', 'eine Position ausserhalb der Kindliste'],
    ]) {
      test(`weist ${description} ab`, async ({ page }) => {
        await open(page)
        const before = await page.evaluate(
          () => document.getElementById('runtime-root').innerHTML
        )

        const error = await page.evaluate(name => window.runtime.attempt(name), action)

        expect(error).not.toBe('')
        expect(
          await page.evaluate(() => document.getElementById('runtime-root').innerHTML)
        ).toBe(before)
      })
    }
  })
})

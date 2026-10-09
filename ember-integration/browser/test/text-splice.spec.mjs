import { test, expect } from '@playwright/test'

// P08, Textseite. Die JVM-Suite des Nachbar-Repos deckt UTF-16-Offsets und abgewiesene
// Bereiche bereits ab. Hier geht es um das, was dort nicht pruefbar ist: die Identitaet des
// DOM-Textknotens und der Nachweis, dass ein unveraenderter Wert wirklich *keinen*
// Schreibzugriff ausloest.

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.runtime.mount(document.getElementById('root')))
}

/** Zaehlt DOM-Mutationen waehrend `action`. */
async function mutationsDuring(page, action) {
  await page.evaluate(() => {
    window.__records = []
    window.__observer = new MutationObserver(records => window.__records.push(...records))
    window.__observer.observe(document.getElementById('runtime-root'), {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
    })
  })
  await action()
  return page.evaluate(() => {
    window.__observer.takeRecords().forEach(record => window.__records.push(record))
    window.__observer.disconnect()
    return window.__records.length
  })
}

test.describe('spliceText im echten Browser', () => {
  test('rechnet in UTF-16-Einheiten', async ({ page }) => {
    // "A😀BC": das Emoji belegt zwei Einheiten. Wer es entfernen will, loescht ab 1 zwei
    // Einheiten -- der Host kennt keine Graphemgrenzen, das ist Sache des Modells (§11).
    await open(page)
    await page.evaluate(() => window.runtime.splice(1, 2, ''))

    expect(await page.evaluate(() => window.runtime.text())).toBe('ABC')
    expect(await page.locator('#movable').textContent()).toBe('ABC')
  })

  test('behaelt denselben DOM-Textknoten', async ({ page }) => {
    // Der Vertrag, an dem fuer den Editor alles haengt (§15.1). Ein neu erzeugter Textknoten
    // naehme Caret, Selection und eine laufende IME-Eingabe mit ins Grab.
    await open(page)

    const same = await page.evaluate(() => {
      const before = document.querySelector('#movable').firstChild
      window.runtime.splice(0, 1, 'X')
      return before === document.querySelector('#movable').firstChild
    })

    expect(same).toBe(true)
    expect(await page.locator('#movable').textContent()).toBe('X😀BC')
  })

  test('behaelt ihn auch ueber mehrere Splices', async ({ page }) => {
    await open(page)

    const same = await page.evaluate(() => {
      const before = document.querySelector('#movable').firstChild
      window.runtime.splice(0, 0, 'vorne')
      window.runtime.splice(3, 2, '')
      window.runtime.splice(1, 1, 'ZZ')
      return before === document.querySelector('#movable').firstChild
    })

    expect(same).toBe(true)
  })

  test('schreibt bei gleichem Ergebnis gar nicht', async ({ page }) => {
    // Der No-op-Vertrag, und der MutationObserver ist der einzige ehrliche Zeuge dafuer: er
    // sieht jeden Schreibzugriff, auch einen, der denselben Wert setzt.
    await open(page)

    const records = await mutationsDuring(page, () =>
      page.evaluate(() => window.runtime.setText('A😀BC'))
    )

    expect(records).toBe(0)
  })

  test('schreibt auch bei einem wirkungslosen Splice nicht', async ({ page }) => {
    await open(page)

    const records = await mutationsDuring(page, () =>
      page.evaluate(() => window.runtime.splice(1, 2, '😀'))
    )

    expect(records).toBe(0)
  })

  test('schreibt bei einer echten Aenderung genau einmal', async ({ page }) => {
    // Gegenprobe: ohne sie wuerde der Test oben auch dann gruen sein, wenn der Observer
    // ueberhaupt nichts sieht.
    await open(page)

    const records = await mutationsDuring(page, () =>
      page.evaluate(() => window.runtime.splice(0, 1, 'B'))
    )

    expect(records).toBe(1)
  })

  test('weist einen ungueltigen Bereich ab, ohne den Text anzufassen', async ({ page }) => {
    await open(page)

    const outcome = await page.evaluate(() => {
      try {
        window.runtime.splice(99, 1, 'x')
        return 'angenommen'
      } catch (error) {
        return error.constructor.name
      }
    })

    expect(outcome).not.toBe('angenommen')
    expect(await page.evaluate(() => window.runtime.text())).toBe('A😀BC')
    expect(await page.locator('#movable').textContent()).toBe('A😀BC')
  })

  test('haelt Komponente und DOM deckungsgleich', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.runtime.splice(0, 1, 'Start ')
      window.runtime.splice(0, 0, '>> ')
    })

    const model = await page.evaluate(() => window.runtime.text())
    expect(await page.locator('#movable').textContent()).toBe(model)
  })
})

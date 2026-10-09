import { test, expect } from '@playwright/test'

// P09, Browserseite. `ProjectionSpec` deckt die Projektion bereits headless ab -- Index,
// Reihenfolge, Renderprofile, Instanzerhalt auf Komponentenebene. Hier steht nur, was dort
// grundsaetzlich nicht pruefbar ist: DOM-Identitaet, der Umfang der Schreibzugriffe und der
// Vergleich zwischen SSR und Browser.

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.projection.mount(document.getElementById('root')))
}

/** Sammelt DOM-Mutationen unterhalb der Wurzel waehrend `action`. */
async function mutationsDuring(page, action) {
  await page.evaluate(() => {
    window.__records = []
    window.__observer = new MutationObserver(records => window.__records.push(...records))
    window.__observer.observe(document.getElementById('root'), {
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
    return window.__records.map(record => record.type)
  })
}

const run = id => `[data-ember-node="${id}"]`

test.describe('Die Dokumentansicht im echten Browser', () => {
  test('rendert semantisches HTML', async ({ page }) => {
    await open(page)

    expect(await page.locator('article > p').count()).toBe(2)
    expect(await page.locator(run('t0')).textContent()).toBe('Hallo')
    expect(await page.locator(run('t1')).textContent()).toBe('Welt')
  })

  test('rendert initial dasselbe wie der SSR-Weg', async ({ page }) => {
    // Die Abnahme aus P09: "Gleiches initiales Rendering in SSR und Browser." Dass beide
    // Seiten dieselbe `HtmlSemantics` benutzen, ist der Grund -- dieser Test ist die Probe
    // darauf, dass es auch beim Cursor bleibt und nicht nur bei der Beschreibung.
    await open(page)

    const [dom, ssr] = await page.evaluate(() => [
      document.getElementById('root').innerHTML,
      window.projection.ssrHtml(),
    ])

    expect(dom).toBe(ssr)
  })

  test('haelt Modell und Darstellung deckungsgleich', async ({ page }) => {
    await open(page)
    await page.evaluate(() => window.projection.splice('t0', 5, 0, ' du'))

    const model = await page.evaluate(() => window.projection.read())
    expect(model).toBe('Hallo du\nWelt')
    expect(await page.locator(run('t0')).textContent()).toBe('Hallo du')
  })
})

test.describe('Ein Textedit', () => {
  test('behaelt denselben DOM-Textknoten', async ({ page }) => {
    // Der Vertrag, an dem fuer den Editor alles haengt (§15.1). Ein neu erzeugter Textknoten
    // naehme Caret, Selection und eine laufende IME-Eingabe mit ins Grab. Headless ist das
    // nicht zu belegen: es ist ein `===` auf DOM-Objekten.
    await open(page)

    const same = await page.evaluate(() => {
      const before = document.querySelector('[data-ember-node="t0"]').firstChild
      window.projection.splice('t0', 5, 0, '!')
      return before === document.querySelector('[data-ember-node="t0"]').firstChild
    })

    expect(same).toBe(true)
    expect(await page.locator(run('t0')).textContent()).toBe('Hallo!')
  })

  test('schreibt genau einmal, und zwar in den Text', async ({ page }) => {
    // §15.1: "unveraenderte Nodes werden nicht erneut komponiert". Der Absatz, sein Attribut
    // und der Nachbarabsatz duerfen gar nicht erst angefasst werden -- ein einziger
    // `characterData`-Eintrag ist die ganze Aenderung.
    await open(page)

    const types = await mutationsDuring(page, () =>
      page.evaluate(() => window.projection.splice('t0', 0, 0, 'X'))
    )

    expect(types).toEqual(['characterData'])
  })

  test('laesst den Nachbarabsatz unberuehrt', async ({ page }) => {
    await open(page)

    const same = await page.evaluate(() => {
      const before = document.querySelector('[data-ember-node="t1"]').firstChild
      window.projection.splice('t0', 0, 0, 'X')
      return before === document.querySelector('[data-ember-node="t1"]').firstChild
    })

    expect(same).toBe(true)
  })

  test('schreibt bei gleichem Wert gar nicht', async ({ page }) => {
    // Der No-op-Vertrag von `TextComponent`, hier durch die ganze Kette hindurch: Transaktion,
    // Commit, Projektion, Komponente. Der MutationObserver ist der einzige ehrliche Zeuge --
    // ein gleicher Ausgabestring waere auch nach einem Neuschreiben gleich.
    await open(page)

    const types = await mutationsDuring(page, () =>
      page.evaluate(() => window.projection.setText('t0', 'Hallo'))
    )

    expect(types).toEqual([])
  })
})

test.describe('Struktur', () => {
  test('bewegt bei einem Move dasselbe Element', async ({ page }) => {
    // §15.1: "Move erhaelt Node-Identitaet." Ueber `transferTo`, damit beide Schluesselindizes
    // konsistent bleiben -- und im DOM ueber `Runtime.move`, nicht ueber Neubau.
    await open(page)
    // Seit P12 wachsen benachbarte Laeufe mit gleichen Marks wieder zusammen (§8.2). Ein
    // zusammengefuehrter Knoten hat keine Komponente mehr, an der sich Identitaet pruefen
    // liesse -- verschiedene Marks halten die beiden auseinander.
    await page.evaluate(() => window.projection.markRun('t1'))

    const same = await page.evaluate(() => {
      const before = document.querySelector('[data-ember-node="t1"]')
      window.projection.move('t1', 'p0', 1)
      return before === document.querySelector('[data-ember-node="t1"]')
    })

    expect(same).toBe(true)
    expect(await page.locator(`${run('p0')} > ${run('t1')}`).count()).toBe(1)
    expect(await page.locator(run('p0')).textContent()).toBe('HalloWelt')
  })

  test('nimmt einen entfernten Knoten aus dem DOM', async ({ page }) => {
    await open(page)
    await page.evaluate(() => window.projection.remove('p1'))

    expect(await page.locator(run('p1')).count()).toBe(0)
    expect(await page.locator(run('t1')).count()).toBe(0)
    expect(await page.locator('article > p').count()).toBe(1)
  })

  test('ordnet Geschwister in Dokumentreihenfolge', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.projection.insertMarked('p0', 1, 'extra', 'B')
    })
    expect(await page.locator(run('p0')).textContent()).toBe('HalloB')

    await page.evaluate(() => window.projection.move('extra', 'p0', 0))
    expect(await page.locator(run('p0')).textContent()).toBe('BHallo')
  })

  test('gibt jedem Textlauf einen eigenen Wrapper', async ({ page }) => {
    // §15.1: "Ein Textleaf bekommt einen stabilen Wrapper mit einem Textkind." Ohne ihn waeren
    // zwei benachbarte Laeufe im DOM ein einziger Textknoten -- die Grenze zwischen ihnen
    // liesse sich weder beim Hydrieren noch fuer die Selection wiederfinden.
    await open(page)
    await page.evaluate(() => window.projection.insertMarked('p0', 1, 'extra', 'Welt'))

    const kinds = await page.evaluate(() =>
      [...document.querySelector('[data-ember-node="p0"]').childNodes]
        .filter(node => node.nodeType !== Node.COMMENT_NODE)
        .map(node => node.nodeName)
    )

    expect(kinds).toEqual(['SPAN', 'SPAN'])
  })
})

test.describe('Commit und Projektion', () => {
  test('meldet die dargestellte Revision erst nach dem Rendern', async ({ page }) => {
    // §5 trennt beide Zeitpunkte. Wer wissen will, ob etwas zu sehen ist, fragt `onProjected`
    // -- und wenn die Meldung kommt, steht es im DOM.
    await open(page)
    const initial = await page.evaluate(() => window.projection.projected())

    const seen = await page.evaluate(() => {
      window.projection.splice('t0', 5, 0, '!')
      return {
        projected: window.projection.projected(),
        notified: window.projection.notified(),
        revision: window.projection.revision(),
        text: document.querySelector('[data-ember-node="t0"]').textContent,
      }
    })

    // Der Anfangsstand wird gelesen, nicht gemeldet: beim Mount kann es noch keinen Zuhoerer
    // geben, die Ansicht existiert erst danach.
    expect(initial).toBe(0)
    expect(seen.projected).toBe(seen.revision)
    expect(seen.notified).toBe(seen.revision)
    expect(seen.text).toBe('Hallo!')
  })

  test('haelt nach dispose nichts mehr fest', async ({ page }) => {
    await open(page)
    await page.evaluate(() => window.projection.dispose())

    expect(await page.locator('#root').innerHTML()).toBe('')
  })
})

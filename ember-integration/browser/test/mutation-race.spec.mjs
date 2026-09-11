import { test, expect } from '@playwright/test'

// Fremde Mutationen und begrenzte Reparatur (P23, §15.2, §15.4).
//
// Der Fall ist nicht der Browser beim Tippen -- das ist `native-input.spec.mjs` --, sondern alles
// andere, was in eine Seite schreibt: eine Erweiterung, ein Uebersetzungswerkzeug, ein
// Passwortmanager, eine Browserfunktion, die niemand dokumentiert hat. §15.4: "Die Runtime darf
// von nativen Mutationen getrennte oder ersetzte Hosts nicht weiter als gueltig behandeln."
//
// Geprueft wird der Abgleich gegen das *Dokument*, nicht gegen eine Vorhersage der eigenen
// Schreibzugriffe. §15.2 schliesst die naheliegende Loesung ausdruecklich aus: "ein synchrones
// Boolean `suppress` unterscheidet eigene und native Mutationen nicht zuverlaessig."

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.editing.mount(document.getElementById('root')))
  await page.locator('#root').focus()
}

const problems = (page) => page.evaluate(() => window.editing.viewProblems())
const repair = (page) => page.evaluate(() => window.editing.repairView())
const model = (page) => page.evaluate(() => window.editing.text())

test.describe('Fremde Mutationen im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.editing?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Der Abgleich
  // -------------------------------------------------------------------------------------

  test('sieht eine unberuehrte Ansicht als unberuehrt an', async ({ page }) => {
    // Die Gegenprobe, ohne die jede weitere Zusicherung wertlos waere: der Abgleich darf nicht
    // schon im Normalfall anschlagen.
    await open(page)

    expect(await problems(page)).toBe('')
    expect(await repair(page)).toContain('stimmt')
  })

  test('bemerkt einen fremd geaenderten Text', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      document.querySelector('[data-ember-node="t0"]').firstChild.data = 'Fremd'
    })

    expect(await problems(page)).toContain('t0')
    expect(await problems(page)).toContain('Text')
  })

  test('bemerkt ein fremd eingefuegtes Element', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      const marker = document.createElement('mark')
      marker.textContent = 'Werbung'
      document.querySelector('[data-ember-node="p0"]').appendChild(marker)
    })

    expect(await problems(page)).toContain('p0')
  })

  test('bemerkt ein fremd veraendertes Attribut', async ({ page }) => {
    // §17.5s Abgleich prueft die Attribute, die die Semantik nennt -- eine Knoten-ID, die jemand
    // umgeschrieben hat, ist der Fall, den eine reine Strukturpruefung durchlaesst.
    await open(page)
    await page.evaluate(() => {
      document.querySelector('[data-ember-node="t0"]').setAttribute('data-ember-node', 'fremd')
    })

    expect(await problems(page)).not.toBe('')
  })

  test('stoert sich nicht an einem zusaetzlichen Attribut', async ({ page }) => {
    // Nur die Attribute, die die Semantik nennt. Eine Seite darf eigene mitbringen -- eine
    // Layoutklasse, ein Analytics-Attribut -- und ein Editor, der daran scheiterte, waere in
    // jeder realen Anwendung unbrauchbar.
    await open(page)
    await page.evaluate(() => {
      document.querySelector('[data-ember-node="p0"]').setAttribute('data-analytics', 'x')
    })

    expect(await problems(page)).toBe('')
  })

  test('stoert sich nicht an einem Atom mit eigenem Adapter', async ({ page }) => {
    // Ein Knoten mit eigener `NodeView` hat per Entwurf keine `HtmlSemantics` (§15.1). Das als
    // Schaden zu lesen setzte jeden Editor mit einem Atom in Dauerreparatur -- ein Browsertest
    // hat genau das getan.
    await open(page)

    expect(await page.evaluate(() => window.editing.text())).toContain('Zweite Zeile')
    expect(await problems(page)).toBe('')
  })

  // -------------------------------------------------------------------------------------
  // Die Reparatur (§15.4)
  // -------------------------------------------------------------------------------------

  test('baut einen fremd geaenderten Lauf aus dem Dokument neu auf', async ({ page }) => {
    // §15.4: "laesst UI diesen Bereich aus dem gueltigen State neu aufbauen." Das Dokument ist
    // hier die Wahrheit -- was die Erweiterung tat, war keine Dokumentaenderung.
    await open(page)
    await page.evaluate(() => {
      document.querySelector('[data-ember-node="t0"]').firstChild.data = 'Fremd'
    })

    expect(await repair(page)).toContain('neu aufgebaut')
    expect(await problems(page)).toBe('')
    expect(await page.evaluate(() => window.editing.domText())).toContain('Hallo Welt')
    // Und das Dokument blieb unberuehrt.
    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })

  test('baut einen fremd bestueckten Block neu auf', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      const marker = document.createElement('mark')
      marker.textContent = 'Werbung'
      document.querySelector('[data-ember-node="p0"]').appendChild(marker)
    })

    expect(await repair(page)).toContain('neu aufgebaut')
    expect(await page.evaluate(() => window.editing.domText())).not.toContain('Werbung')
  })

  test('schreibt dabei kein innerHTML', async ({ page }) => {
    // §15.4: "Keine Plugins schreiben zur Reparatur `innerHTML`." Der Beweis, den ein Test
    // fuehren kann: der Setter wird ueberwacht und darf waehrend der Reparatur nicht laufen.
    await open(page)
    await page.evaluate(() => {
      window.__innerHtmlWrites = 0
      const descriptor = Object.getOwnPropertyDescriptor(Element.prototype, 'innerHTML')
      Object.defineProperty(Element.prototype, 'innerHTML', {
        ...descriptor,
        set(value) {
          window.__innerHtmlWrites += 1
          descriptor.set.call(this, value)
        },
      })
      document.querySelector('[data-ember-node="t0"]').firstChild.data = 'Fremd'
    })

    await repair(page)

    expect(await page.evaluate(() => window.__innerHtmlWrites)).toBe(0)
  })

  test('gibt auf, was es nicht reparieren kann', async ({ page }) => {
    // Die Wurzel laesst sich nicht neu aufbauen -- das waere eine Ersetzung der ganzen Ansicht
    // und gehoert dem, der sie besitzt (§15.1). Ein Schaden dort ist deshalb der Fall, in dem die
    // Reparatur nicht greifen kann, und §15.4 verlangt dann kein zweites Anlaufen: "keine
    // Endlosschleife aus Observer→Render→Observer."
    await open(page)
    await page.evaluate(() => {
      document.querySelector('article').setAttribute('data-ember-node', 'fremd')
    })

    const first = await repair(page)
    const second = await repair(page)

    expect(first).toContain('laesst sich nicht reparieren')
    expect(second).toContain('laesst sich nicht reparieren')
  })

  test('versucht es nach dem Limit gar nicht mehr', async ({ page }) => {
    // Der Unterschied zwischen "hat es versucht und es half nicht" und "versucht es nicht mehr":
    // nach dem begrenzten Versuch wird nichts mehr angefasst, auch wenn es reparierbar waere.
    await open(page)
    await page.evaluate(() => {
      document.querySelector('article').setAttribute('data-ember-node', 'fremd')
    })
    await repair(page)

    // Jetzt ist der Schaden behoben -- aber das Limit ist verbraucht, und ein neuer Schaden
    // bleibt liegen, bis jemand den Zustand fuer gesund erklaert.
    await page.evaluate(() => {
      document.querySelector('article').setAttribute('data-ember-node', 'root')
      document.querySelector('[data-ember-node="t0"]').firstChild.data = 'Fremd'
    })

    expect(await repair(page)).toContain('laesst sich nicht reparieren')
    expect(await page.evaluate(() => window.editing.domText())).toContain('Fremd')
  })

  test('haelt den Text dabei bereit', async ({ page }) => {
    // §15.4: "Rohtext bzw. der letzte Source-Draft bleibt fuer Recovery verfuegbar." Das Dokument
    // ist unberuehrt, und aus ihm kommt der Text -- eine gescheiterte Ansicht ist kein
    // Datenverlust.
    await open(page)
    await page.evaluate(() => {
      document.querySelector('[data-ember-node="p0"]').remove()
    })

    await repair(page)

    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })

  // -------------------------------------------------------------------------------------
  // Zusammen mit der Eingabe
  // -------------------------------------------------------------------------------------

  test('laesst nach einer Reparatur weiter tippen', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      document.querySelector('[data-ember-node="t0"]').firstChild.data = 'Fremd'
      window.editing.repairView()
      window.editing.setCaret('t0', 5)
      window.editing.clearOutcomes()
    })

    await page.keyboard.type('!')

    expect(await model(page)).toBe('Hallo! Welt\nZweite Zeile')
  })
})

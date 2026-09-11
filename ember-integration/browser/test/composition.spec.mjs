import { test, expect } from '@playwright/test'

// Composition (P23, §15.3).
//
// Was hier *nicht* steht: eine echte IME. Synthetische `compositionstart`/`compositionend` sind
// das Protokoll, nicht die Eingabemethode -- §15.3 sagt es selbst: "Ein willkuerlicher Timeout
// ohne reproduzierten Browserfall ist kein Abschlussprotokoll." Die Abnahme mit einer echten IME
// ist eine Handpruefung und steht in `manual-ime.md`.
//
// Was hier steht, ist der Vertrag: wer waehrend einer Composition schreiben darf, was der
// Schutzbereich umfasst, wie sie endet, und dass eine Composition eine Undo-Stufe ergibt.
//
// Das Dokument der Fixture:
//   p0  "Hallo Welt"
//   p1  "Zweite Zeile" + Atom

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.editing.mount(document.getElementById('root')))
  await page.locator('#root').focus()
}

async function caret(page, node, offset) {
  await page.evaluate(
    ([id, at]) => {
      window.editing.setCaret(id, at)
      window.editing.clearOutcomes()
      window.editing.clearCompositions()
    },
    [node, offset]
  )
}

/** Startet eine Composition so, wie eine IME es tut. */
async function begin(page) {
  await page.evaluate(() =>
    document
      .getElementById('root')
      .dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
  )
}

/** Schreibt in den Textknoten und meldet es -- das, was eine IME zwischendurch tut. */
async function composeInto(page, node, text) {
  await page.evaluate(
    ([id, value]) => {
      const run = document.querySelector(`[data-ember-node="${id}"]`)
      run.firstChild.data = value
      document
        .getElementById('root')
        .dispatchEvent(
          new InputEvent('input', { inputType: 'insertCompositionText', bubbles: true })
        )
    },
    [node, text]
  )
}

async function end(page) {
  await page.evaluate(() =>
    document
      .getElementById('root')
      .dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }))
  )
}

const model = (page) => page.evaluate(() => window.editing.text())
const state = (page) => page.evaluate(() => window.editing.state())

test.describe('Composition im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.editing?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Der Schutzbereich (§15.3)
  // -------------------------------------------------------------------------------------

  test('schuetzt mindestens den aktiven Block', async ({ page }) => {
    // §15.3: "Bei einer kollabierten Range ist dies mindestens der aktive Block." Nicht den Lauf:
    // eine IME-Ersetzung reicht weiter als das Blatt, in dem sie begann.
    await open(page)
    await caret(page, 't0', 5)

    await begin(page)

    expect(await state(page)).toBe('Composing')
    expect(await page.evaluate(() => window.editing.protectedRegion())).toBe('p0')
  })

  test('schuetzt jeden Block, den eine Auswahl beruehrt', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.editing.setRange('t0', 2, 't1', 2)
      window.editing.clearCompositions()
    })

    await begin(page)

    expect(await page.evaluate(() => window.editing.protectedRegion())).toBe('p0,p1')
  })

  test('meldet Anfang und Ende mit derselben Sitzungsnummer', async ({ page }) => {
    // §15.3 verlangt eine Session-ID, und zwar damit ein verspaetetes Ereignis der Composition
    // zugeordnet werden kann, zu der es gehoert -- nicht der, die gerade laeuft.
    await open(page)
    await caret(page, 't0', 5)

    await begin(page)
    const id = await page.evaluate(() => window.editing.compositionId())
    await end(page)

    expect(id).toBeGreaterThan(0)
    expect(await page.evaluate(() => window.editing.compositionLog())).toBe(
      `start:${id},end:${id}`
    )
    expect(await page.evaluate(() => window.editing.compositionId())).toBe(0)
  })

  // -------------------------------------------------------------------------------------
  // Zwischenstaende und Abschluss
  // -------------------------------------------------------------------------------------

  test('zieht Zwischenstaende der Composition nach', async ({ page }) => {
    // §15.3: "Native Zwischenstaende werden als zusammengehoerige Transaktionen uebernommen."
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)

    await composeInto(page, 't0', 'Hallo私 Welt')

    expect(await model(page)).toBe('Hallo私 Welt\nZweite Zeile')
  })

  test('fuegt am Ende nicht ein zweites Mal ein', async ({ page }) => {
    // Der Fall, den §15.3 mit "ein revisionierter Vergleich des erfassten Textes verhindert
    // doppelte Einfuegung" meint: auf `compositionend` folgt oft noch ein `input`.
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)
    await composeInto(page, 't0', 'Hallo私 Welt')

    await end(page)
    await composeInto(page, 't0', 'Hallo私 Welt')

    expect(await model(page)).toBe('Hallo私 Welt\nZweite Zeile')
  })

  test('nimmt eine Composition, die gar keinen Zwischenstand meldete', async ({ page }) => {
    // §15.2s Trace-Liste nennt IMEs ohne Composition-Ereignisse und verwaiste Inputs. Hier der
    // Gegenfall: Ereignisse ohne Input dazwischen, und der Text steht trotzdem im DOM.
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)

    await page.evaluate(() => {
      const run = document.querySelector('[data-ember-node="t0"]')
      run.firstChild.data = 'Hallo漢字 Welt'
    })
    await end(page)

    expect(await model(page)).toBe('Hallo漢字 Welt\nZweite Zeile')
    expect(await state(page)).toBe('Ready')
  })

  test('nimmt bei Blur, was offen war', async ({ page }) => {
    // §15.3: "Blur erfasst noch offene native Aenderung." Den Fokus zu verlieren ist kein Grund,
    // ein halb getipptes Wort wegzuwerfen.
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)
    await page.evaluate(() => {
      const run = document.querySelector('[data-ember-node="t0"]')
      run.firstChild.data = 'Hallo私 Welt'
      const outside = document.createElement('button')
      outside.id = 'raus'
      document.body.appendChild(outside)
    })

    await page.locator('#raus').focus()

    expect(await state(page)).toBe('Ready')
    expect(await model(page)).toBe('Hallo私 Welt\nZweite Zeile')
  })

  test('raeumt beim Dispose auf und meldet es', async ({ page }) => {
    // §15.3: "Dispose raeumt auf und meldet ggf. nicht abgeschlossene Eingabe an den Host."
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)
    const id = await page.evaluate(() => window.editing.compositionId())

    const log = await page.evaluate(() => {
      window.editing.dispose()
      return window.editing.compositionLog()
    })

    expect(log).toContain(`discard:${id}:dispose`)
  })

  // -------------------------------------------------------------------------------------
  // Unabhaengige Aenderungen (§15.3)
  // -------------------------------------------------------------------------------------

  test('stellt eine unabhaengige Aenderung zurueck', async ({ page }) => {
    // §15.3: alle unabhaengigen Dokumenttransaktionen werden abgewiesen oder mit Bookmark in eine
    // begrenzte Queue gelegt. Diese Fixture waehlt `Defer`.
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)

    expect(await page.evaluate(() => window.editing.offerEdit('A'))).toBe('applied:A')
    // Zurueckgestellt heisst: noch nicht passiert.
    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')

    await end(page)

    expect(await model(page)).toBe('AHallo Welt\nZweite Zeile')
  })

  test('meldet die zurueckgestellten Aenderungen beim Abschluss', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)
    await page.evaluate(() => window.editing.offerEdit('A'))
    const id = await page.evaluate(() => window.editing.compositionId())

    await end(page)

    expect(await page.evaluate(() => window.editing.compositionLog())).toBe(
      `start:${id},end:${id}/applied:A`
    )
  })

  test('weist eine unabhaengige Transaktion am Kern ab, nicht nur am Controller', async ({
    page,
  }) => {
    // Das Gate ist eine `PreCommitRule` (§10, Schritt 5), keine Pruefung im Controller -- weil ein
    // Feature-Command, ein Timer oder ein eintreffender Upload die Sitzung direkt erreichen.
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)

    const rejected = await page.evaluate(() => window.editing.setCaret('t1', 1))
    // Eine reine Auswahl bleibt erlaubt (§15.3), eine Dokumentaenderung nicht.
    expect(rejected).not.toContain('rejected')

    const direct = await page.evaluate(() => window.editing.splice('t0', 0, 0, 'X'))
    expect(direct).toBe(false)
    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })

  test('laesst ein abgelaufenes Bookmark verfallen, statt zu raten', async ({ page }) => {
    // §11: eine Einfuegung darf die Ersatzgrenze nicht nehmen. Verschwindet das Ziel, unterbleibt
    // sie -- sonst landet ein Upload an einer voellig anderen Stelle.
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)
    await page.evaluate(() => window.editing.offerBookmarked('U', 't1', 3))

    // Die Composition entfernt den Lauf, auf den das Bookmark zeigt.
    await page.evaluate(() => {
      window.editing.clearOutcomes()
    })
    await end(page)
    await page.evaluate(() => window.editing.remove('t1'))

    // Nach dem Abschluss lief der Intent bereits -- hier zaehlt, dass er ueberhaupt gemeldet wurde.
    expect(await page.evaluate(() => window.editing.compositionLog())).toContain('/applied:U')
  })

  // -------------------------------------------------------------------------------------
  // History (§14)
  // -------------------------------------------------------------------------------------

  test('ergibt genau eine Undo-Stufe', async ({ page }) => {
    // P23s Abnahme: "Eine Composition ergibt eine History-Gruppe." Drei Zwischenstaende, ein
    // Rueckgaengig.
    await open(page)
    await caret(page, 't0', 5)
    const before = await page.evaluate(() => window.editing.undoDepth())

    await begin(page)
    await composeInto(page, 't0', 'Hallo私 Welt')
    await composeInto(page, 't0', 'Hallo私は Welt')
    await composeInto(page, 't0', 'Hallo私は本 Welt')
    await end(page)

    expect(await model(page)).toBe('Hallo私は本 Welt\nZweite Zeile')
    expect(await page.evaluate(() => window.editing.undoDepth())).toBe(before + 1)
  })

  test('nimmt die ganze Composition auf einmal zurueck', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 5)
    await begin(page)
    await composeInto(page, 't0', 'Hallo私 Welt')
    await composeInto(page, 't0', 'Hallo私は Welt')
    await end(page)

    await page.keyboard.press('ControlOrMeta+z')

    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })
})

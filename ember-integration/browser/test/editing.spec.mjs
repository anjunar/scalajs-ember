import { test, expect } from '@playwright/test'

// Normale Browserbearbeitung (P22, §15.2, §22).
//
// Alles hier laeuft ueber echte Tasten. `page.keyboard.type` erzeugt echte `beforeinput`- und
// `input`-Ereignisse mit echten `cancelable`-Flags -- und genau das ist der Punkt: ob ein
// Tastendruck ueberhaupt ein `beforeinput` erzeugt, ob es abbrechbar ist und ob `preventDefault`
// die DOM-Aenderung wirklich verhindert, beantwortet keine synthetische Pruefung.
//
// Die Regeln selbst -- welche Absicht ein `inputType` bedeutet, wann etwas doppelt ankommt, was
// der kleinste Splice zwischen zwei Strings ist -- stehen headless in `InputPipelineSpec`.
//
// Das Dokument:
//   p0  "Hallo Welt"
//   p1  "Zweite Zeile"

async function open(page, options = {}) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(
    (tabIndents) => window.editing.mount(document.getElementById('root'), tabIndents),
    options.tabIndents === true
  )
  await page.locator('#root').focus()
}

/** Setzt den Caret und raeumt das Protokoll ab, damit ein Test nur seine Ereignisse sieht. */
async function caret(page, node, offset) {
  await page.evaluate(
    ([id, at]) => {
      window.editing.setCaret(id, at)
      window.editing.clearOutcomes()
    },
    [node, offset]
  )
}

const model = (page) => page.evaluate(() => window.editing.text())
const log = (page) => page.evaluate(() => window.editing.outcomeLog())

test.describe('Tippen im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.editing?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Text
  // -------------------------------------------------------------------------------------

  test('fuegt getippten Text an der Auswahl ein', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 5)

    await page.keyboard.type('!')

    expect(await model(page)).toBe('Hallo! Welt\nZweite Zeile')
  })

  test('haelt DOM und Modell deckungsgleich', async ({ page }) => {
    // §15.1: die Projektion schreibt, niemand sonst. Der Controller fasst das Dokument-DOM nicht
    // an -- also muss der sichtbare Text aus dem Commit stammen und nicht aus dem Browser.
    await open(page)
    await caret(page, 't0', 5)

    await page.keyboard.type(' schoene')

    expect(await model(page)).toBe('Hallo schoene Welt\nZweite Zeile')
    expect(await page.evaluate(() => window.editing.domText())).toContain('Hallo schoene Welt')
  })

  test('verarbeitet jeden Tastendruck genau einmal', async ({ page }) => {
    // P22s Abnahme: "Jede Eingabe genau einmal." Drei Zeichen, drei Uebernahmen -- und sonst
    // nichts. Ein verhindertes `beforeinput` erzeugt gar kein `input`, also gibt es hier kein Echo
    // zu deduplizieren; der Dedupe-Pfad traegt die Engines, die beides schicken.
    //
    // Die Tastendruecke selbst tauchen nicht auf: ein Buchstabe ohne Modifier ist keine
    // Tastenkombination, und der Controller sieht dafuer nicht einmal in seiner Tabelle nach.
    await open(page)
    await caret(page, 't0', 0)

    await page.keyboard.type('abc')

    expect(await model(page)).toBe('abcHallo Welt\nZweite Zeile')
    expect(await log(page)).toBe('taken:insert-text,taken:insert-text,taken:insert-text')
  })

  test('setzt den Caret hinter das Eingefuegte', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 0)

    await page.keyboard.type('ab')

    expect(await page.evaluate(() => window.editing.selection())).toBe('text:t0:2|text:t0:2')
  })

  test('ersetzt eine Auswahl', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.editing.setRange('t0', 6, 't0', 10)
      window.editing.clearOutcomes()
    })

    await page.keyboard.type('Erde')

    expect(await model(page)).toBe('Hallo Erde\nZweite Zeile')
  })

  test('faellt nicht ueber ein Zeichen ausserhalb der BMP', async ({ page }) => {
    // §11: UTF-16 ist das Offsetmass, und ein Emoji ist zwei Einheiten. Ein Offset dazwischen
    // teilt einen halben Codepoint.
    await open(page)
    await caret(page, 't0', 5)

    await page.keyboard.type('😀')

    expect(await model(page)).toBe('Hallo😀 Welt\nZweite Zeile')
    expect(await page.evaluate(() => window.editing.selection())).toBe('text:t0:7|text:t0:7')
  })

  // -------------------------------------------------------------------------------------
  // Loeschen
  // -------------------------------------------------------------------------------------

  test('loescht rueckwaerts', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 5)

    await page.keyboard.press('Backspace')

    expect(await model(page)).toBe('Hall Welt\nZweite Zeile')
  })

  test('loescht vorwaerts', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 5)

    await page.keyboard.press('Delete')

    expect(await model(page)).toBe('HalloWelt\nZweite Zeile')
  })

  test('loescht ein Graphem und nicht eine Codeeinheit', async ({ page }) => {
    // §11: "Benutzeraktionen wie Backspace und Pfeilnavigation operieren auf Graphem-/
    // Wortgrenzen; sie duerfen weder Surrogatpaare noch kombinierte Zeichen zerlegen."
    await open(page)
    await caret(page, 't0', 5)
    await page.keyboard.type('😀')
    await page.keyboard.press('Backspace')

    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })

  test('loescht die Auswahl, wenn es eine gibt', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.editing.setRange('t0', 5, 't0', 10)
      window.editing.clearOutcomes()
    })

    await page.keyboard.press('Backspace')

    expect(await model(page)).toBe('Hallo\nZweite Zeile')
  })

  // -------------------------------------------------------------------------------------
  // Struktur
  // -------------------------------------------------------------------------------------

  test('teilt den Block bei Enter', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 5)

    await page.keyboard.press('Enter')

    expect(await page.evaluate(() => window.editing.blocks())).toBe('p,p,p')
    expect(await model(page)).toBe('Hallo\n Welt\nZweite Zeile')
  })

  test('setzt bei Shift+Enter einen Umbruch statt eines Blocks', async ({ page }) => {
    // §8.2 haelt weichen und harten Umbruch auseinander, und der Browser meldet sie als zwei
    // verschiedene Absichten -- `insertParagraph` und `insertLineBreak`.
    await open(page)
    await caret(page, 't0', 5)

    await page.keyboard.press('Shift+Enter')

    expect(await page.evaluate(() => window.editing.blocks())).toBe('p,p')
    expect(await model(page)).toBe('Hallo⏎ Welt\nZweite Zeile')
  })

  // -------------------------------------------------------------------------------------
  // Shortcuts (§22)
  // -------------------------------------------------------------------------------------

  test('schaltet eine Mark ueber die Auswahl', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.editing.setRange('t0', 0, 't0', 5)
      window.editing.clearOutcomes()
    })

    await page.keyboard.press('ControlOrMeta+b')

    expect(await page.evaluate(() => window.editing.marksAtCaret())).toBe('strong')
    await expect(page.locator('strong')).toHaveText('Hallo')
  })

  test('macht rueckgaengig und wieder her', async ({ page }) => {
    // §15.2: "Native und modellbasierte Undo-Stacks duerfen sich nicht widersprechen." Der
    // Browserstack wird nie benutzt -- weder ueber die Taste noch ueber `historyUndo`.
    await open(page)
    await caret(page, 't0', 5)
    await page.keyboard.type('XY')

    await page.keyboard.press('ControlOrMeta+z')
    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')

    await page.keyboard.press('ControlOrMeta+Shift+z')
    expect(await model(page)).toBe('HalloXY Welt\nZweite Zeile')
  })

  test('laesst einen unbelegten Shortcut nativ', async ({ page }) => {
    // Der Teil, den P22s Risikoliste betont: `preventDefault` folgt der Uebernahme, nicht der
    // blossen Existenz eines Handlers. Ctrl+P gehoert dem Browser.
    await open(page)
    await caret(page, 't0', 0)

    await page.keyboard.press('ControlOrMeta+p').catch(() => {})

    expect(await log(page)).toContain('native:')
    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })

  // -------------------------------------------------------------------------------------
  // Tab (§22)
  // -------------------------------------------------------------------------------------

  test('laesst Tab die Editierflaeche verlassen', async ({ page }) => {
    // §22: "Tab verlaesst die normale Editierflaeche." Voreinstellung, ohne Ausnahme.
    await open(page)
    await caret(page, 't0', 0)
    await page.evaluate(() => {
      const after = document.createElement('button')
      after.id = 'danach'
      document.body.appendChild(after)
    })

    await page.keyboard.press('Tab')

    expect(await page.evaluate(() => document.activeElement.id)).toBe('danach')
  })

  test('rueckt ein, wo das eingeschaltet wurde -- und laesst trotzdem raus', async ({ page }) => {
    // "Listeneinrueckung ... ist ein ausdruecklich aktiviertes Verhalten mit erreichbarer
    // Ausstiegsmoeglichkeit. Keine permanente Keyboard-Falle."
    await open(page, { tabIndents: true })
    await page.evaluate(() => {
      const after = document.createElement('button')
      after.id = 'danach'
      document.body.appendChild(after)
    })
    await caret(page, 't0', 0)

    await page.keyboard.press('Tab')
    expect(await page.evaluate(() => document.activeElement.id)).toBe('root')

    // Escape, dann Tab -- und der Fokus geht weiter.
    await page.keyboard.press('Escape')
    await page.keyboard.press('Tab')
    expect(await page.evaluate(() => document.activeElement.id)).toBe('danach')
  })

  // -------------------------------------------------------------------------------------
  // Readonly (§22)
  // -------------------------------------------------------------------------------------

  test('weist im Readonly-Modus jede Eingabe ab, statt sie durchzulassen', async ({ page }) => {
    // P22s Risikoliste: "Readonly-/Limit-/Schema-Reject verhindert native Ersatzmutation." Eine
    // Ablehnung ohne `preventDefault` liesse den Browser das DOM aendern, waehrend das Modell
    // nein gesagt hat.
    await open(page)
    await caret(page, 't0', 0)
    await page.evaluate(() => window.editing.setReadOnly(true))

    await page.keyboard.type('x')
    await page.keyboard.press('Backspace')

    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
    expect(await page.evaluate(() => window.editing.domText())).toContain('Hallo Welt')
  })

  test('bleibt readonly fokussierbar und lesbar', async ({ page }) => {
    // §22: "Fokusfaehigkeit und Editierbarkeit sind getrennte Entscheidungen."
    await open(page)
    await page.evaluate(() => window.editing.setReadOnly(true))
    await page.locator('#root').focus()

    expect(await page.evaluate(() => document.activeElement.id)).toBe('root')
    await expect(page.locator('#root')).toHaveAttribute('aria-readonly', 'true')
  })

  test('editiert wieder, sobald readonly weg ist', async ({ page }) => {
    await open(page)
    await page.evaluate(() => window.editing.setReadOnly(true))
    await page.evaluate(() => window.editing.setReadOnly(false))
    await caret(page, 't0', 0)

    await page.keyboard.type('x')

    expect(await model(page)).toBe('xHallo Welt\nZweite Zeile')
  })


  // -------------------------------------------------------------------------------------
  // Atome (§22)
  // -------------------------------------------------------------------------------------

  test('loescht ein Atom mit Backspace dahinter', async ({ page }) => {
    // §22: "Atomare Medien sind per Tastatur erreichbar und loeschbar." Vorher nicht: ein Caret
    // loest zu einer Position in einem Textlauf auf, und ein Atom ist keiner -- Backspace griff
    // daran vorbei und nahm ein Zeichen aus dem Lauf davor. Gefunden beim Benutzen der Demo.
    await open(page)
    await page.evaluate(() => {
      window.editing.setCaretAfterAtom()
      window.editing.clearOutcomes()
    })

    await page.keyboard.press('Backspace')

    expect(await page.evaluate(() => window.editing.hasAtom())).toBe(false)
    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })

  test('loescht es mit Entfernen davor', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.editing.setCaret('t1', 12)
      window.editing.clearOutcomes()
    })

    await page.keyboard.press('Delete')

    expect(await page.evaluate(() => window.editing.hasAtom())).toBe(false)
  })

  test('loescht ein ausgewaehltes Atom', async ({ page }) => {
    // §11 fuehrt `NodeSelection` als eigene Art. Vorher lief jeder Loeschweg darauf ins Leere:
    // es gab keine Range zu sehen, und Backspace tat gar nichts.
    await open(page)
    await page.evaluate(() => {
      window.editing.selectAtom()
      window.editing.clearOutcomes()
    })

    await page.keyboard.press('Backspace')

    expect(await page.evaluate(() => window.editing.hasAtom())).toBe(false)
  })

  test('laesst den Text daneben in Ruhe', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      window.editing.setCaret('t1', 6)
      window.editing.clearOutcomes()
    })

    await page.keyboard.press('Backspace')

    expect(await page.evaluate(() => window.editing.hasAtom())).toBe(true)
    expect(await model(page)).toBe('Hallo Welt\nZweit Zeile')
  })

  // -------------------------------------------------------------------------------------
  // Barrierefreiheit (§22)
  // -------------------------------------------------------------------------------------

  test('beschreibt die Editierflaeche', async ({ page }) => {
    // §22: "Die aktive Editierflaeche erhaelt einen zugaenglichen Namen, role='textbox' und
    // aria-multiline='true'." Den Namen setzt die Anwendung; die Rolle der Controller.
    await open(page)

    await expect(page.locator('#root')).toHaveAttribute('role', 'textbox')
    await expect(page.locator('#root')).toHaveAttribute('aria-multiline', 'true')
    await expect(page.locator('#root')).toHaveAttribute('contenteditable', 'true')
  })

  test('raeumt beim Dispose alles ab', async ({ page }) => {
    await open(page)
    await caret(page, 't0', 0)
    await page.evaluate(() => window.editing.dispose())

    await page.keyboard.type('x')

    // Kein `contenteditable` mehr, also auch keine Eingabe -- und kein Handler, der noch lauscht.
    expect(await page.evaluate(() => document.getElementById('root').hasAttribute('contenteditable')))
      .toBe(false)
  })
})

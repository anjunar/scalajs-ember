import { test, expect } from '@playwright/test'

// Der native Eingabepfad (P22, §15.2, §15.4).
//
// §15.2 beschreibt ihn fuer den Fall, dass `beforeinput` nicht abbrechbar ist oder gar nicht
// kommt: "Native Aenderung beobachten; `input` liest begrenzten betroffenen Bereich und erzeugt
// eine NativeInput-Tx." Das betrifft IME, Autokorrektur, Spracherkennung und alles, was ein
// Browser von sich aus tut.
//
// Erzeugt wird der Pfad hier nicht durch ein gefaelschtes Ereignis, sondern durch einen Editor
// *ohne* Bindings fuer Text: dann uebernimmt niemand, der Browser aendert das DOM, und das
// anschliessende `input` muss das Modell nachziehen. Das ist derselbe Weg, den ein nicht
// abbrechbares `beforeinput` nimmt -- nur reproduzierbar.

async function openBare(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.editing.mount(document.getElementById('root'), false, true))
  await page.locator('#root').focus()
}

async function openNormal(page) {
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
    },
    [node, offset]
  )
}

const model = (page) => page.evaluate(() => window.editing.text())
const log = (page) => page.evaluate(() => window.editing.outcomeLog())
const state = (page) => page.evaluate(() => window.editing.state())

test.describe('Native Eingabe im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.editing?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Nachziehen
  // -------------------------------------------------------------------------------------

  test('zieht getippten Text nach, den niemand uebernommen hat', async ({ page }) => {
    await openBare(page)
    await caret(page, 't0', 5)

    await page.keyboard.type('X')

    expect(await log(page)).toBe('native:insert-text,imported:t0')
    expect(await model(page)).toBe('HalloX Welt\nZweite Zeile')
  })

  test('zieht auch mehrere Zeichen nach', async ({ page }) => {
    await openBare(page)
    await caret(page, 't0', 0)

    await page.keyboard.type('abc')

    expect(await model(page)).toBe('abcHallo Welt\nZweite Zeile')
  })

  test('zieht eine native Loeschung nach', async ({ page }) => {
    await openBare(page)
    await caret(page, 't0', 5)

    await page.keyboard.press('Backspace')

    expect(await model(page)).toBe('Hall Welt\nZweite Zeile')
  })

  test('behaelt dabei den DOM-Textknoten', async ({ page }) => {
    // §15.1s eigentliche Zusicherung: ein Splice erreicht `replaceData`, eine Zuweisung schreibt
    // den ganzen Lauf neu. Der Unterschied ist ein Caret, der stehen bleibt -- und eine laufende
    // IME-Eingabe, die ueberlebt.
    await openBare(page)
    await page.evaluate(() => {
      const run = document.querySelector('[data-ember-node="t0"]')
      window.__node = run.firstChild
    })
    await caret(page, 't0', 5)

    await page.keyboard.type('X')

    const same = await page.evaluate(() => {
      const run = document.querySelector('[data-ember-node="t0"]')
      return run.firstChild === window.__node
    })
    expect(same).toBe(true)
  })

  test('uebernimmt den nativen Caret nach dem Import', async ({ page }) => {
    await openBare(page)
    await caret(page, 't0', 5)

    await page.keyboard.type('X')

    expect(await page.evaluate(() => window.editing.selection())).toBe('text:t0:6|text:t0:6')
  })

  test('schreibt nichts, wenn das DOM schon sagt, was das Modell sagt', async ({ page }) => {
    // Ein `input` ohne Unterschied ist kein Grund fuer eine Transaktion. Eine leere Revision
    // waere eine Undo-Stufe, die nichts rueckgaengig macht.
    await openBare(page)
    await caret(page, 't0', 5)
    const before = await page.evaluate(() => window.editing.revision())

    await page.evaluate(() => {
      document
        .getElementById('root')
        .dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText' }))
    })

    expect(await page.evaluate(() => window.editing.revision())).toBe(before)
  })

  // -------------------------------------------------------------------------------------
  // Was sich nicht importieren laesst (§15.4)
  // -------------------------------------------------------------------------------------

  test('meldet eine native Struktur, statt sie zu raten', async ({ page }) => {
    // §15.4: "Eine native Struktur, die nicht verlustfrei validiert werden kann, fuehrt in
    // Recovery mit gesichertem Text." Enter ohne Bindung laesst den Browser den Absatz teilen --
    // und ein Splice auf einem Textlauf kann das nicht ausdruecken.
    await openBare(page)
    await caret(page, 't0', 5)

    await page.keyboard.press('Enter')

    expect(await log(page)).toContain('unimported')
    expect(await state(page)).toBe('Recovering')
  })

  test('laesst den getippten Text dabei im DOM stehen', async ({ page }) => {
    // Der Grund, warum der Befund den Text mitfuehrt: was jemand gerade geschrieben hat, darf
    // eine unerwartete Struktur nicht mitnehmen.
    await openBare(page)
    await caret(page, 't0', 10)
    await page.keyboard.press('Enter')
    await page.keyboard.type('Neu')

    expect(await page.evaluate(() => window.editing.domText())).toContain('Neu')
  })

  test('nimmt in Recovery keine Eingabe mehr an', async ({ page }) => {
    // Kein "Endlosschleife aus Observer→Render→Observer" (§15.4): der Controller hoert auf,
    // Ereignisse zu beanspruchen, bis jemand aufgeraeumt hat.
    await openBare(page)
    await caret(page, 't0', 5)
    await page.keyboard.press('Enter')
    await page.evaluate(() => window.editing.clearOutcomes())

    await page.keyboard.type('x')

    expect(await log(page)).toContain('idle:Recovering')
  })

  test('macht nach resume wieder weiter', async ({ page }) => {
    await openBare(page)
    await caret(page, 't0', 5)
    await page.keyboard.press('Enter')

    await page.evaluate(() => window.editing.resume())
    expect(await state(page)).toBe('Ready')
  })

  // -------------------------------------------------------------------------------------
  // Ownership (§15.2)
  // -------------------------------------------------------------------------------------

  test('beansprucht die Eingabe in einer Textarea eines Atoms nicht', async ({ page }) => {
    // §15.2: "native Inputs/Textareas in Atom-Views, unmanaged Bereiche und verschachtelte
    // Editoren gehoeren nicht automatisch zum aeusseren Editor." Die Ereignisse blubbern bis zum
    // Editing-Host -- sie dort als Dokumentaenderung zu lesen, tippte die Notiz ins Dokument.
    await openNormal(page)
    await page.locator('[data-widget-input]').focus()
    await page.evaluate(() => window.editing.clearOutcomes())

    await page.keyboard.type('Notizzusatz')

    expect(await log(page)).toContain('not-ours')
    expect(await model(page)).toBe('Hallo Welt\nZweite Zeile')
  })

  test('laesst den Text in der Textarea des Atoms stehen', async ({ page }) => {
    await openNormal(page)
    await page.locator('[data-widget-input]').focus()

    await page.keyboard.type('!')

    // Der Fokus setzt den Caret an den Anfang des Feldes -- das ist die Textarea, nicht
    // der Editor, und genau darum geht es hier.
    expect(await page.inputValue('[data-widget-input]')).toBe('!Notiz')
  })

  // -------------------------------------------------------------------------------------
  // Composition (§15.3; das Protokoll ist P23)
  // -------------------------------------------------------------------------------------

  test('beansprucht waehrend einer Composition nichts', async ({ page }) => {
    // §15.3: "Re-Render oder Selection-Schreiben kann laufende native Texteingabe zerstoeren."
    // P22 behauptet keine vollstaendige IME-Freigabe -- es haelt sich heraus und liest hinterher.
    await openNormal(page)
    await caret(page, 't0', 5)

    await page.evaluate(() => {
      const host = document.getElementById('root')
      host.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
    })

    expect(await state(page)).toBe('Composing')

    await page.keyboard.type('x')
    expect(await log(page)).toContain('idle:Composing')
  })

  test('liest nach dem Ende der Composition, was sie hinterlassen hat', async ({ page }) => {
    await openNormal(page)
    await caret(page, 't0', 5)

    await page.evaluate(() => {
      const host = document.getElementById('root')
      host.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
      // Was eine IME tut: sie schreibt in den Textknoten, den die Projektion haelt.
      const run = document.querySelector('[data-ember-node="t0"]')
      run.firstChild.data = 'Hallo私 Welt'
      host.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }))
    })

    expect(await state(page)).toBe('Ready')
    expect(await model(page)).toBe('Hallo私 Welt\nZweite Zeile')
  })
})

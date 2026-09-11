import { test, expect } from '@playwright/test'

// Fokus, Bookmarks und Geltungsbereich (P21, §§11, 15.4, 22).
//
// Die Regeln stehen headless in `SelectionPolicySpec` -- wann der Fokus wandern darf, was ein
// abgelaufenes Bookmark ist. Hier steht, was nur eine Engine beantwortet: wohin der Fokus
// tatsaechlich geht, was ein `focusout` in eine Toolbar bedeutet, und ob ein Port in einem
// iframe oder einem Shadow Root ueberhaupt etwas sieht.

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.selection.mount(document.getElementById('root')))
}

/** Eine Schaltflaeche ausserhalb des Editors -- das Modell einer Toolbar. */
async function addToolbar(page) {
  await page.evaluate(() => {
    const button = document.createElement('button')
    button.id = 'toolbar'
    button.textContent = 'Fett'
    document.body.appendChild(button)
  })
}

test.describe('Fokus im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.selection?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Wer den Fokus hat
  // -------------------------------------------------------------------------------------

  test('erkennt den Fokus im Host', async ({ page }) => {
    await open(page)
    expect(await page.evaluate(() => window.selection.focusWithin())).toBe(false)

    await page.locator('#root').focus()
    expect(await page.evaluate(() => window.selection.focusWithin())).toBe(true)
  })

  test('zaehlt ein natives Feld im Editor als innen', async ({ page }) => {
    // `focusin`/`focusout` und nicht `focus`/`blur`: ein Atom kann etwas Fokussierbares
    // enthalten, und `blur` am Host behauptete dann, der Fokus sei weg.
    await open(page)
    await page.locator('[data-widget-input]').focus()

    expect(await page.evaluate(() => window.selection.focusWithin())).toBe(true)
  })

  test('meldet Kommen und Gehen', async ({ page }) => {
    await open(page)
    await addToolbar(page)

    await page.locator('#root').focus()
    await page.locator('#toolbar').focus()
    await page.waitForTimeout(30)

    expect(await page.evaluate(() => window.selection.focusLog())).toBe('true,false')
  })

  // -------------------------------------------------------------------------------------
  // Hintergrundschreiben (§22)
  // -------------------------------------------------------------------------------------

  test('schreibt nicht in einen unfokussierten Host', async ({ page }) => {
    // §22: "Hintergrundupdates stehlen weder Page- noch Textarea-Fokus." Ein Caret, der ohne
    // Zutun erscheint, scrollt die Seite dorthin.
    await open(page)
    // Nur das Modell. Eine Auswahl in den Host zu schreiben wuerde ihn in Chromium fokussieren,
    // und dann waere der Test seine eigene Voraussetzung los.
    await page.evaluate(() => window.selection.selectModelOnly('t1', 2, 2))

    expect(await page.evaluate(() => window.selection.syncBackground())).toBe(
      'skipped:NotFocused'
    )
  })

  test('schreibt sehr wohl in einen fokussierten', async ({ page }) => {
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 2, 't1', 2))
    await page.evaluate(() => document.getSelection().collapse(document.body, 0))

    expect(await page.evaluate(() => window.selection.syncBackground())).toBe('written')
  })

  test('schreibt nicht zweimal dasselbe', async ({ page }) => {
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 2, 't1', 2))

    expect(await page.evaluate(() => window.selection.syncBackground())).toBe(
      'skipped:AlreadyThere'
    )
  })

  // -------------------------------------------------------------------------------------
  // Bookmarks (§11, §22)
  // -------------------------------------------------------------------------------------

  test('merkt sich die Auswahl und ob der Editor den Fokus hatte', async ({ page }) => {
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 1, 't1', 4))

    expect(await page.evaluate(() => window.selection.capture())).toBe(true)
    expect(await page.evaluate(() => window.selection.capturedHadFocus())).toBe(true)
  })

  test('fuehrt ein Bookmark durch eine Bearbeitung nach', async ({ page }) => {
    // §11: Mappings sind komponierbar und fuer Bookmarks nutzbar. Zwei Zeichen vor dem Punkt
    // eingefuegt heisst: der Punkt wandert um zwei.
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 4, 't1', 4))
    await page.evaluate(() => window.selection.capture())

    await page.evaluate(() => window.selection.splice('t1', 0, 0, 'XY'))

    expect(await page.evaluate(() => window.selection.restore('always'))).toBe(
      'restored:text:t1:6|text:t1:6:false'
    )
  })

  test('faellt auf die Grenze zurueck, wenn der Inhalt verschwunden ist', async ({ page }) => {
    // Der Unterschied, den `MappedPoint.Displaced` traegt: "Ein Caret darf auf die Grenze
    // zurueckfallen -- der Cursor muss irgendwo stehen. Ein Upload-Bookmark darf das nicht."
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t2', 2, 't2', 2))
    await page.evaluate(() => window.selection.capture())

    await page.evaluate(() => window.selection.remove('t2'))

    const outcome = await page.evaluate(() => window.selection.restore('always'))
    expect(outcome).toContain('restored:children:p0:')
  })

  test('stellt den Fokus bei einem Hintergrundvorgang nicht her', async ({ page }) => {
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 2, 't1', 2))
    await page.evaluate(() => window.selection.capture())

    await addToolbar(page)
    await page.locator('#toolbar').focus()

    // `selection-only` schreibt in einen unfokussierten Host gar nicht -- beides ist §22.
    expect(await page.evaluate(() => window.selection.restore('selection-only'))).toBe(
      'not-written:skipped:NotFocused'
    )
    expect(await page.evaluate(() => document.activeElement.id)).toBe('toolbar')
  })

  test('gibt den Fokus einem Dialog zurueck, der ihn genommen hatte', async ({ page }) => {
    // §22: "Oeffnen eines Dialogs speichert ein gemapptes Selection-Bookmark. Schliessen stellt
    // Fokus nur im passenden Interaktionskontext wieder her."
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 2, 't1', 5))
    await page.evaluate(() => window.selection.capture())

    await addToolbar(page)
    await page.locator('#toolbar').focus()

    expect(await page.evaluate(() => window.selection.restore('if-it-was-ours'))).toBe(
      'restored:text:t1:2|text:t1:5:true'
    )
    expect(await page.evaluate(() => document.activeElement.id)).toBe('root')
    expect(await page.evaluate(() => document.getSelection().toString())).toBe('llo')
  })

  test('nimmt ihn nicht, wenn er nie beim Editor war', async ({ page }) => {
    await open(page)
    await addToolbar(page)
    await page.locator('#toolbar').focus()
    await page.evaluate(() => window.selection.selectModelOnly('t1', 2, 5))

    expect(await page.evaluate(() => window.selection.capture())).toBe(true)
    expect(await page.evaluate(() => window.selection.capturedHadFocus())).toBe(false)

    expect(await page.evaluate(() => window.selection.restore('if-it-was-ours'))).toBe(
      'not-written:skipped:NotFocused'
    )
    expect(await page.evaluate(() => document.activeElement.id)).toBe('toolbar')
  })

  // -------------------------------------------------------------------------------------
  // Viele Leaves (§11, P21s Testliste)
  // -------------------------------------------------------------------------------------

  test('findet einen Punkt auch unter vielen Geschwistern', async ({ page }) => {
    await open(page)
    expect(await page.evaluate(() => window.selection.appendRuns('p1', 60))).toBe(true)

    expect(await page.evaluate(() => window.selection.selectText('x59', 3, 'x59', 3))).toBe(
      'written'
    )
    expect(await page.evaluate(() => window.selection.read())).toBe('text:x59:3|text:x59:3')

    // Und die Kindgrenze ganz hinten -- die Stelle, an der ein Zaehlfehler auffaellt.
    expect(await page.evaluate(() => window.selection.selectChildren('p1', 60, 60))).toBe(
      'written'
    )
    expect(await page.evaluate(() => window.selection.read())).toBe(
      'children:p1:60|children:p1:60'
    )
  })

  // -------------------------------------------------------------------------------------
  // Zwei Editoren, iframe, Shadow DOM (§15.4)
  // -------------------------------------------------------------------------------------

  test('haelt zwei Editoren auf einer Seite auseinander', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      const second = document.createElement('div')
      second.id = 'zweiter'
      document.body.appendChild(second)
      window.selection.mountNeighbour(second)
    })

    await page.evaluate(() => window.selection.neighbourSelectText('nt', 3))

    // Der zweite hat eine Auswahl, der erste keine -- und liest sie auch nicht als seine.
    expect(await page.evaluate(() => window.selection.neighbourRead())).toBe(
      'text:nt:3|text:nt:3'
    )
    expect(await page.evaluate(() => window.selection.read())).toBe('outside')
    expect(await page.evaluate(() => window.selection.modelSelection())).toBe('none')
  })

  test('arbeitet in einem iframe mit dessen eigenem Fenster', async ({ page }) => {
    // §15.4: "Fuer Editor-Hosts in iframes werden ownerDocument/defaultView verwendet." Ein Port,
    // der nach `window.getSelection()` greift, liest hier eine leere Auswahl des aeusseren
    // Dokuments und meldet nichts -- ohne zu scheitern, was schlimmer ist.
    await open(page)
    await page.evaluate(() => {
      const frame = document.createElement('iframe')
      frame.id = 'rahmen'
      document.body.appendChild(frame)
      const inner = frame.contentDocument
      inner.body.innerHTML = '<div id="innen"></div>'
      window.selection.mountNeighbour(inner.getElementById('innen'))
    })

    expect(await page.evaluate(() => window.selection.neighbourCapability())).toBe('Document')

    await page.evaluate(() => window.selection.neighbourSelectText('nt', 4))

    expect(await page.evaluate(() => window.selection.neighbourRead())).toBe(
      'text:nt:4|text:nt:4'
    )
    // Die aeussere Auswahl bleibt unberuehrt -- zwei Dokumente, zwei Selections.
    expect(await page.evaluate(() => document.getSelection().rangeCount === 0)).toBe(true)
  })

  test('sagt fuer einen Shadow Root, was die Engine wirklich kann', async ({ page }, testInfo) => {
    // §15.4: "Shadow-DOM-Selection ist ein eigener Capability-Test; es wird nicht behauptet,
    // globale window.getSelection loese diesen Fall." Chromium hat `ShadowRoot.getSelection`,
    // Firefox und WebKit haben es nicht -- und das ist eine Aussage ueber die Engine, keine
    // ueber den Editor.
    await open(page)
    await page.evaluate(() => {
      const carrier = document.createElement('div')
      carrier.id = 'traeger'
      document.body.appendChild(carrier)
      const root = carrier.attachShadow({ mode: 'open' })
      const inner = document.createElement('div')
      inner.id = 'im-schatten'
      root.appendChild(inner)
      window.selection.mountNeighbour(inner)
    })

    const capability = await page.evaluate(() => window.selection.neighbourCapability())
    const native = await page.evaluate(
      () => typeof document.getElementById('traeger').shadowRoot.getSelection === 'function'
    )

    expect(capability).toBe(native ? 'ShadowNative' : 'ShadowUnsupported')

    if (!native) {
      // Und dann wird nicht so getan, als ginge es doch.
      expect(await page.evaluate(() => window.selection.neighbourSelectText('nt', 2))).toBe(
        'skipped:Unsupported'
      )
      expect(await page.evaluate(() => window.selection.neighbourRead())).toBe('absent')
    }
  })

  // -------------------------------------------------------------------------------------
  // Bidi (§11)
  // -------------------------------------------------------------------------------------

  test('uebernimmt eine Pfeilbewegung in RTL-Text, statt sie zu berechnen', async ({ page }) => {
    // §11: "Bidi-Visualordnung wird nicht aus logischer Dokumentreihenfolge erraten." Der Test
    // behauptet deshalb keine Richtung -- er prueft, dass Modell und Browser danach dasselbe
    // sagen. Genau das ist die Zusicherung: importiert, nicht ausgerechnet.
    await open(page)
    await page.evaluate(() => document.getElementById('root').setAttribute('dir', 'rtl'))
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 3, 't1', 3))
    await page.waitForTimeout(30)

    await page.keyboard.press('ArrowRight')
    await page.waitForTimeout(50)

    const native = await page.evaluate(() => {
      const selection = document.getSelection()
      const owner = selection.anchorNode.parentElement.closest('[data-ember-node]')
      return `text:${owner.getAttribute('data-ember-node')}:${selection.anchorOffset}`
    })

    expect(await page.evaluate(() => window.selection.modelSelection())).toBe(
      `${native}|${native}`
    )
  })
})

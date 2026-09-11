import { test, expect } from '@playwright/test'

// DOM-Selection und Positionsabbildung (P21, §11).
//
// Die Entscheidungsregeln -- wann geschrieben werden darf, wann der Fokus wandern darf, was ein
// abgelaufenes Bookmark ist -- pruefen die Scala-Suiten (`SelectionPolicySpec`). Hier steht die
// Abbildungstabelle selbst, und die ist eine Aussage ueber echtes DOM: wo ein Gruppenanker
// liegt, wie eine Markkette aussieht, was in einem leeren Absatz steht.
//
// Das Dokument der Fixture:
//
//   h0  <h2>       t0 "Titel"
//   p0  <p>        t1 "Hallo "   t2 "Welt" (strong)
//   p1  <p>        leer
//   c0  <pre><code> t3 "zeile"
//   p2  <p>        a0 Atom mit <textarea>

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => window.selection.mount(document.getElementById('root')))
}

/** Setzt die native Auswahl direkt, ohne den Port -- so entsteht die Leserichtung. */
async function selectNative(page, anchorSelector, anchorOffset, focusSelector, focusOffset) {
  return page.evaluate(
    ([anchor, anchorAt, focus, focusAt]) => {
      const resolve = (selector, offset) => {
        const element = document.querySelector(selector)
        // Ein Textoffset meint den Textknoten, ein Elementoffset das Element selbst.
        const text = element.firstChild
        return offset === null ? [element, 0] : [text, offset]
      }
      const [anchorNode, anchorIndex] = resolve(anchor, anchorAt)
      const [focusNode, focusIndex] = resolve(focus, focusAt)
      const selection = document.getSelection()
      selection.collapse(anchorNode, anchorIndex)
      selection.extend(focusNode, focusIndex)
    },
    [anchorSelector, anchorOffset, focusSelector, focusOffset]
  )
}

const run = id => `[data-ember-node="${id}"]`

test.describe('Selection im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.selection?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Modell -> DOM
  // -------------------------------------------------------------------------------------

  test('setzt einen Caret in einen gewoehnlichen Lauf', async ({ page }) => {
    await open(page)

    expect(await page.evaluate(() => window.selection.selectText('t1', 3, 't1', 3))).toBe('written')

    const where = await page.evaluate(() => {
      const selection = document.getSelection()
      return [
        selection.anchorNode.nodeType,
        selection.anchorNode.parentElement.getAttribute('data-ember-node'),
        selection.anchorOffset,
        selection.isCollapsed,
      ].join(':')
    })
    // 3 == TEXT_NODE. Der Punkt landet im Textknoten des Laufs, nicht an seinem Wrapper.
    expect(where).toBe('3:t1:3:true')
  })

  test('findet den Textknoten durch eine Markkette hindurch', async ({ page }) => {
    // §15.1 rendert einen markierten Lauf als `<span><strong>Text</strong></span>`. Der
    // Textknoten liegt zwei Ebenen tief, und ein Port, der vom Wrapper aus zaehlt, landet am
    // <strong> statt im Text.
    await open(page)

    expect(await page.evaluate(() => window.selection.selectText('t2', 2, 't2', 2))).toBe('written')

    expect(
      await page.evaluate(() => document.getSelection().anchorNode.parentElement.tagName)
    ).toBe('STRONG')
  })

  test('trifft die Kindgrenze eines Absatzes trotz der Gruppenanker', async ({ page }) => {
    // Der Kern der Sache (§11): unter einem Absatz stehen im DOM Kommentaranker, die keine
    // Dokumentkinder sind. Kindoffset 1 meint "vor t2" und nicht "vor dem zweiten DOM-Kind".
    await open(page)

    expect(await page.evaluate(() => window.selection.selectChildren('p0', 1, 1))).toBe('written')

    const landed = await page.evaluate(() => {
      const selection = document.getSelection()
      const node = selection.anchorNode
      const following = node.childNodes[selection.anchorOffset]
      return following && following.getAttribute
        ? following.getAttribute('data-ember-node')
        : String(following && following.nodeName)
    })
    expect(landed).toBe('t2')
  })

  test('setzt einen Caret in einen leeren Absatz', async ({ page }) => {
    await open(page)

    expect(await page.evaluate(() => window.selection.selectChildren('p1', 0, 0))).toBe('written')

    expect(
      await page.evaluate(() =>
        document.getSelection().anchorNode.getAttribute('data-ember-node')
      )
    ).toBe('p1')
  })

  test('setzt den Caret im Codeblock ins <code> und nicht ins <pre>', async ({ page }) => {
    // Innentags (§15.1): ein Knoten, zwei Tags. Die Kinder haengen im inneren, und der
    // Inhaltshost der Komponente sagt das -- abgezaehlt waere es geraten.
    await open(page)

    expect(await page.evaluate(() => window.selection.selectChildren('c0', 0, 0))).toBe('written')

    expect(await page.evaluate(() => document.getSelection().anchorNode.tagName)).toBe('CODE')
  })

  test('spannt eine Auswahl ueber zwei Blocke', async ({ page }) => {
    await open(page)

    expect(await page.evaluate(() => window.selection.selectText('t0', 1, 't2', 3))).toBe('written')

    const spans = await page.evaluate(() => {
      const selection = document.getSelection()
      return [
        selection.anchorNode.parentElement.closest('[data-ember-node]').getAttribute('data-ember-node'),
        selection.focusNode.parentElement.closest('[data-ember-node]').getAttribute('data-ember-node'),
        selection.isCollapsed,
      ].join(':')
    })
    expect(spans).toBe('t0:t2:false')
  })

  test('erhaelt die Richtung einer rueckwaerts gezogenen Auswahl', async ({ page }) => {
    // §11: "Anchor/Focus werden niemals nur zugunsten sortierter Endpunkte ueberschrieben."
    // Sonst springt der Cursor beim naechsten Pfeiltastendruck ans falsche Ende.
    await open(page)

    expect(await page.evaluate(() => window.selection.selectText('t2', 3, 't0', 1))).toBe('written')

    expect(await page.evaluate(() => window.selection.direction())).toBe('Backward')

    const backwards = await page.evaluate(() => {
      const selection = document.getSelection()
      const range = selection.getRangeAt(0)
      // Der Anker steht hinter dem Fokus, wenn die Range am Fokus beginnt.
      return range.startContainer === selection.focusNode
    })
    expect(backwards).toBe(true)
  })

  test('macht aus einer Knotenauswahl den Bereich, der sie umschliesst', async ({ page }) => {
    // §11 haelt `NodeSelection` als eigene Art fest -- mehrere ausgewaehlte Bilder sind kein
    // Textbereich. Der Browser kennt das nicht; er bekommt die Spanne, das Modell behaelt die
    // Menge.
    await open(page)

    expect(await page.evaluate(() => window.selection.selectNodes('p0,p1'))).toBe('written')
    expect(await page.evaluate(() => window.selection.modelSelection())).toBe('nodes:p0,p1')
    expect(await page.evaluate(() => document.getSelection().isCollapsed)).toBe(false)
  })

  // -------------------------------------------------------------------------------------
  // DOM -> Modell
  // -------------------------------------------------------------------------------------

  test('liest einen nativen Caret als Textpunkt', async ({ page }) => {
    await open(page)
    await selectNative(page, run('t1'), 4, run('t1'), 4)

    expect(await page.evaluate(() => window.selection.read())).toBe('text:t1:4|text:t1:4')
  })

  test('liest eine Elementposition als Kindgrenze', async ({ page }) => {
    await open(page)
    await page.evaluate(() => {
      const paragraph = document.querySelector('[data-ember-node="p0"]')
      const selection = document.getSelection()
      // Hinter alles, was im DOM steht -- Anker eingeschlossen. Dokumentkinder sind zwei.
      selection.collapse(paragraph, paragraph.childNodes.length)
    })

    expect(await page.evaluate(() => window.selection.read())).toBe(
      'children:p0:2|children:p0:2'
    )
  })

  test('zaehlt die Gruppenanker nicht als Kinder', async ({ page }) => {
    await open(page)
    const anchors = await page.evaluate(() => {
      const paragraph = document.querySelector('[data-ember-node="p0"]')
      return [...paragraph.childNodes].filter(node => node.nodeType === 8).length
    })
    // Ohne Anker waere der Test wertlos -- er soll zeigen, dass sie uebergangen werden.
    expect(anchors).toBeGreaterThan(0)

    await page.evaluate(() => {
      const paragraph = document.querySelector('[data-ember-node="p0"]')
      const selection = document.getSelection()
      // Genau hinter den ersten Anker: im DOM Offset 1, im Dokument immer noch 0 Kinder davor.
      selection.collapse(paragraph, 1)
    })

    expect(await page.evaluate(() => window.selection.read())).toBe(
      'children:p0:0|children:p0:0'
    )
  })

  test('ordnet eine Position in der Markkette dem Lauf zu', async ({ page }) => {
    await open(page)

    expect(await page.evaluate(() => window.selection.nodeAt('strong', -1))).toBe('t2')
  })

  test('ordnet einen Gruppenanker dem Elternknoten zu', async ({ page }) => {
    await open(page)

    expect(await page.evaluate(() => window.selection.nodeAt('[data-ember-node="p0"]', 0))).toBe(
      'p0'
    )
  })

  test('ordnet das <code> dem Codeblock zu, nicht einem eigenen Knoten', async ({ page }) => {
    await open(page)

    expect(await page.evaluate(() => window.selection.nodeAt('code', -1))).toBe('c0')
  })

  test('liest eine Auswahl ausserhalb des Hosts nicht', async ({ page }) => {
    // §11: "SelectionPort liest nur Selection innerhalb seines Editing-Hosts."
    await open(page)
    await page.evaluate(() => {
      const outside = document.createElement('p')
      outside.id = 'fremd'
      outside.textContent = 'Nicht der Editor'
      document.body.appendChild(outside)
      const selection = document.getSelection()
      selection.collapse(outside.firstChild, 3)
    })

    expect(await page.evaluate(() => window.selection.read())).toBe('outside')
  })

  // -------------------------------------------------------------------------------------
  // Das Atom (§11, §15.2)
  // -------------------------------------------------------------------------------------

  test('behandelt eine Position im Atom als Grenze davor, nicht als Text darin', async ({
    page,
  }) => {
    // P21s Abnahme: "Native Inputs innerhalb Atom-Views werden nicht als Editortext behandelt."
    await open(page)

    expect(await page.evaluate(() => window.selection.nodeAt('[data-widget-input]', -1))).toBe(
      'a0'
    )

    // Die Abbildung hat eine Antwort: die Grenze vor dem Atom, im Elternknoten.
    expect(await page.evaluate(() => window.selection.pointAt('[data-widget]', -1))).toBe(
      'children:p2:0'
    )

    // Der Port nimmt sie trotzdem nicht als Dokumentauswahl an (§15.2, Event-Ownership).
    await page.evaluate(() => {
      const widget = document.querySelector('[data-widget]')
      document.getSelection().collapse(widget, 0)
    })

    expect(await page.evaluate(() => window.selection.read())).toBe('foreign:a0')
  })

  test('schreibt keinen Punkt in ein Atom hinein', async ({ page }) => {
    await open(page)

    // Zwei Instanzen weisen das ab, und beide sollen es tun. Der Kern zuerst: ein Kindpunkt auf
    // einem Knoten ohne Kinder ist keine gueltige Auswahl (§11), und die Transaktion scheitert.
    expect(await page.evaluate(() => window.selection.selectChildren('a0', 0, 0))).toContain(
      'rejected:'
    )

    // Und der Port selbst, an derselben Position vorbei am Kern: sein Inneres ist kein
    // Textbereich, also gibt es dort nichts anzusteuern.
    expect(await page.evaluate(() => window.selection.writeRawChildren('a0', 0))).toBe(
      'failed:InsideAtom'
    )
  })

  test('laesst die Auswahl in der Textarea des Atoms in Ruhe', async ({ page }) => {
    // Der Fokus liegt im Host -- ein natives Feld darin ist kein fremder Ort --, aber was in
    // ihm ausgewaehlt ist, gehoert nicht dem Dokument.
    await open(page)
    await page.locator('[data-widget-input]').focus()
    await page.evaluate(() => {
      const area = document.querySelector('[data-widget-input]')
      area.setSelectionRange(1, 3)
    })

    expect(await page.evaluate(() => window.selection.focusWithin())).toBe(true)
    expect(await page.evaluate(() => window.selection.modelSelection())).toBe('none')
  })

  // -------------------------------------------------------------------------------------
  // Der Rundweg und die Schleife
  // -------------------------------------------------------------------------------------

  test('liest zurueck, was es geschrieben hat', async ({ page }) => {
    // P21s Abnahme: "read(write(selection)) ist fuer unterstuetzte Punkte semantisch
    // aequivalent."
    await open(page)

    const cases = [
      ['t0', 0, 't0', 0],
      ['t1', 6, 't1', 6],
      ['t2', 4, 't2', 4],
      ['t3', 2, 't3', 5],
      ['t1', 2, 't2', 1],
    ]

    for (const [anchor, anchorAt, focus, focusAt] of cases) {
      const written = await page.evaluate(
        ([a, ao, f, fo]) => window.selection.selectText(a, ao, f, fo),
        [anchor, anchorAt, focus, focusAt]
      )
      expect(written).toBe('written')

      const readBack = await page.evaluate(() => window.selection.read())
      expect(readBack).toBe(`text:${anchor}:${anchorAt}|text:${focus}:${focusAt}`)
    }
  })

  test('geht auch fuer Kindgrenzen hin und zurueck', async ({ page }) => {
    await open(page)

    for (const [parent, offset] of [
      ['p0', 0],
      ['p0', 2],
      ['p1', 0],
      ['root', 3],
    ]) {
      const written = await page.evaluate(
        ([id, at]) => window.selection.selectChildren(id, at, at),
        [parent, offset]
      )
      expect(written).toBe('written')
      expect(await page.evaluate(() => window.selection.read())).toBe(
        `children:${parent}:${offset}|children:${parent}:${offset}`
      )
    }
  })

  test('erzeugt aus einem eigenen Schreibvorgang keinen Import', async ({ page }) => {
    // §15.2: "eigene Selection-Schreibvorgaenge anhand Revision und tatsaechlichem Wert
    // erkennen." Sonst laeuft Schreiben -> selectionchange -> Import -> Schreiben im Kreis.
    await open(page)

    await page.evaluate(() => window.selection.selectText('t1', 2, 't1', 2))
    // `selectionchange` kommt asynchron am Ende der Aufgabe -- ein synchrones Flag traefe es nie.
    await page.waitForTimeout(50)

    expect(await page.evaluate(() => window.selection.importCount())).toBe(0)
  })

  test('importiert dagegen, was der Benutzer bewegt hat', async ({ page }) => {
    await open(page)
    await selectNative(page, run('t0'), 2, run('t0'), 2)
    await page.waitForTimeout(50)

    expect(await page.evaluate(() => window.selection.importCount())).toBe(1)
    expect(await page.evaluate(() => window.selection.modelSelection())).toBe(
      'text:t0:2|text:t0:2'
    )
  })

  test('importiert einen unveraenderten Stand nicht ein zweites Mal', async ({ page }) => {
    await open(page)
    await selectNative(page, run('t0'), 2, run('t0'), 2)
    await page.waitForTimeout(50)
    await selectNative(page, run('t0'), 2, run('t0'), 2)
    await page.waitForTimeout(50)

    expect(await page.evaluate(() => window.selection.importCount())).toBe(1)
  })

  // -------------------------------------------------------------------------------------
  // Pfeiltasten, nativ (§11)
  // -------------------------------------------------------------------------------------

  test('uebernimmt eine native Pfeilbewegung', async ({ page }) => {
    // §11: "Browser-Pfeilnavigation darf zunaechst nativ laufen; selectionchange importiert das
    // Ergebnis." Kein Keydown-Handler, keine geratene Bewegung.
    await open(page)
    await page.locator('#root').focus()
    await page.evaluate(() => window.selection.selectText('t1', 2, 't1', 2))
    await page.waitForTimeout(30)

    await page.keyboard.press('ArrowRight')
    await page.waitForTimeout(50)

    expect(await page.evaluate(() => window.selection.modelSelection())).toBe(
      'text:t1:3|text:t1:3'
    )
  })

  test('meldet die Faehigkeit des Hosts', async ({ page }) => {
    // §15.4 verlangt einen eigenen Capability-Test statt der Behauptung, `window.getSelection`
    // loese jeden Fall.
    await open(page)

    expect(await page.evaluate(() => window.selection.capability())).toBe('Document')
  })
})

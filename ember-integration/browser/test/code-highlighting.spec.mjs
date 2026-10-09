import { test, expect } from '@playwright/test'

// Syntax-Highlighting (X02).
//
// Gefaerbt wird mit der CSS Custom Highlight API: Ranges ueber dem vorhandenen Textknoten, kein
// Element und kein geteilter Knoten. Nachgewiesen wird hier die Abnahme aus X02 im echten Browser:
// "Text/Selection/IME unveraendert", "veraltetes Worker-Ergebnis ignoriert", "Cleanup".
//
// Das Dokument der Fixture:
//   p0  "Intro"
//   c0  scala  "val x = 1 // note\ndef f = \"s\""
//   p1  "Outro"

const source = 'val x = 1 // note\ndef f = "s"'

async function open(page, { held = false, frame = false } = {}) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(
    ([withHeld, withFrame]) =>
      window.highlight.mount(document.getElementById('root'), withHeld, withFrame),
    [held, frame]
  )
}

/** Painting tests need the API; the fallback test below runs everywhere. */
async function requireApi(page) {
  const supported = await page.evaluate(() => window.highlight.supported())
  test.skip(!supported, 'Keine CSS Custom Highlight API in dieser Engine')
}

async function caret(page, node, offset) {
  await page.locator('#root').focus()
  await page.evaluate(([id, at]) => window.highlight.setCaret(id, at), [node, offset])
}

const ranges = (page, kind) => page.evaluate((name) => window.highlight.ranges(name), kind)
const code = (page) => page.evaluate(() => window.highlight.code())
const codeText = (page) =>
  page.evaluate(() => document.querySelector('[data-ember-node="tc"]').firstChild)

async function recordMutations(page) {
  await page.evaluate(() => {
    window.__records = []
    window.__observer = new MutationObserver((records) => window.__records.push(...records))
    window.__observer.observe(document.getElementById('root'), {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
    })
  })
}

/** The records since `recordMutations`, as `{ type, inCode }`. */
async function takeMutations(page) {
  return page.evaluate(() => {
    window.__observer.takeRecords().forEach((record) => window.__records.push(record))
    window.__observer.disconnect()
    const pre = document.querySelector('pre')
    return window.__records.map((record) => ({
      type: record.type,
      inCode: !!pre && pre.contains(record.target),
    }))
  })
}

async function rememberTextNode(page) {
  await page.evaluate(() => {
    window.__text = document.querySelector('[data-ember-node="tc"]').firstChild
  })
}

const sameTextNode = (page) =>
  page.evaluate(
    () => window.__text === document.querySelector('[data-ember-node="tc"]').firstChild
  )

test.describe('Syntax-Highlighting im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.highlight?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Faerben
  // -------------------------------------------------------------------------------------

  test('faerbt Schluesselwoerter, Kommentare, Strings und Namen', async ({ page }) => {
    await open(page)
    await requireApi(page)

    expect(await ranges(page, 'keyword')).toEqual(['val', 'def'])
    expect(await ranges(page, 'comment')).toEqual(['// note'])
    expect(await ranges(page, 'string')).toEqual(['"s"'])
    expect(await ranges(page, 'function')).toEqual(['f'])
    expect(await ranges(page, 'number')).toEqual(['1'])
  })

  test('laesst den Codeblock bei genau einem Textknoten', async ({ page }) => {
    // Der Vertrag, auf dem Positionsabbildung, Recovery und Composition stehen (§11, §15).
    await open(page)

    const shape = await page.evaluate(() =>
      [...document.querySelector('[data-ember-node="tc"]').childNodes].map((node) => node.nodeType)
    )

    expect(shape).toEqual([3])
  })

  test('schreibt beim Faerben nichts in den DOM', async ({ page }) => {
    await open(page)
    await requireApi(page)
    await recordMutations(page)

    await page.evaluate(() => window.highlight.refresh())

    expect(await takeMutations(page)).toEqual([])
    expect(await ranges(page, 'keyword')).toEqual(['val', 'def'])
  })

  test('faerbt beim Tippen nach und behaelt den Textknoten', async ({ page }) => {
    await open(page)
    await requireApi(page)
    await caret(page, 'tc', 0)
    await rememberTextNode(page)
    await recordMutations(page)

    await page.keyboard.type('lazy ')

    expect(await code(page)).toBe(`lazy ${source}`)
    expect(await ranges(page, 'keyword')).toEqual(['lazy', 'val', 'def'])
    expect(await sameTextNode(page)).toBe(true)
    const structural = (await takeMutations(page)).filter(
      (record) => record.inCode && record.type === 'childList'
    )
    expect(structural).toEqual([])
  })

  test('laesst Caret und Auswahl stehen', async ({ page }) => {
    await open(page)
    await requireApi(page)
    await caret(page, 'tc', 4)

    const read = () =>
      page.evaluate(() => {
        const selection = document.getSelection()
        const text = document.querySelector('[data-ember-node="tc"]').firstChild
        return [selection.anchorNode === text, selection.anchorOffset, selection.focusOffset]
      })
    const before = await read()

    await page.evaluate(() => window.highlight.refresh())

    expect(before).toEqual([true, 4, 4])
    expect(await read()).toEqual(before)
  })

  // -------------------------------------------------------------------------------------
  // Revisionen
  // -------------------------------------------------------------------------------------

  test('verwirft ein veraltetes Worker-Ergebnis', async ({ page }) => {
    // X02: "veraltetes Worker-Ergebnis ignoriert". Die Fixture stellt einen Worker davor, der erst
    // auf Zuruf antwortet -- und auch auf eine Frage, die niemand mehr stellt.
    await open(page, { held: true })
    await requireApi(page)
    expect(await page.evaluate(() => window.highlight.held())).toBe(1)

    await caret(page, 'tc', 0)
    await page.keyboard.type('x')
    expect(await code(page)).toBe(`x${source}`)
    expect(await page.evaluate(() => window.highlight.held())).toBe(2)

    await page.evaluate(() => window.highlight.answer(1))
    await page.evaluate(() => window.highlight.answer(0))

    // `xval` is a name. The old answer would have painted `val` over its first three letters.
    expect(await page.evaluate(() => window.highlight.stale())).toBe(1)
    expect(await ranges(page, 'keyword')).toEqual(['def'])
  })

  test('faerbt waehrend einer Composition ohne Schreibzugriff auf den DOM', async ({ page }) => {
    // §15.3: jeder Schreibzugriff in den Bereich einer laufenden Eingabe zerstoert sie. Ranges
    // sind keiner.
    await open(page)
    await requireApi(page)
    await caret(page, 'tc', 0)
    await rememberTextNode(page)
    await recordMutations(page)

    await page.evaluate(() =>
      document
        .getElementById('root')
        .dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
    )
    await page.evaluate((value) => {
      document.querySelector('[data-ember-node="tc"]').firstChild.data = value
      document
        .getElementById('root')
        .dispatchEvent(new InputEvent('input', { inputType: 'insertCompositionText', bubbles: true }))
    }, `var ${source}`)
    await page.evaluate(() => window.highlight.flush())

    expect(await page.evaluate(() => window.highlight.state())).toBe('Composing')

    await page.evaluate(() =>
      document
        .getElementById('root')
        .dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }))
    )

    expect(await code(page)).toBe(`var ${source}`)
    expect(await sameTextNode(page)).toBe(true)
    expect(await ranges(page, 'keyword')).toEqual(['var', 'val', 'def'])
    const structural = (await takeMutations(page)).filter(
      (record) => record.inCode && record.type === 'childList'
    )
    expect(structural).toEqual([])
  })

  // -------------------------------------------------------------------------------------
  // Aufraeumen
  // -------------------------------------------------------------------------------------

  test('entfernt die Farben, wenn der Block kein Code mehr ist', async ({ page }) => {
    await open(page)
    await requireApi(page)

    await page.evaluate(() => window.highlight.toParagraph())

    expect(await ranges(page, 'keyword')).toEqual([])
    expect(await page.evaluate(() => window.highlight.names())).toEqual([])
  })

  test('raeumt ab, wenn die Sprache niemand kennt', async ({ page }) => {
    await open(page)
    await requireApi(page)

    await page.evaluate(() => window.highlight.setLanguage('cobol'))

    expect(await ranges(page, 'keyword')).toEqual([])
    expect(await page.evaluate(() => window.highlight.painted())).toBe(0)
  })

  test('gibt beim Entsorgen jeden Namen frei und laesst den Text stehen', async ({ page }) => {
    await open(page)
    await requireApi(page)

    await page.evaluate(() => window.highlight.disposeDecorations())

    expect(await page.evaluate(() => window.highlight.names())).toEqual([])
    expect(await page.evaluate(() => document.querySelector('pre').textContent)).toBe(source)
  })

  test('teilt die Namen mit einem zweiten Editor, ohne dessen Farben zu loeschen', async ({
    page,
  }) => {
    await open(page)
    await requireApi(page)

    await page.evaluate(() => {
      const second = document.createElement('div')
      second.id = 'second'
      document.body.append(second)
      window.highlight.mountSecond(second)
    })
    expect(await ranges(page, 'keyword')).toHaveLength(4)

    await page.evaluate(() => window.highlight.disposeSecond())

    expect(await ranges(page, 'keyword')).toEqual(['val', 'def'])
  })

  // -------------------------------------------------------------------------------------
  // Zeitpunkt und Rueckfall
  // -------------------------------------------------------------------------------------

  test('faerbt im naechsten Frame', async ({ page }) => {
    await open(page, { frame: true })
    await requireApi(page)

    await page.waitForFunction(() => window.highlight.ranges('keyword').length === 2)
  })

  test('kommt ohne Highlight API aus', async ({ page }) => {
    // Kein Farbauftrag, kein Fehler, und der Editor bleibt ein Editor.
    await page.addInitScript(() => {
      delete window.Highlight
    })
    await open(page)

    expect(await page.evaluate(() => window.highlight.supported())).toBe(false)

    await caret(page, 'tc', 0)
    await page.keyboard.type('lazy ')

    expect(await code(page)).toBe(`lazy ${source}`)
    expect(await page.evaluate(() => window.highlight.painted())).toBe(0)
    expect(await codeText(page)).not.toBeNull()
  })
})

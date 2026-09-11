import { test, expect } from '@playwright/test'

// Der Source-Modus im echten Browser (P19b, §16).
//
// Die Vertragsregeln selbst -- Besitz, Baseline, Atomaritaet -- pruefen die Scala-Suiten
// headless. Was hier dazukommt, ist das, was nur eine echte Engine beantwortet: dass dieselbe
// benannte Textarea im laufenden Editor bleibt, dass sie beim Aktivieren verschwindet statt
// abgeschaltet zu werden, und dass ein Wechsel in den Quelltextmodus sie zurueckbringt.

async function mount(page, source = '# Titel\n\nEin Absatz.', reject = false) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(
    ([text, refuse]) => window.form.mount(document.getElementById('root'), text, refuse),
    [source, reject]
  )
}

const field = 'textarea[name="body"]'

test.describe('Der Source-Modus im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.form?.dispose())
  })

  test('haelt genau eine benannte Textarea', async ({ page }) => {
    // §16: "Nach erfolgreicher Aktivierung bleibt genau ein erfolgreiches benanntes
    // Formularfeld." Nicht zwei, und nicht keines.
    await mount(page)

    await expect(page.locator(field)).toHaveCount(1)
    await expect(page.locator('[name]')).toHaveCount(1)
  })

  test('verbirgt sie nach der Aktivierung, ohne sie abzuschalten', async ({ page }) => {
    // Verborgen ja, disabled nie: ein disabled Control ist kein *erfolgreiches* Formularfeld,
    // und das Formular saendete fuer diesen Namen gar nichts.
    await mount(page)

    await expect(page.locator(field)).toBeHidden()
    await expect(page.locator(field)).toBeEnabled()
  })

  test('zeigt sie im Quelltextmodus wieder', async ({ page }) => {
    await mount(page)
    const problem = await page.evaluate(() => window.form.enterSource())

    expect(problem).toBe('')
    await expect(page.locator(field)).toBeVisible()
    await expect(page.locator('[data-editor-field] ')).toHaveAttribute('data-editor-mode', 'source')
  })

  test('schreibt den Quelltext in die Textarea', async ({ page }) => {
    await mount(page, '# Ueberschrift\n\nText.')
    await page.evaluate(() => window.form.enterSource())

    await expect(page.locator(field)).toHaveValue(/# Ueberschrift/)
  })

  test('nimmt den Entwurf aus der Textarea entgegen', async ({ page }) => {
    await mount(page)
    await page.evaluate(() => window.form.enterSource())
    await page.locator(field).fill('# Vom Benutzer')

    const value = await page.evaluate(() => {
      window.form.editDraft(document.querySelector('textarea[name="body"]').value)
      return window.form.submitValue
    })

    expect(value).toBe('# Vom Benutzer')
  })

  test('schuetzt den Entwurf vor einer fremden Aenderung', async ({ page }) => {
    // §16: "Document→Form-Projektion darf diesen Draft nicht ueberschreiben." Der Kern des
    // Modus, hier an der Textarea nachgewiesen und nicht nur am Modell.
    await mount(page)
    await page.evaluate(() => {
      window.form.enterSource()
      window.form.editDraft('Mein Text')
      window.form.outsideChange('Fremd')
    })

    expect(await page.evaluate(() => window.form.submitValue)).toBe('Mein Text')
    await expect(page.locator(field)).toHaveValue('Mein Text')
  })

  test('stellt eine fremde Aenderung zurueck und holt sie danach nach', async ({ page }) => {
    // §16 nennt Upload-Completion als den Fall. "erst danach werden wartende Intents neu
    // validiert" -- gegen das importierte Dokument, nicht gegen das alte.
    await mount(page)

    const queued = await page.evaluate(() => {
      window.form.enterSource()
      window.form.editDraft('Importiert')
      return window.form.outsideChange('Fremd')
    })
    expect(queued).toBe('deferred')

    const applied = await page.evaluate(() => window.form.applyDraft())
    expect(applied).toBe('')

    // Der zurueckgestellte Intent lief nach dem Import und hat den importierten Text ersetzt.
    expect(await page.evaluate(() => window.form.firstRun)).toBe('Fremd')
    expect(await page.evaluate(() => window.form.deferred.length)).toBe(0)
  })

  test('weist eine fremde Aenderung ab, wenn die Anwendung das waehlt', async ({ page }) => {
    await mount(page, '# Titel\n\nEin Absatz.', true)

    const outcome = await page.evaluate(() => {
      window.form.enterSource()
      return window.form.outsideChange('Fremd')
    })

    expect(outcome).toMatch(/^refused:/)
    expect(outcome).toContain('Quelltext')
  })

  test('uebernimmt den Entwurf und kehrt zurueck', async ({ page }) => {
    await mount(page)

    const problem = await page.evaluate(() => {
      window.form.enterSource()
      window.form.editDraft('# Ganz neu')
      return window.form.applyDraft()
    })

    expect(problem).toBe('')
    await expect(page.locator(field)).toBeHidden()
    await expect(page.locator('[data-editor-preview] h1')).toHaveText('Ganz neu')
  })

  test('haelt den sichtbaren Text fest, wenn der Import scheitert', async ({ page }) => {
    // §16: "Decode-/Konfliktfehler erhalten den sichtbaren String." Ein halb uebernommener
    // Stand waere schlimmer als keiner.
    await mount(page)

    const problem = await page.evaluate(() => {
      window.form.enterSource()
      window.form.editDraft('Mein Text')
      // Nicht ueber `request`: eine Aenderung, die den Binding-Weg gar nicht nimmt. Genau
      // dagegen gibt es die Baseline.
      window.form.outsideChangeUnmediated('Fremd')
      return window.form.applyDraft()
    })

    // Der Entwurf wurde gegen eine Revision geschrieben, die es nicht mehr gibt.
    expect(problem).toContain('Revision')
    expect(await page.evaluate(() => window.form.submitValue)).toBe('Mein Text')
    expect(await page.evaluate(() => window.form.mode)).toBe('source')
  })

  test('verwirft den Entwurf nur auf ausdrueckliche Aktion', async ({ page }) => {
    await mount(page, '# Bleibt\n')

    await page.evaluate(() => {
      window.form.enterSource()
      window.form.editDraft('Wird verworfen')
      window.form.discardDraft()
    })

    expect(await page.evaluate(() => window.form.mode)).toBe('rich')
    expect(await page.evaluate(() => window.form.submitValue)).toMatch(/# Bleibt/)
    await expect(page.locator(field)).toHaveValue(/# Bleibt/)
  })

  test('fuehrt den Formwert nach jedem Commit nach', async ({ page }) => {
    // §16: "Im Rich-Modus wird der Submit-Wert nach jedem Dokumentcommit synchron
    // aktualisiert." Ohne das saende ein Enter oder `requestSubmit` den vorigen Stand.
    await mount(page, 'Vorher\n')

    await page.evaluate(() => window.form.outsideChange('Nachher'))

    expect(await page.evaluate(() => window.form.submitValue)).toMatch(/Nachher/)
    await expect(page.locator(field)).toHaveValue(/Nachher/)
  })

  test('importiert vor dem Absenden', async ({ page }) => {
    await mount(page)

    const value = await page.evaluate(() => {
      window.form.enterSource()
      window.form.editDraft('# Vor dem Senden')
      return window.form.valueForSubmit()
    })

    expect(value).toMatch(/# Vor dem Senden/)
    expect(await page.evaluate(() => window.form.mode)).toBe('rich')
  })
})

import { test, expect } from '@playwright/test'

// Hydration mit Verlustschutz (P20, §17).
//
// Diese Suite ist der Teil, den keine headless Pruefung beantworten kann. §17 ist eine Aussage
// ueber eine Seite, die der Server schon geschickt hat: was stand in der Textarea, bevor das
// Skript lief, welche Hosts ueberleben einen Claim, und was bleibt stehen, wenn er scheitert.
//
// Die Regeln selbst -- wann aktiviert werden darf, wann nicht -- pruefen die Scala-Suiten
// (`HydrationBoundarySpec`). Was hier dazukommt, ist die Wirklichkeit.

/** Laedt die serverseitig gerenderte Seite und hydriert sie mit demselben Dokument. */
async function serve(page, source = '# Titel\n\nEin Absatz.') {
  await page.goto('/form?source=' + encodeURIComponent(source))
  await page.addScriptTag({
    type: 'module',
    content:
      "import { formFixtures } from '/main.js'\n" +
      'window.form = formFixtures\n' +
      'window.ready = true\n',
  })
  await page.waitForFunction(() => window.ready === true)
}

async function hydrate(page, source = '# Titel\n\nEin Absatz.') {
  await page.evaluate(
    // Der Container haelt genau das Feld -- ein Hydrationscursor beginnt beim ersten Kind,
    // und ein Label davor waere fuer ihn ein unerwartetes Element.
    (text) => window.form.hydrate(document.getElementById('editor-host'), text),
    source
  )
}

const field = 'textarea[name="body"]'

test.describe('Hydration im echten Browser', () => {
  test.afterEach(async ({ page }) => {
    await page.evaluate(() => window.form?.dispose()).catch(() => {})
  })

  // -------------------------------------------------------------------------------------
  // Capture vor dem ersten Claim (§17.2)
  // -------------------------------------------------------------------------------------

  test('erfasst den getippten Wert, nicht das Attribut', async ({ page }) => {
    // §17.2: "Attribute oder `defaultValue` reichen dafuer nicht." Wer vor dem Skript getippt
    // hat, hat `value` geaendert -- das Attribut traegt noch, was der Server schickte.
    await serve(page)
    await page.locator(field).fill('Vor dem Skript getippt')
    await hydrate(page)

    expect(await page.evaluate(() => window.form.capturedSource)).toBe('Vor dem Skript getippt')
  })

  test('erfasst Fokus und Auswahl mitsamt Richtung', async ({ page }) => {
    // P20 nennt `SelectionDirection` ausdruecklich. Ohne sie waere jede wiederhergestellte
    // Auswahl vorwaerts gerichtet, und der naechste Pfeiltastendruck bewegte das falsche Ende.
    await serve(page)
    await page.locator(field).focus()
    await page.evaluate(() => {
      const area = document.querySelector('textarea[name="body"]')
      area.setSelectionRange(2, 5, 'backward')
    })
    await hydrate(page)

    expect(await page.evaluate(() => window.form.capturedFocus)).toBe(true)
    expect(await page.evaluate(() => window.form.capturedSelection)).toBe('2:5:Backward')
  })

  test('erfasst keinen Fokus, wenn keiner da war', async ({ page }) => {
    await serve(page)
    await hydrate(page)

    expect(await page.evaluate(() => window.form.capturedFocus)).toBe(false)
    // §17.7: "Ansonsten keine Fokus-/Selection-Schreibaktion." Ein Editor, der sich beim Laden
    // selbst fokussiert, nimmt den Fokus dort weg, wo der Benutzer gerade war.
    expect(await page.evaluate(() => window.form.mayRestoreSelection)).toBe(false)
  })

  // -------------------------------------------------------------------------------------
  // Der gelungene Claim
  // -------------------------------------------------------------------------------------

  test('behaelt die Host-Identitaet eines gueltigen Teilbaums', async ({ page }) => {
    // §17s Abnahme: "ein gueltiger Rich-Subtree behaelt Host-Identitaet". Der Sinn von
    // Hydration -- waeren die Knoten neu, koennte man auch neu rendern.
    await serve(page)
    await page.evaluate(() => {
      document.querySelector('[data-editor-preview] h1').setAttribute('data-marker', 'vorher')
    })

    await hydrate(page)

    await expect(page.locator('[data-editor-preview] h1')).toHaveAttribute('data-marker', 'vorher')
    expect(await page.evaluate(() => window.form.claimSucceeded)).toBe(true)
  })

  test('aktiviert erst nach erfolgreichem Abschluss', async ({ page }) => {
    await serve(page)
    await hydrate(page)

    expect(await page.evaluate(() => window.form.activationState)).toBe('active')
  })

  test('verbirgt die Textarea erst bei der Aktivierung', async ({ page }) => {
    // §16/§17: ohne JavaScript sichtbar, verborgen erst nach erfolgreicher Aktivierung.
    await serve(page)
    await expect(page.locator(field)).toBeVisible()

    await hydrate(page)
    await page.evaluate(() => window.form.activate())

    await expect(page.locator(field)).toBeHidden()
    await expect(page.locator(field)).toBeEnabled()
  })

  test('ist idempotent', async ({ page }) => {
    // §17s Abweichungstabelle: "Wiederholtes Enhancement: Idempotent; genau ein Controller,
    // keine doppelten Handler oder Observer."
    await serve(page)
    await hydrate(page)

    await page.evaluate(() => {
      window.form.activate()
      window.form.activate()
      window.form.activate()
    })

    expect(await page.evaluate(() => window.form.activationCount)).toBe(3)
    await expect(page.locator(field)).toHaveCount(1)
    await expect(page.locator('[data-editor-preview]')).toHaveCount(1)
  })

  // -------------------------------------------------------------------------------------
  // Der fehlgeschlagene Claim (§17.3, §17.5)
  // -------------------------------------------------------------------------------------

  test('faengt einen abweichenden Text ab', async ({ page }) => {
    // §17.5: "Ein zusaetzlicher Editor-Check validiert IDs, Textinhalt und semantisch relevante
    // Attribute […]. JFX-Strict allein beweist dies heute nicht." Genau das hier: die Struktur
    // stimmt, der Text nicht.
    await serve(page)
    await page.evaluate(() => {
      document.querySelector('[data-editor-preview] h1 span').textContent = 'Etwas anderes'
    })

    await hydrate(page)

    expect(await page.evaluate(() => window.form.claimSucceeded)).toBe(false)
    expect(await page.evaluate(() => window.form.hydrationFailure)).toContain('Text')
  })

  test('faengt eine abweichende Knoten-ID ab', async ({ page }) => {
    await serve(page)
    await page.evaluate(() => {
      document.querySelector('[data-editor-preview] h1').setAttribute('data-ember-node', 'fremd')
    })

    await hydrate(page)

    expect(await page.evaluate(() => window.form.claimSucceeded)).toBe(false)
    expect(await page.evaluate(() => window.form.hydrationFailure)).toContain('data-ember-node')
  })

  test('faengt ein abweichendes Tag ab', async ({ page }) => {
    await serve(page)
    await page.evaluate(() => {
      const heading = document.querySelector('[data-editor-preview] h1')
      const replacement = document.createElement('h2')
      for (const attribute of heading.attributes)
        replacement.setAttribute(attribute.name, attribute.value)
      replacement.innerHTML = heading.innerHTML
      heading.replaceWith(replacement)
    })

    await hydrate(page)

    expect(await page.evaluate(() => window.form.claimSucceeded)).toBe(false)
  })

  test('laesst den Fallback stehen, wenn der Claim scheitert', async ({ page }) => {
    // §17.3: "Der Fallback liegt ausserhalb der austauschbaren Rich-View-Boundary und bleibt bei
    // deren Fehler erhalten." Der Grund, warum die Textarea nicht in der Boundary liegt: ein
    // fehlgeschlagener Claim nimmt den Teilbaum mit, in dem er stattfand.
    await serve(page)
    await page.locator(field).fill('Das darf nicht verschwinden')
    await page.evaluate(() => {
      document.querySelector('[data-editor-preview] h1 span').textContent = 'Kaputt'
    })

    await hydrate(page)

    await expect(page.locator(field)).toHaveCount(1)
    await expect(page.locator(field)).toHaveValue('Das darf nicht verschwinden')
    await expect(page.locator(field)).toBeEnabled()
  })

  test('baut nur die Boundary neu auf, und genau einmal', async ({ page }) => {
    // §17: "Lokale fehlgeschlagene Komponenten disposen; Boundary via JFX aus gueltigem State
    // neu mounten." Ein Neuaufbau, keine Schleife -- und eine Vorschau, keine zwei.
    await serve(page)
    await page.evaluate(() => {
      document.querySelector('[data-editor-preview] h1 span').textContent = 'Kaputt'
    })

    await hydrate(page)

    await expect(page.locator('[data-editor-preview]')).toHaveCount(1)
    await expect(page.locator('[data-editor-preview] h1')).toHaveText('Titel')
  })

  test('aktiviert nach einem gescheiterten Claim nicht', async ({ page }) => {
    // §17s Abnahme: "Editierbarkeit erst nach erfolgreichem Abschluss."
    await serve(page)
    await page.evaluate(() => {
      document.querySelector('[data-editor-preview] h1 span').textContent = 'Kaputt'
    })

    await hydrate(page)

    expect(await page.evaluate(() => window.form.activationState)).toMatch(/^failed:/)
    await page.evaluate(() => window.form.activate())
    expect(await page.evaluate(() => window.form.activationCount)).toBe(0)
  })

  // -------------------------------------------------------------------------------------
  // Vor-Hydration-Nutzereingabe (§17.4)
  // -------------------------------------------------------------------------------------

  test('schiebt die Aktivierung auf, wenn der Quelltext geaendert wurde', async ({ page }) => {
    // §17.4: "Falls Source seit SSR geaendert wurde, bleibt sie zunaechst unangetastet."
    await serve(page)
    await page.locator(field).fill('# Selbst getippt')
    await page.locator(field).blur()

    await hydrate(page)

    expect(await page.evaluate(() => window.form.activationState)).toBe(
      'deferred:UnimportedSource'
    )
  })

  test('uebernimmt ihn auf Anforderung und aktiviert dann', async ({ page }) => {
    await serve(page)
    await page.locator(field).fill('# Selbst getippt')
    await page.locator(field).blur()

    await hydrate(page)
    const problem = await page.evaluate(() => window.form.importCapturedSource())

    expect(problem).toBe('')
    expect(await page.evaluate(() => window.form.activationState)).toBe('active')
    await expect(page.locator('[data-editor-preview] h1')).toHaveText('Selbst getippt')
  })

  test('schiebt auf, solange das Feld fokussiert ist', async ({ page }) => {
    // §17.2: eine vor dem Attach begonnene Composition laesst sich nicht nachtraeglich
    // abfragen. Fokus ist der einzige Ort, an dem eine laufen koennte -- also wird konservativ
    // gewartet, statt "keine" zu raten.
    await serve(page)
    await page.locator(field).focus()

    await hydrate(page)

    expect(await page.evaluate(() => window.form.activationState)).toBe('deferred:InputSession')
  })
})

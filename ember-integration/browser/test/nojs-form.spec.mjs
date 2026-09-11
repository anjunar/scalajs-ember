import { test, expect } from '@playwright/test'

// Das Feld ohne JavaScript (P19b, §16).
//
// Was diese Suite prueft, kann ein SsrCursor grundsaetzlich nicht: ob ein echter Browser die
// Textarea absendet, ob der Wert den HTML-Parser unveraendert ueberlebt und ob ein fuehrender
// Zeilenumbruch die Parserregel fuer `textarea` uebersteht. §16 verlangt genau das:
// "Die Textarea ist ohne JavaScript sichtbar, benannt, fokussierbar und normal submitbar."
//
// JavaScript ist hier ausgeschaltet. Die Seite kommt serverseitig gerendert -- durch denselben
// EditorFieldView, den der Browserpfad benutzt --, und der Testserver stellt Action und
// Persistenz, weil §16 beides der Anwendung ueberlaesst.
test.use({ javaScriptEnabled: false })

const field = 'textarea[name="body"]'

test.describe('Das Formularfeld ohne JavaScript', () => {
  test('ist sichtbar, benannt und beschriftet', async ({ page }) => {
    await page.goto('/form')

    const textarea = page.locator(field)
    await expect(textarea).toBeVisible()
    await expect(textarea).toHaveAttribute('aria-label', 'Inhalt')
  })

  test('ist nicht disabled', async ({ page }) => {
    // §16: "genau ein *erfolgreiches* benanntes Formularfeld". Ein disabled Control ist nicht
    // erfolgreich -- das Formular saendete fuer diesen Namen gar nichts.
    await page.goto('/form')

    await expect(page.locator(field)).toBeEnabled()
  })

  test('ist fokussierbar', async ({ page }) => {
    await page.goto('/form')
    await page.locator(field).focus()

    await expect(page.locator(field)).toBeFocused()
  })

  test('traegt den Quelltext des Dokuments', async ({ page }) => {
    await page.goto('/form?source=' + encodeURIComponent('# Titel\n\nEin Absatz.'))

    await expect(page.locator(field)).toHaveValue(/# Titel/)
    await expect(page.locator(field)).toHaveValue(/Ein Absatz\./)
  })

  test('sendet genau ein benanntes Feld', async ({ page }) => {
    // Der Editor-Host darf keinen konkurrierenden Formularnamen haben (§16). Zwei Werte fuer
    // einen Namen waeren fuer den Server nicht aufloesbar.
    await page.goto('/form')
    await page.locator('#save').click()

    await expect(page.locator('#count')).toHaveText('1')
    await expect(page.locator('[data-field="body"]')).toHaveCount(1)
  })

  test('sendet, was in der Textarea steht', async ({ page }) => {
    await page.goto('/form')
    await page.locator(field).fill('# Vom Benutzer\n\nGetippt.')
    await page.locator('#save').click()

    const received = page.locator('[data-field="body"] pre')
    await expect(received).toContainText('# Vom Benutzer')
    await expect(received).toContainText('Getippt.')
  })

  test('ueberlebt Zeichen, die HTML zerlegen wuerden', async ({ page }) => {
    // §16 nennt sie ausdruecklich: "sicheres Escaping insbesondere von `&` und `</textarea>`".
    // Unmaskiert beendete das zweite die Textarea mitten im Wert, und der Rest waere Markup.
    const tricky = 'Ein & Zeichen, ein </textarea> und ein Emoji \u{1F680}'

    await page.goto('/form')
    await page.locator(field).fill(tricky)
    await page.locator('#save').click()

    await expect(page.locator('[data-field="body"] pre')).toHaveText(tricky)
  })

  test('ueberlebt sie auch serverseitig gerendert', async ({ page }) => {
    // Derselbe Text, aber vom Server in die Textarea geschrieben statt vom Benutzer getippt.
    // Das ist die Richtung, in der das Escaping zaehlt.
    const tricky = 'Ein & Zeichen und ein </textarea>'

    await page.goto('/form?source=' + encodeURIComponent(tricky))
    await page.locator('#save').click()

    await expect(page.locator('[data-field="body"] pre')).toContainText('</textarea>')
    await expect(page.locator('[data-field="body"] pre')).toContainText('&')
  })

  test('sendet einen fuehrenden Zeilenumbruch mit', async ({ page }) => {
    // §16 verlangt "Erhalt fuehrender Zeilenumbrueche trotz HTML-Parserregel". Serverseitig ist
    // dieser Fall hier nicht erreichbar: ein Markdown-Dokument *beginnt* nicht mit Leerzeilen,
    // der Parser wirft sie weg. Die Serialisierungsseite des Vertrags gehoert zu P19a und wird
    // im Nachbar-Repo geprueft.
    //
    // Erreichbar und pruefenswert ist die andere Richtung: was der Benutzer tippt, kommt beim
    // Server an -- fuehrender Umbruch eingeschlossen.
    await page.goto('/form')
    await page.locator(field).fill('\nMit fuehrendem Umbruch')
    await page.locator('#save').click()

    const received = await page.locator('[data-field="body"] pre').textContent()
    expect(received.startsWith('\n') || received.startsWith('\r\n')).toBe(true)
    expect(received).toContain('Mit fuehrendem Umbruch')
  })

  test('setzt auf den Serverwert zurueck', async ({ page }) => {
    // Natives `reset`, ohne JavaScript. §16 fuehrt Reset als ausdrueckliche Formaktion.
    await page.goto('/form?source=' + encodeURIComponent('Ursprung'))

    await page.locator(field).fill('Geaendert')
    await page.locator('#revert').click()

    await expect(page.locator(field)).toHaveValue(/Ursprung/)
  })

  test('zeigt die Vorschau als semantisches HTML', async ({ page }) => {
    // §16: SSR rendert semantisches HTML. Ohne JavaScript ist das alles, was ein Leser sieht.
    await page.goto('/form?source=' + encodeURIComponent('# Titel\n\nEin Absatz.'))

    await expect(page.locator('[data-editor-preview] h1')).toHaveText('Titel')
    await expect(page.locator('[data-editor-preview] p')).toHaveText('Ein Absatz.')
  })

  test('gibt der Vorschau keinen Formularnamen', async ({ page }) => {
    await page.goto('/form')

    await expect(page.locator('[data-editor-preview] [name]')).toHaveCount(0)
  })
})

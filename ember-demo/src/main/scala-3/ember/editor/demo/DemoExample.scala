package ember.editor.demo

final case class DemoExample(
    id: String,
    label: String,
    eyebrow: String,
    title: String,
    description: String,
    markdown: String
)

object DemoExample:
  val all = Vector(
    DemoExample(
      "article",
      "Artikel schreiben",
      "DER EDITOR",
      "Raum für gute Gedanken.",
      "Schreiben, strukturieren und gestalten. Probiere Ember an einem vollständigen Artikel aus.",
      """# Weniger Ablenkung. Mehr Ideen.
        |
        |Gute Texte brauchen einen klaren Gedanken und einen Ort, an dem er wachsen kann.
        |Dieser Artikel gehört dir: Markiere einen Satz, formatiere ihn oder schreibe einfach weiter.
        |
        |## Ein guter Anfang
        |
        |Fang mit dem an, was dir **wichtig** ist. Die erste Fassung darf *unfertig* sein.
        |Für die Struktur sorgen Überschriften, Absätze und Listen.
        |
        |> Schreiben heißt, die eigenen Gedanken besser kennenzulernen.
        |
        |### Drei kleine Schritte
        |
        |1. Sammle eine Idee, die du teilen möchtest.
        |2. Gib ihr eine verständliche Struktur.
        |3. Lies den Text noch einmal aus der Sicht deiner Leser.
        |
        |Mehr über das Projekt findest du auf [GitHub](https://github.com/anjunar/scalajs-ember).
        |""".stripMargin
    ),
    DemoExample(
      "notes",
      "Notizen & Listen",
      "STRUKTURIERTER TEXT",
      "Aus Gedanken wird ein Plan.",
      "Verschachtelte Listen, Zitate und schnelle Notizen – mit eigener Undo-Historie für dieses Beispiel.",
      """# Ein kleines Team, eine große Idee
        |
        |## Unsere nächste Woche
        |
        |- Montag: Ideen sammeln
        |  - Rückmeldungen aus dem Team lesen
        |  - Die wichtigsten Fragen aufschreiben
        |- Mittwoch: Einen ersten Entwurf teilen
        |- Freitag: Gemeinsam ausprobieren
        |
        |## Was wir mitnehmen
        |
        |> Lieber einen kleinen Schritt ausprobieren als den perfekten Plan aufschieben.
        |
        |**Tipp:** In einer Liste rückt Tab den Eintrag ein, Shift+Tab rückt ihn aus.
        |Mit Escape und anschließend Tab verlässt du den Editor.
        |""".stripMargin
    ),
    DemoExample(
      "technical",
      "Code & Medien",
      "MEHR ALS FLIESSTEXT",
      "Ideen brauchen Beispiele.",
      "Codeblöcke, Links und Bilder in einem Dokument. Bildadressen und Beschreibungen bearbeitest du im Viewport-Fenster.",
      """# Hallo, Ember.
        |
        |Ein Editor besteht aus mehr als einer Textfläche. Hier treffen **Inhalt**, Struktur und Interaktion aufeinander.
        |
        |## Ein kleines Beispiel
        |
        |```scala
        |val message = "Hallo, Welt!"
        |println(message)
        |```
        |
        |Mit `Strg+Z` gehst du einen Schritt zurück. Zusammenhängende Eingaben bleiben dabei zusammen.
        |
        |## Ein Bild sagt mehr
        |
        |![Abstrakte Landschaft in warmen Abendfarben](./landscape.svg "Abendlicht")
        |
        |Das Bild liegt hier im Repo. Über **Bild** kannst du die Adresse, den Alt-Text und die Breite ändern.
        |
        |---
        |
        |Weiterlesen: [Das Ember-Projekt](https://github.com/anjunar/scalajs-ember).
        |""".stripMargin
    ),
    DemoExample(
      "blank",
      "Leeres Dokument",
      "DEIN SPIELRAUM",
      "Was möchtest du schreiben?",
      "Eine freie Seite für eigene Inhalte. Deine Änderungen bleiben beim Wechsel zwischen den Beispielen erhalten.",
      "\n"
    )
  )

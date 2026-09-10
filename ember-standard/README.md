# scalajs-ember-standard

Die Standardadapter des Ember-Editors: der Ort, an dem Knotenarten und Renderer einander
kennen. Einzeln wählbar, nicht als Sammelregistrierung.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§6, 15.1, 16.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-standard` |
| Scala-Paket | `ember.editor.standard` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-html`, `scalajs-ember-jfx` |

## Stand

P09 abgeschlossen. Vorhanden: `ParagraphSupport` mit Wurzel, Absatz und Textlauf. Heading,
Quote, Listen, Links, Code und Bilder folgen mit P12–P16, jeweils als eigenes `*Support`.

## Warum das ein eigenes Modul ist

§6: „`standard` ist bewusst ein optionales Integrationsmodul: Dadurch kennen die Node-Module
weder Markdown noch JFX und die Format-SPIs keine konkreten Feature-Nodes."

Hier laufen beide Seiten zusammen — und nur hier. `ember-rich-text` weiß nichts von HTML,
`ember-html` nichts von Absätzen. Eine Anwendung, die ihr Dokument nur als JSON verarbeitet,
linkt dieses Modul nie mit.

§6 nennt auch das Risiko: „Eager Sammelregistrierungen halten optionale Module fest." Deshalb
ist jeder Adapter ein eigener Wert:

```scala
ParagraphSupport.root       // article
ParagraphSupport.paragraph  // p
ParagraphSupport.text       // span mit einem Textkind

ParagraphSupport.semantics  // alle drei als HtmlSupport
ParagraphSupport.views      // dieselben als ViewSupport für ember-jfx
```

`semantics` und `views` sind eine Bequemlichkeit, kein Zwang. Wer nur Absätze braucht, nimmt
die einzelnen Einträge.

## Die Entscheidungen dahinter

**`article` statt `div` für die Wurzel.** Sie ist ein in sich abgeschlossener Inhalt, und §16
verlangt für die ausgelieferte Fassung semantisches HTML: ein Leser ohne Stylesheet und ein
Screenreader sollen dasselbe Dokument vorfinden.

**`span` um jeden Textlauf, in beiden Profilen.** §15.1 verlangt den Wrapper ausdrücklich und
nennt den Grund gleich mit: er „vermeidet zusammengefasste benachbarte SSR-Textnodes und
erlaubt eine eindeutige ID→Textpunkt-Zuordnung". Zwei Läufe nebeneinander wären ohne ihn in
der Ausgabe ein einziger Textknoten.

**`data-ember-node` nur in der Editieransicht.** §19.1 lässt browserseitige Wrapper und
Editor-Attribute beim Austausch entfernen. Statt sie hinterher herauszunehmen, entstehen sie in
der Content-Fassung gar nicht erst.

## Verwendung

```scala
// Editierfläche
DocumentView.mount(session, DomCursor.root(container), ParagraphSupport.views)

// Ausgeliefertes HTML
DocumentView.renderToHtml(document, ParagraphSupport.views)
```

## Tests

```bash
sbt --server "scalajs-ember-standard/Test/testOnly *"
```

`ProjectionSpec` ist der Rendererbeweis aus P09 und läuft headless gegen einen `SsrCursor`.
Das ist keine Notlösung, sondern derselbe Weg: `DocumentView` nimmt einen beliebigen Cursor,
und dass SSR und Browser dasselbe liefern, ist damit keine Absprache zwischen zwei
Implementierungen.

Geprüft werden Index und Reihenfolge, beide Renderprofile, Instanzerhalt bei Textedit und Move,
das Verlassen des Index bei Remove, zwei Sitzungen mit denselben IDs, `onProjected` gegen
`projectedRevision` — und dass ein Textedit in einem 4001-Knoten-Dokument weniger als fünf
Komponenten anfasst (§15.1, Abnahme).

Was dort grundsätzlich nicht prüfbar ist — DOM-Identität und der Umfang der Schreibzugriffe —
steht im Harness: [ember-integration/browser](../ember-integration/browser/README.md),
`projection.spec.mjs`.

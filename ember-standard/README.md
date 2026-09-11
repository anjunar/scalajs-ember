# scalajs-ember-standard

Die Standardadapter des Ember-Editors: der Ort, an dem Knotenarten und Renderer einander
kennen. Einzeln wählbar, nicht als Sammelregistrierung.

Verbindlicher Entwurf: [UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §§6, 15.1, 16.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-standard` |
| Scala-Paket | `ember.editor.standard` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-list`, `scalajs-ember-link`, `scalajs-ember-code`, `scalajs-ember-image`, `scalajs-ember-markdown`, `scalajs-ember-json`, `scalajs-ember-html`, `scalajs-ember-ui` |

## Stand

P09 und P12 bis P18 abgeschlossen. Vorhanden: `ParagraphSupport` (Wurzel, Absatz, Textlauf),
`RichTextSupport` (Überschrift, Zitat, Umbrüche) samt `StandardMarkTags` — der Tabelle, die aus
den fünf eingebauten Marks HTML-Tags macht — `ListSupport` (`ul`/`ol`/`li` samt Startnummer),
`LinkSupport` (`a` samt der Entscheidung über `target` und `rel`), `CodeSupport` (`pre`/`code`
samt Sprachklasse) und `ImageSupport` (`img` als Void-Element).

Dazu zwei Adaptersätze, die **nicht** HTML machen und aus demselben Grund hier stehen — §6 gibt
den Node-Modulen nur den Kern, und dies ist der eine Ort, an dem beide Seiten auf dem
Klassenpfad liegen:

| | |
| --- | --- |
| `MarkdownRules` / `MarkdownSupports` | Syntax auf Knotenarten, P18. |
| `StandardJsonCodecs` / `StandardJsonSupport` | die Built-in-Codecs für JSON, P18. |

Beide sind **einzeln wählbar**. §6 verbietet „eager Sammelregistrierungen", und das ist keine
Formsache: läge `StandardJsonCodecs` in `ember-json`, zöge die Wahl von JSON Listen, Links,
Code und Bilder mit herein, ob die Anwendung sie hat oder nicht. Ein Test hält es nach —
`StandardJsonSupport.richText` weist ein Dokument mit Liste ab.

### Markdown: drei Arten von Regel

Ein Block wird ein Knoten, ein Inline wird ein Knoten — und ein Inline wird eine **Mark**, kein
Knoten. Die dritte ist die, die man leicht übersieht: `*a*` ist kein Knoten um einen Lauf
herum, sondern ein Lauf mit einer Eigenschaft (§8.2).

Die Policies fahren mit. `MarkdownSupports.everything(linkPolicy, mediaPolicy)` nimmt dieselben
zwei wie die Commands, und §19.1 verlangt genau das. Es gibt keinen zweiten Weg zu einem
`LinkUrl` oder `MediaUrl`, also **können** Import und Dialog nicht auseinanderlaufen.

Was Markdown nicht schreiben kann, wird gemeldet statt verschwiegen: Unterstreichung und
Durchstreichung (§18.2 zählt beide ausdrücklich nicht zur CommonMark-Garantie), Bildmaße,
MediaId. Unter `Strict` schlägt der Export fehl, unter `AllowLossy` steht es in
`EncodedMarkdown.losses`.

Ein abgewiesenes **Linkziel** lässt den Text stehen, eine abgewiesene **Bildquelle** nicht. Die
Wörter eines Satzes zu verlieren, weil seine Adresse falsch war, wäre der schlechtere Ausgang;
ein Bild ohne Quelle ist dagegen nichts.

`strong` und `em`, nicht `b` und `i`: §16 verlangt semantisches HTML, und das sagt, was gemeint
ist, statt wie es aussieht. Underline bekommt `u` — nicht weil HTML dafür eine gute Antwort
hätte, sondern weil §8.2 die Mark aufzählt.

## Warum das ein eigenes Modul ist

§6: „`standard` ist bewusst ein optionales Integrationsmodul: Dadurch kennen die Node-Module
weder Markdown noch UI und die Format-SPIs keine konkreten Feature-Nodes."

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
ParagraphSupport.views      // dieselben als ViewSupport für ember-ui
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

**`alt=""` wird geschrieben, nicht weggelassen.** §20: ein dekoratives Bild verwendet
ausdrücklich leeren Alt-Text. Das Attribut wegzulassen ließe einen Screenreader stattdessen den
Dateinamen vorlesen — der leere Alt-Text bedeutet etwas, das Fehlen bedeutet etwas anderes.

**`width` und `height`, wann immer das Dokument sie hat.** §20: absolute Werte „können
Layoutsprünge reduzieren". Ein Browser, der das Verhältnis kennt, reserviert den Platz, bevor
das Bild ankommt. Prüfen muss der Adapter dabei nichts — `PositivePixels` kann keine Null
tragen.

**Kein Abruf, kein Nachmessen.** §20: „keine externe URL wird vom Parser oder SSR-Server
automatisch abgerufen." Der Adapter macht aus einem Knoten Attribute; ob das Bild existiert,
ist die Frage des Browsers, und SSR stellt sie nie.

**Dekodieren ist so streng wie der Command.** `ImageJsonSupport.codec(policy)` nimmt dieselbe
`MediaUrlPolicy` entgegen wie `ImageExtension`. Eine Quelle aus einem Payload ist genau so
ungeprüft wie eine aus einem Dialog, und es gibt keinen zweiten Weg zu einem `MediaUrl` — die
beiden Pfade **können** nicht auseinanderlaufen.

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

`MarkdownDocumentSpec` fährt den Weg aus §18.1 am Stück: Quelltext zu Syntax zu Knoten und
zurück, ohne HTML und ohne DOM dazwischen. `StandardJsonRoundTripSpec` prüft die Built-in-
Codecs — über Round-Trips und nicht über erwartete Payloads, weil ein Round-Trip belegt, dass
Encoder und Decoder sich einig sind, und das ist die Eigenschaft, von der ein gespeichertes
Dokument abhängt.

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

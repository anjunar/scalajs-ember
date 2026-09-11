# scalajs-ember-html

Der semantische HTML-Vertrag des Ember-Editors: wie eine Knotenart aussieht, und eine
unveränderliche Fragmentdarstellung für Import und Export. Headless — kein DOM, keine
UI-Runtime.

Verbindlicher Entwurf: [UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §§15.1, 19.1.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-html` |
| Scala-Paket | `ember.editor.html` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

P09 abgeschlossen, aber nur zur Hälfte: die Semantik-SPI steht, der Importparser nicht. Was
es gibt, ist die Ausgabeseite — `HtmlSemantics`, `HtmlShape`, `HtmlAttribute`, `HtmlSupport`
und die Serialisierung `HtmlFragment.render`. `HtmlImportRule` und die Standardregeln folgen
mit P24.

## Eine Beschreibung, zwei Ausgaben

`HtmlSemantics[N]` sagt, wie genau **ein** Knoten aussieht — ohne seine Kinder:

```scala
val paragraph: HtmlSemantics[ParagraphNode] = new HtmlSemantics[ParagraphNode]:
  val nodeType: NodeType[ParagraphNode] = ParagraphNode
  def shapeOf(node: ParagraphNode, profile: RenderProfile): HtmlShape =
    HtmlShape.Element("p")
```

Dass die Beschränkung auf einen Knoten wichtig ist, ist keine Formsache. Würde eine Gestalt
ihre Kinder mitbeschreiben, wäre sie ein View-Baum, und irgendetwas müsste ihn diffen — also
genau das zweite Rendering-System, das §2 ausschließt. So beschreibt jeder Knoten sich selbst,
und wer die Kinder hält, ist die UI-Runtime.

Aus derselben Beschreibung entstehen beide Ausgaben: die SSR-Fassung und die Editierfläche im
Browser. Dass sie übereinstimmen, ist deshalb keine Absprache zwischen zwei Implementierungen,
sondern dieselbe Zeile Code — `ember-ui` leitet seine `NodeView`s mit
`ViewSupport.semantic(support)` daraus ab.

## Renderprofile

| Profil | Wofür |
| --- | --- |
| `RenderProfile.Content` | Was ein Leser bekommt und was exportiert wird |
| `RenderProfile.Editor` | Dasselbe plus Editor-Metadaten — Knoten-IDs, später `contenteditable` und ARIA |

§19.1 verlangt, dass Editor-Attribute beim Austausch verschwinden. Sie entstehen in der
Content-Fassung deshalb gar nicht erst, statt hinterher entfernt zu werden.

## Ein Textlauf ist ein Element, kein roher Text

`HtmlShape` hat zwei Fälle, und **keiner** davon ist roher Text:

```scala
case Element(tag: String, attributes: Vector[HtmlAttribute] = Vector.empty)
case TextRun(tag: String, value: String, attributes: Vector[HtmlAttribute] = Vector.empty)
```

`TextRun` ist §15.1s „stabiler Wrapper mit einem Textkind", und der Grund steht dort gleich
dabei: er „vermeidet zusammengefasste benachbarte SSR-Textnodes und erlaubt eine eindeutige
ID→Textpunkt-Zuordnung". Zwei Läufe nebeneinander wären in der Ausgabe ein einziger
Textknoten — beim Hydrieren ließe sich nicht mehr sagen, wo der eine aufhört.

`KeyedChildren` verlangt von der anderen Seite dasselbe: *„Keyed children require physical
element components."* Ein Textknoten hat keinen eigenen Host, an dem sich eine Reihenfolge
festmachen ließe.

## Attribute sind eng

`HtmlAttribute` ist ein eigener Typ statt `(String, String)`, damit die Prüfung an einer
Stelle steht. Erlaubt sind `id`, `lang`, `dir`, `href`, `title`, `alt`, `src`, `width`,
`height` und alles mit dem Präfix `data-ember-`. `onclick` und `style` sind hier keine
Attribute, sondern ein Fehler — §19.1 schließt Eventattribute und beliebige CSS-Strings als
Dokumentformat aus.

```scala
HtmlAttribute.parse("href", "/a")   // Some(...)
HtmlAttribute.parse("onclick", "x") // None
HtmlAttribute("onclick", "x")       // wirft EditorContractViolation
HtmlAttribute.editor("node", "p0")  // data-ember-node="p0"
```

Der Konstruktor der Case-Klasse ist privat. Sonst käme `copy(name = "onclick")` an der Prüfung
vorbei — und `apply` selbst liefe im Kreis, weil es das synthetische verdeckt.

## HtmlFragment lebt nicht

§19.1 ist ungewöhnlich deutlich: das ist **„kein diffbarer View-Baum"** und hat „keine
Mount-/Update-API". Ein Fragment entsteht aus etwas und wird zu etwas.

```scala
HtmlFragment.render(
  HtmlFragment.Element("p", Vector.empty, Vector(HtmlFragment.Text("Hallo")))
) // <p>Hallo</p>
```

Die Ausgabe ist bewusst kompakt und ohne Einrückung: sie wird mit der Browserausgabe
verglichen, und jedes eingefügte Leerzeichen wäre im Dokument ein Textknoten, den es dort nicht
gibt. Leere Elemente (`br`, `img`, …) mit Kindern sind ein Fehler, kein Schönheitsfehler.

## Tests

Dieses Modul hat keine eigene Suite. Seine Aussagen sind erst zusammen mit einem Renderer
prüfbar — `ProjectionSpec` in `ember-standard` fährt beide Profile, den Wrapper und die
Attributgrenze durch die echte Projektion:

```bash
sbt --server "scalajs-ember-standard/Test/testOnly *"
```

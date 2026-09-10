# scalajs-ember-rich-text

Das Rich-Text-Profil des Ember-Editors: Absätze, Editing-Semantik und Unicode-Grenzen.
Headless wie der Kern — ohne DOM, ohne JFX-Runtime.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§8, 11.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-rich-text` |
| Scala-Paket | `ember.editor.richtext` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

P06 abgeschlossen. Vorhanden: `ParagraphNode`, die vier Editing-Commands, die
Normalisierung und die Unicode-Segmentierung. Marks, Heading, Quote und Breaks folgen mit
P12.

## Verwendung

```scala
val generator = NodeIdGenerator.sequential()
val resolved  = ExtensionResolver.resolve(Vector(RichText(generator))).getOrElse(…)
val document  = RichText.emptyDocument(resolved.schema, generator).getOrElse(…)
val editor    = EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(…)

editor.update(_.setSelection(RichText.caretAtStart(editor.document)))
editor.dispatch(RichText.InsertText, "Hallo")
editor.dispatch(RichText.InsertParagraph)
editor.dispatch(RichText.DeleteBackward)
```

`RichText` trägt auch `RootNode` und `TextNode` bei, obwohl beide im Kern definiert sind.
Der Kern ist ein Modell, kein Profil — er registriert nichts von selbst. Module, die auf
rich-text aufbauen, tragen sie nicht erneut bei, sondern deklarieren `dependsOn`.

## Dokumentform

`root > paragraph* > text*`. `TextEditing` arbeitet deshalb mit „Block" als Elternknoten
eines Textlaufs statt mit `ParagraphNode` — so bleiben die Funktionen für Listen, Quotes und
Headings erweiterbar, ohne jetzt schon Fälle zu behandeln, die es noch nicht gibt.

### Normalisierung

Drei Transforms halten die Fläche editierbar:

| Regel | Warum |
| --- | --- |
| Wurzel braucht Block | Ein leergelöschtes Dokument hätte sonst keine gültige Caretposition mehr |
| Block braucht Textlauf | Text schreibt man in einen Lauf, nicht an eine Kindposition |
| Überflüssige leere Läufe weg | Beim Zusammenführen bleibt regelmäßig ein leerer übrig; unsichtbar, aber er verschiebt Kindpositionen |

Die dritte hat zwei Wächter, beide notwendig: sie greift nur, wenn der Block noch einen
**nicht leeren** Lauf hat (sonst Endlosschleife mit der zweiten Regel), und sie rührt den Lauf
nicht an, auf den die Auswahl zeigt (ein aufgeräumter Baum ist keinen verlorenen Cursor wert).

Zwei Dinge tut das Modul bewusst **nicht**: Enter teilt am Absatzanfang und -ende nicht, und
beim Zusammenführen zweier Blöcke werden die Textläufe an der Naht nicht verschmolzen. Beides
erzeugte oder beseitigte Läufe, über deren Marks dieses Modul nichts weiß — das ist §8.2s
Normalisierung und gehört zu P12.

## Unicode-Grenzen

`UnicodeTextBoundaries` implementiert die Grapheme-Cluster-Regeln **GB1–GB13** aus UAX #29 —
nicht angenähert, sondern die Regeln selbst, einschließlich der beiden kontextabhängigen:

- **GB11** hält Emoji-ZWJ-Sequenzen zusammen. Ohne sie zerfiele 👨‍👩‍👧 in fünf Teile, und ein
  Backspace löschte nur das Mädchen.
- **GB12/GB13** zählen Regional Indicators paarweise. Vier davon sind zwei Flaggen, nicht eine
  und nicht vier.

Genau daran scheitert der Codepoint-Fallback, vor dem die Risikozeile von P06 warnt.

### Was zugesichert ist, und was nicht

`unicodeVersion` lautet `"16.0.0 (Teilmenge, ohne GB9c)"`. Die Klammer ist der Punkt: der Wert
dient dem Vergleich von Fixtures, nicht als Konformitätsangabe.

Die Zeicheneigenschaften kommen aus zwei Quellen. Die **strukturellen** Klassen — CR, LF, ZWJ,
ZWNJ, Regional Indicator, Variationsselektoren, Emoji-Modifikatoren, Hangul-Jamo, Prepend —
stehen als ausdrückliche Bereiche im Quelltext; sie ändern sich zwischen Unicode-Versionen
praktisch nicht. Die **kategoriegetriebenen** — Extend aus Mn/Me, SpacingMark aus Mc, Control
aus Cc/Cf/Zl/Zp — kommen aus `Character.getType`, also aus der Zeichentabelle der
Scala.js-Standardbibliothek und damit möglicherweise aus einem älteren Stand.

Bekannte Lücken:

- **GB9c** (Indic Conjunct Break, Unicode 15.1) ist nicht implementiert. Devanagari-Cluster mit
  Virama werden an Stellen getrennt, an denen UAX #29 sie zusammenhält.
- **`Extended_Pictographic`** ist über gepflegte Bereiche angenähert, nicht aus `emoji-data.txt`
  erzeugt.
- **Wortgrenzen** sind eine dokumentierte Vereinfachung, nicht UAX #29 §4: Nicht-Wortzeichen
  überspringen, dann Wortzeichen überspringen. Apostrophe in Wörtern (`don't`), Zahlengruppen
  (`1,000`) und Schriften ohne Leerzeichen kann sie nicht.

Ein `Intl.Segmenter`-Adapter darf später danebentreten — der Vertrag lässt das ausdrücklich zu,
und `unicodeVersion` macht sichtbar, warum zwei Implementierungen bei neueren Emoji verschieden
urteilen können.

## Tests

```bash
sbt --server "scalajs-ember-rich-text/Test/testOnly *"
```

`UnicodeBoundarySpec` prüft die Fälle, an denen ein naiver Ansatz scheitert: Surrogatpaare,
Markenstapel, ZWJ-Familien, Hauttöne, Flaggenparität, CRLF, Hangul. `TextEditingSpec` fährt
komplette Bearbeitungsfolgen und validiert nach jedem Schritt unabhängig gegen den
`DocumentValidator` und den Auswahlvertrag des Kerns.

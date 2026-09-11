# scalajs-ember-rich-text

Das Rich-Text-Profil des Ember-Editors: Absätze, Editing-Semantik und Unicode-Grenzen.
Headless wie der Kern — ohne DOM, ohne UI-Runtime.

Verbindlicher Entwurf: [UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §§8, 11.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-rich-text` |
| Scala-Paket | `ember.editor.richtext` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

P06 und P12 abgeschlossen. Vorhanden: `ParagraphNode`, `HeadingNode`, `QuoteNode`, `BreakNode`
und `ThematicBreakNode`, die fünf eingebauten Marks, Bereichsformatierung, das Zustandsfeld
`TypingMarks`, die Textlauf-Normalisierung, die Editing- und Blockbefehle sowie die
UAX-29-Segmentierung. Listen folgen mit P13, Links mit P14, Code mit P15.

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

## Marks

Fünf eingebaute (§8.2): Strong, Emphasis, Underline, Strike, InlineCode. Case Objects ohne
Nutzdaten — die Identität einer Mark ist ihr Typ, und ohne Payload ist ein beliebiger CSS-String
als Dokumentformat nicht bloß unerwünscht, sondern unmöglich.

**`InlineCode` schließt die übrigen aus, und sie ihn.** §8.2 überlässt Widersprüche dem Profil;
der Grund hier ist kein Geschmack. Markdown kann in einer Code-Spanne nichts fett schreiben —
Backticks machen ihren Inhalt wörtlich —, ein Lauf mit beidem wäre also ein Dokument, das §18
nicht verlustfrei exportieren kann. Die Regel gilt in beide Richtungen; keiner gewinnt dadurch,
dass er zuletzt angewandt wurde.

Links sind **keine** Mark, sondern ein Inline-Container mit Kindern und Ziel (§8.2, P14).

### Bereichsformatierung

`RangeFormatting.toggleMark` schneidet die Läufe an beiden Enden und schreibt die Marks der
Stücke dazwischen. Ein Toggle über einen gemischten Bereich **setzt überall** — die Alternative,
jeden Lauf einzeln zu invertieren, lässt einen zweiten Druck für den Benutzer wie ein No-op
aussehen, während die Stücke stillschweigend tauschen.

Am kollabierten Caret entsteht kein Text, sondern ein Eintrag in `TypingMarks` (§11).

`RangeFormatting.activeMarks(state)` liest, was eine Toolbar als aktiv anzeigt — aus einem
`EditorState`, nicht aus einer laufenden Transaktion: eine Toolbar hat einen.

## TypingMarks

Das Zustandsfeld aus §11: `Inherit` oder ein explizites `MarkSet` **an einer gemappten
Caretposition**. Der Punkt ist keine Zierde — ohne ihn ließe sich „der Caret hat sich durch mein
Tippen bewegt" nicht von „jemand hat woanders hingeklickt" unterscheiden, und genau das verlangt
§11.

Der gemerkte Punkt wird durch die Abbildung der Transaktion geführt, bevor er mit dem Caret
verglichen wird. Darin steckt der ganze Trick: ein getipptes Zeichen bewegt den Caret von 4 nach
5 *und* bildet den Punkt von 4 auf 5 ab — beide stimmen weiter überein, die Wahl überlebt. Ein
Klick bewegt den Caret, ohne etwas abzubilden; sie ist weg.

Das Feld deklariert `HistoryRestorePolicy.Restore`. §11 verbietet ausdrücklich, die wirksamen
Marks nach einem Undo aus der Darstellung abzuleiten — ein nicht wiederhergestelltes Feld ließe
genau dieses Raten als einzige Möglichkeit.

## Textlauf-Normalisierung

§8.2: benachbarte Läufe desselben Elternknotens mit gleichen Marks wachsen zu einem maximalen
Lauf zusammen.

```text
Ausgang:              Text("Hallo Welt!", {})
"Welt" fett:          Text("Hallo ", {}), Text("Welt", {Strong}), Text("!", {})
Fett wieder entfernt: Text("Hallo Welt!", {})
```

Als **Transform**, nicht als Schritt in der Formatierung (§8.2 verlangt es so): dadurch läuft es
in derselben Transaktion wie die Ursache — der Merge kostet keinen eigenen Undo-Schritt — und
greift auch nach einem Löschvorgang, der zwei Blöcke zusammenfügt.

**Die Regel hängt am Lauf, nicht am Block.** Die naheliegende Form („für jeden Absatz über seine
Kinder laufen") würde nie ausgeführt: §3.4 hält fest, dass Vorfahren auf dem Pfad einer Änderung
keine Transform-Kandidaten sind — `ChangeSet.touchedAncestors` gibt es genau dafür. Eine
Markänderung macht den Absatz nicht dirty. Am Lauf aufgehängt wird sie gefragt, wenn eine Naht
entstehen kann, und funktioniert dadurch in jedem Container, ohne einen einzigen zu kennen.

Nicht zusammengeführt wird über Block-, Break- oder Atomgrenzen und bei verschiedenen Marks —
alle vier fallen aus einer Regel: nur direkt benachbarte `TextNode`s desselben Elternknotens mit
gleichem `MarkSet`. Nichts davon muss wissen, was ein Link oder ein Atom ist, weshalb P14 und
P16 hier nicht nachbessern müssen.

## Blocktypen

| | |
| --- | --- |
| `HeadingNode` | Ein Container plus typisiertes `HeadingLevel` — sechs Stufen, kein `Int`. Sechs Knotenarten wären sechsmal alles, und `SetHeading` wäre eine Typersetzung statt einer Feldänderung. |
| `QuoteNode` | Hält **Blöcke** (§8.2). Zitieren heißt einen Absatz in einen Container legen, nicht eine Eigenschaft an ihm setzen — deshalb bewegen `Quote`/`Unquote` Kinder. |
| `BreakNode` | `Soft` und `Hard` bleiben unterscheidbar (§8.2), damit Markdown und HTML ihre Bedeutung behalten. Ein Atom, kein `
` im Text: ein Caret kann auf beiden Seiten stehen. |
| `ThematicBreakNode` | Blockebene, deshalb ein eigener Typ und keine dritte `BreakKind`. |

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

Enter teilt am Absatzanfang und -ende weiterhin nicht — das bleibt eine bewusste Auslassung.
Die Naht beim Zusammenführen zweier Blöcke räumt seit P12 die Normalisierung auf.

## Atome im Fluss

Ein [[AtomNode]] — ein Bild etwa — steht zwischen Textläufen, und §22 verlangt, dass er per
Tastatur erreichbar und **löschbar** ist. Das war er nicht.

Der Grund liegt in einer Annahme, die für Text richtig ist: ein Caret wird zu einer Position in
einem **Textlauf** aufgelöst. Ein Atom ist keiner. Ein Backspace hinter einem Bild griff deshalb
daran vorbei und nahm das letzte Zeichen des Laufs *davor* — das Bild blieb, etwas anderes
verschwand. Gefunden beim Benutzen der Demo.

Seit P22 gilt:

| Caret | Backspace | Entfernen |
| --- | --- | --- |
| direkt hinter einem Atom | entfernt das Atom | Text wie bisher |
| direkt vor einem Atom | Text wie bisher | entfernt das Atom |
| mitten im Text | ein Graphemcluster | ein Graphemcluster |
| `NodeSelection` | entfernt die ausgewählten Knoten | dasselbe |

„Direkt hinter" hat zwei Gestalten, und beide kommen vor: die Kindgrenze hinter dem Atom (ein
Klick) und der Anfang des Laufs dahinter (Pfeilnavigation, die immer in Text landet). Eine Regel,
die nur eine davon kennte, funktionierte in der Hälfte der Fälle.

### Ein Bereich, der ein Atom umschliesst

Was ein Klick auf ein Bild erzeugt: der Browser waehlt es aus, und der Port bildet das auf einen
Bereich von der Grenze davor bis zur Grenze dahinter ab. Auch das ging schief -- und zwar aus
einem Grund, der nichts mit Atomen zu tun hatte.

`deleteAcross` entfernte **alle** Geschwister hinter dem Startknoten und alle vor dem Endknoten.
Innerhalb *eines* Blocks heisst das: alles bis zum Blockende. Ein ausgewaehltes Bild nahm den
ganzen Lauf dahinter mit, und eine Auswahl von einem markierten Lauf in den naechsten loeschte
Text weit hinter ihrem Ende.

Liegen beide Enden im selben Block, faellt seither nur weg, was **dazwischen** liegt.

### Nicht während einer Composition

Seit P23 lässt die Regel ihre Merges liegen, solange eine geschützte Texteingabe läuft (§8.2,
§15.3). Ein Merge ersetzt den inneren Textknoten eines Laufs, und ein Browser, der gerade
hineinkomponiert, verliert damit die Eingabe — ohne Ereignis und ohne Weg zurück.

Erkannt wird das an einem Tag in `TransactionMeta`, der im **Kern** benannt ist: `ember-browser`
setzt ihn, dieses Modul liest ihn, und die beiden kennen einander nicht. Die Naht wird
geschlossen, wenn die Sitzung endet — dieselbe Regel, auf einem Zustand, in den niemand tippt.

### Die Naht danach

Wird ein Knoten **zwischen** zwei Läufen entfernt, ändert sich keiner der beiden — also ist auch
keiner ein Transform-Kandidat, und die Normalisierung aus §8.2 wird nie gefragt. Das Dokument
behielt zwei benachbarte Läufe mit gleichen Marks, wo einer hingehört (`"Hallo " | " Welt"` statt
`"Hallo  Welt"`).

Die Regel selbst hängt mit gutem Grund am Lauf und nicht am Block (siehe
[Textlauf-Normalisierung](#textlauf-normalisierung)). Ihre Prämisse — eine Naht entsteht nur,
wenn einem Lauf etwas zustößt — stimmt für Textänderungen und nicht für diesen Fall. Deshalb
schließt der Aufrufer, was er aufgerissen hat; die **Entscheidung** bleibt bei der Regel
(`TextRunNormalization.mergeable`).

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

Aus P12 kommen vier weitere: `TextRunNormalizationSpec` (der in der Architektur ausgeschriebene
Fall samt Gegenfällen), `RangeFormattingSpec`, `TypingMarksSpec` und `RichTextStructureSpec`.

**Keine Undo/Redo-Tests hier.** §6 stellt `history` neben das Profil, nicht darunter; dieses
Modul kann es nicht linken. Geprüft wird stattdessen, was von hier aus prüfbar ist: dass der
geschnittene und der zusammengeführte Zustand über dieselben Operationen erreichbar sind und
keiner eine Sackgasse ist — und dass `TypingMarks` die Wiederherstellung deklariert, auf die
`ember-history` reagiert.

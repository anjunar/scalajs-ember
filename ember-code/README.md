# scalajs-ember-code

Codeblöcke mit typisierten Sprachmetadaten — unabhängig von Highlighting. Headless und optional.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§8, 18.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-code` |
| Scala-Paket | `ember.editor.code` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text` |

## Stand

P15 abgeschlossen. Vorhanden: `CodeBlockNode`, `CodeInfo`/`CodeLanguage`, vier Befehle, die
Enter-Behandlung und zwei Normalisierungsregeln.

## Verwendung

```scala
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), CodeExtension(generator)))
  .getOrElse(…)

session.dispatch(CodeCommands.ToggleCodeBlock, CodeInfo.of("scala"))
session.dispatch(CodeCommands.SetCodeInfo, CodeInfo.parse("scala {highlight=3-5}"))
session.dispatch(CodeCommands.IndentLine)
session.dispatch(CodeCommands.OutdentLine)
```

## Kein Highlighter

P15s Abnahme ist ausdrücklich: „Kein Syntax-Highlighter und kein CodeMirror als
Produktionsabhängigkeit." Die Sprache ist ein **Metadatum** — sie sagt, *was* der Text ist, nicht
wie er aussieht.

Die Risikozeile sagt, warum das so bleiben muss: „Sichtbares Highlighting darf später keine
persistente Mark-Zerlegung jeder Codezeile erzwingen." Ein Dokument, in dem jedes Token ein
markierter Lauf wäre, ließe sich weder als Dokument lesen noch als Fence schreiben. Was dieses
Modul garantiert, ist genau das, was ein Highlighter braucht und was Markdown-Fences brauchen:
**der Inhalt, wörtlich, einschließlich seiner Leerzeilen.** Ein späteres
`ember-code-highlighting` (§6) färbt in einer Ansicht ein, ohne das Dokument anzufassen.

## Das Inhaltsmodell

§8.2: „CodeBlock erlaubt Text mit Zeilenumbrüchen, aber keine beliebigen Rich-Text-Kinder."
Genau ein `TextNode`, dessen Text Zeilenumbrüche enthalten darf, ohne Marks.

Der eine Lauf ist kein Implementierungszufall. Der Inhalt eines Codeblocks ist **ein String** —
das ist, was ein Fence schreibt, was ein Compiler liest und was ein Autor kopiert. Mehrere Läufe
bräuchten eine eigene Merge-Regel und ließen eine Tür für Marks offen, die dieser Knotentyp
zuhalten soll.

### Zwei Regeln, nicht eine

| Regel | Hängt an | Wofür |
| --- | --- | --- |
| `contentIsOneRun` | `CodeBlockNode` | mehrere Kinder werden ein Lauf; ein hineinbewegter Absatz verliert seine Hülle und behält seinen Text; ein leerer Block bekommt einen Lauf |
| `runInCodeIsPlain` | `TextNode` | ein Lauf in einem Codeblock trägt keine Marks |

Die Trennung ist nicht kosmetisch. Eine Markänderung berührt den **Lauf**, nicht den Block, und
§3.4 hält fest, dass ein Vorfahr auf dem Pfad einer Änderung kein Transform-Kandidat ist —
`ChangeSet.touchedAncestors` gibt es genau dafür. Eine Regel am Block würde nie gefragt.

Das ist inzwischen die dritte Erscheinung derselben Form, nach dem Textlauf-Merge in P12 und den
benachbarten Listen in P13: **die Regel gehört an den Knoten, der sich ändert, nicht an den, dem
er gehört.**

### Warum repariert statt abgelehnt

Inhalt, der in einen Codeblock wandert, soll Code **werden**. Ablehnen ließe die ganze
Transaktion an einem Paste scheitern, Wegwerfen verlöre Text. Den Text nehmen und die Struktur
zurücklassen ist, was der Autor mit dem Hineinziehen meinte.

Läufe werden **ohne** Trenner zusammengezogen — zwei nebeneinander waren eine Zeile —, ganze
Blöcke mit einem Umbruch: zwei Absätze, die hineinwandern, waren zwei Zeilen.

## Info-String

§18.2 verlangt „Info-/Sprachmetadaten typisiert behandeln". Markdown schreibt einen Info-String;
sein **erstes Wort** ist die Sprache, der Rest ist, was die Werkzeugkette des Autors wollte:

```text
```scala {highlight=3-5}
     ^^^^^ Sprache   ^^^^^^^^^^^^^^ Meta
```

Beides getrennt zu halten erlaubt `CodeSupport`, `language-scala` zu schreiben, ohne einen Parser
zu erfinden, und P18, den Info-String unverändert zurückzuschreiben. Zusammen als ein String
zwänge jeden Konsumenten, ihn erneut zu zerlegen — jeder ein bisschen anders.

`CodeLanguage` weist zurück, was ein Fence nicht schreiben kann: Whitespace, Steuerzeichen und
Backticks. Ein Info-String, der keine gültige Sprache nennt, ist **kein Fehler** — er ist ein
Info-String ohne Sprache, und sein Text bleibt als Meta erhalten, damit ein Roundtrip ihn nicht
verliert.

## Enter

Innerhalb eines Codeblocks fügt Enter einen Umbruch ein. Den Block zu teilen machte aus einem
Listing zwei, und §8.2 lässt den Inhalt gerade deshalb Umbrüche tragen.

**Außer wenn es „lass mich raus" heißt.** Enter auf einer leeren letzten Zeile verlässt den
Block. Diese Konvention gibt es in jedem Editor mit Codeblöcken, aus einem guten Grund: ein
Codeblock hat keine Kante, über die ein Caret treten könnte — ohne sie gäbe es keinen Weg
hinaus. Der Umbruch, der dorthin geführt hat, wird dabei entfernt; er war die Bitte zu gehen,
nicht Teil des Codes.

Außerhalb gibt der Handler `Pass` zurück, und der Rich-Text-Handler teilt den Block wie sonst
(§12).

## Ein- und Ausrücken

Optional, wie der Plan es führt. Eine Einheit sind **zwei Leerzeichen**, kein Tab: ein Tab
rendert in der Breite, die der Betrachter des Lesers wählt, und das ist das eine, was
Code-Einrückung nicht tun darf.

Ausrücken entfernt so viel, wie da ist — eine um drei Leerzeichen eingerückte Zeile verliert
zwei, nicht drei. Alles zu entfernen machte den Befehl unfähig, einen einzelnen Druck seines
Gegenstücks zurückzunehmen.

## Umwandeln

Ein Absatz wird zum Codeblock über `Replace`: die ID und die Kinder bleiben (§10), also überlebt
jeder Punkt im Block.

Zurück wird **eine Zeile ein Absatz.** Ein `\n` in einem Absatzlauf wäre ein Dokument, das kein
Renderer richtig zeigt: HTML macht daraus ein Leerzeichen, und der Inhalt änderte still seine
Bedeutung. Hard Breaks wären vertretbar — aber Codezeilen sind Zeilen, und wer ein Listing
zurückverwandelt, erwartet Absätze.

## Tests

```bash
sbt --server "scalajs-ember-code/Test/testOnly *"
```

`CodeSpec` prüft den Info-String, das Inhaltsmodell samt seiner Reparaturen, Enter und Exit,
Ein-/Ausrücken und — als eigene Zusicherung — dass nach jeder Folge von Befehlen genau ein
unmarkierter Lauf übrig bleibt.

Das `pre`/`code`-Rendering steht in
`ember-standard/…/CodeProjectionSpec.scala`, dort auch der Nachweis, dass ein Typwechsel eine
View-Ersetzung ist und ein bloßes Attribut keine.

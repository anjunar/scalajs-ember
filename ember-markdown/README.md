# scalajs-ember-markdown

Ein CommonMark-Blockparser in Scala. Kein DOM, kein HTML als Zwischenstufe, keine
JavaScript-Abhängigkeit.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §18.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-markdown` |
| Scala-Paket | `ember.editor.markdown` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

**P17 abgeschlossen — Blöcke, nicht Inlines.**

Vorhanden: `MarkdownBlock` samt Syntaxbaum, `Markdown.parseSyntax`, `SourceMap`,
`MarkdownProfile` und `ParseLimits`. Der Parser deckt die Blockstruktur von CommonMark
**0.31.2** ab: Absätze, ATX- und Setext-Überschriften, Zitate, Listen mit Eng/Weit-Unterschied,
gezäunter und eingerückter Code, Trenner und HTML-Blöcke.

**Nicht vorhanden: die Inline-Ebene.** Emphasis, Links, Code-Spans, Entities und
Backslash-Escapes sind nicht aufgelöst — sie stehen unverändert im `source`-Feld des Absatzes
oder der Überschrift, die sie enthält. Link-Referenzdefinitionen ebenso, weil ihre Auflösung
den Inline-Parser braucht. Das ist P18.

Das Profil sagt es selbst:

```scala
MarkdownProfile.commonMarkSafe.conformance  // Conformance.BlocksOnly
```

§18.1 verlangt genau das: „Bis die Konformitätsfälle vollständig bestanden sind, wird nur die
tatsächlich getestete Teilmenge beworben." Ein Kommentar wäre ein Versprechen; ein Feld ist ein
Wert, den eine Anwendung lesen kann.

## Verwendung

```scala
Markdown.parseSyntax(quelltext, MarkdownProfile.commonMarkSafe) match
  case Right(ergebnis) =>
    ergebnis.document.children.foreach {
      case MarkdownBlock.Heading(_, span, level, _, source) => …
      case MarkdownBlock.CodeBlock(_, _, literal, fence)    => …
      case _                                                => …
    }
  case Left(fehler) => zeige(fehler.render)
```

`MarkdownProfile.untrustedPaste` sind dieselben Regeln unter Paste-Grenzen — für einen
Quelltext, den der Benutzer nicht selbst geschrieben hat.

## Ein Syntaxbaum, kein Dokument

§18.1: „Die Syntax-AST ist immutable und nur ein Import-/Exportwert, kein zweiter dauerhaft
synchron gehaltener Editorzustand."

Nichts hier weiß, was ein `ParagraphNode` ist, und nichts hier lässt sich bearbeiten. Genau
deshalb hängt das Modul allein am Kern: ein Parser, der direkt `ParagraphNode` erzeugte, müsste
das Rich-Text-Profil kennen, und eine Anwendung mit eigenen Blocktypen könnte ihn nicht
verwenden. Die Regeln, die Syntax auf registrierte NodeTypes abbilden, liegen im
Integrationsmodul (§6) und kommen mit P18.

**Inline-Inhalt heißt `source`, nicht `text`.** Das ist kein Stil: ein Feld namens `text` lädt
dazu ein, es anzuzeigen, und unverarbeitetes Markdown als Text anzuzeigen ist genau der Fehler,
den die Benennung verhindert.

## Herkunft und Lizenz

`BlockParser.scala` ist eine **Portierung der Regelstruktur** von `lib/blocks.js` aus
commonmark.js 0.31.2 — BSD-2-Clause, Copyright (c) 2014 John MacFarlane. Der vollständige
Lizenztext steht in [NOTICE](NOTICE).

Übernommen wurde die Form: die Reihenfolge der Blockanfänge, die Fortsetzungsbedingung jedes
Containers, die Lazy-Continuation-Regel und die Padding-Arithmetik der Listenmarker. Das sind
die Teile, die P17s Risikozeile „echte Parserarbeit" nennt, und sie aus der Spezifikation neu
herzuleiten hätte einen schlechteren Parser und dieselben Regeln ergeben.

Nicht übernommen wurde der Code. Die Unterschiede sind Absicht:

| | |
| --- | --- |
| **Offsets statt Zeile/Spalte** | commonmark.js meldet `sourcepos` als Zeile/Spalte mit tab-expandierten Spalten. §18.2 will UTF-16-Bereiche, und alles andere in diesem Editor zählt so (§11). Nachträglich umzurechnen ist die Stelle, an der sich ein Off-by-one versteckt. |
| **Ein Budget** | §18.2 begrenzt Arbeitsschritte; die Vorlage kennt so etwas nicht. |
| **Kein Inline-Parser** | Wo commonmark.js `processInlines` ruft und Referenzdefinitionen entfernt, bleibt der Quelltext hier stehen. |
| **Ein unveränderliches Ergebnis** | Der mutable Baum offener Blöcke lebt nur während des Parsens. Nichts außerhalb der Datei kann einen halbfertigen Block sehen. |

Die Reihenfolge der Blockanfänge ist **tragend**, nicht kosmetisch: eine
Setext-Unterstreichung muss vor dem Trenner versucht werden, sonst beendet `---` den Absatz
darüber, statt ihn zu einer Überschrift zu machen. Ein Listenpunkt muss nach dem Trenner
kommen, sonst startet `- - -` drei verschachtelte Listen. Sie umzustellen ist kein Refactoring.

## Grenzen

§18.2: „Größe, Tiefe, Tokenzahl und Arbeitsschritte sind begrenzt."

| | Voreinstellung | `paste` |
| --- | --- | --- |
| `maxSourceChars` | 4 MiB | 256 KiB |
| `maxLines` | 200 000 | 10 000 |
| `maxDepth` | 100 | 24 |
| `maxBlocks` | 100 000 | 5 000 |
| `maxSteps` | 20 000 000 | 1 000 000 |

**Warum ein Schrittbudget und nicht nur eine Tiefe.** Weil die teuren Eingaben nicht immer tief
sind. `"> " * 50000` ist 100 kB und schachtelt fünfzigtausend Zitate — das fängt `maxDepth`.
Aber eine Zeile aus zehntausend Backticks ist weder groß noch tief und kostet trotzdem. Das
Budget wird pro Zeile und pro versuchtem Blockanfang belastet, also läuft alles, was lineare
Eingabe in überlineare Arbeit verwandelt, aus dem Vorrat statt aus der Zeit.

Die Testsuite zeigt den Unterschied an zwei gleich großen Quellen: `"text\n" * 30` kostet 30
Schritte, `"*x*\n" * 30` kostet 60 — weil `*` den Vorfilter passiert und alle Blockanfänge
probiert werden.

**Rekursion gibt es genau eine Stelle**, den Aufbau des unveränderlichen Baums, und `maxDepth`
begrenzt sie. Ohne die Grenze stürzt eine tief geschachtelte Eingabe ab, statt einen Fehlerwert
zu liefern — mit ihr ist es `ParseError.LimitExceeded`.

**Ein Syntaxfehler ist keiner der Fälle**, und das ist kein Versehen: CommonMark hat keine
ungültige Eingabe. Jede Zeichenkette ist ein gültiges Dokument, also ist alles, was hier
schiefgehen kann, eine Ressourcengrenze.

## Quellbereiche

Jeder Block trägt seinen `SourceSpan` in UTF-16-Einheiten. Die `SourceMap` ist der **umgekehrte
Index** — Offset zu Block —, und das ist die Frage, die ein Baum ohne Lauf nicht beantwortet:

```scala
ergebnis.sourceMap.blockAt(ergebnis.document, offset)  // der innerste Block dort
ergebnis.sourceMap.spanOf(block.id)                    // und zurück
ergebnis.sourceMap.lineAt(offset)                      // Zeilennummer, binär gesucht
```

Eine Blockgrenze gehört zu dem, was **folgt**: `SourceSpan.contains` ist halboffen, ein Caret
zwischen zwei Absätzen landet im zweiten. Das ist die dokumentierte Affinität, die §18.2 an
syntaktischen Delimitern verlangt, und sie entspricht dem Verhalten des Carets überall sonst
(§11).

Die Dokumentpositionen der zweiten Hälfte von §18.2 fehlen noch — P17 erzeugt kein Dokument,
also gibt es keine. Sie kommen mit dem Adapter in P18.

## Tests

```bash
sbt --server "scalajs-ember-markdown/Test/testOnly *"
```

`MarkdownBlockSpec` benennt die Fälle einzeln und prüft die **Struktur**, nicht gerendertes
HTML. `CommonMarkBlockSpec` fährt die offizielle Konformitätssuite — alle **652 Beispiele**,
versioniert unter `src/test/resources/markdown/spec-0.31.2.txt`.

Sie misst drei verschiedene Dinge:

1. **Robustheit.** Alle 652 Beispiele parsen, innerhalb der Voreinstellungen, ohne Ausnahme.
2. **Wohlgeformte Spannen.** Jede Spanne jedes Parses liegt in der ihres Elternteils und im
   Quelltext. Eine SourceMap mit einer Spanne außerhalb ihres Blocks ist schlimmer als keine.
3. **Abdeckung als Zahl.** Wie viele Beispiele ein **reiner Blockrenderer** zeichengenau
   reproduziert: derzeit **337 von 652**. Die Zahl wird exakt geprüft und nicht als Untergrenze
   — so fällt eine Verschlechterung ebenso auf wie eine Verbesserung, die jemand nachzutragen
   vergisst.

Die 337 sind **kein Konformitätswert.** Die meisten übrigen Beispiele scheitern an
Inline-Inhalt, den der Blockparser bewusst stehen lässt. Nach Abschnitt sieht man, wo der
Parser steht und wo P18 anfängt:

| Abschnitt | |
| --- | --- |
| List items | 48/48 |
| Block quotes | 25/25 |
| Tabs | 11/11 |
| Indented code blocks | 12/12 |
| Lists | 25/26 |
| Fenced code blocks | 26/29 |
| Thematic breaks | 18/19 |
| Setext headings | 22/27 |
| ATX headings | 15/18 |
| HTML blocks | 35/44 |
| Emphasis and strong emphasis | 40/132 |
| Links | 11/90 |
| Code spans | 2/22 |

Die obere Hälfte ist Blockarbeit und im Wesentlichen fertig. Die untere ist P18.

### Warum die Fixtures zu Scala-Quelltext werden

Weil es zur Laufzeit nichts zu lesen gibt: die Tests laufen als Scala.js-Modul, ohne
Dateisystem und ohne Classpath. `project/MarkdownSpecFixtures.scala` übersetzt `spec.txt` zur
Bauzeit in eine Scala-Datei.

Der Nebeneffekt ist erwünscht. Die Spezifikationsversion steht im Dateinamen und landet als
Konstante im erzeugten Code, die Beispielzahl fällt beim Erzeugen an. Die
„Korpus-/Spezifikationsversion" aus P17s Abnahme ist damit eine Zahl, die der Build ausrechnet,
und keine Behauptung in einem Kommentar.

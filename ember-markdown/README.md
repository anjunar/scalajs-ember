# scalajs-ember-markdown

Ein CommonMark-Parser und -Writer in Scala. Kein DOM, kein HTML als Zwischenstufe, keine
JavaScript-Abhängigkeit.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §18.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-markdown` |
| Scala-Paket | `ember.editor.markdown` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

**P17 und P18 abgeschlossen.**

Vorhanden: `MarkdownBlock` und `MarkdownInline` samt Syntaxbaum, `Markdown.parseSyntax`,
`MarkdownWriter`, `MarkdownCodec`, `SourceMap`, `DocumentSourceMap`, `MarkdownProfile`,
`ParseLimits` und `EntityTable`. Der Parser
deckt CommonMark **0.31.2** ab — Blöcke wie Inlines: Absätze, Überschriften, Zitate, Listen,
Code, Trenner, HTML-Blöcke, Emphasis, Links samt Referenzdefinitionen, Autolinks, Bilder,
Code-Spans, Escapes und Zeichenreferenzen.

**Wie weit das reicht, ist gemessen: 651 von 652** Beispielen der offiziellen
Konformitätssuite kommen zeichengenau heraus. Der eine Rest ist kein Zufall — siehe
[Entities](#entities).

Dazu die typisierte SPI, die Syntax auf registrierte NodeTypes abbildet (§18.1):
`MarkdownRule`, `MarkdownCodec` und `DocumentSourceMap`. Die Regeln selbst liegen in
`ember-standard` — hier weiß nichts, was ein `ParagraphNode` ist.

Das Profil sagt, wo es steht:

```scala
MarkdownProfile.commonMarkSafe.conformance  // Conformance.Inlines
MarkdownProfile.blocksOnly.conformance      // Conformance.BlocksOnly
```

§18.1 verlangt genau das: „Bis die Konformitätsfälle vollständig bestanden sind, wird nur die
tatsächlich getestete Teilmenge beworben." Ein Kommentar wäre ein Versprechen; ein Feld ist ein
Wert, den eine Anwendung lesen kann — und was `Inlines` wert ist, steht als Zahl in der
Testsuite.

## Verwendung

```scala
Markdown.parseSyntax(quelltext, MarkdownProfile.commonMarkSafe) match
  case Right(ergebnis) =>
    ergebnis.document.children.foreach {
      case MarkdownBlock.Heading(_, span, level, _, inlines) => …
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
Integrationsmodul (§6) und sind die noch offene Hälfte von P18.

**Ziele sind `String`, nicht `LinkUrl`.** Das sieht nach einer verpassten Gelegenheit für das
„der Typ ist die Tür"-Muster aus, das der Rest dieses Editors verwendet — und ist Absicht:
`LinkUrl` liegt in `ember-link`, `MediaUrl` in `ember-image`, und §6 gibt diesem Modul den Kern
allein. Die Tür steht trotzdem, einen Schritt später: `ember-standard` fährt die Policy der
Anwendung, wenn aus Syntax ein Dokument wird — genau wie §19.1 es verlangt. Der Parser
normalisiert, der Adapter entscheidet.

## Herkunft und Lizenz

`BlockParser.scala` und `InlineParser.scala` sind **Portierungen der Regelstruktur** von
`lib/blocks.js` und `lib/inlines.js` aus commonmark.js 0.31.2 — BSD-2-Clause, Copyright (c)
2014 John MacFarlane. Der vollständige Lizenztext steht in [NOTICE](NOTICE).

Übernommen wurde die Form: die Reihenfolge der Blockanfänge, die Fortsetzungsbedingung jedes
Containers, die Lazy-Continuation-Regel und die Padding-Arithmetik der Listenmarker. Das sind
die Teile, die P17s Risikozeile „echte Parserarbeit" nennt, und sie aus der Spezifikation neu
herzuleiten hätte einen schlechteren Parser und dieselben Regeln ergeben.

Nicht übernommen wurde der Code. Die Unterschiede sind Absicht:

| | |
| --- | --- |
| **Offsets statt Zeile/Spalte** | commonmark.js meldet `sourcepos` als Zeile/Spalte mit tab-expandierten Spalten. §18.2 will UTF-16-Bereiche, und alles andere in diesem Editor zählt so (§11). Nachträglich umzurechnen ist die Stelle, an der sich ein Off-by-one versteckt. |
| **Ein Budget** | §18.2 begrenzt Arbeitsschritte; die Vorlage kennt so etwas nicht. |
| **Keine Smart Punctuation** | Die Vorlage kann Anführungszeichen runden und `--` zu Gedankenstrichen machen. Hier gibt es dafür keinen Schalter: §18.2 verlangt einen Round-Trip, der Bedeutung erhält, und die Zeichensetzung des Autors umzuschreiben ist eine Änderung des Inhalts. |
| **Eine benannte Entity-Teilmenge** | Siehe [Entities](#entities). |
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

Auch Inlines tragen ihre Spanne — bis zum einzelnen Delimiter. Das kann die Vorlage gar nicht:
sie verfolgt Quellpositionen nur bis zur Blockebene. Der Fall, an dem eine naive Umrechnung
scheitert, steht als Test: der Inhalt eines Zitats ist kein Ausschnitt des Quelltexts, weil
`> ` beim Blockparsen wegfällt — jeder Block führt deshalb eine Zeilenkarte zurück in die
Quelle.

Die Dokumentpositionen der zweiten Hälfte von §18.2 fehlen noch: ohne Dokumentadapter gibt es
keine Dokumentpositionen. Sie kommen mit ihm.

## Entities

Numerische Zeichenreferenzen sind **vollständig** — `&#35;`, `&#x1F600;`, U+0000 auf U+FFFD
abgebildet. Für sie braucht es keine Tabelle, also gibt es dort auch keine Teilmenge zu
entschuldigen.

Benannte sind eine **benannte Teilmenge**, und das ist eine Entscheidung: CommonMark verweist
auf die WHATWG-Liste mit 2231 Namen, von denen die meisten in keinem Editordokument je
vorkommen. Sie mitzuliefern hieße, rund 150 kB Tabelle in jedes Browser-Bundle zu legen, das
dieses Modul linkt — für `&angmsdaa;` und Verwandte.

`EntityTable.common` deckt stattdessen ab, was in Prosa steht: die fünf XML-Namen, das
gesamte Latin-1-Supplement (ohne das `Gr&ouml;&szlig;e` einen Round-Trip nicht überlebt und ein
deutsches Dokument still aufhört, deutsch zu sein), Anführungszeichen, Striche, Währung,
Rechtliches, die üblichen mathematischen Zeichen und Pfeile. Rund 150 Einträge.

Ein unbekannter Name bleibt **unverändert stehen**, wird also von einem Round-Trip nicht
verloren. Und eine Anwendung, die mehr braucht, gibt mehr mit:

```scala
MarkdownProfile.commonMarkSafe.copy(entities = EntityTable.common.and("Dcaron" -> "Ď"))
MarkdownProfile.commonMarkSafe.copy(entities = EntityTable.numericOnly)
```

Dasselbe Muster wie `LinkUrlPolicy` und `MediaUrlPolicy`: die Bibliothek wählt eine vertretbare
Voreinstellung, die Anwendung überschreibt sie und weiß dann, dass sie es getan hat.

Das ist auch der eine der 652 Konformitätsfälle, der nicht durchgeht.

## Schreiben

`MarkdownWriter.write` macht aus einem Syntaxbaum wieder Markdown. §18.2 ist präzise darin,
was das heißt — und die Präzision zählt, weil die naheliegende Erwartung die falsche ist:

| | |
| --- | --- |
| **Zugesichert** | `decode(encode(x)) ≃ x` — schreiben, neu parsen, derselbe Baum. |
| **Nicht zugesichert** | `encode(decode(source)) == source`. §18.2: „ist kein Ziel." |

Der Writer wählt eine kanonische Schreibweise. Eine Liste mit `+` kommt mit `-` zurück. Das ist
keine Bequemlichkeit: die Eingabeschreibweise zu erhalten hieße, sie durch das Dokumentmodell zu
tragen, und das Modell hält, was der Text **bedeutet**, nicht wie ihn jemand getippt hat. Die
Ausnahme ist `HeadingStyle` — er kostet ein Feld und ließe sich später nicht rekonstruieren.

Was der Writer dafür garantiert:

- **Sichere Zaunlänge** (§18.2). Ein Inhalt mit drei Backticks bekommt vier. Dasselbe eine Ebene
  tiefer für Code-Spans, samt Randleerzeichen, wenn der Inhalt mit einem Backtick anfängt.
- **Positionsabhängiges Escaping.** Ein `#` am Zeilenanfang wird maskiert, eines mitten in der
  Zeile nicht. Zu viel zu maskieren ist sicher und macht die Ausgabe unlesbar.
- **Ein Rückstrich statt zwei Leerzeichen** für den harten Umbruch. Unsichtbarer Leerraum am
  Zeilenende überlebt keinen Editor, der ihn trimmt, und §18.2 verlangt den Unterschied
  erhalten.

## Dokument und zurück

`MarkdownCodec` ist der Weg zwischen Quelltext und Dokument. §18.1 verlangt ihn ohne Umweg —
"Kein HTML-/DOM-Zwischenschritt" —, und genau so läuft er: `source → syntax → nodes` und
zurück, ohne HTML-String und ohne DOM dazwischen.

```scala
MarkdownCodec.decode(quelltext, schema, regeln, generator, wurzel)   // Either[MarkdownError, DecodedDocument]
MarkdownCodec.encode(dokument, regeln, LossPolicy.Strict)            // Either[MarkdownError, EncodedMarkdown]
```

### Drei Arten von Regel, weil die Syntax drei Formen hat

| | |
| --- | --- |
| `MarkdownBlockRule` | Ein Block wird ein Knoten. Absatz, Überschrift, Zitat, Liste, Code. |
| `MarkdownInlineRule` | Ein Inline wird ein Knoten oder mehrere. Text, Bild, Link. |
| `MarkdownMarkRule` | Ein Inline wird eine **Mark**, kein Knoten. Emphasis, Strong, Inline-Code. |

Die dritte ist die, die man leicht übersieht und später nicht mehr nachrüsten kann. `*a*` ist
kein Knoten um einen Lauf herum, sondern ein Lauf mit einer Mark (§8.2). Der Codec trägt
deshalb eine `MarkSet` den Inline-Baum hinunter, statt eine Hülle zu bauen — eine Regel, die
eine Hülle gewollt hätte, hätte ein Dokument erzeugt, das das Rich-Text-Profil ablehnt.

### Verlust ist eine Entscheidung, keine Überraschung

§18.2: „`Strict` verweigert Informationsverlust, `AllowLossy` muss die Anwendung bewusst
wählen." Was Markdown nicht schreiben kann, ist in §18.2 aufgezählt und wird gemeldet statt
verschwiegen:

| | |
| --- | --- |
| Unterstreichung, Durchstreichung | keine CommonMark-Garantie |
| Bildmaße, MediaId | dito |
| ein Linkziel, das die Policy abweist | der **Text** überlebt, das Ziel nicht |

Unter `Strict` ist jedes davon ein `MarkdownError.WouldLose` und es entsteht kein Export. Unter
`AllowLossy` entsteht einer, und `EncodedMarkdown.losses` sagt, was fehlt. Die Demo wählt
`AllowLossy` und druckt die Verluste unter den Quelltext — sichtbar statt still.

### Die Policies fahren mit

`MarkdownSupports.everything(linkPolicy, mediaPolicy)` nimmt dieselben zwei Policies wie die
Commands. §19.1 verlangt das: „URLs werden nach Entities-/Whitespace-Normalisierung durch die
jeweilige Link-/Media-Policy geprüft." Es gibt keinen zweiten Weg zu einem `LinkUrl` oder
`MediaUrl`, also **können** Import und Dialog nicht auseinanderlaufen.

### Dokumentpositionen

`DecodedDocument.sourceMap` ist die zweite Hälfte von §18.2: Knoten zu Quellbereich und zurück.

```scala
ergebnis.sourceMap.spanOf(knoten.id)   // wo dieser Knoten herkommt
ergebnis.sourceMap.nodeAt(offset)      // welcher Knoten hier steht
```

Ein Absatz und sein einziger Lauf decken **dieselben** Zeichen ab — da entscheidet keine
Spannenregel mehr. Der Gleichstand geht an den zuerst aufgezeichneten Knoten, und das ist nicht
willkürlich: der Codec dekodiert Kinder vor ihren Eltern, also ist der frühere Eintrag der
tiefere Knoten. Deshalb steht dort ein `Vector` und keine `Map` — eine Map beantwortete
dieselbe Frage je nach Hashing anders.

## Tests

```bash
sbt --server "scalajs-ember-markdown/Test/testOnly *"
```

Fünf Suiten, die verschiedene Fragen stellen:

| Suite | Frage |
| --- | --- |
| `MarkdownBlockSpec` | Stimmt die Blockstruktur, Fall für Fall? |
| `MarkdownInlineSpec` | Stimmt die Inline-Struktur, Fall für Fall? |
| `CommonMarkConformanceSpec` | **Wie viel** von der Spezifikation stimmt? |
| `MarkdownRoundTripSpec` | Überlebt ein Baum das Schreiben und Neu-Parsen? |
| `MarkdownSourceMapSpec` | Stimmen die Quellbereiche — und läuft die SPI ohne Profil? |

`MarkdownSourceMapSpec` baut sich eigene Knotenarten und eigene Marks. Das ist kein Behelf,
sondern die Probe: würde `MarkdownCodec` je wissen müssen, was ein `ParagraphNode` ist, hörte
diese Datei auf zu kompilieren. Dieselbe Überlegung wie beim lokalen `BlockNode` in
`ember-image`.

Die Regeln des Standardprofils werden dort getestet, wo sie liegen:
`ember-standard/…/MarkdownDocumentSpec.scala`.

Die beiden ersten prüfen die **Struktur**, nicht gerendertes HTML — der Baum ist das, was der
Rest des Editors konsumiert. Die beiden letzten fahren die offizielle Konformitätssuite, alle
**652 Beispiele**, versioniert unter `src/test/resources/markdown/spec-0.31.2.txt`.

### Was die Zahlen sagen

| | |
| --- | --- |
| **651 von 652** | reproduziert der Parser zeichengenau. |
| **621 von 652** | überleben Schreiben und Neu-Parsen unverändert in der Form. |

Beide werden **exakt** geprüft und nicht als Untergrenze — so fällt eine Verschlechterung
ebenso auf wie eine Verbesserung, die jemand nachzutragen vergisst. Bei einem Fehlschlag druckt
die Suite die Zählung je Abschnitt, weil eine einzelne Zahl nie sagt, **was** sich bewegt hat.

Die 621 sind niedriger als die 651, und das ist erwartbar: der Round-Trip prüft eine strengere
Eigenschaft. Eine Eingabe, die der Parser richtig liest, kann der Writer trotzdem in einer
Schreibweise ausgeben, die sich anders zurückliest — Listenpunkte mit ungewöhnlicher Einrückung
sind der häufigste Fall. Jede dieser Differenzen ist ein Befund und keine Zusicherung.

### Was die Suite nicht behauptet

P17s Risikozeile ist deutlich: „Keine behauptete vollständige CommonMark-Konformität aus
einfachen Happy-Path-Tests." Also behauptet sie nichts. Sie misst drei Dinge, und jedes ist eine
andere Art von Aussage:

1. **Robustheit.** Alle 652 Beispiele parsen, innerhalb der Voreinstellungen, ohne Ausnahme.
2. **Wohlgeformte Spannen.** Jede Spanne jedes Parses liegt in der ihres Elternteils und im
   Quelltext. Eine SourceMap mit einer Spanne außerhalb ihres Blocks ist schlimmer als keine.
3. **Abdeckung als Zahl.**

### Warum die Fixtures zu Scala-Quelltext werden

Weil es zur Laufzeit nichts zu lesen gibt: die Tests laufen als Scala.js-Modul, ohne
Dateisystem und ohne Classpath. `project/MarkdownSpecFixtures.scala` übersetzt `spec.txt` zur
Bauzeit in eine Scala-Datei.

Der Nebeneffekt ist erwünscht. Die Spezifikationsversion steht im Dateinamen und landet als
Konstante im erzeugten Code, die Beispielzahl fällt beim Erzeugen an. Die
„Korpus-/Spezifikationsversion" aus P17s Abnahme ist damit eine Zahl, die der Build ausrechnet,
und keine Behauptung in einem Kommentar.

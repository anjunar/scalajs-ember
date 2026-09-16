# scalajs-ember-code-highlighting

Syntax-Highlighting für Codeblöcke — als abgeleiteter View-State, nicht als Dokument. Optional.

Verbindlicher Entwurf: [UI_EDITOR_IMPLEMENTATION.md](../UI_EDITOR_IMPLEMENTATION.md) X02,
[UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §§5, 11, 15.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-code-highlighting` |
| Scala-Paket | `ember.editor.codehighlighting` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-code`, `scalajs-ember-ui` |

## Stand

X02 umgesetzt. Vorhanden: ein zeilenbasierter Lexer mit Zustandsstapel, inkrementelles
Nachlexen, Grammatiken für **Scala, JavaScript/TypeScript, JSON, HTML/XML, CSS, Shell und
Markdown**, ein austauschbarer `Highlighter`-Dienst mit revisionierten Ergebnissen, der
`HighlightScheduler` und `CodeDecorations` für den Browser. Weitere Sprachen folgen später.

## Verwendung

```scala
val view        = DocumentView.mount(session, cursor, CodeSupport.views)
val decorations = CodeDecorations.attach(session, view)
// …
decorations.dispose()
```

Und im Stylesheet, einmal für alle Editoren der Seite:

```css
::highlight(ember-tok-keyword) { color: #a0461d; }
::highlight(ember-tok-string)  { color: #4a7524; }
::highlight(ember-tok-comment) { color: #858b82; }
```

Für Ausgaben ohne Editor — Export, Leseseite, E-Mail — dieselben Farben als Klassen:

```scala
StaticHighlight.html(code, Some("scala"))  // <span class="ember-tok-keyword">val</span> …
```

## Warum kein DOM

Ein Codeblock ist **ein** Textknoten (§8.2, P15), und der Editor steht darauf: die
Positionsabbildung zählt UTF-16-Offsets in genau diesen Knoten (§11), Recovery vergleicht ihn
mit dem Dokument (§15.4), und eine laufende Composition verträgt keinen Umbau des DOM um sie
herum (§15.3). Token-Spans würden den Knoten bei jedem Tastendruck zerteilen — X02 nennt das
Risiko ausdrücklich: „Mehrere Textspans verändern DOM-Offsets."

Die **CSS Custom Highlight API** färbt stattdessen `Range`s über dem vorhandenen Text. Es entsteht
kein Element, kein Knoten wird geteilt, ein `MutationObserver` sieht nichts. Live-Ranges wandern
mit dem Text, den sie abdecken: zwischen einer Änderung und dem nächsten Ergebnis rutschen die
alten Farben mit, statt auf falschen Zeichen zu landen.

Gemalt wird nur, solange der DOM-Text **genau** der Text ist, für den das Ergebnis berechnet
wurde. Während einer Composition ist er das nicht — der Browser ist dem Modell voraus —, und der
Block wartet einfach auf den nächsten Commit.

Ohne die API bleibt der Code ungefärbt, ohne Fehler (`CodeDecorations.isSupported`).

### Serverseitiges Rendern

Der Editor-SSR bleibt ungefärbt, und das ist Absicht: die serverseitige Ausgabe muss exakt das
sein, was die Projektion im Browser baut, sonst verweigert die Hydration sie (§17). Spans sind für
Seiten, die bleiben, was sie sind — `StaticHighlight`. Der Lexer ist reines Scala und rechnet auf
dem Server dasselbe wie im Browser, ganz ohne Worker.

## Warum nicht der Markdown- oder HTML-Parser

Beide sind exakt, und Exaktheit braucht eine Farbe nicht. Ein Highlighter färbt bei jedem
Tastendruck neu, also muss er zeilenweise arbeiten und mitten im Block wieder aufsetzen können.
Ein Parser, der erst nach der ganzen Eingabe entscheidet — CommonMarks Delimiter-Stack tut genau
das —, kann das nicht. Der HTML-Tokenizer aus P24 liefert außerdem keine Quellpositionen und löst
Entities auf; er gehört zur Sicherheitsgrenze und wird für eine Anzeige nicht verbogen.

Übernommen ist die Einheit: halboffene UTF-16-Bereiche, wie `SourceSpan` im Markdown-Modul und wie
jede Textposition in §11.

## Grammatiken

Eine Grammatik ist eine Menge benannter Zustände mit geordneten Regeln (Monarch-/Prism-Stil):

```scala
Grammar("scala")(
  state("root")(
    Rule.token("//.*", TokenKind.Comment),
    Rule.token("""/\*""", TokenKind.Comment).push("blockComment"),
    Rule.words(TokenKind.Keyword, identifier, "def", "val", "class"),
    …
  ),
  state("blockComment")(
    Rule.token("""\*/""", TokenKind.Comment).pop,
    …
  )
)
```

| Baustein | Wofür |
| --- | --- |
| `Rule.token` / `Rule.skip` | Ein Muster wird ein Token bzw. wird ungefärbt verbraucht |
| `Rule.groups` | Capture-Gruppen als eigene Tokens — `def` und der Name dahinter |
| `Rule.words` | Ein ganzes Wort aus einer Liste; `valid` ist nicht `val` + `id` |
| `.push` / `.pop` | Zustandswechsel: String, Kommentar, Interpolation |
| `.embed(grammar, end)` | Andere Sprache bis `end`: JavaScript in `<script>`, CSS in `<style>` |
| `Rule.embedMatched(select)` | Sprache und Ende aus dem Treffer selbst: ein Markdown-Fence färbt seinen Inhalt in der Sprache seines Info-Strings und endet an einem mindestens gleich langen Fence am Zeilenanfang |
| `.when(guard)` | Rückblick, den ein Muster nicht kann: `/` als Regex oder Division, `#` als Kommentar |
| `lineBound = true` | Zustand endet mit der Zeile — ein offener `"` in Scala |

Die Token-Arten sind eine **geschlossene** Menge (`TokenKind`), damit ein Theme einmal geschrieben
wird und für jede Sprache gilt. Sprachen werden über `HighlightLanguages.withLanguage(grammar,
aliases*)` registriert und ohne Groß-/Kleinschreibung nachgeschlagen — der Name kommt aus
`CodeInfo`, also genau aus dem Fence.

| Sprache | Namen |
| --- | --- |
| Scala | `scala`, `sc`, `sbt` |
| JavaScript | `javascript`, `js`, `mjs`, `cjs`, `jsx` |
| TypeScript | `typescript`, `ts`, `mts`, `cts`, `tsx` |
| JSON | `json`, `jsonc`, `json5` |
| HTML/XML | `html`, `htm`, `xhtml`, `xml`, `svg` |
| CSS | `css` |
| Shell | `shell`, `sh`, `bash`, `zsh`, `shellscript` |
| Markdown | `markdown`, `md` |

Die Grammatiken färben, sie validieren nicht. Was sie nie tun dürfen, ist einen String oder
Kommentar zu verlieren — das färbte jede folgende Zeile um. Diese Regeln sind der sorgfältige
Teil, und die Tests halten sie fest.

## Inkrementell

`LexedText` hält pro Zeile den Anfangszustand, die Tokens und den Endzustand. Nach einer Änderung
wird ab der ersten geänderten Zeile neu gelext, bis eine Zeile dort endet, wo früher eine Zeile
**im selben Zustand** begann, und der Rest des Textes unverändert ist. Ab da gelten die alten
Zeilen, nur verschoben.

Tippen in einer Zeile lext eine Zeile. Ein geöffneter Blockkommentar lext alles darunter, weil
jede Zeile darunter jetzt in einem Kommentar beginnt — und keine Zeile mehr, sobald er wieder
geschlossen ist. Das ist gemessen, nicht behauptet (`relexed`).

Grenzen gegen eingefügte Monster: `HighlightLimits(maxChars = 200000, maxLineChars = 4000)`. Ein
Block darüber bleibt ungefärbt; eine zu lange Zeile ebenfalls, der Zustand läuft unverändert
durch sie hindurch.

## Revisionen

`Highlighter` ist asynchron im Vertrag, auch wenn `LocalHighlighter` sofort antwortet: ein
Worker-Adapter antwortet später oder gar nicht. Jedes Ergebnis trägt Block, Revision und Text
zurück. Der `HighlightScheduler` zeigt es nur, wenn es die **letzte** Anfrage für den Block
beantwortet und der Text noch der Text des Blocks ist (§5: ein externer Effekt gilt nur für seine
Revision). Alles andere zählt als veraltet und wird verworfen.

Der Scheduler liest Commits und schreibt keine: keine Transaktion, kein Command, kein
State-Field. „Highlighting ausblenden ändert kein Dokument/History" ist damit strukturell — der
Test prüft, dass das Dokument vor und nach einem vollständigen Lebenszyklus **dasselbe Objekt**
ist, was jeden JSON- und Markdown-Roundtrip einschließt.

## Mehrere Editoren

Highlight-Namen gelten pro Dokument. Jede Instanz fügt ihre Ranges dem einen
`ember-tok-keyword`-Highlight hinzu und entfernt genau diese wieder; der Name wird freigegeben,
wenn die letzte Range weg ist. Zwei Editoren auf einer Seite teilen also ein Theme und löschen
einander nichts. Das Fenster kommt aus dem `ownerDocument` des Textknotens — ein Editor im iframe
malt in dessen Registry.

## Tests

```bash
sbt --server "scalajs-ember-code-highlighting/Test/testOnly *"
```

| Suite | Prüft |
| --- | --- |
| `LexerSpec` | Jede Sprache an den Stellen, an denen Zustände kippen: verschachtelte Kommentare, Interpolation mit Klammern, Regex gegen Division, `</script>` im String, `$#` gegen Kommentar, Fences |
| `IncrementalLexingSpec` | 2400 generierte Änderungen: inkrementell **gleich** vollständig, Tokens und Zustände; eine Zeile pro Tippen; Grenzen; `StaticHighlight` erhält den Text exakt |
| `HighlightSchedulerSpec` | Welche Blöcke gefragt werden, veraltete Antworten, nicht bereite Ansicht, Sprach- und Typwechsel, Dispose, unberührtes Dokument |

Im echten Browser (`ember-integration/browser/test/code-highlighting.spec.mjs`): Färben, genau ein
Textknoten, keine Mutation beim Färben, Tippen, Caret und Auswahl unverändert, verworfenes
Worker-Ergebnis, Composition ohne Strukturmutation, Aufräumen, zwei Editoren, nächster Frame und
der Rückfall ohne API.

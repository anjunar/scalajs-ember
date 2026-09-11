# scalajs-ember-browser

Was der Editor tut, wenn die Seite schon da ist: eine serverseitig gerenderte Ansicht
übernehmen, ohne zu zerstören, was bis dahin auf der Seite stand — und danach logische und
Browserauswahl in beide Richtungen abbilden.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§11, 15.4,
17, 22.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-browser` |
| Scala-Paket | `ember.editor.browser` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-html`, `scalajs-ember-jfx`, `com.anjunar:scalajs-jfx-core` |

## Stand

**P20, P21 und P22 abgeschlossen.** Composition, Observer-Abgleich und Recovery sind P23 und
stehen noch aus.

| Hydration (P20) | |
| --- | --- |
| `HydrationSnapshot` | was auf der Seite stand, **bevor** irgendetwas geclaimt wurde |
| `EditorHydration` | der Abgleich, den ein Strukturvergleich nicht leistet |
| `EditorActivation` | die Entscheidung, ob editiert werden darf — und warum noch nicht |

| Selection und Fokus (P21) | |
| --- | --- |
| `BrowserScope` | welches Dokument, welches Fenster, welche Selection — und was davon es gibt |
| `DomPositionMap` | die explizite Abbildungstabelle aus §11, in beide Richtungen |
| `SelectionPort` | lesen, schreiben, beobachten; und keine Rückkopplungsschleife |
| `FocusController` | wer den Fokus hat, was gemerkt wird, wann er zurückgegeben wird |
| `DomKinds` | `nodeType` statt `instanceof` — warum, steht unten |

| Eingabe (P22) | |
| --- | --- |
| `InputIntent` | was der Browser will, in seinem eigenen Vokabular |
| `BeforeInputAdapter` | die Tabelle von `inputType` auf Absicht |
| `BrowserInputController` | die Zustandsmaschine: Ereignisse rein, Commands raus |
| `NativeInputReader` | was der Browser schon getan hat, als Dokumentänderung gelesen |
| `InputOperationLog` | genau einmal, auch wenn eine Aktion zweimal ankommt |
| `KeyboardBindings` | die Shortcut-Tabelle, samt Tab-Regel |

Welche Absicht welches Feature-Command wird, steht **nicht** hier, sondern in
[ember-browser-support](../ember-browser-support/README.md) — §7 verbietet diesem Modul den
Import eines Features, und das ist der Grund, warum ein Editor ohne Listen bei
`insertUnorderedList` nicht kaputtgeht, sondern das Ereignis nativ lässt.

Die austauschbare Boundary selbst kommt aus jfx-core (`HydrationBoundary`, P20s generischer
Anteil). Dieses Modul liefert, was der Editor darüber hinaus weiß.

## Erfassen, bevor geclaimt wird

§17 ist beim Zeitpunkt ausdrücklich:

> **Vor dem ersten Claim**, der Werte überschreiben könnte, erfasst die äußere Form-Boundary
> die tatsächliche `textarea.value`, `selectionStart`, `selectionEnd`, `selectionDirection` und
> Fokus. Attribute oder `defaultValue` reichen dafür nicht.

Der Unterschied zwischen `value` und `defaultValue` ist der ganze Punkt: wer getippt hat, bevor
das Skript lief, hat `value` geändert — das Attribut trägt noch, was der Server schickte. Es zu
lesen hieße, die Eingabe still zu verwerfen; `value` **nach** einem Claim zu lesen hieße, zu
lesen, was der Claim geschrieben hat.

`selectionDirection` fehlt in `scalajs-dom` 2.8.1. Der dynamische Zugriff in
`HydrationSnapshot.of` ist deshalb die eine schmale Stelle und keine Gewohnheit. Der Wert
selbst wird als `SelectionDirection` des Kerns (§11) geführt, nicht als zweites Enum daneben —
zwei Begriffe für dieselbe Sache bräuchten an jeder Verwendung eine Umrechnung, und genau dort
liefen sie irgendwann auseinander.

Eine Composition, die **vor** dem Attach begann, lässt sich nachträglich nicht abfragen. Das
Feld `composing` behauptet deshalb nichts; die Unsicherheit steht in
`HydrationSnapshot.unknownInputSession`, und ein bereits fokussiertes Feld genügt zum
Aufschieben. Nicht weil Fokus Composition bedeutet, sondern weil ein fokussiertes Feld der
einzige Ort ist, an dem eine laufen könnte — „nein" zu raten hieße, Text mitten in einer
Composition zu ersetzen.

## Der Abgleich, den JFX-Strict nicht leistet

> Ein zusätzlicher Editor-Check validiert IDs, Textinhalt und semantisch relevante Attribute
> gegen das erwartete Profil, **bevor** Binding sie verdeckt. JFX-Strict allein beweist dies
> heute nicht.

Der hydrierende Cursor prüft, ob er das Tag findet, das er erwartet. Das ist notwendig und
nicht hinreichend: ein `<p>` mit falschem `data-ember-node` oder mit richtiger ID und anderem
Text besteht eine reine Strukturprüfung und wird danach still zu einem Dokument, das etwas
anderes sagt als das ausgelieferte.

`EditorHydration.preflight` vergleicht gegen **dieselbe** `HtmlSupport`, die die SSR-Ausgabe
erzeugt hat. Eine zweite Beschreibung dessen, „was der Server geschickt haben sollte", wäre
eine zweite Wahrheit — und ihr Auseinanderlaufen sähe exakt aus wie eine Hydrationsabweichung.

Zwei Einschränkungen mit Absicht:

- **Nur die Attribute, die die Semantik nennt.** Eine Seite darf eigene hinzufügen — eine
  Layoutklasse, ein Analytics-Attribut. Ein Editor, der daran scheiterte, wäre in jeder realen
  Anwendung unbrauchbar.
- **Nur Elementkinder.** Die JFX-Runtime schreibt Kommentaranker für Gruppen
  (`<!--jfx:KeyedChildren:start-->`); sie sind keine Dokumentknoten, und sie mitzuzählen ließe
  jeden Container abweichen.

`preflightContent` ist die Variante für einen Container, der die Wurzel **enthält**. Eine
Boundary reicht ihren eigenen Host herüber — den Kasten um die Ansicht, nicht die Ansicht —,
denn §17.3 legt den Fallback außerhalb. Gegen den Container zu prüfen meldete auf jeder
korrekten Seite `<article>` gegen `<div>`.

Höchstens acht Abweichungen werden gesammelt. Eine Abweichung weit oben lässt meist jeden
Knoten darunter abweichen, und eine Diagnose von zehntausend Zeilen ist keine.

## Aktivieren — zwei Bedingungen, nicht eine

> Nach lokal vollständig erfolgreichem Claim **und** äußerem Hydration-Abschluss werden
> Controller und `contenteditable` aktiviert.

Eine Boundary kann ihren Teilbaum übernommen haben, während die Seite um sie herum noch
adoptiert wird. `EditorActivation.decide` ist deshalb eine Funktion ihrer Eingaben und kein
Controller: jede Bedingung, die §17 nennt, ist eine Eigenschaft von Werten, die der Aufrufer
ohnehin hat, und ein Controller hielte Kopien davon, die widersprechen könnten. „Wiederholtes
Enhancement ist idempotent" gilt damit von selbst.

| Ergebnis | wann |
| --- | --- |
| `Failed` | der lokale Claim ist gescheitert — und das ist kein Warten, sondern ein Ende |
| `Deferred(PageHydrating)` | die äußere Hydration läuft noch |
| `Deferred(InputSession)` | eine Eingabesitzung ist offen oder könnte es sein |
| `Deferred(UnimportedSource)` | der Quelltext wurde seit dem Rendern geändert (§17.4) |
| `Active` | editierbar |

Die Reihenfolge zählt: ein gescheiterter Claim steht **vor** jeder Zurückstellung. Ihn als
„aufgeschoben" zu melden ließe einen Aufrufer auf etwas warten, das nicht kommt.

`mayRestoreSelection` ist die negative Hälfte von §17.7 und die teurere: **nur** wenn der
Nutzer das Feld tatsächlich fokussiert hatte, darf eine Auswahl geschrieben werden. Ein Editor,
der sich beim Laden selbst fokussiert, nimmt den Fokus dort weg, wo der Benutzer war.

## Positionen: die Tabelle, die nichts abzählt

§11 nennt das Problem und die Abhilfe in einem Satz:

> Bei DOM-Elementoffsets zählen DOM-Kinder einschließlich Renderhilfen anders als Dokumentkinder;
> die explizite Mapping-Tabelle löst dies auf.

Unter einem Container stehen Dinge, für die das Dokument kein Wort hat: die Gruppenanker der
Runtime (`<!--jfx:KeyedChildren:start-->`), das innere `<code>` eines Codeblocks, später ein
Platzhalter-`<br>`. DOM-Kinder zu zählen und das Ergebnis Kindoffset zu nennen, ist bei jedem
Knoten um einen anderen Betrag falsch.

Deshalb zählt hier nichts. Jeder Offset kommt aus der Lage der **Hosts der Dokumentkinder**, und
jede Auflösung geht über die Projektion:

| Punkt | DOM-Position |
| --- | --- |
| `Text(run, o)` | der Textknoten des Laufs, Offset `o` — beide messen UTF-16 |
| `Children(p, i)`, `i < n` | im Inhaltselement von `p`, vor dem Host des `i`-ten Kindes |
| `Children(p, n)` | ebenda, hinter dem Host des letzten Kindes |
| `Children(p, 0)`, `p` leer | ebenda, Offset 0 — kein Dokumentkind steht davor |

Zwei Zugänge in `ember-jfx` liefern die Ausgangspunkte: `ContainerElement.contentHost` (die
Kinder eines Codeblocks hängen im `<code>`) und `TextRunElement.textHost` (der Textknoten liegt
unter der Markkette). Die Komponente weiß beides. Es von außen nach Tagzahl abzuzählen wäre eine
zweite Beschreibung derselben Struktur — und die läuft beim ersten Mark, das nicht als genau ein
Element rendert, auseinander.

### Die Gegenrichtung geht abwärts

Um zu einem DOM-Knoten den Dokumentknoten zu finden, liegt ein Index DOM→ID nahe. Der wäre eine
zweite Ownership-Liste, und §15.1 verbietet der Projektion genau das. `nodeAt` steigt stattdessen
das **Dokument** hinab und fragt pro Ebene nur `Node.contains`. Passt kein Kind mehr, gehört der
Knoten dem erreichten — Markkette, Innentag und Gruppenanker sind dessen eigenes Markup.

Das Attribut `data-ember-node` kommt dabei nicht vor. Es ist eine Entscheidung des Renderprofils
(§19.1); ein Port, der es parst, funktioniert nur für diese Semantik und funktioniert *falsch*
weiter, wenn ein Profil aufhört, es zu setzen.

## Schreiben ist die gefährliche Richtung

Lesen ist billig und immer erlaubt: `selectionchange` meldet, wohin der Benutzer gegangen ist,
und §11 lässt Pfeilnavigation ausdrücklich nativ laufen. Schreiben kann einen Caret wegziehen,
den Fokus nehmen und das Ereignis auslösen, das es selbst verursacht hat. Jeder Schreibvorgang
passiert deshalb zuerst `SelectionWriteGate`:

| Ergebnis | Grund |
| --- | --- |
| `StaleProjection` | §11: nur bei passender Projektionsrevision schreiben |
| `NotFocused` | §22: Hintergrundupdates stehlen keinen Fokus |
| `Unsupported` | ein Shadow Root ohne `getSelection` (§15.4) |
| `AlreadyThere` | das DOM sagt das schon; ein Schreibvorgang feuerte nur ein Ereignis |
| `NoSelection` | das Modell hat keine — die Browserauswahl zu löschen nähme den Caret weg |

`WriteIntent.Explicit` umgeht die Fokusbedingung, aber keine der anderen. Es heißt **nicht**
„ohne den Fokus anzufassen": dieser Port ruft nie `focus()`, doch eine Auswahl in ein
`contenteditable` zu schreiben fokussiert es in Chromium trotzdem. Deshalb ist es nicht die
Voreinstellung.

### Keine Schleife

§15.2 verlangt, „eigene Selection-Schreibvorgänge anhand Revision und tatsächlichem Wert zu
erkennen". Beide Hälften sind nötig. Ein synchrones Flag trägt nicht, weil `selectionchange`
asynchron zugestellt wird — lange nachdem das Flag zurückgesetzt ist. Eine Revision allein trägt
auch nicht, weil der Benutzer den Caret bewegen kann, ohne dass sich etwas ändert. Der Port merkt
sich die vier DOM-Werte, die er zuletzt geschrieben hat, samt der Revision dazu; ein Ereignis mit
genau diesen Werten ist sein eigenes Echo.

### Was dem Editor nicht gehört

§15.2 prüft vor jeder Eingabeverarbeitung die Ownership: „native Inputs/Textareas in Atom-Views,
unmanaged Bereiche und verschachtelte Editoren gehören nicht automatisch zum äußeren Editor."
Für Selection heißt das: liegt das **aktive Element** in einem Atom, ist die Dokumentauswahl nicht
die des Editors, und `read()` meldet `Foreign`.

Das aktive Element steht dabei vor den Endpunkten, und ein Firefox-Lauf hat gezeigt warum: solange
ein natives Feld in einem Atom den Fokus hat, setzt Firefox die Dokumentauswahl an den **Anfang**
des Editing-Hosts. Eine Prüfung nur von Anker und Fokus hätte daraufhin einen Caret an den
Dokumentanfang importiert, während der Benutzer ganz woanders stand.

## Fokus, getrennt vom Port

Weil „die Auswahl zurücksetzen, ohne den Fokus zu nehmen" sagbar sein muss — genau das braucht
eine Toolbar. Läge der Fokus im Port, wäre jeder Schreibvorgang eine Fokusentscheidung.

`FocusController` beobachtet `focusin`/`focusout` statt `focus`/`blur`: das erste Paar blubbert,
und ein Editor enthält Fokussierbares — eine Mediensteuerung in einem Atom, ein verschachteltes
Feld. Fokus dorthin ist immer noch Fokus im Editor.

`capture()` merkt sich die Auswahl als zwei Bookmarks — §11 bildet Anchor und Focus **unabhängig**
ab — plus die Angabe, ob der Editor den Fokus hatte. Daran hängt §22s „Schließen stellt Fokus nur
im passenden Interaktionskontext wieder her":

| `FocusIntent` | nimmt den Fokus |
| --- | --- |
| `SelectionOnly` | nie. Was ein Hintergrundupdate darf |
| `IfItWasOurs` | nur, wenn der Editor ihn beim Merken hatte — der Dialogfall |
| `Always` | ja, auf ausdrückliche Geste |

Nie dagegen, wenn der Fokus schon im Host liegt (ein erneutes `focus()` scrollt nur) oder der Host
gar nicht fokussierbar ist — §22 hält Fokusfähigkeit und Editierbarkeit auseinander.

Ein Bookmark nimmt beim Auflösen die Ersatzgrenze in Kauf (`resolveOrFallback`). Der Unterschied
steht im Kern: „Ein Caret darf auf die Grenze zurückfallen — der Cursor muss irgendwo stehen. Ein
Upload-Bookmark darf das nicht."

## Ein Dokument ist nicht das Dokument

§15.4: „Für Editor-Hosts in iframes werden ownerDocument/defaultView verwendet. Shadow-DOM-Selection
ist ein eigener Capability-Test." `BrowserScope` ist die eine Stelle, an der das entschieden wird;
nichts hier greift nach `dom.window`.

| `SelectionCapability` | |
| --- | --- |
| `Document` | der Regelfall: ein Host in einem Dokument mit Fenster |
| `ShadowNative` | ein Shadow Root, dessen Engine `getSelection` anbietet (heute Chromium) |
| `ShadowUnsupported` | einer ohne. Dort wird nichts gelesen statt etwas Falsches |
| `Detached` | ein Host ohne Fenster |

`ShadowUnsupported` liest bewusst **nichts**. Die Auswahl des Dokuments meldet dort den
Shadow-Host und verschweigt alles darin — ein Wert, der plausibel aussieht und nichts bedeutet.

### `nodeType` statt `instanceof`

`DomKinds` gibt es wegen eines Fehlers, den erst ein iframe zeigte. Eine Scala.js-Typprüfung auf
einen Fassadentyp kompiliert zu `instanceof`, und `instanceof Text` prüft gegen den
`Text`-Konstruktor **dieses** Fensters. Ein Textknoten aus dem Dokument eines iframes ist eine
Instanz von dessen Konstruktor und besteht den Test nie: jede Abbildung dort meldete „nicht
projiziert", während die richtigen Elemente im DOM standen.

Das ist die schwerer sichtbare Hälfte von §15.4 — es geht nicht nur darum, welches `window`
gefragt wird, sondern darum, nicht anzunehmen, es gäbe nur eines. `nodeType` ist eine Zahl aus der
Spezifikation und bedeutet in jedem Realm dasselbe.

## Eingabe: drei Wege hinein, und warum es drei sind

| | |
| --- | --- |
| `beforeinput`, abbrechbar | Der gute Fall. Die Absicht steht fest, **bevor** etwas passiert; ein Command läuft, die native Aktion wird verhindert. |
| `input` | Was bleibt, wenn `beforeinput` nicht abbrechbar war oder gar nicht kam. Das DOM ist voraus, und `NativeInputReader` zieht das Modell nach. |
| `keydown` | Nur Shortcuts und strukturelle Tasten. Text **nie** — §15.2: „Text generell über Input-Pipeline", denn eine Keydown-Tabelle sieht weder Diktat noch Autokorrektur noch eine mobile Tastatur. |

### `preventDefault` folgt der Übernahme, nicht dem Handler

Die Regel, die P22 ausdrücklich nennt:

> Event-Ownership und erfolgreiche Modellübernahme **oder bewusste Ablehnung** bestimmen
> `preventDefault`, nicht die bloße Existenz eines Handlers.

Also verhindert wird bei `TakenOver` und bei `Refused` — und sonst nie. Eine Ablehnung ohne
`preventDefault` ließe den Browser ein Dokument ändern, zu dem das Modell nein gesagt hat; ein
`preventDefault` ohne Übernahme schluckt eine Eingabe, die niemand verarbeitet hat.

### Genau einmal

Dieselbe Benutzeraktion erreicht den Editor mehrfach: `beforeinput` und dann `input`, oder
`paste` und `beforeinput`. `InputOperationLog` macht den zweiten zum No-op — ein Protokoll und
kein Flag, weil die Ereignisse nicht verlässlich paarweise kommen und ein Browser mehrere
`beforeinput` vor einem `input` schicken darf (§15.2 nennt Autokorrektur als den Fall).

In der Praxis greift es seltener als erwartet: **ein verhindertes `beforeinput` erzeugt gar kein
`input`.** Der Dedupe-Pfad trägt die Engines, die trotzdem beides schicken.

### Der native Pfad und seine Fallgrube

`NativeInputReader` vergleicht den Lauf, in dem der Caret steht, mit dem Dokument und liefert den
kleinsten Splice. Drei Ergebnisse sind möglich, und das mittlere ist das, das nur ein Browser
zeigt:

| | |
| --- | --- |
| `Text` | ein Lauf, ein Splice. Der Normalfall. |
| `SplitRun` | der Text stimmt, aber der Browser hat mehrere Textknoten im Wrapper hinterlassen. **Firefox tut das beim nativen Einfügen eines Zeichens außerhalb der BMP.** Der Text ist importierbar, die Ansicht muss neu gebaut werden. |
| `Unimportable` | Struktur, die kein Splice ausdrückt. Der gefundene Text reist mit (§15.4), der Controller geht in `Recovering`, und niemand rät. |

Bei `SplitRun` folgt auf den Commit `DocumentView.resetRun` — §15.4s „lässt JFX diesen Bereich aus
dem gültigen State neu aufbauen". Der Caret wird dabei **gerechnet** und nicht gelesen: ein
aufgeteiltes DOM lässt sich mit der Ein-Textknoten-Annahme der Positionstabelle nicht adressieren.

Die zweite Fallgrube liegt in der Projektion und wurde als `aababc` nach dem Tippen von `abc`
sichtbar: eine native Eingabe wird **aus** dem DOM gelesen, also steht der Text dort schon, wenn
der Commit ankommt — und der Splice fügte ihn ein zweites Mal ein. `DocumentProjection`
vergleicht seither gegen den committeten Text, bevor es schreibt.

## Readonly und Fokus

§22 hält zwei Dinge auseinander, die wie eines aussehen: „Fokusfähigkeit und Editierbarkeit sind
getrennte Entscheidungen." Ein readonly Editor ist weiterhin fokussierbar, auswählbar und
vorlesbar — er ändert sich nur nicht.

Das hat zwei konkrete Folgen, beide von Browsertests erzwungen:

- `contenteditable="false"` nimmt ein Element aus der Tab-Reihenfolge. Der Host bekommt deshalb in
  **beiden** Modi `tabindex="0"` — sonst wäre ein readonly Editor per Tastatur unerreichbar, und
  ein Moduswechsel risse den Fokus aus dem Text.
- **WebKit navigiert bei Backspace zurück**, wenn ein fokussiertes Element nicht editierbar ist.
  Ein readonly Editor weist die Editiertasten deshalb ausdrücklich ab, statt zu hoffen.

## Composition (P22s Anteil)

P22 behauptet keine vollständige IME-Freigabe und verhält sich entsprechend: `compositionstart`
führt in `Composing`, dort wird **nichts** beansprucht und nichts geschrieben — §15.3: „Re-Render
oder Selection-Schreiben kann laufende native Texteingabe zerstören" —, und was die Composition
hinterlassen hat, liest derselbe Reader, durch den auch jede andere native Änderung geht. Das
Protokoll mit Schreibsperre und Abschlussregeln ist P23.

## Tests

```bash
sbt --server "scalajs-ember-browser/Test/testOnly *"
```

`HydrationBoundarySpec`, `SelectionPolicySpec` und `InputPipelineSpec` prüfen die Regeln als
Regeln: wann aktiviert, geschrieben, fokussiert werden darf, was ein abgelaufenes Bookmark ist,
welche Absicht ein `inputType` bedeutet und was der kleinste Splice zwischen zwei Strings ist.

Was eine lebende Seite braucht, steht im Browser-Gate
([ember-integration/browser](../ember-integration/browser/README.md)):
`editor-hydration.spec.mjs`, `selection.spec.mjs`, `focus.spec.mjs`, `editing.spec.mjs` und
`native-input.spec.mjs`. Wo ein Gruppenanker liegt, wie eine Markkette aussieht, wohin der Fokus
wirklich geht, ob ein Tastendruck überhaupt ein abbrechbares `beforeinput` erzeugt, was eine
Engine mit einem Shadow Root macht — dazu hat keine headless Prüfung etwas zu sagen.

Die Aufteilung ist dieselbe wie bei §16 und aus demselben Grund: eine Regel, die durch eine
ihrer Darstellungen geprüft wird, ist einmal geprüft.

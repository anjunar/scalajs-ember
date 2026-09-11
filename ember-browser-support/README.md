# scalajs-ember-browser-support

Wo Browserabsichten auf Feature-Commands treffen. Ein Tastendruck ist hier noch kein `ToggleMark`
— dieses Modul entscheidet, dass er einer wird.

Verbindlicher Entwurf: [UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §§6, 7, 15.2, 22.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-browser-support` |
| Scala-Paket | `ember.editor.browsersupport` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `-rich-text`, `-list`, `-link`, `-code`, `-history`, `-ui`, `-browser` |

## Stand

**P22 und der History-Anteil von P23 abgeschlossen.** Vorhanden: `RichTextBindings`,
`HistoryBindings`, `ListBindings`, `LinkBindings`, `CodeBindings` und die zusammengesetzten
`EditorBindings`.

## Warum es dieses Modul gibt

§7 verbietet `ember-browser` den Import eines Feature-Moduls, und die Regel verdient sich ihren
Platz genau hier. Eine Eingabeabsicht ist **Browservokabular**: `formatBold` heißt so in der
Input-Events-Spezifikation, nicht im Editor. Welche Mark daraus wird — ob überhaupt eine —, weiß
erst ein Profil.

Die Folge ist die nützliche Sorte Trennung: ein Editor ohne Listen ist kein Editor, der bei
`insertUnorderedList` kaputtgeht. Er hat für diese Absicht schlicht keine Bindung, und der
Controller lässt das Ereignis dann **nativ** laufen, statt es zu schlucken. Derselbe Mechanismus
trägt jede Absicht, die niemand beansprucht.

```scala
val bindings = HistoryBindings.input ++ ListBindings.input ++ RichTextBindings.input
val keys     = EditorBindings.everythingKeyboard

BrowserInputController.attachTo(session, view, port, bindings, keys)
```

## Reihenfolge ist eine Entscheidung

`InputBindings` und `KeyboardBindings` lösen nach dem ersten Treffer auf. Antworten zwei Module
auf dieselbe Absicht — eine Liste will bei Enter das Item teilen, Rich Text den Block —, dann
entscheidet, wer vorne steht. Das ist eine Designentscheidung der Anwendung und steht deshalb in
der Liste, nicht in einem zweiten Prioritätsschema neben dem der Commands (§12).

`HistoryBindings.input` steht vorn: `historyUndo` darf auf nichts durchfallen, denn wovon es
durchfiele, wäre der Undo-Stack des Browsers.

## Was bewusst nicht gebunden ist

| | |
| --- | --- |
| `Transfer` (Paste, Drop, Cut) | §21s Clipboardmodul. Eine Bindung, die hier den Klartext einfügte, wäre die Paste-Implementierung — ohne §21s Sanitizing. Bis dahin bleibt Paste nativ und wird importiert. |
| `insertLink` | Ein Link braucht eine URL, und ein `beforeinput` trägt keine, der ein Editor trauen dürfte. Das Fragen gehört der Anwendung; `LinkBindings.keyboardWith` nimmt ihren Dialog als Funktion. |
| `ReplaceText` (Autokorrektur) | Ersetzt einen Bereich, den der Benutzer nicht ausgewählt hat — er kommt aus `getTargetRanges`. Bis P23 den DOM-Abgleich bringt, der das sicher macht, ist der ehrliche Weg, den Browser machen zu lassen und das Ergebnis zu importieren. |
| `Superscript`, `Subscript` | §8.2 überlässt die Markmenge dem Profil. Eine unbelegte Formatierung ist keine Lücke, sondern ein Editor, der sie nicht anbietet. |

## Tab

§22 ist hier ungewöhnlich streng, und das zu Recht — es ist die eine Taste, die einen
Tastaturbenutzer einsperren kann:

> Tab verlässt die normale Editierfläche. Listeneinrückung oder Code-Tab ist ein ausdrücklich
> aktiviertes Verhalten mit erreichbarer Ausstiegsmöglichkeit. Keine permanente Keyboard-Falle.

Deshalb binden `ListBindings.keyboard` und `CodeBindings.keyboard` Tab **nicht**. Wer Einrückung
auf Tab will, nimmt zusätzlich `tabIndentation` **und** setzt `TabPolicy.IndentsUntilEscape` am
Controller — die Policy ist der Ausgang, und die Taste ohne sie zu binden baute genau die Falle.

Einrücken geht außerdem immer auch ohne Tab (`Ctrl/Cmd+]` und `Ctrl/Cmd+[`), damit die
Tab-Bindung wirklich optional bleibt und nicht der einzige Weg ist.

## Shift+Enter liegt auf der Tastatur, Enter nicht

§15.2 sagt „Text generell über Input-Pipeline", und das gilt: eine Keydown-Tabelle sieht weder
Diktat noch Autokorrektur noch eine mobile Tastatur. Enter folgt dieser Regel — `insertParagraph`
bedeutet überall dasselbe.

Shift+Enter nicht: **WebKit meldet dafür `insertParagraph`**, also würde der Absichtspfad den
Block teilen statt einen Umbruch zu setzen. Ein verhindertes `keydown` erzeugt gar kein
`beforeinput`, und damit ist die Tastaturroute die einzige Stelle, an der sich alle drei Engines
einig sind. Ein Browsertest hat das gefunden.

## Eine Composition ist eine Undo-Stufe

§15.3 verlangt es, §14 macht es möglich, und §7 sorgt dafür, dass es hier steht:
`HistoryBindings.groupCompositions` hängt an den Meldungen des Controllers und öffnet bzw.
schließt eine History-Gruppe.

Warum die History es nicht selbst merkt: §14 gruppiert nach dem, was tatsächlich passiert ist,
und für Tippen ist das richtig. Eine Composition ist der Fall, in dem die Regeln die Gruppe nicht
sehen können — eine Eingabemethode erzeugt Zwischenstände, die wie unabhängige Änderungen
aussehen, und der Benutzer hat eine Taste gedrückt.

Eine verworfene Composition schließt die Gruppe ebenfalls. Eine offen gelassene Gruppe schluckte
alles, was danach getippt wird.

## Undo gehört dem Modell

§15.2: „Native und modellbasierte Undo-Stacks dürfen sich nicht widersprechen." Zwei Stacks über
einem Dokument sind die Art Fehler, die wie Datenverlust aussieht — der Browser erinnert sich an
DOM-Zustände, die das Modell nie erzeugt hat.

Der Browserstack wird nie benutzt. `historyUndo` wird übernommen (womit das native Undo zugleich
verhindert ist), und die Tastenkombination ist zusätzlich gebunden, weil §15.2 warnt, ein
`beforeinput`-Featuretest „garantiert nicht alle Inputtypen".

## Tests

Dieses Modul ist eine Tabelle, und Tabellen prüft man dort, wo sie wirken: im Browser-Gate
([ember-integration/browser](../ember-integration/browser/README.md), `editing.spec.mjs`). Die
Auflösungsregeln selbst — erster Treffer gewinnt, keine Bindung heißt nativ — stehen headless in
`ember-browser/…/InputPipelineSpec.scala`.

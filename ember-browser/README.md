# scalajs-ember-browser

Was der Editor tut, wenn die Seite schon da ist: eine serverseitig gerenderte Ansicht
übernehmen, ohne zu zerstören, was bis dahin auf der Seite stand.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §17.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-browser` |
| Scala-Paket | `ember.editor.browser` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-html`, `scalajs-ember-jfx`, `com.anjunar:scalajs-jfx-core` |

## Stand

**P20 abgeschlossen.** Vorhanden: `HydrationSnapshot`, `EditorHydration` und
`EditorActivation`. Selection-Port, Fokus und Eingabe sind P21/P22 und stehen noch aus.

## Die drei Teile

| | |
| --- | --- |
| `HydrationSnapshot` | was auf der Seite stand, **bevor** irgendetwas geclaimt wurde |
| `EditorHydration` | der Abgleich, den ein Strukturvergleich nicht leistet |
| `EditorActivation` | die Entscheidung, ob editiert werden darf — und warum noch nicht |

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

## Tests

```bash
sbt --server "scalajs-ember-browser/Test/testOnly *"
```

`HydrationBoundarySpec` prüft die Regeln als Regeln. Was eine lebende Seite braucht — liest die
Erfassung den getippten Wert, überlebt der Fallback einen gescheiterten Claim, behält ein
gültiger Teilbaum seine Host-Identität —, steht im Browser-Gate:
[ember-integration/browser](../ember-integration/browser/README.md), `editor-hydration.spec.mjs`.

Die Aufteilung ist dieselbe wie bei §16 und aus demselben Grund: eine Regel, die durch eine
ihrer Darstellungen geprüft wird, ist einmal geprüft.

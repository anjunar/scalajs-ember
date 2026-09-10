# Browser-Harness der Ember-Integration

Fuehrt die tatsaechlich gelinkte Scala.js-Anwendung in Chromium, Firefox und WebKit aus.
Nicht publiziert, kein Teil eines Artefakts.

## Warum es das gibt

§24: "Viele npm-Core-Tests verwenden einen Stub; jsdom liefert keine belastbare
IME-/Selection-Engine. Der neue Browser-Harness muss den tatsaechlich gelinkten Scala-Editor
ausfuehren."

Ein gruener Lauf hier heisst: Ember-Engine und JFX-Runtime arbeiten im selben Bundle
zusammen, echte Tastendruecke laufen bis ins Modell durch, und der Dispose raeumt beides ab.

## Ausfuehren

```
sbt --server "scalajs-ember-integration/fullLinkJS"
cd ember-integration/browser
npm ci
npx playwright install chromium firefox webkit
npm run verify
```

`npm run verify` laeuft zweistufig:

| Schritt | Was er beweist |
| --- | --- |
| `test:server` | Das Modul laedt im Serverprozess, ohne `window` oder `document` zu beruehren (§15.2). Voraussetzung fuer SSR. |
| `test:browser` | Die volle Kette in drei echten Engines. |

Ohne vorherigen Link brechen beide mit einer Meldung ab, statt gegen eine alte Ausgabe zu
laufen.

## Was hier nicht geprueft wird

Die Fixture-App rendert nach jedem Commit vollstaendig neu und hat kein `contenteditable`.
Beides ist Absicht:

- Die gezielte, keyed Projektion entsteht in P09. Hier waere sie verfrueht.
- Native Eingabe mit Composition, Mutation-Observer und Recovery ist P21 bis P23.

Reale IME- und Screen-Reader-Abnahmen brauchen dokumentierte manuelle Tests (§24) und lassen
sich durch synthetische Ereignisse nicht ersetzen.

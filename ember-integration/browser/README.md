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

## Firefox auf diesem Arbeitsplatz

Der von Playwright mitgelieferte Firefox startet auf manchen Windows-Staenden nicht.
Playwright meldet nur `browserType.launch: spawn UNKNOWN` -- noch vor dem ersten Test, es
laeuft also kein einziger Fall.

Die Ursache steht im Anwendungsereignisprotokoll:

```
Fehler beim Generieren des Aktivierungskontextes fuer "...\firefox\firefox.exe".
Die abhaengige Assemblierung "mozglue,language="*",type="win32",version="1.0.0.0""
konnte nicht gefunden werden.
```

`firefox.exe` verlangt im Manifest die private Side-by-Side-Assembly `mozglue`. Die
zugehoerige `mozglue.dll` liegt daneben und traegt ein passendes eingebettetes Manifest --
Windows nimmt es trotzdem nicht an. Geprueft und ausgeschlossen:

- Kein beschaedigter Download: `playwright install --force firefox` liefert dieselbe Datei.
- Keine Identitaetsabweichung: angefordertes und deklariertes `assemblyIdentity` stimmen
  ueberein.
- Nicht die Ablageform: eine zusaetzliche `mozglue.manifest` daneben aendert nichts.
- **Nicht die Maschine**: ein regulaer installierter Firefox derselben Version hat dieselbe
  Manifest-Abhaengigkeit und startet einwandfrei.

Das Problem liegt also im mitgelieferten Build, nicht im Projekt und nicht in der
SxS-Mechanik des Rechners.

### Ausweg

Playwright kann den installierten Firefox ansteuern:

```
EMBER_FIREFOX_CHANNEL=moz-firefox npm run test:browser
```

Damit sind alle drei Engines gruen.

Der Kanal spricht WebDriver BiDi statt Juggler. Getestet wird dieselbe Engine, aber ueber
einen anderen Steuerkanal -- deshalb ist er ausdruecklich **nicht** die Voreinstellung. Die
CI laeuft unter Linux gegen den mitgelieferten Build, und der bleibt der kanonische
Zielbrowser.

Wer die Ursache abschliessend klaeren will, braucht eine Administratorkonsole:

```
sxstrace Trace -logfile:sxs.etl
"...\ms-playwright\firefox-1543\firefox\firefox.exe" --version
sxstrace Stoptrace
sxstrace Parse -logfile:sxs.etl -outfile:sxs.txt
```

## Was hier nicht geprueft wird

Die Fixture-App rendert nach jedem Commit vollstaendig neu und hat kein `contenteditable`.
Beides ist Absicht:

- Die gezielte, keyed Projektion entsteht in P09. Hier waere sie verfrueht.
- Native Eingabe mit Composition, Mutation-Observer und Recovery ist P21 bis P23.

Reale IME- und Screen-Reader-Abnahmen brauchen dokumentierte manuelle Tests (§24) und lassen
sich durch synthetische Ereignisse nicht ersetzen.

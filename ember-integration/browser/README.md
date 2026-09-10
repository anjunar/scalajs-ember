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

## Die Suiten

| Datei | Was sie prüft |
| --- | --- |
| `identity.spec.mjs` | Die Ember-Engine über die volle Kette: DOM-Ereignis, Command, Transaktion, Commit, Projektion. Dazu Dispose und der Error-Sink. |
| `text-splice.spec.mjs` | `spliceText` aus jfx-core: UTF-16-Offsets, Identität des DOM-Textknotens, und dass ein unveränderter Wert **keinen** Schreibzugriff auslöst. |
| `move.spec.mjs` | `Runtime.move`: Element- und Listeneridentität, Synchronität von logischer Kindliste und DOM, abgewiesene Operationen ohne Nebenwirkung. |
| `projection.spec.mjs` | Die keyed `DocumentView` aus P09: DOM-Identität über Textedit und Move, der Umfang der Schreibzugriffe, und dass SSR und Browser initial dasselbe liefern. |

`text-splice` und `move` verdoppeln nicht die Suite des Nachbar-Repos. `HostEditingSpec` deckt
dieselben Verträge dort JVM-seitig ab; was es nicht kann, ist **DOM-Knotenidentität** — das
ist ein `===`-Vergleich auf echten DOM-Objekten und braucht einen Browser. Für den Editor
hängt daran alles: ein neu erzeugter Textknoten nähme Caret, Selection und eine laufende
IME-Eingabe mit ins Grab.

Der No-op-Vertrag („identischer Text erzeugt keine Mutation") wird mit einem
`MutationObserver` belegt — der einzige ehrliche Zeuge, denn er sieht auch einen
Schreibzugriff, der denselben Wert setzt. Mit Gegenprobe, sonst wäre der Test auch bei totem
Observer grün.

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

## Zwei Fixtures, zwei Verfahren

`emberFixtures` (aus `identity.spec.mjs` gefahren) rendert nach jedem Commit vollstaendig neu.
Das ist absichtlich die naive Variante: dort geht es darum, dass die volle Kette ueberhaupt
traegt, und dafuer ist die einfachste denkbare Projektion die ehrlichste.

`projectionFixtures` faehrt daneben die echte `DocumentView`. Der Vergleich beider Wege in
derselben Seite ist kein Zufall -- was `projection.spec.mjs` zeigt, ist genau der Unterschied:
ein Textedit schreibt einen einzigen `characterData`-Eintrag statt den Baum neu aufzubauen.

## Was hier nicht geprueft wird

Keine der Fixtures hat `contenteditable`:

- Native Eingabe mit Composition, Mutation-Observer und Recovery ist P21 bis P23.

Reale IME- und Screen-Reader-Abnahmen brauchen dokumentierte manuelle Tests (§24) und lassen
sich durch synthetische Ereignisse nicht ersetzen.

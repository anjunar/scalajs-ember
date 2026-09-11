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

**Der Link muss `fullLinkJS` sein.** Der Server liest `target/ember-browser-tests/`;
`fastLinkJS` schreibt nach `target/ember-browser-tests-fast/`, und der Harness sieht davon
nichts. Er scheitert laut, wenn **kein** Output da ist — ein **alter** sieht genauso aus wie
ein aktueller. Wer waehrend einer Fehlersuche `fastLinkJS` laufen laesst, debuggt das Bundle
von vorhin; das hat in P20 einen halben Diagnosezyklus gekostet.

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

## Das Formularfeld (P19b)

`nojs-form.spec.mjs` laeuft mit **abgeschaltetem JavaScript**. Das ist der Punkt: §16 verlangt
eine Textarea, die ohne JavaScript sichtbar, benannt, fokussierbar und normal submitbar ist,
und das kann nur eine echte Engine beantworten.

Der Testserver rendert das Feld dabei **im Serverprozess** — er importiert das Scala.js-Modul
und ruft `formFixtures.renderForNoScript`. Moeglich ist das nur, weil §15.2 zusichert, dass ein
Modulimport weder `window` noch `document` liest; `server-import.mjs` prueft dieselbe Zusicherung
von der anderen Seite. Ohne sie gaebe es kein serverseitig gerendertes Feld zum Absenden, und
der Test muesste einen handgeschriebenen HTML-String pruefen statt den echten.

Gefunden hat diese Suite eine Verletzung von §16, die der SSR-Test nicht finden konnte: die
Textarea war schon beim Rendern verborgen. Im HTML stand das korrekt, und genau das prueft ein
SSR-Test — dass eine Seite ohne JavaScript damit ein Formular hatte, das niemand ausfuellen
kann, sieht man erst im Browser.

`source-form.spec.mjs` faehrt den Quelltextmodus mit JavaScript: dass dieselbe benannte Textarea
bleibt, dass sie beim Aktivieren verschwindet statt abgeschaltet zu werden, und dass ein
Entwurf eine fremde Dokumentaenderung ueberlebt.

Die Vertragsregeln selbst — Besitz, Baseline, Atomaritaet — stehen headless in
`ember-forms/…/EditorFieldSpec.scala`. Eine Regel prueft man besser als eine ihrer
Darstellungen.

## Hydration (P20)

`editor-hydration.spec.mjs` ist der Teil von §17, den keine headless Pruefung beantwortet. §17
ist eine Aussage ueber eine Seite, die der Server schon geschickt hat: was in der Textarea
stand, bevor das Skript lief, welche Hosts einen Claim ueberleben, und was stehen bleibt, wenn
er scheitert.

Die Suite laedt `/form`, haengt danach das Modul als `<script type="module">` an und hydriert
erst dann — dieselbe Reihenfolge, die eine echte Seite hat, und der einzige Weg, vor dem Skript
zu tippen.

Der Hydrationscontainer ist `<div id="editor-host">` und nicht das `<form>`:
`HydratingCursor.root(container)` beginnt beim **ersten hydrierbaren Kind**, und das war sonst
das `<label>` davor. Eine Anwendung muss dasselbe tun — der Container ist die Grenze des
Komponentenbaums, nicht der Kasten drumherum.

Was hier gefunden wurde und headless unsichtbar war: die Vorschau hydrierte ueber den Cursor der
Boundary-Komponente statt ueber den isolierten, den sie ihrem Block gibt. Ein `SsrCursor` hat
nichts zu uebernehmen und faellt darauf nicht herein.

Die Entscheidungsregeln — wann aktiviert werden darf und warum nicht — stehen headless in
`ember-browser/…/HydrationBoundarySpec.scala`.

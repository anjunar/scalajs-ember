# IME-Abnahme von Hand

P23 nennt die Bedingung ausdrücklich:

> Reale IME-Abnahme erst mit dokumentiertem Geräteergebnis.

Diese Datei ist das Formular dafür. Ohne ausgefüllte Zeilen gilt die IME-Unterstützung als
**nicht abgenommen**, gleich wie viele automatische Tests grün sind.

## Warum das nicht automatisierbar ist

`composition.spec.mjs` prüft das *Protokoll*: wer während einer Composition schreiben darf, was
der Schutzbereich umfasst, wie sie endet, dass eine Undo-Stufe entsteht. Dafür genügen
synthetische `compositionstart`/`compositionend`, und dafür sind sie auch das richtige Werkzeug.

Was sie nicht prüfen können, ist die Eingabemethode selbst. §15.3 sagt es in einem Satz:

> Ein willkürlicher Timeout ohne reproduzierten Browserfall ist kein Abschlussprotokoll.

Und §15.2 zählt auf, woran das liegt — reale Fälle, die keine Spezifikation vorhersagt:
koreanische 10-Tasten-Eingabe auf iOS ohne `compositionstart`/`compositionend`, aber mit nicht
kollabiertem Delete-TargetRange; Android, das trotz `preventDefault` nativ löscht; mehrere
`beforeinput` vor einem `input` bei Autokorrektur; verwaistes `insertCompositionText` nach einem
Format-Command.

Eine synthetische Composition reproduziert keinen davon. Ein Mensch mit der betreffenden
Tastatur schon.

## Vorbereitung

```bash
sbt --server "scalajs-ember-integration/fullLinkJS"
cd ember-integration/browser && node server.mjs
```

Dann `http://127.0.0.1:4188/` öffnen und in der Konsole:

```js
window.editing.mount(document.getElementById('root'))
document.getElementById('root').focus()
```

Nützlich währenddessen:

| | |
| --- | --- |
| `window.editing.text()` | das Dokument, Block für Block |
| `window.editing.domText()` | was im DOM steht |
| `window.editing.state()` | `Ready`, `Composing`, `Recovering` |
| `window.editing.compositionLog()` | Anfang, Ende und Sitzungsnummern |
| `window.editing.undoDepth()` | Anzahl der Undo-Stufen |
| `window.editing.viewProblems()` | wo Ansicht und Dokument auseinandergehen |

## Die Fälle

Jede Zeile wird mit **Gerät, Betriebssystem, Browser und Eingabemethode** ausgefüllt. „Geht" ohne
diese Angaben ist kein Ergebnis.

### 1. Gewöhnliche Composition

Den Caret mitten in den ersten Absatz setzen, ein Wort in der IME schreiben und bestätigen.

- [ ] Der bestätigte Text steht **einmal** im Dokument, nicht zweimal.
- [ ] `state()` ist während der Eingabe `Composing` und danach `Ready`.
- [ ] `undoDepth()` ist um genau **eins** gewachsen.
- [ ] Ein `Ctrl/Cmd+Z` nimmt das ganze Wort zurück, nicht eine Silbe.

### 2. Abbruch

Eine Composition beginnen und mit `Escape` abbrechen.

- [ ] Im Dokument steht nichts Halbfertiges.
- [ ] `state()` ist wieder `Ready`.

### 3. Fokusverlust mitten im Wort

Eine Composition beginnen und in ein Feld außerhalb des Editors klicken.

- [ ] Was bis dahin im DOM stand, ist im Dokument angekommen (§15.3: „Blur erfasst noch offene
      native Änderung").
- [ ] `state()` ist `Ready`.

### 4. Ersetzung über Grenzen hinweg

Einen Bereich auswählen, der eine **Mark** (fett), ein **Atom** oder eine **Blockgrenze** kreuzt,
und darüber komponieren.

- [ ] Der Text steht danach richtig da.
- [ ] `viewProblems()` ist leer.
- [ ] Undo nimmt die Ersetzung in einem Schritt zurück.

### 5. Autokorrektur und Vorschlagsleiste

Auf einer mobilen Tastatur ein Wort tippen und einen Vorschlag annehmen.

- [ ] Ersetzt wird das Wort, nicht die Auswahl.
- [ ] Nichts wird doppelt eingefügt.

### 6. Löschen während der Eingabe

Während einer laufenden Composition `Backspace` drücken.

- [ ] Es verschwindet genau ein Zeichen der Composition, nichts davor.

### 7. Eine IME ohne Composition-Ereignisse

Vor allem die koreanische 10-Tasten-Eingabe auf iOS (§15.2 nennt sie).

- [ ] Der Text kommt trotzdem an.
- [ ] `compositionLog()` notieren — leer ist hier ein gültiges und wichtiges Ergebnis.

### 8. Eine unabhängige Änderung während der Eingabe

In der Konsole während einer laufenden Composition:

```js
window.editing.offerEdit('X')
window.editing.splice('t0', 0, 0, 'Y')
```

- [ ] `offerEdit` wird zurückgestellt und läuft erst nach dem Abschluss.
- [ ] `splice` wird abgewiesen (`false`) — das Gate greift auch an der Sitzung vorbei am
      Controller.
- [ ] Die Composition bleibt unbeschädigt.

## Ergebnisse

| Datum | Gerät / OS | Browser | Eingabemethode | Fälle | Befund |
| --- | --- | --- | --- | --- | --- |
| | | | | | |

Ein Fall, der scheitert, gehört als Trace hierher: `inputType`, `data`, `cancelable` und die
Reihenfolge der Ereignisse. §15.2 verlangt das ausdrücklich — „Feature Detection, Event-Traces
und reale Tests bestimmen den Adaptervertrag" —, und aus einem Trace wird ein automatischer Test,
aus einem „geht nicht" nicht.

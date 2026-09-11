# scalajs-ember-forms

Das Editorfeld als Formularfeld: genau eine benannte Textarea, ein geschützter Quelltextentwurf
und ein Weg, der ohne JavaScript funktioniert.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §16.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-forms` |
| Scala-Paket | `ember.editor.forms` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-markdown`, `scalajs-ember-json`, `scalajs-ember-html`, `scalajs-ember-jfx` |

## Stand

**P19b abgeschlossen.** Vorhanden: `FieldCodec`, `EditorField` samt `EncodedFieldValue`,
`SourceDraft`, `EditorFormBinding` und `EditorFieldView`.

§6 führt außerdem `browser` und `jfx-forms` als Abhängigkeiten. Beide fehlen hier mit Absicht:
`ember-browser` gibt es erst ab P20, und die Textarea kommt aus jfx-core (P19a). Der
Media-Service aus §6 ist P26.

## Verwendung

```scala
val field   = EditorFields.markdown("body", regeln, generator)
val session = EditorSession.create(dokument, extensions, config)   // mit FormFieldExtension(field)
val binding = new EditorFormBinding(session, field)

binding.submitValue        // immer eindeutig
binding.enterSource()      // die Textarea übernimmt
binding.editDraft(text)    // unbestätigte Eingabe
binding.applyDraft()       // atomar gegen die Baseline
```

`EditorFields.json(name, support)` ist die zweite Wahl. §16 verlangt, dass die Anwendung sich
ausdrücklich entscheidet — es gibt keine Voreinstellung und keinen automatischen Wechsel.

## Die Formatgrenze liegt **vor** dem Commit

Das ist der Teil von §16, der leicht übersehen wird und alles andere trägt:

> Diese Formatgrenze wird **vor Commit** geprüft, auch für programmgesteuerte Commands. […]
> Ein `ToggleUnderline` in einem Strict-CommonMark-Feld kann daher keinen kanonischen Zustand
> erzeugen, dessen Formwert veraltet bleibt.

Der Kern hatte die Form dafür schon: `StateField.reduce` läuft in §10s Schritt 5 gegen den
fertig normalisierten Kandidaten, und ein `Left` weist die Transaktion ab. Deshalb sind die
„Darstellbarkeitsregel" und das „abgeleitete StateField" hier **ein** Objekt und nicht zwei —
eine separate `PreCommitRule`, die dieselbe Frage stellte, würde entweder das Encode
verdoppeln oder ihm widersprechen.

Die Folge ist die, die §16 nennt: ein `ToggleUnderline` in einem Strict-Feld erzeugt **kein**
Dokument, dessen Formwert veraltet ist. Es erzeugt gar keines — Dokument, Formwert und History
bleiben unberührt. Ein Test hält jedes der drei einzeln nach; „nur die UI deaktivieren" wäre
etwas anderes.

Das abgeleitete Feld wird **nicht** persistiert und bei Undo neu berechnet (§16). Ein
gecachtes Encode-Ergebnis in einem History-Snapshot wäre ein Cache, den niemand invalidiert.

## Der Quelltextentwurf

`SourceDraft` hält den bearbeiteten String, die Baseline-Revision und die Textarea-Auswahl. Er
ist **kein zweites Dokument**, und §16 sagt das zweimal:

> Document→Form-Projektion darf diesen Draft nicht überschreiben.
>
> Der Draft ist ein nicht bestätigter Eingabewert, keine zweite kanonische
> Dokumentrepräsentation.

Die beiden Fehlerbilder, gegen die das schützt, sind symmetrisch: das Dokument überschreibt
still, was jemand getippt hat — oder der Entwurf wird für die Wahrheit gehalten und verwirft
still, was er nie gesehen hat. Die **Baseline** macht das zweite erkennbar: ein Entwurf gegen
Revision 7, angewandt bei Revision 9, ist veraltet, und `FieldError.StaleDraft` sagt es, statt
zu raten.

Ein fehlgeschlagener Import **erhält den sichtbaren String** (§16). Drei Wege führen dorthin:
der Text parst nicht, das Dokument ist weitergezogen, oder das Ergebnis hat im Format keine
Darstellung. In allen dreien bleibt der Modus stehen und der Text da.

Verwerfen ist eine **ausdrückliche Formaktion** — nie implizit, nie als Nebeneffekt.

### Fremde Änderungen während der Bearbeitung

§16 lässt zwei Antworten zu, und die Anwendung wählt über `IntentPolicy`:

| | |
| --- | --- |
| `Defer` | zurückstellen. Nach dem Import werden sie **neu validiert**, nicht abgespielt. |
| `Reject` | jetzt abweisen, mit `FieldError.SourceBusy`. |

Zurückgestellte Intents sind deshalb **Thunks** und keine Werte: sie sollen das importierte
Dokument sehen, nicht das, gegen das sie geschrieben wurden. §16 nennt Upload-Completion als
den Fall, der das nötig macht.

## Ohne JavaScript

Die Textarea ist ohne JavaScript **sichtbar**, benannt, fokussierbar und normal submitbar.
Verborgen wird sie erst *nach erfolgreicher Aktivierung* — und diese Reihenfolge ist der ganze
Non-JS-Vertrag. Eine serverseitig gerenderte Seite, deren Textarea schon beim Rendern
verschwindet, hat ein Formular, das niemand ausfüllen kann.

Genau das stand hier zuerst falsch, und ein Browsertest hat es gefunden. Ein SSR-Test hätte es
nicht: er prüft, was im HTML steht, und `hidden` stand korrekt drin.

**Verborgen ja, `disabled` nie.** Ein disabled Control ist kein *erfolgreiches* Formularfeld —
das Formular sendete für diesen Namen gar nichts.

Die Vorschau trägt **keinen** Formularnamen (§16: „Der Editor-Host hat keinen konkurrierenden
Formularnamen"). Zwei Werte für einen Namen wären für einen Server nicht auflösbar.

Action, Methode, CSRF, Validierung, Persistenz und Fehlerrückgabe liefert die Anwendung. Der
Editor erfindet dafür keinen HTTP-Endpunkt.

## Die Kosten, ausgesprochen

§16 verlangt es ausdrücklich:

> Das Materialisieren/Zuweisen des vollständigen Formularstrings kostet dennoch mindestens
> dessen Länge. Diese Kosten werden getrennt von Core-/Projection-Lokalität gemessen und nicht
> als O(1) dargestellt.

Also deutlich: ein Tastendruck fasst in der Projektion eine Handvoll Komponenten an — P09 misst
das —, **und** kodiert zusätzlich das ganze Dokument zu einem String. Das zweite ist linear in
der Dokumentgröße, bei jedem Commit. Das ist kein wegzuoptimierender Fehler, sondern was „ein
einziger Formwert" bedeutet.

## Tests

```bash
sbt --server "scalajs-ember-forms/Test/testOnly *"
```

`EditorFieldSpec` prüft die Verträge aus §16 headless: Besitz, Baseline, Atomarität, die
Formatgrenze und die Intent-Politik. Die HTML-Struktur aus §16 ist eine
„Strukturillustration"; was sie festlegt, sind Regeln, und eine Regel prüft man besser als eine
ihrer Darstellungen.

`EditorFieldViewSpec` rendert durch einen `SsrCursor` — denselben Weg, den ein Server nimmt.

Die Knotenarten dieser Suiten sind **lokal**. §6 gibt diesem Modul kein Rich-Text-Profil, und
ein eigener Blocktyp ist die Probe, dass es keines braucht — dasselbe Argument wie beim lokalen
`BlockNode` in `ember-image`.

Was nur eine echte Engine beantwortet, steht im Browser-Gate:
[ember-integration/browser](../ember-integration/browser/README.md), `nojs-form.spec.mjs` und
`source-form.spec.mjs`. Der Testserver rendert das Feld dabei **im Serverprozess** durch
denselben `EditorFieldView` — möglich nur, weil §15.2 zusichert, dass ein Modulimport weder
`window` noch `document` liest.

# Medienservice und Upload-Lifecycle (P26)

`MediaService[F]` ist ein injizierter Anwendungsservice:

```scala
def upload(file: F, cancellation: MediaCancellation): Future[MediaReference]
```

Der Coordinator ist ohne Browser nutzbar; `F` ist dort beispielsweise eine
Testkennung und im Browser ein `dom.File`. Storage, HTTP, Backendvalidierung,
CSRF und Bereinigung nicht mehr referenzierter Dateien bleiben beim Service.
Der Editor führt weder einen produktiven Upload-Endpunkt noch ein Storage-Modell ein.

## Einbindung

`ImageExtension` und für Bereichsersetzung `ClipboardExtension` registrieren.
Der Coordinator erhält dieselbe dauerhafte URL-Policy wie das Dokumentprofil:

```scala
val media = new MediaCoordinator[dom.File](
  session, service, generator,
  policy = mediaPolicy,
  availability = () =>
    if binding.mode == FieldMode.Source then MediaAvailability.SourceBusy
    else if input.state != ControllerState.Ready then MediaAvailability.CompositionBusy
    else if input.mode != EditorMode.Editable then MediaAvailability.ReadOnly
    else MediaAvailability.Ready,
  previews = BrowserMediaPreviews.in(selection.scope.window.get),
  changed = renderMediaStatus
)
val picker = new BrowserMediaPicker(
  fileInput, session, selection, media,
  alt = () => readAltText(),
  report = reportPickerResult
)
// From a real user activation, e.g. the application's button click:
picker.open()
// Pass picker.receive as the BrowserClipboardController's files callback.
val composition = input.onComposition { _ =>
  if input.state == ControllerState.Ready then media.resume()
}
```

Nach erfolgreichem `applyDraft()` oder nach `discardDraft()` ebenfalls
`media.resume()` aufrufen. Der Aufruf prüft den aktuellen Zustand und das Ziel neu;
er spielt keinen gespeicherten Draft ab. Die `availability`-Funktion muss alle
Schreibsperren der Anwendung abbilden. Die normalen Pre-Commit-/Formatregeln gelten
zusätzlich. Ein weiterhin gesperrtes Ziel bleibt in `Waiting`.

Beim Abbau erst die Composition-Registrierung, den Clipboard-Controller, den
Picker und den Coordinator entsorgen, danach Input/Selection/View/Session.
`media.dispose()` bricht ausstehende Anfragen sofort ab und gibt Previews frei.
Ist nur die Session bereits entsorgt, kann eine spätere Completion ebenfalls
nichts einfügen; sofortige Ressourcenfreigabe erfordert den Coordinator-Abbau.

## Ziele und Dokumentgeneration

`capture(range)` speichert eine validierte, geordnete Range samt Revision,
Coordinator-Identität, Generation und gegebenenfalls dem ausgewählten Inhalt.
`capture(bookmark)` übernimmt ein noch auflösbares Clipboard-/Drop-Ziel.
`upload(file, target, alt)` und `insert(reference, target, alt)` liefern eine
Auftrags-ID, **keine Bestätigung einer bereits erfolgten Einfügung**. Deren Ergebnis
steht in `statuses` bzw. im `changed`-Callback.

Während des Dateidialogs geänderter Auswahltext führt bereits vor Uploadbeginn
zur Ablehnung. Nach dem Beginn verfolgt jeder Auftrag seine Punkte bei jedem
Commit und benötigt damit keine unbegrenzte globale Mapping-Retention. Mehr als
64 Zwischenedits sind geprüft. Entfernte Ziele werden nicht durch eine ungefähre
Ersatzposition ersetzt. Geänderter ausgewählter Inhalt wird bei Completion nicht
gelöscht. Normales Tippen außerhalb der Auswahl darf das Ziel weiterbewegen.

Ein wirksamer History-/Import-Commit oder ein nichttrivialer `Transaction.restore`
verwirft ausstehende Aufträge. Dafür trägt `ChangeSet.documentReplaced` den
Restore-Vertrag ausdrücklich weiter, auch wenn Knoten-IDs überleben. Ein echter
No-op-Restore bleibt ein No-op. Für einen Anwendungswechsel zwischen Datensätzen
mit identischem Dokumentinhalt steht zusätzlich `media.invalidate()` bereit.

## Ergebnisse, Abbruch und Parallelität

`MediaPhase` unterscheidet `Uploading`, `Waiting`, `Inserted`, `Failed`,
`Cancelled` und `Discarded`. Fortschritt und optionale Object-URL stehen nur im
`MediaStatus`; im Dokument entsteht erst die fertige `ImageNode` mit dauerhafter
Referenz. Jede erfolgreiche Einfügung bildet eine eigene History-Stufe. Undo
entfernt das Bild, niemals eine gespeicherte Datei.

Der Service kann seinen Transport über `cancellation.onCancel(() => abort())`
anschließen und mit `reportProgress(fraction)` Fortschritt melden. Abbruch wird
höchstens einmal signalisiert. Späte Fortschritte und Completions wirken nicht
mehr. Ein erfolgreicher Abschluss löst keinen Abort aus und setzt `isCancelled`
nicht auf wahr. Preview-Freigabe wird für jeden terminalen Weg einmal ausgeführt.

Standardlimits: acht aktive Aufträge und 32 aufbewahrte terminale Statuswerte,
beide konfigurierbar. Der Picker prüft die gesamte Dateiliste vor dem Start
gegen Kapazität, Dateityp und Größe. Standardmäßig sind PNG, JPEG, GIF und WebP
bis 10 MiB erlaubt; die Backendprüfung bleibt erforderlich.

Bei Mehrfachauswahl ersetzt nur die erste Datei den ausgewählten Bereich;
weitere Dateien fügen am gemappten hinteren Rand ein. Completions dürfen in anderer
Reihenfolge eintreffen. Jeder Auftrag ist unabhängig abbrechbar. Scheitert der
erste, bleibt der Auswahltext bestehen; erfolgreiche weitere Bilder können trotzdem
eingefügt werden. Die erste Datei wird nicht durch einen späteren Auftrag ersetzt.

## Referenz- und Formatgrenze

Jede Serviceantwort wird nochmals durch die empfangende `MediaUrlPolicy` geprüft.
Ein Service, der HTTP erlaubt, kann damit die HTTPS-Policy des Editors nicht
umgehen. `data:`, `blob:` und Base64-Dateidaten werden keine MediaReferences.
Externe HTTPS-/relative Referenzen verwenden `insert(...)` ohne Upload.

Alt-Text wird ausdrücklich übergeben. Ein dekoratives Bild hat leeren Alt-Text;
der Dateiname wird nicht automatisch verwendet.

**Strict CommonMark kann eine zusätzliche `MediaId` nicht speichern.** Eine
Markdown-Anwendung kann eine dauerhafte URL als vollständige Referenz verwenden;
bei der Testanwendung enthält diese bereits den Inhaltshash. Wer getrennte
Media-IDs erhalten muss, wählt ein geeignetes Format, etwa das JSON-Feld.
Der Coordinator entfernt Metadaten nicht selbst. Eine unrepräsentierbare
Serviceantwort scheitert atomar an der bestehenden Feldregel, einschließlich einer
eventuell vorgesehenen Bereichslöschung. Dieser Fehlerpfad ist im Browser geprüft.

## Multipart ohne JavaScript

Die Anwendung empfängt Source, File, Alt-Text und eine erlaubte fachliche Position.
Nach Validierung und Speicherung importiert sie Source und verwendet die normalen
Core-/Image-Operationen. Ein Browser-DOM-Offset ist kein Server-Einfügeziel.
Fehler liefern die ursprüngliche Source und sonstige Formularwerte zurück.

Der ausführbare, ausdrücklich begrenzte Testserver-Vertrag steht in
[`ember-integration/browser/media-service-contract.md`](../ember-integration/browser/media-service-contract.md).
`MediaCoordinatorSpec` prüft den Lifecycle headless. Die Browserprüfungen liegen
in `media.spec.mjs` und `multipart.spec.mjs`; der Multipart-Test läuft mit
deaktiviertem JavaScript gegen das echte serverseitige `EditorFieldView`.

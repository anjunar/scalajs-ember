# Ember Editor Showcase

Eine eigenständige Scala.js-Anwendung in diesem Repo, aufgebaut wie die Showcase in
`scalajs-ui/scala/scalajs-ui-demo`: Navigation, echte UI-Komponenten, Beispieldokumente
und direkt eingebundene Viewport-Fenster.

Die Demo linkt die **lokalen Ember-Projekte**. Sie benötigt weder den Nachbar-Checkout
noch eine vorherige Veröffentlichung von Ember. Nur `scalajs-ui-core` und
`scalajs-ui-viewport` werden als veröffentlichte Maven-Artefakte (1.0.1) aufgelöst.
Sie hat keine Lexical-Abhängigkeit und wird selbst nicht veröffentlicht.

## Starten

Voraussetzungen: Java/sbt und Node.js.

Aus der Repo-Wurzel:

```powershell
npm --prefix ember-demo run dev
```

Der Befehl führt `sbt --server "scalajs-ember-demo/fastLinkJS"` aus und startet danach
[http://127.0.0.1:4200](http://127.0.0.1:4200). Zum normalen Start ist kein `npm install`
erforderlich; die kleinen Build-/Server-Skripte benötigen nur Node.js.

Die veröffentlichte Showcase liegt unter
[anjunar.github.io/scalajs-ember](https://anjunar.github.io/scalajs-ember/). Jeder Push auf
`master` baut zunächst die optimierte Anwendung, prüft sie unter dem echten Unterpfad
`/scalajs-ember/` und veröffentlicht anschließend das Pages-Artefakt. Pull Requests bauen und
prüfen dasselbe Artefakt, veröffentlichen es aber nicht.

Alternativ getrennt, etwa für die Entwicklung:

```powershell
sbt --server "~scalajs-ember-demo/fastLinkJS"
```

In einem zweiten Terminal:

```powershell
npm --prefix ember-demo run serve
```

Nach einem Scala-Build die Seite neu laden. CSS wird bei jedem Laden direkt aus dem
Repo ausgeliefert. `EMBER_DEMO_PORT` setzt einen anderen Port.

## Beispiele und Bedienung

- **Artikel schreiben**: Überschriften, Textformatierung, Zitat, nummerierte Liste und Link.
- **Notizen & Listen**: verschachtelte Listen mit Ein- und Ausrücken.
- **Code & Medien**: Scala-Codeblock, Bild aus dem Repo und Trennlinie.
- **Leeres Dokument**: eine tatsächlich editierbare leere Seite.

Die Navigation verwendet Links mit Hash-Routen, einschließlich Browser-Zurück/Vorwärts.
Jedes Beispiel behält Dokument, Auswahl und Undo-Historie beim Seitenwechsel.
Ein Neuladen der Seite verwirft die Änderungen; es gibt weder Backend noch automatische Speicherung.
JSON und Markdown können über die Quellansicht heruntergeladen werden.

Die Ribbon gruppiert Verlauf, Schrift, Absatz, Listen und Einfügen. Aktive Formate werden
angezeigt, nicht verfügbare Aktionen deaktiviert. Tastaturbedienung: ein Tab-Stopp für
die Ribbon, Pfeiltasten/Home/End zwischen ihren Aktionen. Strg+Z und Strg+Shift+Z steuern
Undo/Redo. Im Editor rückt Tab Listen und Code ein; Escape, dann Tab verlässt die Fläche.

Links und Bilder werden in gewöhnlichen `Viewport.WindowConf`-Fenstern bearbeitet.
Auswahl und Fokus werden beim Öffnen/Schließen erhalten. Adressen werden mit den Ember-Policies
geprüft. Bilder lassen sich über URL, Alt-Text, Titel und Breite bearbeiten.
Dateiuploads benötigen einen Anwendungsservice und sind in dieser lokalen Demo nicht eingerichtet.

Weitere Funktionen: Hell/Dunkel, Lesemodus, Zurücksetzen mit Bestätigung und eine
zuschaltbare Live-Ansicht für Markdown, JSON, HTML und den Dokumentbaum.
Auf schmalen Bildschirmen bleiben Navigation und Ribbon horizontal scrollbar, die
Quellansicht rückt unter den Editor.

## Formatgrenzen

Die Demo verwendet das native CommonMark-Profil, nicht den erweiterten Markdown-Dialekt
von `scalajs-ui-editor`. Tabellen und Raw HTML gehören nicht zu diesem Beispielprofil.
Bildbreiten bleiben im Dokument und in JSON erhalten. Die Markdown-Vorschau zeigt
Exportverluste ausdrücklich an; der Markdown-Download verwendet `Strict` und verweigert
einen verlustbehafteten Export. Für solche Dokumente steht der JSON-Download bereit.

Die Quellansicht dient zur Beobachtung und ist nicht editierbar.

Der Pages-Build rendert die vollständige initiale Artikelansicht mit `SsrCursor` in Node und
schreibt sie in `index.html`. Im Browser übernimmt `HydratingCursor` genau diesen Komponentenbaum;
erst nach dem vollständigen Claim werden Navigation, Editor-Controller und Toolbar aktiviert.
GitHub Pages liefert damit vorgerendertes HTML aus, obwohl es selbst keinen Serverprozess pro
Anfrage betreibt. Die isolierten Fehler-, Recovery- und Verlustschutzfälle bleiben zusätzlich im
separaten `ember-integration`-Modul abgedeckt.

## Prüfen

```powershell
npm --prefix ember-demo ci
npm --prefix ember-demo exec -- playwright install chromium
npm --prefix ember-demo run verify
```

`verify` baut mit `fullLinkJS` und testet danach die optimierte Anwendung in Chromium
auf einem eigenen Server unter Port 4201. Der interaktive Server auf 4200 bleibt dabei unberührt.
Die Tests prüfen Navigation mit erhaltenen Änderungen/Undo, Formatierung, Linkvalidierung,
Dialogauswahl und Fokus, Bildbreiten/Undo, echte Downloads, Lesemodus, Zurücksetzen und
das mobile Layout. Eine reale Geräte-/IME-/Screenreader-Abnahme wird damit nicht ersetzt.

Nur erneut testen: `npm --prefix ember-demo test` (benötigt aktuellen Full-Link).

Den vollständigen Pages-Pfad lokal prüfen:

```powershell
npm --prefix ember-demo run verify:pages
```

Das erzeugt `target/ember-pages`, startet die Prüfung unter
`http://127.0.0.1:4202/scalajs-ember/` und führt die sechs Browserabläufe sowie drei zusätzliche
SSR-/No-JavaScript-/Hydrationstests gegen das tatsächliche statische Artefakt aus. Eine manuelle Vorschau startet mit
`npm --prefix ember-demo run preview:pages`.
Optimierten Build manuell ansehen:

```powershell
npm --prefix ember-demo run build:full
$env:EMBER_DEMO_FULL = "1"
npm --prefix ember-demo run serve
```

## Aufbau

| Datei | Aufgabe |
| --- | --- |
| `Main.scala` | Node-SSR und Browser-Hydration desselben UI-Komponentenbaums |
| `DemoApp.scala` | Navigation, Theme, Viewport und langlebige Beispielsitzungen |
| `DemoExample.scala` | Beispieldokumente |
| `DemoSession.scala` | Lokale Ember-Module, Schema, History und Codecs |
| `DemoPage.scala` | Editorfläche, Browseradapter, Lesemodus und Live-Ansichten |
| `DemoRibbon.scala` | Gruppierte Commands und ihre Zustände |
| `DemoDialogs.scala`, `DemoDialogForm.scala` | Direkte Viewport-Fenster mit Formularinhalt |
| `dev/` | HTML, CSS, lokale SVGs sowie Build-/Start-/Server-Skripte |
| `test/showcase.spec.mjs` | Browserregressionen gegen den Full-Link |
| `test/pages-ssr.spec.mjs` | Vorgerendertes HTML, No-JavaScript-Lesen und Hydration |

Alle Scala-Quellen liegen unter `src/main/scala-3/ember/editor/demo`.

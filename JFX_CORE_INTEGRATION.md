# JFX Core: Grundlagen für externe interaktive Komponenten

Stand: 9. September 2026. Die hier beschriebenen Scala.js-APIs sind in `jfx-core`
implementiert — im Nachbar-Repo `../scalajs-jfx`, nicht hier. Ein externer Editor kann davon
abhängen; Dokumentmodell, Transaktionen, Textnormalisierung, Commands, Selection-Mapping,
Eingabecontroller und Plugins werden im eigenen Repository entwickelt. Der vorhandene
Editor-Prototyp wurde hierfür nicht verwendet.

Dieses Repository (`scalajs-ember`) ist der Konsument. Die Einbindung erfolgt als
Quell-Abhängigkeit über das Verzeichnis, nicht über ein veröffentlichtes Artefakt:

```scala
lazy val jfxCore = ProjectRef(file("../scalajs-jfx"), "scalajs-jfx-core")
```

Alle Quellpfade unten sind relativ zu `../scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/`
zu lesen. Die Verträge sind Befund, kein Entwurf — sie wurden gegen den tatsächlichen
Quellstand geprüft.

## Öffentliche Verträge

| API | Verhalten |
| --- | --- |
| `TextNode.spliceText(start, deleteCount, inserted)` | Ändert denselben Text-Host mit UTF-16-Offsets. Ungültige Bereiche werden vor der Änderung abgewiesen. |
| `TextComponent.spliceText(...)` | Derselbe Vertrag für montierte und noch nicht montierte Komponenten. |
| `Runtime.move(component, destination, index)` | Verschiebt dieselbe physische Elementkomponente und aktualisiert ihre Runtime-Elternzuordnung. Kein erneutes `compose` oder Dispose. |
| `Runtime.contentCursor(component)` | Liefert den Inhaltscursor einer montierten Komponente, einschließlich der Endmarke virtueller Bereiche. |
| `KeyedChildren[K, V, C]` | Stabile Komponenten je Schlüssel; explizites Update für veränderte Werte, Reorder und Entfernung über die Runtime. |
| `TextArea` / `TextArea.textArea` | Native Textarea mit SSR-RCDATA, getrenntem aktuellem Wert und Reset-Basiswert sowie Übernahme vorhandener Eingaben bei Hydration. |
| `HydrationBoundary[A]` | Erfassung und Vorprüfung vor Host-Bindings, isolierter Claim-Abschluss, einmaliger lokaler Neuaufbau bei Fehlern. |
| `AbstractComponent.beforeHostBinding` | Prüft den beanspruchten Host, bevor Klassen und Text-Bindings ihn verändern können. |
| `HostMutationGuard.protect(host)` | Liefert eine explizit freizugebende `Disposable`-Schreibsperre für einen DOM-/SSR-Unterbaum. |
| `DomNodes.raw`, `option`, `wrap` | Öffentliche DOM-Anbindung für native Events, Range/Selection und externe Browseradapter. |

## Text und Schlüsselzuordnung

Text-Splices verwenden dieselben UTF-16-Koordinaten wie DOM `Text`. Sie entscheiden
nicht über Graphem-, Mark- oder Dokumentgrenzen. `setText` und `spliceText` erzeugen
bei identischem Ergebnis keinen DOM-Schreibzugriff; echte Browser-Splices verwenden
`CharacterData.replaceData`. Selection-Mapping über Dokumentoperationen bleibt Aufgabe
des aufrufenden Modells.

```scala
val children = new KeyedChildren[String, Item, ItemView](
  initialItems,
  _.id,
  item => new ItemView(item),
  (view, item) => view.setItem(item)
)
// Nach Runtime.mount und abgeschlossener Hydration:
children.setItems(nextItems)
children.componentFor(id)
children.transferTo(id, otherChildren, destinationIndex)
```

`Item` und `ItemView` sind Anwendungstypen. `KeyedChildren` besitzt ausschließlich seine
eigenen Kinder. Eine Übertragung zwischen zwei solchen Sammlungen erfolgt über
`transferTo`, damit beide Schlüsselindizes konsistent bleiben. Duplikate und aktive
Schreibsperren werden vor einer Aktualisierung geprüft. Ein werfender benutzerdefinierter
Updater wird nicht zurückgerollt; der Aufrufer kann seinen kanonischen Snapshot erneut
projizieren. Werte sollten unveränderlich sein, damit der Gleichheitsvergleich Änderungen erkennt.

Moves unterstützen physische Elementkomponenten mit eigenem Host. Der Zielindex ist
die endgültige Position nach Herausnahme aus dem bisherigen Parent. Virtuelle oder
Text-Wurzeln, Root-Reparenting, Zyklen, andere DOM-Dokumente, andere Renderkontexte und
geänderte geerbte Service-Instanzen werden abgewiesen. Geerbte Services werden nach
Referenzidentität geprüft; lokale Overrides bleiben erhalten. Dynamische Projektion
beginnt nach `cursor.afterHydration`.

Der DOM-Pfad nutzt verfügbares `moveBefore`, sonst `insertBefore` mit Erhalt von Fokus,
gerichteter Textauswahl und Textfeld-Auswahl. Native IME-Zustände sind dadurch nicht
rekonstruierbar. Ein aktiver Eingabebereich muss für diesen Zeitraum geschützt werden.

## Native Textarea und SSR

`jfx.core.layout.TextArea` stellt `value`, `defaultValue`, `valueProperty`, `setValue`,
`setDefaultValue`, `reset` und `readNativeValue` bereit. `valueProperty` ist lesbar;
Schreibzugriffe erfolgen ausdrücklich über `setValue`. Native `input`-Events und
nicht abgebrochene Formular-Resets aktualisieren die Property. Skriptänderungen ohne
Event lassen sich mit `readNativeValue` einlesen. Listener werden bei Dispose entfernt.

SSR verwendet sicher escapten Textarea-Inhalt, keine Text-Kommentaranker. Führende
Zeilenumbrüche überstehen die HTML-Parserregel; CRLF/CR werden zu LF normalisiert.
Eine benannte Textarea ist ohne JavaScript editierbar und nativ submitbar. Für
`name`, Formularzuordnung, `action`, `method` und Persistenz ist die Anwendung zuständig.

Hydration verändert weder `.value` noch `.defaultValue`, Fokus oder Textfeld-Auswahl:
Die tatsächlichen Browserwerte haben Vorrang vor dem Konstruktorwert. `reset()` setzt
den aktuellen Wert auf `defaultValue`, entsprechend `setValue(defaultValue)`.
Nur der native Formular-Reset setzt zusätzlich das interne Dirty-Flag des Browsers zurück.
Da HTML nur einen anfänglichen Textarea-Inhalt kodiert, wird bei SSR der aktuelle Wert
ausgegeben; nach dem Parsen ist dieser zugleich die native Reset-Basis.

## Lokale Hydration

Eine `HydrationBoundary[A]` besitzt ein stabiles physisches Containerelement. Ihr
`capture` liest einen Snapshot, bevor Host-Bindings laufen; `preflight` kann danach
Profil, Version oder Struktur prüfen. Der Body komponiert gewöhnliche JFX-Kinder.
Ein Fehler beim Preflight, beim Claim oder durch überschüssige SSR-Kinder räumt nur
die Kinder dieser Boundary auf und führt genau einen frischen Aufbau aus.

Callbacks aus dem fehlgeschlagenen Versuch werden verworfen. Erfolgreiche und neu
aufgebaute Kinder werden erst nach Abschluss der umgebenden Hydration aktiviert.
Dispose vor Aktivierung storniert diese Callbacks. Andere Formularfelder sollten
außerhalb des austauschbaren Containers liegen und bleiben bei lokaler Recovery erhalten.

Die Boundary ist kein universeller DOM-Diff. Sie setzt einen korrekt beanspruchbaren
Container voraus. Ein fehlender/falscher Container, ein Fehler in `capture` oder ein
Fehler beim erneuten Aufbau propagiert an den Aufrufer. Für solche äußeren Fehler
muss die Anwendung ihren Bootstrap und die vorherige Datensicherung organisieren.
Die Validierung semantischer Textwerte übernimmt `preflight` bzw. der
`beforeHostBinding`-Hook eigener Komponenten.

## Schreibschutz

```scala
val lease = HostMutationGuard.protect(component.host)
// Native Eingabe beobachten; keine JFX-Projektion in den geschützten Bereich.
lease.dispose()
// Jetzt den aktuellen Modellzustand projizieren.
```

Die Sperre blockiert JFX-Schreibzugriffe im Unterbaum sowie dessen Verschieben,
Entfernen und die Entfernung seiner Vorfahren. `HostWriteBlocked` entsteht vor
Textänderung, Mount/Move/Unmount und Änderung der logischen Kindzuordnung. Mehrere
überlappende Sperren sind unabhängig; Freigabe ist idempotent. Vor dem Unmount muss
der Eigentümer seine Sperren freigeben. Es gibt keine implizite Warteschlange.

Roh-DOM-Schreibzugriffe und native Browsermutationen werden nicht abgefangen.
Observable Model-Properties und beliebige Anwendungscallbacks sind keine Host-Transaktionen;
die Anwendung muss eine durch die Sperre unterbrochene Projektion selbst erneut anstoßen.
Die Sperre ersetzt weder einen MutationObserver noch Composition- und Selection-Controller.

## Abnahme

`HostEditingSpec` deckt SSR, Lifecycle, Moves, Context-Invarianten, Keyed-Updates,
Schreibschutz und Textarea ab. Das nicht publizierte sbt-Modul
`scalajs-jfx-core-browser-tests` exportiert nur eine Testanwendung. Der Playwright-Harness
unter `npm/jfx-core-browser-tests` führt dieselben öffentlichen APIs in Chromium,
Firefox und WebKit aus, einschließlich nativer Formulare ohne JavaScript.

Diese Gates laufen **im Nachbar-Repo**, nicht hier:

```powershell
cd ../scalajs-jfx
sbt --server "Test/testOnly *"
sbt --server "scalajs-jfx-core-browser-tests/fullLinkJS" "scalajs-jfx-bridge/fullLinkJS"
npm exec --workspace npm/jfx-core-browser-tests -- playwright install chromium firefox webkit
npm run verify --workspaces --if-present
```

Die CI installiert unter Linux zusätzlich die Browser-Systemabhängigkeiten mit
`playwright install --with-deps`. Für eine gezielte lokale Prüfung:

```powershell
npm run test --workspace npm/jfx-core-browser-tests -- --project chromium --project webkit
```

Das sind JFX-Vertragstests. Reale IME-, Accessibility- und Editor-Interoperabilitätstests
gehören zur Abnahme des späteren Editor-Repositories.

Lokaler Abnahmestand unter Windows: 406 Scala-Tests und 38 Browserfälle in Chromium/
WebKit bestanden. Der Firefox-Lauf scheitert vor dem ersten Test am Start des von
Playwright gelieferten Browsers (`SideBySide`: abhängige Assembly `mozglue` fehlt),
auch nach erneuter Installation. Die Firefox-Projekte bleiben im Standardlauf und
in der CI aktiviert; die vollständige Browserabnahme ist deshalb noch offen.

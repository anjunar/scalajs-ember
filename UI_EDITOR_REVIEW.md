# Review der Editor-Implementierung

Stand: **14. September 2026**, geprüfter Commit **`8b3092f`**. Grundlage sind
[Architektur](UI_EDITOR_ARCHITECTURE.md) und [Implementierungsplan](UI_EDITOR_IMPLEMENTATION.md).
Der Arbeitsbaum war zu Beginn sauber. Die Erstprüfung veränderte keine Produktivimplementierung;
die anschließend beauftragten Korrekturen sind unten getrennt dokumentiert.

Die Trennung von headless Modell, Feature-Modulen, Formatadaptern und UI-Projektion ist
weitgehend umgesetzt. **Die Erstprüfung fand 14 konkrete Fehler bzw. Absicherungslücken.**
Dazu gehörten Verletzungen der URL-Policy, verlorene Eingaben, Textumordnung und
verlustbehaftete Exporte trotz `Strict`. Die damaligen Tests erfassten viele Einzeloperationen,
aber mehrere Übergänge zwischen Features fehlen. P25–P30 und die ausdrücklich ausstehende
reale IME-Abnahme wurden nicht als Implementierungsfehler gewertet.

## Korrekturstand vom 14. September 2026

**R01–R14 sind im Arbeitsbaum behoben.** Die ursprünglichen Befunde und Testzahlen weiter
unten beziehen sich auf `8b3092f`; sie bleiben als nachvollziehbare Fehlerbeschreibung erhalten.
Die Korrekturen wurden als zusammengehöriges Review-Paket umgesetzt und noch nicht committet.

| Befund | Umgesetzte Korrektur | Regulärer Nachweis |
| --- | --- | --- |
| R01 | Media-URLs mit Backslash nach Normalisierung zurückweisen, bevor Authority oder relativer Pfad freigegeben werden. | `MediaReviewProbe` |
| R02 | Claims beim nächsten eigenen `beforeinput` verwerfen; `input` anhand des tatsächlichen DOM-Deltas importieren, auch bei gleichem Input-Typ. | `review-regressions.spec.mjs`, mit und ohne späteres `beforeinput` |
| R03 | Lebende Host-Zugehörigkeit prüfen; strukturell betroffene Bereiche automatisch neu binden. Textprojektion prüft zusätzlich vor jedem Schreibzugriff; native Texte aus ersetzten Hosts werden vor dem Neubinden importiert. Recovery erkennt auch identische, abgetrennte Hosts. | Drei Browserfälle: automatische Reparatur, synchrone Änderung vor Observer-Zustellung, native Eingabe auf Ersatzknoten |
| R04 | Nur zusammenhängende ausgewählte Geschwister gemeinsam verlinken. | `LinkReviewProbe` |
| R05 | Alle benachbarten Code-Literale erhalten; notwendiges Code-Padding schreiben; Whitespace an Mark-Grenzen als Entities erhalten. | `ReviewFormatProbeSpec`, einschließlich führender/nachlaufender Leerzeichen und reiner Leerzeichenläufe |
| R06 | Inline-Baum iterativ materialisieren; Tiefe und Arbeitsbudget prüfen und Fehler bis zum Parse-Ergebnis weitergeben. | `ReviewFormatDepthProbeSpec`, `ReviewFormatProbeSpec`, vorhandene Parser-Suites |
| R07 | Den gesamten Bereich zwischen den Endpunkten entlang ihrer Vorfahrenpfade löschen; leere Restcontainer entfernen. | `ListReviewProbe` |
| R08 | Inline-Container ausdrücklich kennzeichnen; Enter teilt den tatsächlichen Block und nötige Inline-Vorfahren. Paragraph/Heading/Link lehnen blockartige Kinder ab. | `LinkReviewProbe`, Link-Anfang, -Mitte und -Ende |
| R09 | Undo-/Redo-Stapelwechsel transaktional vorbereiten und erst nach erfolgreichem Commit übernehmen. | `HistoryReviewProbe`, danach erfolgreicher Undo-/Redo-Zyklus; `HistorySpec` schützt vor rekursiven Snapshots |
| R10 | Restore-Mapping berücksichtigt Textsplices und Geschwisteranker mit Affinität. | `BookmarkReviewProbe`, Edit → Undo → Redo |
| R11 | Mark-Kontext durch Links weiterreichen, Inline-Whitespace erhalten, Unwrap im selben Absatzfluss halten und Block-Leaves wie `hr` korrekt einordnen. | Vier HTML-Fälle in `ReviewFormatProbeSpec`, `HtmlImportSpec` |
| R12 | Zeichenbefehle übernehmen nur Character/Selection; Word/Line bleiben nativ und werden importiert. Native Wortlöschung erzeugt am Zeilenende ein NBSP, das der Import erhält. | Echte Ctrl+Backspace-Eingabe in allen drei Engines |
| R13 | Ownership auch vor Composition-Start/-Ende und Fokusabschluss prüfen. | Native Atom-Textarea kann weder äußere Composition starten noch beenden |
| R14 | CI führt das vollständige Scala-Root-Gate aus. Die falsche `embed`-Testerwartung wurde gemäß dem Void-Element-Vertrag korrigiert. | `.github/workflows/verify.yml`, vollständiger lokaler Testlauf |

| Abschließende Prüfung | Ergebnis |
| --- | --- |
| `sbt --server "Test/testOnly *"` | **1.186/1.186 erfolgreich** |
| `scalafmtCheckAll` | Erfolgreich |
| `scalajs-ember-integration/fullLinkJS` | Erfolgreich |
| `npm run verify` | **Serverimport und 600/600 Browserfälle erfolgreich**, je 200 in Chromium, Firefox und WebKit |
| Markdown-Konformität / Writer-Roundtrip | 651/652 bzw. **628/652**; die feste Roundtrip-Erwartung verbessert sich von 627 auf 628 |

Firefox lief lokal über den bereits dokumentierten Kanal `EMBER_FIREFOX_CHANNEL=moz-firefox`.
Die Browser-Abnahme enthält echte Tastatureingaben und gezielte synthetische Protokolltests;
**reale IME-, Geräte- und Screenreader-Abnahmen bleiben offen**. Ebenso bleiben P25–P30
eigene Folgepakete. Die grüne Suite ist keine darüber hinausgehende Produktfreigabe.

## Befunde

Die folgenden Beschreibungen halten den ursprünglichen Fehlerstand fest; alle Befunde
sind inzwischen wie oben dokumentiert korrigiert. Die Prioritäten gehören zur Erstprüfung:
P1 bedeutete „vor weiterer Freigabe beheben“, P2 eine konkrete Funktions- oder Absicherungslücke.

### R01 · P1 · Media-Host-Allowlist lässt fremde Hosts durch

**Stelle:** `ember-image/src/main/scala-3/ember/editor/image/MediaUrlPolicy.scala:132–144,231–241`.
**Vertrag:** Architektur §20, P16.

`MediaUrlPolicy(hosts = Some(Set("cdn.example")))` akzeptiert
`https://evil.example\@cdn.example/x.png`. Der eigene Authority-Parser erkennt
`cdn.example`; die URL-Auswertung des Browsers erkennt `evil.example`.
Außerdem akzeptiert `MediaUrlPolicy.internalOnly` den Pfad `\\evil.example/x.png`,
den der Browser als externe Netzwerkadresse auflöst. HTML-/JSON-Import und die Ausgabe
als `src` erhalten diesen Wert. Damit kann ein Dokument Bilder von Hosts laden lassen,
die das gewählte Profil ausdrücklich ausschließt.

**Korrektur:** Backslashes und andere mehrdeutige URL-Formen vor der Freigabe zurückweisen
oder anhand einer mit der Browserauswertung übereinstimmenden Normalisierung prüfen.
Relative Quellen dürfen nicht zu einer fremden Authority werden.
**Nachweis:** `MediaReviewProbe`, zwei fehlgeschlagene Ablehnungsassertionen;
URL-Auflösung zusätzlich lokal mit Node geprüft, ohne Abruf der Adresse.

### R02 · P1 · Alte Input-Claims verschlucken spätere native Eingaben

**Stelle:** `ember-browser/src/main/scala-3/ember/editor/browser/InputOperationToken.scala:43–59`;
`BrowserInputController.scala:346` im selben Verzeichnis.
**Vertrag:** Architektur §15.2, P22/P23.

Nach normalem Tippen von `X` bleibt ein Claim für das verhinderte `beforeinput` zurück.
Kommt später ein nicht abbrechbares `beforeinput`/`input` mit `insertText` und `Y`,
verbraucht `consume` den alten Claim allein anhand des Input-Typs. Der Controller meldet
`deduplicated` und importiert `Y` nicht. Reproduziert: DOM `Hallo WeltXY`, Modell
`Hallo WeltX`. Die maximale Anzahl von acht Claims begrenzt den Speicher, nicht deren
Gültigkeitsdauer oder Zuordnung zu einer Benutzeraktion.

**Korrektur:** Claims auf die konkrete laufende Eingabe begrenzen und abgelaufene oder
durch eine spätere native Eingabe überholte Claims verwerfen. Ein nicht übernommenes
`beforeinput` muss anschließend importiert werden können.
**Nachweis:** Browser-Probe `stale-input-claim`, Chromium; echte erste Texteingabe,
anschließend gezielte synthetische Simulation des nicht abbrechbaren Native-Input-Pfads.

### R03 · P1 · Ersetzte Text-Hosts bleiben im normalen Eingabepfad gültig

**Stelle:** `ember-browser/src/main/scala-3/ember/editor/browser/BrowserInputController.scala:314–316`;
`DomPositionMap.scala:226–231` und `NativeMutationObserver.scala:63–107` im selben Verzeichnis.
**Vertrag:** Architektur §§15.2, 15.4, P23.

Der Observer sammelt Mutationen, stößt im Zustand `Ready` aber keinen Abgleich an.
`recovery.repair()` wird nur beim Composition-Abschluss aufgerufen. Ersetzt man den
DOM-Textknoten eines Laufs durch einen inhaltsgleichen Klon und tippt danach `X`, schreibt
die Projektion weiterhin auf den abgetrennten alten Textknoten. Die Positionsabbildung
prüft dessen Bindung, aber nicht, ob er noch zum lebenden Host gehört; ein bloßer
HTML-Inhaltsvergleich würde den identischen Klon ebenfalls nicht unterscheiden. Reproduziert: Modell und
abgetrennter Knoten `HalloX Welt`, sichtbarer DOM `Hallo Welt`, Controller weiterhin `Ready`.

**Korrektur:** Observer-Verarbeitung und Prüfung der Host-Identität in den normalen
Eingabe-/Projektionszyklus integrieren; ungültige Hosts vor weiteren Schreibzugriffen
neu übernehmen, reparieren oder die Änderung kontrolliert ablehnen.
Die bestehenden Mutationstests rufen Reparatur teilweise direkt über Test-APIs auf und
belegen damit nicht die automatische Verdrahtung.
**Nachweis:** Browser-Probe `replaced-text-host`, Chromium mit echter Tastatureingabe.

### R04 · P1 · Verlinken über einen vorhandenen Link ordnet Text um

**Stelle:** `ember-link/src/main/scala-3/ember/editor/link/LinkCommands.scala:78–103`.
**Vertrag:** Architektur §§8.2, 11, P14.

Ausgang: `a[bbb](alte-url)c`. Den gesamten Text auswählen und eine neue URL setzen.
Die nach unmittelbarem Parent gruppierten äußeren Läufe `a` und `c` werden gemeinsam
vor den alten Link bewegt. Das Ergebnis lautet **`acbbb` statt `abbbc`**.

**Korrektur:** Ausgewählte Inline-Inhalte in Dokumentreihenfolge bearbeiten; bestehende
Links und dazwischenliegende Atome müssen diese Reihenfolge erhalten. Nur nach Parent
zu gruppieren genügt für einen gemischten Bereich nicht.
**Nachweis:** `LinkReviewProbe`, Test `preserve the text order`.

### R05 · P1 · Strict-Markdown-Export kann Inhalt und Formatierung verlieren

**Stellen:** `ember-standard/src/main/scala-3/ember/editor/standard/MarkdownSupport.scala:465–469`;
`ember-markdown/src/main/scala-3/ember/editor/markdown/MarkdownWriter.scala:143–172`.
**Vertrag:** Architektur §18.2, P18/P19b.

Drei unabhängig reproduzierte Fälle verletzen `decode(encode(document)) ≃ normalize(document)`:

| Eingabe | Ausgabe / erneuter Import | Ursache |
| --- | --- | --- |
| Zwei benachbarte Inline-Code-Läufe `a`, `b` in einem gültigen Document | Nur `a` bleibt übrig | Der Encoder gruppiert gleiche Marks; `inlineCode.inlineFor` gibt nur `children.headOption` zurück. |
| Starker Textlauf `hello ` mit abschließendem Leerzeichen | `**hello **` wird als unformatierter Text einschließlich Sternchen gelesen | Delimiter werden ohne Prüfung ihrer Öffnungs-/Schließbedingungen angefügt. |
| Inline-Code-Inhalt ` code ` | Ausgabe mit je einem Rand-Leerzeichen innerhalb der Backticks wird zu `code` | Das Writer-Padding behandelt Backticks am Rand, aber nicht das Entfernen eines Leerzeichenpaars durch den Code-Span-Parser. |

Alle drei Exporte liefern Erfolg unter `Strict`. Damit kann auch die Formatgrenze des
Formularfelds einen tatsächlich verlustbehafteten Wert akzeptieren. Der erste Fall ist
auch über die öffentliche Document-/JSON-API relevant: Ein gültiges Document mit
benachbarten Läufen muss beim Export wenigstens wie seine normalisierte Form erhalten bleiben.

**Korrektur:** Alle Code-Kinder erhalten bzw. ihre Literale zusammenführen; gültige
Delimiter und Code-Padding erzeugen. Nicht darstellbare Fälle müssen vor einem Strict-Erfolg
als Verlust erkannt werden. Für jede Tabellenzeile einen eigenen Regressionstest behalten.
**Nachweis:** `ReviewFormatProbeSpec`, drei Tests unter `Strict markdown export`.

### R06 · P1 · Markdown-Inline-Nesting umgeht das Tiefenlimit und wirft einen Stackoverflow

**Stelle:** `ember-markdown/src/main/scala-3/ember/editor/markdown/InlineParser.scala:86–89`;
die Tiefenprüfung in `BlockParser.scala:787–793` erfasst nur Blöcke.
**Vertrag:** Architektur §18.2, P17/P18, `MarkdownProfile.untrustedPaste`.

Schon 20 geschachtelte Bild-Alttexte werden trotz `maxDepth = 5` akzeptiert.
Mit `"![" * 3000 + "x" + "](x)" * 3000` und `MarkdownProfile.untrustedPaste`
wirft `Markdown.parseSyntax` bei nur **18.001 UTF-16-Zeichen**
`JavaScriptException: RangeError: Maximum call stack size exceeded`, statt einen
typisierten Parsefehler zurückzugeben. Ursache ist der unbeschränkte rekursive Aufbau
der Inline-Kinder. Die Größen-/Schrittlimits verhindern diesen Fall nicht.

**Korrektur:** Inline-Verschachtelung beim Aufbau begrenzen und die Überschreitung durch
den normalen Fehlerpfad melden; tiefe Eingaben nicht erst rekursiv materialisieren.
**Nachweis:** `ReviewFormatProbeSpec` mit kleinem Limit und
`ReviewFormatDepthProbeSpec` mit `untrustedPaste`; kein Browser oder Netzwerk erforderlich.

### R07 · P2 · Bereichslöschung über ListItems behält vollständig ausgewählten Text

**Stelle:** `ember-rich-text/src/main/scala-3/ember/editor/richtext/TextEditing.scala:416–424`.
**Vertrag:** P13 nennt ausdrücklich Bereiche über mehrere Items.

Drei ListItems `ABC`, `DEF`, `GHI`; Auswahl von Offset 1 in `ABC` bis Offset 2 in `GHI`;
Backspace. Ergebnis **`AIDEF` statt `AI`**. `removeBlocksBetween` sucht den letzten
Absatz nur in der Kindliste des unmittelbaren Parents des ersten Absatzes. Bei mehreren
ListItems liegen diese Absätze unter verschiedenen Parents; der mittlere bleibt stehen.

**Korrektur:** Den ausgewählten Bereich über seine gemeinsamen Vorfahren hinweg entfernen
und verbleibende ListItem-/Blockgrenzen normalisieren.
**Nachweis:** `ListReviewProbe`.

### R08 · P2 · Enter innerhalb eines Links teilt den falschen Container

**Stelle:** `ember-rich-text/src/main/scala-3/ember/editor/richtext/TextEditing.scala:153–156`.
**Vertrag:** Architektur §8.2, P06/P14.

Ein Absatz enthält einen Link mit Text `bbb`. Enter nach dessen erstem `b` behandelt
den unmittelbaren Text-Parent, also den Link, als Block. Ein neuer Paragraph entsteht
innerhalb des bisherigen Paragraphen; am Root bleibt nur ein Absatz statt zwei.
Die Schema-/Normalisierungsprüfung verhindert diesen Aufbau derzeit nicht.

**Korrektur:** Den tatsächlichen umgebenden Block bestimmen und den Inline-Container beim
Split korrekt aufteilen. Die Paragraph-Invarianten sollten einen verschachtelten Block ablehnen.
**Nachweis:** `LinkReviewProbe`, Test `split the outer paragraph`.

### R09 · P2 · Ein abgebrochener Undo-Dispatch verändert die History

**Stelle:** `ember-history/src/main/scala-3/ember/editor/history/History.scala:254–258`.
**Vertrag:** Architektur §§10, 14, P04/P11.

Nach einer Texteingabe `session.update { tx => tx.dispatch(HistoryCommands.Undo);
tx.remove(NodeId("missing")) }` ausführen. Die Transaktion wird abgewiesen und der
Dokumentinhalt bleibt korrekt erhalten; der Undo-Eintrag wurde jedoch bereits nach
Redo verschoben. `run` ändert `current` im Draft und besitzt keinen Rollback für eine
spätere Operations-, PreCommit- oder Reducer-Ablehnung. Das Problem betrifft den
Command-Pfad, nicht nur die separate `history.undo()`-Methode.

**Korrektur:** History-Stapeländerung erst mit erfolgreichem Commit übernehmen oder als
transaktionalen Zustand führen. Fehlgeschlagene Transaktionen dürfen keine Stufe verbrauchen.
**Nachweis:** `HistoryReviewProbe`.

### R10 · P2 · Bookmarks zeigen nach Undo auf falschen Inhalt

**Stelle:** `ember-core/src/main/scala-3/ember/editor/core/DocumentDiff.scala:111–125`.
**Vertrag:** Architektur §11, P03/P11; bereits relevant für die Deferred-Intent-API.

Bookmark bei Offset 3 in `Hallo` anlegen, `X` vorne einfügen und anschließend Undo.
Der Text ist wieder `Hallo`, aber `bookmark.resolve(session.mappingSince(...))` liefert
**Offset 4 statt 3**. `mappingFor` prüft bei einem Restore nur, ob der alte Offset noch
innerhalb der neuen Länge liegt, und meldet ihn dann als erhalten. Die bereits berechneten
Text-Splices werden für diese Positionsabbildung nicht verwendet.

**Korrektur:** Eindeutig abbildbare Restore-Änderungen auf Bookmarks anwenden; bei nicht
rekonstruierbarem Inhalt explizit ablaufen lassen, statt eine falsche Position als sicher
erhalten zu melden. Die gesondert restaurierte aktuelle Selection löst dieses Problem nicht.
**Nachweis:** `BookmarkReviewProbe`.

### R11 · P2 · HTML-Import verliert Semantik bei Inline-/Block-Übergängen

**Stelle:** `ember-html/src/main/scala-3/ember/editor/html/HtmlImport.scala:104–156,192–193`.
**Vertrag:** Architektur §19.1, P24.

| Fragment | Tatsächliches Ergebnis | Ursache / Korrektur |
| --- | --- | --- |
| `<p><strong><a href='/x'>bold</a></strong></p>` | Linktext verliert Strong | `importChildren` setzt Marks beim Inline-Container auf leer; geerbte Marks weiterreichen. |
| `<strong>hello</strong> <em>world</em>` | `helloworld` | Whitespace-only Text wird auch zwischen bereits gesammelten Inlines verworfen; Kontext berücksichtigen. |
| `Hello <span>world</span>!` | Drei Absätze statt einem | `Unwrap` ruft im Blockpfad erneut `importBlocks` auf und beendet den laufenden Absatz; Inline-Wrapper inline behandeln. |
| `<p>before</p><hr><p>after</p>` | `hr` liegt in einem zusätzlichen Paragraphen und verschwindet beim anschließenden Strict-Markdown-Export | Jeder Leaf wird als inline gesammelt; Block-Atoms anhand ihrer Ebene behandeln. |

Der letzte Fall zeigt auch, dass ein erfolgreiches `Document.build` allein die semantische
Korrektheit des importierten Block-/Inline-Baums derzeit nicht garantiert.
**Nachweis:** Vier einzelne HTML-Tests in `ReviewFormatProbeSpec`.

### R12 · P2 · Wort- und Zeilenlöschung wird zu Zeichenlöschung

**Stelle:** `ember-browser-support/src/main/scala-3/ember/editor/browsersupport/RichTextBindings.scala:41–45`.
**Vertrag:** Architektur §15.2, P22.

Ctrl+Backspace am Ende von `Hallo Welt` hinterlässt `Hallo Wel` statt `Hallo `.
Die Bindings ignorieren `Granularity` und behandeln jede rückwärts-/vorwärtsgerichtete
Löschabsicht als den einfachen Rich-Text-Delete-Command. Die native Wortlöschung wird
dabei als angeblich übernommen verhindert.

**Korrektur:** Nur unterstützte Granularitäten beanspruchen oder die gewünschte
Wort-/Zeilengrenze korrekt anwenden; andernfalls die native Änderung importieren.
**Nachweis:** Browser-Probe `word-delete`, echte Ctrl+Backspace-Eingabe in Chromium.

### R13 · P2 · Composition eines nativen Atom-Felds sperrt den äußeren Editor

**Stelle:** `ember-browser/src/main/scala-3/ember/editor/browser/BrowserInputController.scala:197–201`.
**Vertrag:** Architektur §§15.2, 15.3, P21–P23.

Ein `compositionstart` in der nativen Textarea eines Atoms versetzt den äußeren Controller
in `Composing`. Eine unabhängige Änderung des äußeren Dokuments wird daraufhin abgewiesen.
Die Composition-Listener ignorieren Event-Ziel und Ownership, obwohl die anderen
Eingabe-Listener diese Zugehörigkeit prüfen.

**Korrektur:** Auch Composition-Start/-Ende und den zugehörigen Fokusabschluss dem richtigen
Editing-Host zuordnen. Native Controls und verschachtelte Editoren besitzen ihre eigene Eingabe.
**Nachweis:** Browser-Probe `foreign-composition`; synthetischer Protokolltest, keine reale IME-Abnahme.

### R14 · P2 · Die CI lässt große Teile der Scala-Tests aus

**Stelle:** `.github/workflows/verify.yml:20–21`.
**Vertrag:** Implementierungsplan, Test- und Commit-Vertrag.

Der Scala-Job führt nur Core und Rich-Text aus. Die Browser-Anwendung kompiliert weitere
Module, führt deren Scala-Test-Suites aber nicht aus. Dadurch bleiben beispielsweise
HTML-/JSON-/Markdown-/History-/Forms-Tests ungeprüft. Der aktuell reproduzierte Fehler
in `HtmlSecuritySpec` wird von diesem Workflow nicht erkannt.

**Korrektur:** Das vereinbarte Root-Gate `sbt --server "Test/testOnly *"` in der CI
ausführen oder alle Module gleichwertig explizit aufnehmen.
**Nachweis:** Vergleich des Workflows mit dem vollständigen lokalen Testlauf.

## Prüfungen der ursprünglichen Review-Basis

| Prüfung | Ergebnis am geprüften Commit |
| --- | --- |
| `sbt --server "Test/testOnly *"` | **1.167 Tests: 1.166 erfolgreich, 1 fehlgeschlagen.** |
| Vorhandener Fehler | `HtmlSecuritySpec`, `Active embeds should be refused`, Zeile 116: `ainner` statt `a`. |
| `scalajs-ember-integration/fullLinkJS` | Erfolgreich, separat nach dem fehlgeschlagenen Root-Gate ausgeführt. |
| Browser-Serverimport | Erfolgreich ohne Browserglobals. |
| Bestehende Chromium-Suite | **192/192 erfolgreich.** |
| Bestehende WebKit-Suite | **192/192 erfolgreich.** |
| Bestehende Firefox-Suite | **192/192 erfolgreich** mit `EMBER_FIREFOX_CHANNEL=moz-firefox`, außerhalb der Sandbox. |
| Vollständiges `npm run verify` | **Serverimport und 576 Browserfälle erfolgreich, Exitcode 0.** |
| Gezielte Scala-Proben | **15/15 Regressionen reproduziert**, zusätzlich tiefer Inline-Parse mit echtem Stackoverflow. |
| Gezielte Chromium-Proben | **4/4 Regressionen reproduziert.** |

Der rote vorhandene HTML-Test ist **kein nachgewiesenes XSS**: Die erste problematische
Iteration ist `embed`, ein Void-Element. Bei `<embed>inner</embed>` ist `inner` kein
Kind des Embeds, sondern ein nachfolgender Textknoten. Parser-Profil und Testerwartung
müssen hier ausdrücklich abgeglichen werden; den Parser pauschal bis zu einem
`</embed>` schlucken zu lassen wäre kein begründeter Sicherheitsfix.

Reale IME-/Geräte-/Screenreader-Abnahmen wurden nicht durchgeführt. Die Browser-Proben
trennen echte Tastatureingaben von synthetisch erzeugten Ereignisfolgen. Ein anfänglicher
Firefox-Lauf hing innerhalb der Sandbox beim Seiten-Setup; der vollständige erfolgreiche
Lauf außerhalb grenzte dies als Umgebungsproblem ein. Der von Playwright mitgelieferte
Firefox startet lokal weiterhin mit `spawn UNKNOWN` nicht; verwendet wurde der bereits
im Harness dokumentierte alternative Kanal, keine neue Browser-Ausnahmeregel.

## Reproduktion und Übergabe

Die [Repro-Dateien](review/2026-09-14/README.md) enthalten die ursprünglichen Scala-Proben
als Archiv. Ihre weiterentwickelten Fassungen sind nun reguläre Tests der jeweiligen
Module; das aktuelle `probes.sbt` führt diese ohne zusätzliche Quellverzeichnisse aus.
Die Browser-Regressionen liegen im regulären Drei-Engine-Harness; das ursprüngliche
Vier-Fälle-Skript bleibt für eine gezielte lokale Prüfung verfügbar.

## Dokumentationsstand

P24 ist im Commit `a3feaf2` implementiert, in README und Plan zuvor aber noch als offen
geführt. Architekturkopf, Planstatus und README verweisen jetzt auf dieses Review;
P24 wird damit nicht als abgenommen erklärt. Der bei der UI-Umbenennung entstandene
Selbstpfeil im Architekturdiagramm und die beschädigte Toolbar-Tabellenzeile sind berichtigt.

Auch „Kein Git“ und „alle Phasen offen“ im Plan, „noch keine CI“ in Architektur §24 sowie
der früher als zukünftig beschriebene HTML-Parser in `ember-html/README.md` sind aktualisiert.
Die Phasenbeschreibungen und damaligen Testzahlen bleiben als Entstehungshistorie lesbar;
für den aktuellen Korrekturstand gelten die abschließenden Prüfungen am Anfang dieses Dokuments.

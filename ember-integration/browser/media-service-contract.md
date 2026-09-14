# P26: Vertrag des Medien-Testservers

`media-server.mjs` erweitert ausschließlich den lokalen Integration-Harness auf
`127.0.0.1:4188`. Es ist kein produktiver Uploadservice. Er akzeptiert genau eine
bekannte PNG-Fixture mit passendem MIME-Typ und identischen Dateibytes. Damit sind
Speicherung, Referenzübergabe und Wiederabruf reproduzierbar, ohne einen allgemeinen
Bilddecoder oder ein Backend in den Editor einzuführen.

| Einstieg | Vertrag |
| --- | --- |
| `POST /media/upload` | Multipart mit `file`; Erfolg liefert JSON `{src, mediaId}`, Fehler Status 422. |
| `GET /media/form` | Ohne JavaScript bedienbares Multipart-Formular; Source wird mit `MediaFixtures.renderSource` über das tatsächliche `EditorFieldView` gerendert. |
| `POST /media/submit` | Genau eine `body`-Source, genau eine Datei, `alt`, `title`, `position=append`. Andere Einfügepositionen werden abgewiesen. |
| `GET /media/result/<id>` | Erfolg nach POST/303/GET zeigt den serverseitig erzeugten Formwert und sonstige Werte. |
| `GET /media/assets/<sha256>.png` | Abruf der gespeicherten PNG-Datei über eine dauerhafte relative Referenz. |

Request-Budget: 2 MiB, Source höchstens 1 Mi UTF-16-Codeeinheiten, Alt und Titel
jeweils 2.000 Zeichen; die Fixture-Datei ist zusätzlich auf 64 KiB beschränkt.
Multipart wird durch Nodes `Request.formData()` gelesen, nicht durch selbst
zusammengesetzte Boundary-/String-Trennung. Dateinamen werden nie als Pfade benutzt.
Dateien liegen in `target/p26-media/` unter ihrem SHA-256-Hash und überleben den
Serverprozess. Wiederholtes Hochladen derselben Fixture erzeugt denselben Pfad.
Ergebnisseiten sind auf 100 Einträge im Prozess begrenzt.

`MediaFixtures.append` dekodiert Source und fügt über eine echte Transaktion und
`ImageCommands.InsertImage` einen Absatz mit Bild am Dokumentende hinzu. Der
Strict-Markdown-Vertrag verwendet die inhaltsadressierte URL als vollständige
Referenz; eine separate MediaId bleibt beim Storage und wird nicht als verlustlos
serialisierbarer Markdown-Knotenbestandteil ausgegeben. Die Browserfixture
verwendet denselben URL-Vertrag. Ein separater Test beweist, dass die Engine eine
unrepräsentierbare MediaId bei Strict Markdown nicht still entfernt.

Bei Formularfehlern werden Source, Alt und Titel exakt und HTML-escaped wieder
ausgegeben. Der Browser kann einen Dateipicker aus Sicherheitsgründen nicht erneut
vorbelegen. Eine Datei muss bei Wiederholung erneut gewählt werden.

Die Tests belegen echten HTTP-Multipart-Upload, Wiederabruf der gespeicherten
Bytes, Undo ohne Dateilöschung, ungültige Dateiantworten und den vollständigen
No-JS-Formularweg. Picker werden über reale Button-Klicks samt Benutzeraktivierung
geöffnet. Paste-/Drop- und Composition-Fälle prüfen das Ereignisprotokoll;
physische Touch-Drags oder eine reale IME-Abnahme werden damit nicht behauptet.

Backendautorisierung, CSRF, produktive Dateityperkennung/Decoder, Quoten,
Persistenztransaktionen und Orphan-Cleanup sind Aufgabe der Anwendung. Dieser
Loopback-Fixture-Server beansprucht diese Eigenschaften nicht.

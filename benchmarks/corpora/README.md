# P28-Korpora

`roundtrips.json` ist ein handgeschriebener Regressionkorpus für Unicode/Bidi,
Marks, Listen, Code, Medien, Raw HTML und diagnostizierten HTML-Verlust.
`node tools/verify-editor-corpora.mjs` führt ihn gegen den optimierten Scala-Link aus.
Markdown wird zweimal normalisiert, die semantische HTML-Ausgabe verglichen und
das Dokument zusätzlich verlustfrei durch JSON geführt. HTML-Verlustfälle prüfen
erhaltenen Text, entfernte aktive Inhalte und explizite Diagnosen.

Dieser Korpus ergänzt die vollständige CommonMark-0.31.2-Suite und die bestehenden
JSON-/HTML-/Markdown-Limit- und Sicherheitsprüfungen; er ersetzt sie nicht.

`EditorBench.scala` erzeugt deterministisch 1001/10001/100001 Knoten: eine Wurzel
und 500/5000/50000 kurze Absätze mit je einem Textlauf. Die nominellen Namen
1k/10k/100k enthalten im Bericht immer die tatsächliche Anzahl. Weitere Formen:
ein Textlauf mit 1.000.000 UTF-16-Einheiten und eine 32 Ebenen tiefe Liste mit
129 Knoten. Edits wechseln das erste Zeichen eines mittleren Textlaufs; der
Textumfang bleibt konstant. Es gibt keinen Zufall und keine Netzwerkdatenquelle.

100001 Knoten und 50000 Wurzelkinder überschreiten die **Standard-JSON-Importlimits**
(100000 Nodes, 20000 Kinder). Der Benchmark baut kontrollierte gültige Modelle
direkt; daraus folgt keine Erweiterung der Importfreigabe. Formstring-Kosten sind
separat von Core und Core+History ausgewiesen.

Physische Geräte-Traces werden erst nach tatsächlichen Tests unter
`device-traces/` abgelegt. Momentan liegt kein solcher Nachweis vor; die Anleitung
steht in der [Accessibility-Checkliste](../../ember-integration/browser/accessibility-checklist.md).

# scalajs-ember-json

Das versionierte JSON-Format des Ember-Editors: Wire-ADT, Node-Codecs, Schema-Migration und die
vollständige Prüfung fremder Payloads. Headless — kein DOM, keine UI-Runtime, kein Renderer.

Verbindlicher Entwurf: [UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §19.2.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-json` |
| Scala-Paket | `ember.editor.json` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

P10 abgeschlossen. Vorhanden: `JsonValue` samt Plattformgrenze, `DocumentJson`,
`NodeJsonCodec`/`MarkJsonCodec` mit Registries, `DecodeLimits`, `SchemaMigration` und die Codecs
der beiden Kernknotenarten. Codecs für Paragraph, Heading, Listen, Links und Bilder folgen mit
ihren Feature-Modulen (P16/P18) — dieses Modul kennt keine davon.

## Verwendung

```scala
val support = CoreJsonSupport.all ++ JsonSupport.of(paragraphCodec)

DocumentJson.encodeToString(document, support)          // Either[Vector[EncodeError], String]
DocumentJson.decodeString(source, schema, support)      // Either[Vector[DecodeError], DecodeResult]
```

`DecodeResult` trägt ein `Document` und Diagnosen. Bewusst kein `EditorSession`: §9 hält fest,
dass SSR ein Dokument ohne lokale Selection, History oder Fokus rendert — was gespeichert wird,
ist das Dokument, nicht der Zustand einer Bearbeitung (§5). Entsprechend steht im Payload weder
`selection` noch `history` noch `revision`.

## Das Envelope

```json
{
  "format": "ember-document",
  "formatVersion": 1,
  "schemaVersion": 1,
  "root": "root",
  "nodes": [
    {"id":"root","type":"ember.core.root/1","codecVersion":1,"children":["p0"]},
    {"id":"p0","type":"ember.core.text/1","codecVersion":1,"text":"Hallo"}
  ]
}
```

**Warum die Knoten ein Array sind und kein Objekt.** Ein nach ID geschlüsseltes Objekt wäre
kompakter — und blind gegenüber dem Fehler, auf den es hier am meisten ankommt. `js.JSON.parse`
fasst doppelte Schlüssel zusammen, eine doppelte Knoten-ID verschwände also spurlos, und das
Dokument sähe gültig aus. Als Array bleibt sie sichtbar und wird zu `DuplicateNodeId`.

**Drei Versionen, drei Zuständigkeiten.**

| Feld | Steigt, wenn |
| --- | --- |
| `formatVersion` | sich das Envelope selbst ändert. Wird nicht migriert — ein neueres Format braucht einen neueren Leser. |
| `schemaVersion` | sich die Dokumentstruktur einer Anwendung ändert. Wird über `SchemaMigrations` gehoben. |
| `codecVersion` | sich die Felder **einer** Knotenart ändern. §19.2 hält beide ausdrücklich getrennt. |

Ein Codec darf ältere Stände lesen — er bekommt die gefundene Version im `DecodeContext` und
verzweigt. Einen **neueren** Payload weist das Envelope ab: er kann Felder tragen, deren
Bedeutung dieser Stand nicht kennt, und ihn als alten zu lesen wäre stiller Datenverlust.

## Was ein Codec beschreibt

Nur die eigenen Felder eines Knotens. `id`, `type`, `codecVersion` und `children` schreibt und
liest das Envelope für alle Arten gleich:

```scala
val text: NodeJsonCodec[TextNode] = new NodeJsonCodec[TextNode]:
  val nodeType = TextNode
  def encode(node, context) = Right(Vector("text" -> JsonValue.Str(node.text)))
  def decode(id, payload, context) = payload.string("text", context.path).map(TextNode(id, _))
```

Kinder sind laut §8.2 ausschließlich referenzierte IDs; die Kindliste ist damit eine
strukturelle Eigenschaft des Dokuments, keine Nutzlast einer Knotenart. Ein Codec, der sie
selbst schriebe, könnte sie auch selbst vergessen.

Markierungen sind offen (§8.2), und der Kern kennt keine einzige. `MarkJsonCodec` gibt es
trotzdem schon: ohne sie wäre ein markierter Textlauf heute nicht verlustfrei persistierbar,
und „verlustfrei" ist die Zielzeile dieser Phase.

## Wo `js.JSON` steht

An genau einer Stelle: `JsonText`. Alles darüber arbeitet auf dem geschlossenen `JsonValue`-ADT
— §19.2 verlangt das wörtlich. Der Unterschied ist nicht kosmetisch: ein Codec auf `js.Dynamic`
prüft nur, was er ausdrücklich prüft, und was er vergisst, fällt erst auf, wenn ein fremder
Payload es ausnutzt. Wer einen `JsonValue` in der Hand hat, hat einen konvertierten Wert, dessen
Tiefe und Größe bereits gegen die Limits gehalten wurden.

**Serialisiert wird selbst**, nicht mit `js.JSON.stringify`. Zwei Gründe, beide praktisch:

1. *Feldreihenfolge.* Die Ausgabe muss bei gleichem Dokument byteweise gleich sein, sonst sind
   Roundtrip-Fixtures wertlos. Die Knoten stehen deshalb in Dokumentordnung.
2. *Einbettbarkeit.* Der Payload landet später in einem `<script>`-Tag (§16). Ein `</script` im
   Text würde ihn dort beenden — also entkommen `<`, `>` und `&` grundsätzlich, dazu U+2028 und
   U+2029, die in JavaScript-Quelltext Zeilentrenner sind. Das Ergebnis bleibt gewöhnliches
   JSON.

### Was nicht geprüft werden kann

`js.JSON.parse` fasst doppelte Objektschlüssel zusammen, bevor dieses Modul den Wert sieht: aus
`{"a":1,"a":2}` wird `{"a":2}`. Ein eigener Parser könnte es melden, wäre aber ein zweiter,
schlechter getesteter JSON-Parser für eine Diagnose, die kein Datenverlust ist — der letzte Wert
gewinnt, deterministisch und dokumentiert. Doppelte **Node-IDs** bleiben davon unberührt; sie
sind der Fall, der zählt, und dafür gibt es das Array.

## Grenzen

`DecodeLimits` deckelt Quelltextlänge, JSON-Tiefe, Array- und Objektgröße, Knotenzahl,
Kinderzahl, Textlänge und Dokumenttiefe. Ohne sie entscheidet der Absender, wie viel Speicher
und Rechenzeit der Empfänger aufwendet — bevor irgendeine fachliche Prüfung greift. Die
Voreinstellungen sind für Dokumente, die ein Mensch geschrieben hat, nie erreichbar; sie sind
ein Wert und keine Konstante, damit eine Anwendung sie bewusst anheben kann.

## Unbekannte Knoten

Voreinstellung ist `UnknownNodePolicy.Strict`: Ablehnung mit Pfad und TypeId. §19.2 verlangt für
alles andere eine ausdrückliche Wahl.

`Preserve` erhält den Knoten als `UnsupportedNode` — mit seiner ursprünglichen Wire-ID, seinem
Payload, seinen Kindern und einem Textfallback. Der Payload wird nicht interpretiert: kein Code,
kein HTML, keine automatisch deserialisierte Klasse. Ein Roundtrip durch einen Editor ohne das
betreffende Feature-Modul lässt ihn byteweise unverändert.

`UnsupportedNode` ist ein **Container**, und das ist der Punkt: ein unbekannter Knoten kann
bekannte enthalten. Ohne Kindliste wären die Absätze unter einer unbekannten Tabelle nach dem
Dekodieren unerreichbar, und der `DocumentValidator` lehnte das Dokument mit `UnreachableNode`
ab — die Erhaltung hätte genau das zerstört, wozu es sie gibt.

Eine **bekannte** Art ohne registrierten Codec bleibt auch unter `Preserve` ein Fehler. Das ist
ein Verdrahtungsfehler der Anwendung, kein unbekanntes Datum; ihn zu verstecken hieße, ein
Dokument als fremd auszugeben, das dieser Editor sehr wohl versteht.

## Migration

```scala
val chain = SchemaMigrations.unsafe(
  SchemaMigration(1, 2, envelope => Right(renameType(envelope, "app.section/1", "app.block/1")))
)
DocumentJson.decodeString(source, schema, support, DecodeConfig(schemaVersion = 2, migrations = chain))
```

Schritte sind **rein**, und zwar an der Signatur erkennbar: `JsonValue.Obj => Either[String,
JsonValue.Obj]`. Eine Migration, die eine Sitzung bräuchte, wäre keine Migration, sondern eine
Bearbeitung — und sie liefe genau dann, wenn noch gar kein gültiges Dokument existiert. Sie
arbeitet auf dem ganzen Envelope, weil Umbenennungen, Aufspaltungen und nachgetragene Kinder
allesamt nicht knotenlokal sind.

Höchstens ein Schritt je Ausgangsversion; nur vorwärts; ein Schritt, der über die Zielversion
hinausspringt, ist ein fehlender Pfad. **Fehlende Pfade sind Fehler** (§19.2), keine
stillschweigende Übernahme: einen Payload der Version 1 als Version 3 zu lesen hieße, seine
Felder nach heutigen Regeln zu deuten.

## Was hier nicht noch einmal geprüft wird

Referenzielle Integrität, Zyklen, mehrfache Eltern, Erreichbarkeit und Schemakonformität prüft
`Document.build` im Kern. Ein zweiter Validator hier wäre eine zweite Wahrheit über dieselbe
Frage, und die beiden liefen früher oder später auseinander. Was dieses Modul prüft, ist alles,
was der Kern gar nicht sehen kann: Typen, Zahlenbereiche, Limits, doppelte IDs im Payload und
Versionen.

## Tests

```bash
sbt --server "scalajs-ember-json/Test/testOnly *"
```

`DocumentJsonSpec` fährt Roundtrips, beide Policies, alle Grenzen und die ungültigen Eingaben
gegen lokale typisierte Testnodes — bewusst nicht gegen `ParagraphNode`: §6 stellt `json` neben
die Node-Module, nicht über sie. `SchemaMigrationSpec` prüft die Kette und, am Ende, dass sie
tatsächlich **vor** dem Dekodieren läuft.

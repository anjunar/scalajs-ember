# scalajs-ember-image

Externe Bilder als Inline-Atome mit geprüfter Media-Policy. Kein Upload, kein Dateidialog, keine
Objekt-URL.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§8, 19–20.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-image` |
| Scala-Paket | `ember.editor.image` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

P16 abgeschlossen. Vorhanden: `ImageNode`, `MediaUrl` samt Policy, `MediaReference`,
`PositivePixels` und die beiden Commands `InsertImage`/`UpdateImage`.

## Nur der Kern

§6 stellt `image` neben `rich-text`, nicht darauf. Das ist keine Sparsamkeit, sondern eine
Aussage: ein Bild braucht vom Rich-Text-Profil nichts. Es hat keine Marks, keine Kinder und
keinen Absatz, in dem es stecken müsste — es ist ein Atom, das irgendwo zwischen zwei Zeichen
steht.

Die Testsuite hält das nach: `ImageNodeSpec` baut sich einen eigenen `BlockNode`, weil
`ParagraphNode` hier gar nicht auf dem Klassenpfad liegt. Das ist kein Behelf, sondern die Probe.

## Verwendung

```scala
val policy   = MediaUrlPolicy.default
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), ImageExtension(generator, policy)))
  .getOrElse(…)

policy.parse(eingabe) match
  case Right(src) =>
    val bild = ImageNode(generator.nextFor(session.document), MediaReference(src), "Alt-Text")
    session.dispatch(ImageCommands.InsertImage, bild)
  case Left(error) => zeige(error.render)

session.dispatch(ImageCommands.UpdateImage, (_: ImageNode).copy(alt = "Besser beschrieben"))
```

## Was hier ausdrücklich fehlt

Ein Picker, ein Upload, ein Fortschrittsbalken, ein `AbortSignal`. §20 legt alle vier woanders
hin: „Uploads sind ein Anwendungsservice. Browser-Datei, Progress und AbortSignal gehören zu
einem Browser-/Forms-Port, nicht zum Core-Node."

Was dieses Modul entgegennimmt, ist eine fertige `MediaReference` — etwas, das an einer Adresse
bereits existiert. §20: „Ein Upload liefert erst nach dauerhafter Speicherung eine validierte
MediaReference", und, für den Fall, dass man es vergisst, „auch bei direkter externer URL wird
kein Upload erzwungen".

Die Folge lohnt sich, ausgesprochen zu werden: **ein Bild einzufügen ist eine gewöhnliche
Dokumentänderung.** Eine History-Stufe, kein Lebenszyklus, und ein Undo entfernt den Knoten,
ohne irgendwo eine Datei anzufassen (§20).

**Keine Dateidaten im Dokument.** P16s Abnahme sagt es ausdrücklich; der Typ macht es unmöglich.
Eine `MediaReference` ist eine Adresse und eine optionale Kennung, sonst nichts. Es gibt kein
Feld, in das ein Base64-String oder eine `blob:`-URL passte.

**Kein Abruf.** Weder der Parser noch der SSR-Server holt jemals eine externe URL (§20). Ob das
Bild existiert, ist die Frage des Browsers, später gestellt; SSR stellt sie nie.

## `MediaUrl` — der Typ ist die Tür

Wie bei `LinkUrl`: es gibt keinen Weg, einen `ImageNode` ohne `MediaUrl` zu bauen, und keinen,
ein `MediaUrl` ohne Policy zu bekommen. Der Command-Pfad und der Dekodierpfad **können** nicht
auseinanderlaufen, weil es nur eine Tür gibt — `ImageJsonSupport.codec(policy)` nimmt dieselbe
Policy entgegen und ruft dieselbe Prüfung auf.

Eine Quelle, die in einem JSON-Payload ankommt, ist genau so ungeprüft wie eine aus einem
Dialog, und §20 unterscheidet nicht.

### Strenger als die Link-Policy, und warum

| | Link | Media |
| --- | --- | --- |
| `https` | ✓ | ✓ |
| `http` | ✓ | nur nach ausdrücklicher Entscheidung |
| relativ | ✓ | ✓ |
| `mailto`, `tel` | ✓ | ✗ |
| `data:`, `blob:`, `javascript:`, `file:` | ✗ | ✗ |
| protokollrelativ (`//host/…`) | ✗ | ✗ |
| Host-Allowlist | — | optional |

Ein Link wird von einem Leser gefolgt, der sich dafür entschieden hat. Ein Bild lädt die Seite
selbst, von einem Host, den der Autor genannt hat, mit der Adresse des Lesers dran. Das ist der
ganze Unterschied, und er steht in P14s Risikozeile: „Links und Media haben unterschiedliche
Policies."

`http` in einer `https`-Seite ist außerdem Mixed Content, den Browser ohnehin blockieren.
`MediaUrlPolicy.allowingHttp` gibt es trotzdem — ein Intranet-Editor darf das entscheiden.

### Unsichtbare Zeichen

Die Normalisierung ist dieselbe wie in `ember-link` (Entities zuerst, dann Whitespace und
Steuerzeichen vor dem Doppelpunkt, dann die Groß-/Kleinschreibung nur des Schemas). Ein Punkt
wurde beim Schreiben dieses Moduls nachgezogen und in **beide** Module zurückgetragen:

`isWhitespace || isControl` verfehlt genau die Zeichen, die sich am besten verstecken. Java
schließt das geschützte Leerzeichen aus `isWhitespace` ausdrücklich aus, und `isControl` deckt
nur die Cc-Gruppe ab. Browser ignorieren innerhalb einer URL aber alle drei Klassen:

```text
"java​script:alert(1)"   Zero Width Space
" javascript:alert(1)"   geschütztes Leerzeichen
"﻿javascript:alert(1)"   Byte Order Mark
"java⁠script:alert(1)"   Word Joiner
```

Das Prädikat deckt jetzt Cc, die Zs-Gruppe, U+2000–U+206F und U+FEFF ab — in `ember-image` wie
in `ember-link`, mit Tests in beiden.

### Host-Allowlist

`MediaUrlPolicy(hosts = Some(Set("cdn.example.com")))`. §20 führt sie als „separate,
deterministische Konfiguration": ein Editor darf überallhin verlinken und nur vom eigenen CDN
laden.

Der Vergleich ignoriert Groß-/Kleinschreibung und den Port, und er **überspringt die
Userinfo**. `https://cdn.example.com@boese.example/bild.png` lädt von `boese.example`; wer nur
bis zum ersten Punkt liest, sieht den erlaubten Host und übersieht den echten.

Relative Pfade haben keinen Host und gehen die Allowlist nichts an — sie laden von der Seite
selbst.

## Der Knoten

| Feld | | |
| --- | --- | --- |
| `source` | `MediaReference` | Adresse plus optionale `MediaId` der Anwendung |
| `alt` | `String` | darf leer sein, **und das bedeutet etwas** |
| `title` | `Option[String]` | leer oder nur Whitespace wird abgewiesen |
| `width`, `height` | `Option[PositivePixels]` | 1 bis 100 000 |

**Leerer Alt-Text ist kein fehlender Alt-Text.** §20: „ein dekoratives Bild verwendet
ausdrücklich leeren Alt-Text, nicht automatisch den Dateinamen." Deshalb schreibt der
HTML-Adapter `alt=""` auch dann, wenn nichts drinsteht — das Attribut wegzulassen ließe einen
Screenreader stattdessen den Dateinamen vorlesen.

`PositivePixels` ist validiert, weil eine Größe Layoutplatz reservieren soll (§20). Eine Null
reserviert nichts, und eine Zahl in Millionen sagt nur, dass weiter oben etwas schiefging.

## Einfügen und Ändern

| Fall | Was passiert |
| --- | --- |
| Caret mitten in einem Lauf | der Lauf wird geteilt, das Bild steht zwischen den Hälften |
| Caret am Anfang oder Ende | das Bild steht davor beziehungsweise dahinter |
| Caret auf einer Kindposition | das Bild wird dort eingesetzt |
| kein Caret | `Pass` |
| ID bereits vergeben | eine freie wird gezogen; eine mitgebrachte freie bleibt |

Ein Inline-Atom gehört **zwischen** Zeichen, nicht neben einen Absatz.

`UpdateImage` läuft über `Replace` und erhält damit die Identität (§10): ein Bookmark auf das
Bild überlebt eine Änderung seines Alt-Textes, und das ist die häufigste. Eine mitgegebene neue
ID wird verworfen.

Ein Caret kann nicht **in** einem Atom stehen — es hat keine Textposition. Was eine Auswahl
kann, ist es zu benennen: als `NodeSelection` oder als Kindposition, deren Nachbar es ist.

## Keine Normalisierung

Dieses Modul trägt keinen Transform bei, und das ist Absicht: es gibt keine Invariante zu
reparieren. Ein Bild hat keine Kinder, die falsch stehen könnten, und seine Felder sind bereits
so typisiert, dass ein falsches gar nicht gebaut werden kann.

## Tests

```bash
sbt --server "scalajs-ember-image/Test/testOnly *"
```

`MediaUrlPolicySpec` ist die Suite, um die es hier am meisten geht — jeder Fall darin ist einer,
den ein Browser ausführt. `ImageNodeSpec` fährt Einfügen, Ändern und die Pixelmaße.

Das `<img>`-Rendering und der JSON-Codec stehen in
`ember-standard/…/ImageAdapterSpec.scala` — dort, wo Bilder und Renderer einander kennen (§6).

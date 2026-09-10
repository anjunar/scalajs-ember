# scalajs-ember-link

Typisierte Inline-Links mit geprüfter URL-Policy. Headless und optional — kein Dialog nötig.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§8, 19–20.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-link` |
| Scala-Paket | `ember.editor.link` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text` |

## Stand

P14 abgeschlossen. Vorhanden: `LinkNode`, `LinkUrl` samt Policy, `SetLink`/`RemoveLink` und drei
Normalisierungsregeln.

## Verwendung

```scala
val policy   = LinkUrlPolicy.default
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), LinkExtension(generator, policy)))
  .getOrElse(…)

policy.parse(eingabe) match
  case Right(url)  => session.dispatch(LinkCommands.SetLink, LinkTarget(url, Some("Titel")))
  case Left(error) => zeige(error.render)

session.dispatch(LinkCommands.RemoveLink)
```

**Kein Dialog nötig** (P14, Abnahme). Alles hier nimmt ein `LinkTarget` und liefert eine
Dokumentänderung; woher das Ziel kommt — ein Dialog, ein Paste, ein Markdown-Import, ein Test —
ist eine andere Frage. `ember-ui` (P27) stellt später einen Dialog davor, und nichts in diesem
Modul muss sich dafür ändern.

## Ein Container, keine Mark

§8.2 ist eindeutig: „Links sind Inline-Container, keine Text-Mark." Der Unterschied ist keine
Taxonomie. Eine Mark hat weder Kinder noch Daten über ihre Identität hinaus; ein Link hat beides
— er umschließt einen Abschnitt und trägt ein Ziel. Als Mark modelliert hieße, eine URL in ein
`MarkSet` zu legen, was §8.2 im selben Satz ausschließt, indem es Marks auf „typisierte,
normalisierte Werte" festlegt.

Es bedeutet außerdem, dass Formatierung und Verlinkung sich vertragen, ohne voneinander zu
wissen: fett in einem Link ist eine Mark auf einem Lauf im Link, und keine der beiden Regeln
musste davon erfahren.

## `LinkUrl` — der Typ ist die Tür

P14s Abnahme verlangt: „URL-Validierung identisch bei Command/Import." Eine Konvention macht
daraus ein Versprechen, ein Typ eine Tatsache.

Es gibt keinen Weg, einen `LinkNode` ohne `LinkUrl` zu bauen, und keinen, ein `LinkUrl` ohne
Policy zu bekommen. Command-Pfad und Importpfad **können** nicht auseinanderlaufen, weil es nur
eine Tür gibt.

Der getragene Wert ist die normalisierte Form, nicht das Getippte.

### Normalisierung, und warum die Reihenfolge feststeht

P14s Risikozeile: „Stringpräfix-Tests allein reichen für normalisierte URLs nicht." Eine
Prüfung, die fragt, ob der Text mit `javascript:` beginnt, scheitert an jedem dieser Fälle — und
Browser führen sie alle aus:

```text
" javascript:alert(1)"        führendes Leerzeichen
"java\tscript:alert(1)"       Tab im Schema
"java\nscript:alert(1)"       Zeilenumbruch im Schema
"JaVaScRiPt:alert(1)"         gemischte Groß-/Kleinschreibung
"&#106;avascript:alert(1)"    HTML-Entity für den ersten Buchstaben
"java&#9;script:alert(1)"     Entity für den Tab
```

Deshalb ist die Reihenfolge fest und nicht umstellbar:

1. **Entities zuerst** — eine Entity kann ein Leerzeichen oder einen Schemabuchstaben kodieren.
2. **Whitespace und Steuerzeichen überall vor dem Doppelpunkt** — dort lässt sich ein Schema
   zerreißen.
3. **Groß-/Kleinschreibung nur des Schemas.**

§19.1 verlangt für den Importpfad genau diese Reihenfolge: „URLs werden nach
Entities-/Whitespace-Normalisierung durch die jeweilige Link-/Media-Policy geprüft."

**Nur das Schema wird angefasst.** Pfade sind case-sensitiv, Querys für einen Server, der darauf
achtet, ebenso. Der Host ist es nicht — ihn zu kanonisieren hieße aber, über Ports, IDN und
Userinfo zu entscheiden, und das halb richtig zu machen ist schlechter, als es zu lassen.
Sicherheitsrelevant ist das Schema.

### Was durchgeht

| | |
| --- | --- |
| Voreinstellung | `http`, `https`, `mailto`, `tel` und relative Adressen |
| `LinkUrlPolicy.internalOnly` | nur relative |
| `LinkUrlPolicy(allowRelative = false)` | nur absolute |
| eigenes Profil | `LinkUrlPolicy(schemes = Set("https", "ftp"))` |

Was erlaubt ist, entscheidet die Anwendung. Ein Intranet-Editor und ein öffentlicher sind sich
über `http` nicht einig, und eine Bibliothek kann das nicht für beide wissen.

**Nicht die Media-Policy.** §20 gibt Bildern eigene Regeln — Host-Allowlist, eine Entscheidung
über `http`, eine Absage an `data:` und `blob:` —, und P14s Risikozeile hält die beiden
auseinander: „Links und Media haben unterschiedliche Policies." Ein Dokument darf sehr wohl auf
eine Seite verlinken, von der es kein Bild lüde.

**Ein relatives Ziel, das wie ein Schema beginnt, wird abgewiesen.** `seite:mit:doppelpunkt`
*ist* ein Schema, nach RFC 3986 und für jeden Browser. Der Ausweg steht in derselben Norm: ein
führendes `./`.

## Setzen und Entfernen

| Fall | Was passiert |
| --- | --- |
| Bereich ausgewählt | die abgedeckten Läufe werden an beiden Enden geschnitten und umschlossen |
| Caret in einem Link | dessen Ziel ändert sich |
| Caret sonstwo | `Pass` — es gibt nichts zu umschließen |
| Bereich über mehrere Blöcke | ein Link je Block; ein Link ist inline, ein Knoten hat einen Elternteil |

Das Schneiden erledigt `RangeFormatting` aus P12 — es teilt bereits an einer Grenze, behandelt
eine rückwärts gerichtete Auswahl und lässt die Auswahl danach abgebildet zurück. Mit einer
Umformung, die nichts ändert, aufgerufen, benutzt dieses Modul genau das und nichts weiter.

**Alles ist ein Move.** §11: ein Move erhält den Knoten, also überlebt jeder Punkt im verlinkten
Abschnitt. Beim Entlinken behalten die Läufe ihre IDs, ihren Text und ihre Marks — P14s
Testliste verlangt es —, und danach lässt P12s Normalisierung zusammenwachsen, was zusammengehört:
ein entlinktes Wort mitten im Satz hinterlässt einen Lauf, nicht drei.

## Normalisierung

| Regel | Wofür |
| --- | --- |
| `noNestedLinks` | §8.2: „Ein Link enthält keine anderen Links." Der **innere** verliert seine Hülle — der äußere deckt mehr ab, also überlebt sein Ziel. |
| `emptyLinkGoes` | Ein `<a>` ohne Inhalt ist unsichtbar, unklickbar und überlebt jeden Export. |
| `adjacentLinksJoin` | Zwei benachbarte Links auf dasselbe Ziel werden einer. |

`adjacentLinksJoin` schaut **rückwärts**, aus demselben Grund wie die Listenregel: der neu
erzeugte Link ist der dirty Knoten, und eine vorwärts schauende Regel würde nur den fragen, der
nichts hinter sich hat (§3.4). Dieser Fehler ist inzwischen zweimal passiert; beim dritten Mal
wird er auf Anhieb erkannt.

## Tests

```bash
sbt --server "scalajs-ember-link/Test/testOnly *"
```

`LinkUrlPolicySpec` ist die Suite, um die es hier am meisten geht: jeder Fall darin ist einer,
den ein Browser ausführt und ein naives `startsWith("javascript:")` durchlässt. `LinkSpec` fährt
Setzen, Entfernen, Bereiche über Blockgrenzen und die Normalisierung.

Das semantische Anchor-Rendering und der Nachweis, dass die Projektion bewegt statt neu baut,
stehen in `ember-standard/…/LinkProjectionSpec.scala` — dort, wo Links und Renderer einander
kennen (§6). Dort steht auch die Entscheidung über `target` und `rel`.

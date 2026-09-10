# scalajs-ember-list

Geordnete und ungeordnete Listen: Ein- und Ausrücken, Enter und Backspace an Listengrenzen,
Normalisierung. Headless und optional.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§8, 11, 18.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-list` |
| Scala-Paket | `ember.editor.list` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text` |

## Stand

P13 abgeschlossen. Vorhanden: `ListNode`, `ListItemNode`, die drei Listenbefehle, die
Enter/Backspace-Behandlung und vier Normalisierungsregeln.

## Verwendung

```scala
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), ListExtension(generator)))
  .getOrElse(…)

session.dispatch(ListCommands.ToggleList, ListKind.Unordered)
session.dispatch(ListCommands.Indent)
session.dispatch(ListCommands.Outdent)
```

## Die Form, und warum sie so ist

§8.2 legt sie fest: „eine Liste ListItems, ein ListItem Blockinhalte."

```text
ul
  li
    "Erster Punkt"
    ul
      li
        "Eingerückt"
  li
    "Zweiter Punkt"
    "Noch ein Absatz im selben Punkt"
```

**Ein Item hält Blöcke, keine Inline-Inhalte.** Das ist keine Pedanterie, sondern der Grund,
warum ein Punkt zwei Absätze, eine verschachtelte Liste oder ein Zitat enthalten kann — alles
Dinge, die echte Dokumente tun. Ein Item mit Inline-Inhalt bräuchte für jeden dieser Fälle eine
zweite, parallele Struktur.

Der Preis ist eine Verschachtelungsebene mehr als ein naives Modell, und er wird an genau einer
Stelle bezahlt: ein leeres Item bekommt einen Absatz, damit es eine Caretposition gibt.

**Startnummer und Tightness stehen im Dokument.** §18.2 verlangt es: Markdown kann eine Liste
schreiben, die bei 3 beginnt, und ein Roundtrip, der still umnummeriert, wäre verlustbehaftet.
`tight` wird getragen und nicht abgeleitet — sonst änderte sich das Dokument, sobald jemand
einen zweiten Absatz in ein Item schreibt.

## Alles ist ein Move

Kein Umbau. §11s Mapping-Tabelle sagt, was das bringt: ein Move erhält den Knoten, also
überlebt jeder Punkt darin unangetastet — ein Caret im dritten Wort eines eingerückten Absatzes
steht danach im dritten Wort. Ein neu gebautes Item wäre einfacher zu schreiben und verlöre den
Cursor bei jedem Tab.

### Einrücken

Das Item wandert in das Item **darüber**. Das erste Item lässt sich nicht einrücken — es gibt
nichts, worin. Endet das Item darüber bereits mit einer Liste derselben Art, tritt das Item ihr
bei; sonst entsteht dort eine. Ohne diese Prüfung ergäben drei Einrückungen hintereinander drei
verschachtelte Listen mit je einem Punkt.

### Ausrücken

Zwei Fälle, und sie sind wirklich verschieden:

- **Verschachtelt:** das Item wird zum nächsten Geschwister des Items, das seine Liste enthielt.
- **Oberste Ebene:** es gibt nichts, wessen Geschwister es werden könnte — die Blöcke verlassen
  die Liste ganz.

**In beiden Fällen kommen die nachfolgenden Punkte mit.** Ein Item verlässt seine Liste *unten*,
also in Lesereihenfolge hinter allem, was noch darin steckt. Blieben die Nachfolger zurück,
stünden sie plötzlich vor dem Punkt, dem sie folgten: `[Zwei, Drei]` ausrücken und das Dokument
läse „Drei, Zwei". Beim verschachtelten Ausrücken werden sie zur Unterliste des ausgerückten
Items, an der obersten Ebene zu einer neuen Liste hinter den herausgelösten Blöcken. Das ist
auch, was Ein- und Ausrücken zueinander invers macht.

Eine geteilte nummerierte Liste zählt weiter: aus `1. 2. 3.` wird beim Herauslösen des zweiten
Punkts `1.` und `3.`, nicht `1.` und `1.`.

## Enter und Backspace

Beide bedeuten in einer Liste etwas anderes, und §12s Prioritätskette ist genau der Mechanismus
dafür: die Handler registrieren auf `CommandPriority.High`, über den Rich-Text-Handlern, und
geben `Pass` zurück, sobald der Caret nicht in einer Liste steht. Dieses Modul **ersetzt** das
Absatzteilen nicht — es hat Vorrang, wo Listen im Spiel sind, und tritt sonst beiseite.

| | |
| --- | --- |
| Enter in einem gefüllten Item | teilt es; der Teil hinter dem Caret wird ein neues Item |
| Enter in einem leeren Item | rückt eine Ebene aus; an der obersten Ebene endet die Liste |
| Backspace am Anfang des ersten Blocks | rückt aus, statt zu löschen |
| Backspace sonst | ganz normal |

**Das Teilen ruft die Funktion, nicht den Command.** Der Blocksplit liegt im Rich-Text-Profil,
behandelt Marks und Caretposition, und eine zweite Implementierung hier würde davon abdriften.
Erreichbar ist er aber nicht über einen Dispatch: §10 gibt Command-Handlern einen
`TransformScope`, gerade damit sie *keinen* weiteren Dispatch starten können — „eine
Command-Kette, die sich selbst verlängert, ist genau die verdeckte Reentranz, die §10
ausschließt". Also wird der gemeinsame Code als das aufgerufen, was er ist: eine Funktion auf
dem Entwurf.

## Normalisierung

Vier Regeln, alle als Transform (§3.2):

| Regel | Wofür |
| --- | --- |
| `looseChildNeedsItem` | Ein Block direkt in einer Liste bekommt ein Item. §8.2s Form, und P13s Abnahme: „Kein nackter Paragraph direkt in ListNode." |
| `emptyItemNeedsBlock` | Ein leeres Item bekommt einen Absatz — ein Caret braucht eine Textposition. |
| `adjacentListsJoin` | Zwei benachbarte Listen derselben Art werden eine. |
| `emptyListGoes` | Eine Liste ohne Items verschwindet. |

**Warum reparieren statt ablehnen.** Die Befehle könnten alle sorgfältig genug sein, das nie zu
erzeugen. Sie wären dann alle sorgfältig, einzeln, solange sich jemand daran erinnert — und das
erste fremde Modul, das einen Knoten in eine Liste bewegt, wäre es nicht.

**Warum `adjacentListsJoin` rückwärts schaut.** Weil der dirty Knoten handeln muss. Einen Absatz
zu umschließen erzeugt eine *neue* Liste neben einer bestehenden: die neue steht in
`ChangeSet.created`, die alte ist unberührt und wird nie Transform-Kandidat (§3.4). Eine Regel,
die vorwärts schaut, würde nur den Knoten fragen, der nichts hinter sich hat. Derselbe Fehler wie
beim Textlauf-Merge in P12, in derselben Form.

**Terminierung** ist Abnahmebedingung. Jede Regel entfernt entweder einen Knoten oder verringert
die Zahl der falsch platzierten, und keine erzeugt Arbeit für eine andere — sonst bräche §10s
Arbeitsbudget die Transaktion nach 32 Runden ab.

## Tests

```bash
sbt --server "scalajs-ember-list/Test/testOnly *"
```

`ListEditingSpec` fährt die Befehle, `ListNormalizationSpec` das Dokument — ein fremdes Modul,
ein Paste oder ein späteres Feature kann Knoten überallhin bewegen, und die Invarianten müssen
trotzdem halten.

Der semantische Export und der Nachweis, dass die Projektion bewegt statt neu baut, stehen in
`ember-standard/…/ListProjectionSpec.scala` — dort, wo Listen und Renderer einander kennen (§6).

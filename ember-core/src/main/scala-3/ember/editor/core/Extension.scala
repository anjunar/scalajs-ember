package ember.editor.core

/** Name einer Extension. Eindeutig innerhalb einer Sitzung. */
opaque type ExtensionId = String

object ExtensionId:

  def apply(value: String): ExtensionId =
    if value.nonEmpty && !value.exists(c => c.isWhitespace || c.isControl) then value
    else throw EditorContractViolation(s"Keine gueltige ExtensionId: `$value`")

  given Ordering[ExtensionId] = Ordering.String

  extension (id: ExtensionId) def value: String = id

/** Ersetzt eine eingebaute Knotenart durch eine spezialisierte.
  *
  * §8.3: eingebaute Typen spezialisieren, ohne jede Aufrufstelle anzupassen. Die Ersetzung betrifft
  * '''kuenftige Erzeugung''' -- bestehende Knoten aendert nur eine ausdrueckliche Migration.
  *
  * Der [[replacement]]-Deskriptor tritt an die Stelle des ersetzten. Beide Wire-Namen zeigen danach
  * auf ihn, damit bereits gespeicherte Dokumente weiter dekodierbar bleiben; ein Rendererwechsel
  * allein migriert kein Dokument.
  *
  * Zwei Ersetzungen derselben Art sind ein Aufloesungsfehler, kein Rennen um den letzten Eintrag
  * (§13).
  */
final case class NodeTypeReplacement(replaced: NodeTypeId, replacement: NodeType[?])

/** Was eine Extension zur Sitzung beitraegt.
  *
  * Ausschliesslich typisierte Beitraege, keine allgemeine Konfigurationssprache (§13). Was hier
  * nicht steht, kann eine Extension auch nicht beitragen -- Codecs, NodeViews und Browserverhalten
  * haengen an eigenen Adapterkonfigurationen, damit der Kern keine Referenz auf diese konkreten
  * Format- und UI-Typen bekommt.
  *
  * Die Kombinationsregel ist fuer jede Art ausdruecklich: alle Beitraege werden in
  * Aufloesungsreihenfolge '''angehaengt'''. Kein "der letzte gewinnt" -- wo zwei Beitraege einander
  * tatsaechlich ausschliessen (gleiche NodeTypeId, doppelte Ersetzung), meldet der Resolver einen
  * Fehler, statt still einen davon zu verwerfen.
  */
final case class ExtensionContributions(
    nodeTypes: Vector[NodeType[?]] = Vector.empty,
    replacements: Vector[NodeTypeReplacement] = Vector.empty,
    selectionMappers: Vector[SelectionMapper[?]] = Vector.empty,
    fields: Vector[StateField[?]] = Vector.empty,
    preCommitRules: Vector[PreCommitRule] = Vector.empty,
    transforms: Vector[Transform[?]] = Vector.empty,
    commands: Vector[CommandRegistration[?]] = Vector.empty
):

  def ++(other: ExtensionContributions): ExtensionContributions =
    ExtensionContributions(
      nodeTypes = nodeTypes ++ other.nodeTypes,
      replacements = replacements ++ other.replacements,
      selectionMappers = selectionMappers ++ other.selectionMappers,
      fields = fields ++ other.fields,
      preCommitRules = preCommitRules ++ other.preCommitRules,
      transforms = transforms ++ other.transforms,
      commands = commands ++ other.commands
    )

object ExtensionContributions:
  val empty: ExtensionContributions = ExtensionContributions()

/** Ein unabhaengig installierbares Funktionsbuendel.
  *
  * ==Was eine Extension beim Erzeugen nicht tun darf==
  *
  * §13: kein DOM-Zugriff, keine globale Registrierung. Eine Extension-Fabrik ist ein Wert; sie
  * beschreibt, was beigetragen wird, und tut es nicht selbst. Deshalb laesst sich eine
  * Konfiguration serverseitig aufloesen, in Tests vergleichen und ohne Browser pruefen.
  *
  * ==Lebenszyklus==
  *
  * `resolve → validate → install → dispose in umgekehrter Reihenfolge`. [[contribute]] gehoert zur
  * Aufloesung und laeuft, bevor irgendetwas existiert. [[install]] laeuft erst gegen die fertige
  * Sitzung und liefert seine eigene Aufraeumaktion -- scheitert eine Installation, werden alle bis
  * dahin installierten in umgekehrter Reihenfolge wieder abgebaut.
  */
trait Extension:

  def id: ExtensionId

  /** Extensions, die vorher aufgeloest sein muessen. Fehlende und Zyklen sind Fehler (§13). */
  def dependsOn: Vector[ExtensionId] = Vector.empty

  /** Der rein deklarative Beitrag. Keine Seiteneffekte. */
  def contribute: ExtensionContributions = ExtensionContributions.empty

  /** Bindet Laufzeitressourcen an die fertige Sitzung.
    *
    * Der Regelfall braucht das nicht -- Commands, Transforms und Felder kommen ueber
    * [[contribute]]. Gedacht fuer das, was tatsaechlich eine lebende Sitzung braucht: ein Listener,
    * ein Zeitgeber, eine spaetere Browseranbindung.
    */
  def install(session: EditorSession): Subscription = Subscription.cancelled

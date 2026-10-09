package ember.editor.core

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

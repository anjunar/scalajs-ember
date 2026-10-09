package ember.editor.core

import scala.collection.mutable

/** Das Ergebnis einer erfolgreichen Aufloesung: alles, was eine Sitzung braucht.
  *
  * Das Schema entsteht hier und nicht in der Sitzung, weil das Dokument es schon beim Bauen
  * braucht. Der uebliche Ablauf ist deshalb: aufloesen, Dokument gegen `schema` bauen, Sitzung
  * daraus erzeugen.
  */
final case class ResolvedExtensions(
    order: Vector[ExtensionId],
    extensions: Vector[Extension],
    schema: Schema,
    contributions: ExtensionContributions
):

  /** Die Sitzungskonfiguration aus allen Beitraegen. */
  def sessionConfig(
      mappingRetention: Int = 64,
      transformBudget: TransformBudget = TransformBudget.default,
      strictCommands: Boolean = true,
      errorSink: EditorError => Unit = _ => ()
  ): SessionConfig =
    SessionConfig(
      fields = contributions.fields.distinct,
      preCommitRules = contributions.preCommitRules,
      selectionSupport = SelectionSupport.core.extendedWith(contributions.selectionMappers*),
      transforms = contributions.transforms,
      commands = contributions.commands,
      transformBudget = transformBudget,
      mappingRetention = mappingRetention,
      strictCommands = strictCommands,
      errorSink = errorSink
    )

  /** Ein leeres Dokument gegen dieses Schema. Der uebliche Einstieg. */
  def emptyDocument(rootId: NodeId): Either[Vector[Violation], Document] =
    Document.empty(schema, rootId)

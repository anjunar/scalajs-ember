package ember.editor.core

import scala.collection.mutable

/** Warum eine Extension-Konfiguration nicht aufloesbar ist.
  *
  * Alle diese Fehler entstehen '''vor''' jeder Installation (§13, Akzeptanz). Eine halb aufgebaute
  * Sitzung gibt es nicht.
  */
sealed trait ExtensionError extends EditorError

object ExtensionError:

  final case class DuplicateExtension(id: ExtensionId) extends ExtensionError:
    def message: String               = s"Die Extension `${id.value}` ist mehrfach angegeben."
    override def path: DiagnosticPath = DiagnosticPath.field(id.value)

  final case class MissingDependency(id: ExtensionId, required: ExtensionId) extends ExtensionError:
    def message: String =
      s"`${id.value}` braucht `${required.value}`, das nicht angegeben ist."
    override def path: DiagnosticPath = DiagnosticPath.field(id.value)

  final case class DependencyCycle(involved: Vector[ExtensionId]) extends ExtensionError:
    def message: String =
      involved.map(_.value).mkString("Zyklische Abhaengigkeit zwischen: ", ", ", ".")

  final case class DuplicateNodeType(typeId: NodeTypeId) extends ExtensionError:
    def message: String =
      s"Die NodeTypeId `${typeId.value}` wird von mehreren Extensions beigetragen."
    override def path: DiagnosticPath = DiagnosticPath.field(typeId.value)

  final case class DuplicateReplacement(typeId: NodeTypeId) extends ExtensionError:
    def message: String =
      s"`${typeId.value}` soll mehrfach ersetzt werden. Welche Ersetzung gilt, waere Zufall."
    override def path: DiagnosticPath = DiagnosticPath.field(typeId.value)

  final case class ReplacementOfUnknownType(typeId: NodeTypeId) extends ExtensionError:
    def message: String =
      s"`${typeId.value}` soll ersetzt werden, ist aber gar nicht registriert."
    override def path: DiagnosticPath = DiagnosticPath.field(typeId.value)

  /** Das Dokument wurde gegen ein anderes Schema gebaut als das aufgeloeste. */
  case object SchemaMismatch extends ExtensionError:
    def message: String =
      "Das Dokument gehoert zu einem anderen Schema als die aufgeloesten Extensions. " +
        "Das Dokument muss gegen `resolved.schema` gebaut werden."
  final case class InstallationFailed(id: ExtensionId, cause: String) extends ExtensionError:
    def message: String = s"Die Installation von `${id.value}` ist gescheitert: $cause"
    override def path: DiagnosticPath = DiagnosticPath.field(id.value)

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

/** Loest eine Extension-Konfiguration auf.
  *
  * Prueft '''vollstaendig''', bevor irgendetwas gebaut wird: doppelte Extensions, fehlende
  * Abhaengigkeiten, Zyklen, doppelte Knotenarten, mehrfache oder ins Leere zeigende Ersetzungen. Es
  * gibt keine Reihenfolge, in der eine dieser Verletzungen erst zur Laufzeit auffiele.
  */
object ExtensionResolver:

  def resolve(extensions: Vector[Extension]): Either[Vector[ExtensionError], ResolvedExtensions] =
    for
      _       <- noDuplicateIds(extensions)
      ordered <- topologicalOrder(extensions)
      combined = ordered.map(_.contribute).foldLeft(ExtensionContributions.empty)(_ ++ _)
      _      <- noDuplicateNodeTypes(combined)
      _      <- replacementsAreSound(combined)
      schema <- buildSchema(combined)
    yield ResolvedExtensions(ordered.map(_.id), ordered, schema, combined)

  private def noDuplicateIds(
      extensions: Vector[Extension]
  ): Either[Vector[ExtensionError], Unit] =
    val duplicates = extensions
      .groupBy(_.id)
      .collect { case (id, entries) if entries.sizeIs > 1 => ExtensionError.DuplicateExtension(id) }
      .toVector
      .sortBy(_.id.value)

    if duplicates.isEmpty then Right(()) else Left(duplicates)

  /** Abhaengigkeitsordnung, bei Gleichstand die angegebene Reihenfolge.
    *
    * Iterativ, damit eine tiefe Kette nicht am Stack scheitert -- dieselbe Ueberlegung wie bei der
    * Dokumenttraversierung (§8.3).
    */
  private def topologicalOrder(
      extensions: Vector[Extension]
  ): Either[Vector[ExtensionError], Vector[Extension]] =
    val byId    = extensions.map(extension => extension.id -> extension).toMap
    val missing = for
      extension <- extensions
      required  <- extension.dependsOn
      if !byId.contains(required)
    yield ExtensionError.MissingDependency(extension.id, required)

    if missing.nonEmpty then Left(missing)
    else
      val ordered   = Vector.newBuilder[Extension]
      val placed    = mutable.LinkedHashSet.empty[ExtensionId]
      var remaining = extensions

      var progress = true
      while remaining.nonEmpty && progress do
        val (ready, blocked) = remaining.partition(_.dependsOn.forall(placed.contains))
        progress = ready.nonEmpty
        ready.foreach { extension =>
          ordered += extension
          placed += extension.id
        }
        remaining = blocked

      if remaining.isEmpty then Right(ordered.result())
      else Left(Vector(ExtensionError.DependencyCycle(remaining.map(_.id).sortBy(_.value))))

  private def noDuplicateNodeTypes(
      contributions: ExtensionContributions
  ): Either[Vector[ExtensionError], Unit] =
    val duplicates = contributions.nodeTypes
      .groupBy(_.typeId)
      .collect {
        case (typeId, entries) if entries.sizeIs > 1 =>
          ExtensionError.DuplicateNodeType(typeId)
      }
      .toVector
      .sortBy(_.typeId.value)

    if duplicates.isEmpty then Right(()) else Left(duplicates)

  private def replacementsAreSound(
      contributions: ExtensionContributions
  ): Either[Vector[ExtensionError], Unit] =
    val known = contributions.nodeTypes.map(_.typeId).toSet

    val doubled = contributions.replacements
      .groupBy(_.replaced)
      .collect {
        case (typeId, entries) if entries.sizeIs > 1 =>
          ExtensionError.DuplicateReplacement(typeId)
      }
      .toVector

    val dangling = contributions.replacements
      .filterNot(replacement => known.contains(replacement.replaced))
      .map(replacement => ExtensionError.ReplacementOfUnknownType(replacement.replaced))

    val errors = (doubled ++ dangling).sortBy(_.message)
    if errors.isEmpty then Right(()) else Left(errors)

  /** Setzt die Ersetzungen an die Stelle der ersetzten Deskriptoren.
    *
    * An dieselbe Stelle, damit die Reihenfolge der Typzeugensuche stabil bleibt, und mit einem
    * Alias vom alten Wire-Namen, damit gespeicherte Dokumente weiter dekodierbar sind.
    */
  private def buildSchema(
      contributions: ExtensionContributions
  ): Either[Vector[ExtensionError], Schema] =
    val byReplaced = contributions.replacements.map(entry => entry.replaced -> entry).toMap

    val descriptors = contributions.nodeTypes.map { descriptor =>
      byReplaced.get(descriptor.typeId).fold(descriptor)(_.replacement)
    }
    val aliases = contributions.replacements
      .map(entry => entry.replaced -> entry.replacement.typeId)
      .filterNot((replaced, into) => replaced == into)
      .toMap

    Schema.of(descriptors*) match
      case Right(schema) => Right(schema.withAliases(aliases))
      case Left(errors)  =>
        // Kann nur die Ersetzung selbst verursacht haben: doppelte Beitraege sind bereits
        // ausgeschlossen, doppelte Ersetzungen ebenso.
        Left(
          errors.map(error =>
            ExtensionError.DuplicateNodeType(error match {
              case SchemaError.DuplicateTypeId(typeId) => typeId
            })
          )
        )

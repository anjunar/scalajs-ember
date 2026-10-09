package ember.editor.core

import scala.collection.mutable

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

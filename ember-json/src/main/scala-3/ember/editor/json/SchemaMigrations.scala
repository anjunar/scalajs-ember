package ember.editor.json

import ember.editor.core.*

/** Die bekannten Migrationsschritte, als Kette anwendbar.
  *
  * Hoechstens ein Schritt je Ausgangsversion -- sonst waere der Pfad nicht eindeutig, und welche
  * von zwei moeglichen Umformungen ein Dokument erfaehrt, darf nicht von einer
  * Registrierungsreihenfolge abhaengen.
  */
final class SchemaMigrations private (val steps: Vector[SchemaMigration]):

  private val byFrom: Map[Int, SchemaMigration] = steps.map(step => step.from -> step).toMap

  /** Migriert ein Envelope von `from` auf `to`.
    *
    * Trifft die Kette die Zielversion nicht genau, ist das ein fehlender Pfad und damit ein Fehler
    * (§19.2) -- nicht eine Uebernahme des zuletzt erreichten Standes.
    */
  def apply(
      envelope: JsonValue.Obj,
      from: Int,
      to: Int
  ): Either[DecodeError, JsonValue.Obj] =
    if from == to then Right(envelope)
    else if from > to then Left(DecodeError.MissingMigration(from, to))
    else
      byFrom.get(from) match
        case None                       => Left(DecodeError.MissingMigration(from, to))
        case Some(step) if step.to > to =>
          // Der Schritt springt ueber das Ziel hinweg. Ihn trotzdem anzuwenden hiesse, ein
          // Dokument in eine Version zu heben, die der Aufrufer nicht angefordert hat.
          Left(DecodeError.MissingMigration(from, to))
        case Some(step) =>
          step.migrate(envelope) match
            case Left(reason)    => Left(DecodeError.MigrationFailed(step.from, step.to, reason))
            case Right(migrated) =>
              apply(setVersion(migrated, step.to), step.to, to)

  /** Traegt die erreichte Schemaversion ein.
    *
    * Damit muss eine Migration es nicht selbst tun -- und kann es auch nicht vergessen. Der Schritt
    * beschreibt die Umformung der Daten, die Buchhaltung ueber die Version gehoert hierher.
    */
  private def setVersion(envelope: JsonValue.Obj, version: Int): JsonValue.Obj =
    JsonValue.Obj(
      envelope.fields.filterNot(_._1 == DocumentJson.schemaVersionField) :+
        (DocumentJson.schemaVersionField -> JsonValue.num(version))
    )

object SchemaMigrations:

  /** Keine Migration. Dann ist jede von der Zielversion abweichende Angabe ein Fehler. */
  val none: SchemaMigrations = new SchemaMigrations(Vector.empty)

  def of(steps: SchemaMigration*): Either[Vector[MigrationSetupError], SchemaMigrations] =
    val descending = steps
      .filter(step => step.to <= step.from)
      .map(step => MigrationSetupError.NotAscending(step.from, step.to))
    val ambiguous = steps
      .groupBy(_.from)
      .collect {
        case (from, entries) if entries.sizeIs > 1 => MigrationSetupError.AmbiguousStep(from)
      }
      .toVector
      .sortBy(_.from)

    val errors = descending.toVector ++ ambiguous
    if errors.nonEmpty then Left(errors) else Right(new SchemaMigrations(steps.toVector))

  /** Wie [[of]], wirft aber. Fuer im Code feststehende Ketten und Tests. */
  def unsafe(steps: SchemaMigration*): SchemaMigrations =
    of(steps*) match
      case Right(chain) => chain
      case Left(errors) =>
        throw EditorContractViolation(
          errors.map(_.render).mkString("Ungueltige Migrationskette:\n", "\n", "")
        )

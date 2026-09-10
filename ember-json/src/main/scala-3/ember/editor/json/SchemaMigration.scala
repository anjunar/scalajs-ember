package ember.editor.json

import ember.editor.core.*

/** Ein Migrationsschritt zwischen zwei Schemaversionen.
  *
  * ==Rein, und zwar an der Signatur erkennbar==
  *
  * §19.2: "Migrationsfunktionen sind versioniert und rein." [[migrate]] bekommt das
  * Envelope-Objekt und liefert ein neues -- kein Editor, keine Sitzung, kein Dokument, kein
  * Schema. Eine Migration, die eine Sitzung braeuchte, waere keine Migration, sondern eine
  * Bearbeitung, und sie liefe genau dann, wenn noch gar kein gueltiges Dokument existiert.
  *
  * Sie arbeitet auf dem '''ganzen Envelope''', nicht auf einzelnen Knoten. Eine Umbenennung von
  * Knotenarten, das Aufspalten eines Knotens in zwei oder das Nachtragen eines Kindes sind
  * genau die Faelle, um die es geht, und keiner davon ist knotenlokal.
  *
  * @param from
  *   die Schemaversion, die dieser Schritt liest
  * @param to
  *   die Schemaversion, die er erzeugt. Muss groesser als [[from]] sein.
  * @param migrate
  *   die Umformung. `Left` ist die Begruendung, warum dieser Payload nicht migrierbar ist.
  */
final case class SchemaMigration(
    from: Int,
    to: Int,
    migrate: JsonValue.Obj => Either[String, JsonValue.Obj]
)

/** Fehler beim Aufbau einer [[SchemaMigrations]]-Kette. */
sealed trait MigrationSetupError extends EditorError

object MigrationSetupError:

  final case class NotAscending(from: Int, to: Int) extends MigrationSetupError:
    def message: String =
      s"Ein Migrationsschritt fuehrt von $from nach $to. Migriert wird nur vorwaerts -- ein " +
        "aelterer Leser bekommt ein neueres Dokument nicht durch Herunterrechnen lesbar."

  final case class AmbiguousStep(from: Int) extends MigrationSetupError:
    def message: String =
      s"Zwei Migrationsschritte lesen Schemaversion $from. Der Pfad waere nicht eindeutig."

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
    * Trifft die Kette die Zielversion nicht genau, ist das ein fehlender Pfad und damit ein
    * Fehler (§19.2) -- nicht eine Uebernahme des zuletzt erreichten Standes.
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
        case None => Left(DecodeError.MissingMigration(from, to))
        case Some(step) if step.to > to =>
          // Der Schritt springt ueber das Ziel hinweg. Ihn trotzdem anzuwenden hiesse, ein
          // Dokument in eine Version zu heben, die der Aufrufer nicht angefordert hat.
          Left(DecodeError.MissingMigration(from, to))
        case Some(step) =>
          step.migrate(envelope) match
            case Left(reason) => Left(DecodeError.MigrationFailed(step.from, step.to, reason))
            case Right(migrated) =>
              apply(setVersion(migrated, step.to), step.to, to)

  /** Traegt die erreichte Schemaversion ein.
    *
    * Damit muss eine Migration es nicht selbst tun -- und kann es auch nicht vergessen. Der
    * Schritt beschreibt die Umformung der Daten, die Buchhaltung ueber die Version gehoert
    * hierher.
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
    val descending = steps.filter(step => step.to <= step.from)
      .map(step => MigrationSetupError.NotAscending(step.from, step.to))
    val ambiguous = steps
      .groupBy(_.from)
      .collect { case (from, entries) if entries.sizeIs > 1 => MigrationSetupError.AmbiguousStep(from) }
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

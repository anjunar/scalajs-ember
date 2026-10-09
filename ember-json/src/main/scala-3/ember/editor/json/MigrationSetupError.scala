package ember.editor.json

import ember.editor.core.*

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

package ember.editor.json

import ember.editor.core.*

/** Ein Migrationsschritt zwischen zwei Schemaversionen.
  *
  * ==Rein, und zwar an der Signatur erkennbar==
  *
  * §19.2: "Migrationsfunktionen sind versioniert und rein." [[migrate]] bekommt das Envelope-Objekt
  * und liefert ein neues -- kein Editor, keine Sitzung, kein Dokument, kein Schema. Eine Migration,
  * die eine Sitzung braeuchte, waere keine Migration, sondern eine Bearbeitung, und sie liefe genau
  * dann, wenn noch gar kein gueltiges Dokument existiert.
  *
  * Sie arbeitet auf dem '''ganzen Envelope''', nicht auf einzelnen Knoten. Eine Umbenennung von
  * Knotenarten, das Aufspalten eines Knotens in zwei oder das Nachtragen eines Kindes sind genau
  * die Faelle, um die es geht, und keiner davon ist knotenlokal.
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

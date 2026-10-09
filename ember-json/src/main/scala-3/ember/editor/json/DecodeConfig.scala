package ember.editor.json

import ember.editor.core.*

import scala.annotation.tailrec

/** Einstellungen fuer das Dekodieren.
  *
  * @param schemaVersion
  *   die Version, die diese Anwendung versteht. Ein aelterer Payload wird ueber [[migrations]]
  *   darauf gehoben.
  * @param unknownNodes
  *   was mit unbekannten Knotenarten geschieht. Voreinstellung [[UnknownNodePolicy.Strict]] --
  *   §19.2 verlangt eine ausdrueckliche Wahl fuer alles andere.
  */
final case class DecodeConfig(
    schemaVersion: Int = 1,
    limits: DecodeLimits = DecodeLimits.default,
    unknownNodes: UnknownNodePolicy = UnknownNodePolicy.Strict,
    migrations: SchemaMigrations = SchemaMigrations.none
)

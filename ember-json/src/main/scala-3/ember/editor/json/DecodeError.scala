package ember.editor.json

import ember.editor.core.*

/** Ein Fehlschlag beim Dekodieren. Geschlossen -- die Faelle sind eine Eigenschaft des Formats.
  */
sealed trait DecodeError extends EditorError

object DecodeError:

  final case class MalformedJson(reason: String) extends DecodeError:
    def message: String = s"Kein gueltiges JSON: $reason"

  final case class TypeMismatch(expected: String, found: String, override val path: DiagnosticPath)
      extends DecodeError:
    def message: String = s"Erwartet wurde `$expected`, gefunden `$found`."

  final case class MissingField(name: String, override val path: DiagnosticPath)
      extends DecodeError:
    def message: String = s"Das Feld `$name` fehlt."

  final case class InvalidValue(reason: String, override val path: DiagnosticPath)
      extends DecodeError:
    def message: String = reason

  final case class LimitExceeded(
      limit: String,
      allowed: Int,
      found: Int,
      override val path: DiagnosticPath
  ) extends DecodeError:
    def message: String = s"Die Grenze `$limit` erlaubt $allowed, gefunden wurden $found."

  final case class UnknownFormat(found: String) extends DecodeError:
    def message: String =
      s"`$found` ist kein Ember-Dokument (erwartet: `${DocumentJson.formatName}`)."

  final case class UnsupportedFormatVersion(found: Int, supported: Int) extends DecodeError:
    def message: String =
      s"Formatversion $found ist unbekannt; dieser Stand liest $supported. " +
        "Die Formatversion wird nicht migriert -- ein neueres Format braucht einen neueren Leser."

  final case class UnknownNodeType(typeId: String, override val path: DiagnosticPath)
      extends DecodeError:
    def message: String =
      s"Das Schema kennt die Knotenart `$typeId` nicht. " +
        "Mit `UnknownNodePolicy.Preserve` bliebe sie als UnsupportedNode erhalten (§19.2)."

  final case class NoCodec(typeId: String, override val path: DiagnosticPath) extends DecodeError:
    def message: String =
      s"Fuer die Knotenart `$typeId` ist kein NodeJsonCodec registriert."

  final case class UnknownMark(markId: String, override val path: DiagnosticPath)
      extends DecodeError:
    def message: String = s"Fuer die Markierung `$markId` ist kein MarkJsonCodec registriert."

  final case class UnsupportedCodecVersion(
      typeId: String,
      found: Int,
      supported: Int,
      override val path: DiagnosticPath
  ) extends DecodeError:
    def message: String =
      s"Der Codec fuer `$typeId` liest Version $supported, der Payload nennt $found."

  final case class DuplicateNodeId(nodeId: String, override val path: DiagnosticPath)
      extends DecodeError:
    def message: String = s"Die Knoten-ID `$nodeId` kommt mehrfach vor."

  /** Das dekodierte Dokument verletzt die Invarianten aus §8.2.
    *
    * Der eigentliche Befund steht in [[violations]] -- referenzielle Integritaet, Zyklen, mehrfache
    * Eltern und Schemakonformitaet prueft der Kern, nicht dieses Modul. Ein zweiter Validator hier
    * waere eine zweite Wahrheit ueber dieselbe Frage.
    */
  final case class InvalidDocument(violations: Vector[Violation]) extends DecodeError:
    def message: String =
      violations.map(_.render).mkString("Das dekodierte Dokument ist ungueltig: ", "; ", "")

  final case class MissingMigration(from: Int, to: Int) extends DecodeError:
    def message: String =
      s"Kein Migrationspfad von Schemaversion $from nach $to. " +
        "Fehlende Migrationspfade sind Fehler (§19.2), keine stillschweigende Uebernahme."

  final case class MigrationFailed(from: Int, to: Int, reason: String) extends DecodeError:
    def message: String = s"Die Migration von $from nach $to schlug fehl: $reason"

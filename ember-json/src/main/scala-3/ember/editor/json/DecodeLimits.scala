package ember.editor.json

import ember.editor.core.*

/** Obergrenzen fuer das Dekodieren fremder Payloads.
  *
  * §19.2: "Decode prueft Typen, Zahlenbereiche, Limits, doppelte IDs, referenzielle Integritaet
  * und Schema, bevor eine Session oder View entsteht."
  *
  * ==Warum ueberhaupt Grenzen==
  *
  * Ein Dokument kommt aus dem Netz, aus der Zwischenablage oder aus einem Formularfeld. Ohne
  * Grenzen entscheidet der Absender, wie viel Speicher und Rechenzeit der Empfaenger aufwendet
  * -- und zwar bevor irgendeine fachliche Pruefung greift. Die Werte hier sind bewusst
  * grosszuegig fuer echte Dokumente und knapp gegenueber dem, was ein Angreifer schickt.
  *
  * Sie sind ein Wert, keine Konstante: eine Anwendung, die 200 000 Knoten braucht, setzt sie
  * hoch und weiss dann, dass sie es getan hat.
  *
  * @param maxSourceChars
  *   Laenge des Quelltexts. Wird '''vor''' dem Parsen geprueft -- danach steht der Speicher
  *   schon.
  * @param maxJsonDepth
  *   Schachtelungstiefe des JSON-Werts. Schuetzt die Konvertierung, die rekursiv laeuft.
  * @param maxArrayLength
  *   Elemente eines einzelnen Arrays.
  * @param maxObjectFields
  *   Felder eines einzelnen Objekts.
  * @param maxNodes
  *   Knoten eines Dokuments.
  * @param maxChildren
  *   Kinder eines einzelnen Knotens.
  * @param maxTextChars
  *   Laenge eines einzelnen Textlaufs, in UTF-16-Einheiten wie ueberall im Modell (§11).
  * @param maxDocumentDepth
  *   Tiefe des Dokumentbaums.
  */
final case class DecodeLimits(
    maxSourceChars: Int = 8 * 1024 * 1024,
    maxJsonDepth: Int = 64,
    maxArrayLength: Int = 200_000,
    maxObjectFields: Int = 256,
    maxNodes: Int = 100_000,
    maxChildren: Int = 20_000,
    maxTextChars: Int = 1_000_000,
    maxDocumentDepth: Int = 100
)

object DecodeLimits:

  /** Die Voreinstellung. Fuer Dokumente, die ein Mensch geschrieben hat, nie erreichbar. */
  val default: DecodeLimits = DecodeLimits()

/** Was mit einem Knoten geschieht, dessen Typ das Schema nicht kennt.
  *
  * §19.2 laesst genau zwei Moeglichkeiten zu, und die zweite muss ausdruecklich gewaehlt
  * werden: "Unbekannte Daten werden nur mit expliziter Policy erhalten."
  */
enum UnknownNodePolicy:

  /** Ablehnen, mit Pfad und TypeId. Die Voreinstellung. */
  case Strict

  /** Als [[UnsupportedNode]] erhalten.
    *
    * Der Payload ueberlebt einen Roundtrip unveraendert. Er wird '''nicht''' interpretiert:
    * kein Code, kein HTML, kein automatisch deserialisiertes Objekt -- nur der begrenzte
    * JSON-Wert und ein Textfallback (§19.2).
    */
  case Preserve

/** Ein Hinweis, der das Dekodieren nicht verhindert hat.
  *
  * Getrennt von [[DecodeError]], weil der Unterschied fuer den Aufrufer zaehlt: ein Fehler
  * bedeutet, dass kein Dokument entstanden ist, eine Diagnose, dass eines entstand und etwas
  * daran bemerkenswert ist.
  */
final case class DecodeDiagnostic(message: String, path: DiagnosticPath):
  def render: String = s"${path.render}: $message"

/** Ein Fehlschlag beim Dekodieren. Geschlossen -- die Faelle sind eine Eigenschaft des
  * Formats.
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
    * Der eigentliche Befund steht in [[violations]] -- referenzielle Integritaet, Zyklen,
    * mehrfache Eltern und Schemakonformitaet prueft der Kern, nicht dieses Modul. Ein zweiter
    * Validator hier waere eine zweite Wahrheit ueber dieselbe Frage.
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

/** Ein Fehlschlag beim Kodieren. */
sealed trait EncodeError extends EditorError

object EncodeError:

  final case class NoCodec(nodeId: NodeId, nodeClass: String, override val path: DiagnosticPath)
      extends EncodeError:
    def message: String =
      s"Fuer `${nodeId.value}` ($nodeClass) ist kein NodeJsonCodec registriert."

  final case class NoMarkCodec(nodeId: NodeId, markId: MarkId, override val path: DiagnosticPath)
      extends EncodeError:
    def message: String =
      s"Fuer die Markierung `${markId.value}` an `${nodeId.value}` ist kein MarkJsonCodec " +
        "registriert."

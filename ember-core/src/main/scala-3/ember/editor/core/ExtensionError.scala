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

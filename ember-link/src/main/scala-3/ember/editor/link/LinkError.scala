package ember.editor.link

import ember.editor.core.*

/** Why a URL was refused. */
sealed trait LinkError extends EditorError

object LinkError:

  final case class Empty(override val path: DiagnosticPath) extends LinkError:
    def message: String = "Eine leere Adresse ist kein Link."

  final case class ForbiddenScheme(scheme: String, override val path: DiagnosticPath)
      extends LinkError:
    def message: String =
      s"Das Schema `$scheme` ist als Linkziel nicht zugelassen. " +
        "Erlaubt sind die Schemata des Profils sowie relative Adressen."

  final case class ProtocolRelative(override val path: DiagnosticPath) extends LinkError:
    def message: String =
      "`//host/...` uebernimmt das Schema der Seite. Absolute Adressen nennen ihr Schema."

  final case class Malformed(reason: String, override val path: DiagnosticPath) extends LinkError:
    def message: String = reason

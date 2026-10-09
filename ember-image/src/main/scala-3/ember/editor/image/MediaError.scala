package ember.editor.image

import ember.editor.core.*

/** Why a media source was refused. */
sealed trait MediaError extends EditorError

object MediaError:

  final case class Empty(override val path: DiagnosticPath) extends MediaError:
    def message: String = "Eine leere Adresse ist keine Bildquelle."

  final case class ForbiddenScheme(scheme: String, override val path: DiagnosticPath)
      extends MediaError:
    def message: String =
      s"Das Schema `$scheme` ist als Bildquelle nicht zugelassen. " +
        "§20 laesst `data:`, `blob:` und aktive Schemata ausdruecklich nicht als dauerhafte " +
        "MediaReference zu."

  final case class ProtocolRelative(override val path: DiagnosticPath) extends MediaError:
    def message: String =
      "`//host/...` uebernimmt das Schema der Seite und ist damit keine feste Quelle."

  final case class ForbiddenHost(host: String, override val path: DiagnosticPath)
      extends MediaError:
    def message: String = s"Der Host `$host` steht nicht auf der Allowlist."

  final case class AmbiguousUrl(override val path: DiagnosticPath) extends MediaError:
    def message: String = "Backslashes sind in einer dauerhaften Bildquelle nicht erlaubt."

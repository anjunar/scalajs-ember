package ember.editor.history

import ember.editor.core.*

/** Die Konfiguration einer [[History]]. */
final case class HistoryConfig(
    limits: HistoryLimits = HistoryLimits.default,
    /** §14: "Import setzt History standardmaessig zurueck."
      *
      * Standardmaessig, nicht zwingend: "fachlich gewuenschte Einfuegung importierter Fragmente ist
      * eine normale Aenderung" -- die traegt dann [[Origin.User]] und nicht [[Origin.Import]].
      */
    resetOnImport: Boolean = true
)

object HistoryConfig:
  val default: HistoryConfig = HistoryConfig()

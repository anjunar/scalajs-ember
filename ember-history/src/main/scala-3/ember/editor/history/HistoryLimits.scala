package ember.editor.history

import ember.editor.core.*

/** Der Zeitgeber der History.
  *
  * §14: "Zeitgeber wird injiziert." Nicht aus Reinheitsliebe -- die Zeitfenster der Gruppierung
  * sind sonst nicht pruefbar. Ein Test, der auf echte Millisekunden wartet, um eine Gruppengrenze
  * zu belegen, prueft die Systemuhr und nicht die Regel.
  */
trait HistoryClock:
  /** Millisekunden seit einem beliebigen, aber festen Nullpunkt. */
  def now(): Long

object HistoryClock:

  /** Die Systemuhr. Fuer Anwendungen; Tests nehmen ihre eigene. */
  val system: HistoryClock = () => System.currentTimeMillis()

  /** Eine Uhr, die stillsteht, bis jemand sie stellt. */
  final class Fake(private var current: Long = 0L) extends HistoryClock:
    def now(): Long                 = current
    def advance(millis: Long): Unit = current += millis
    def set(millis: Long): Unit     = current = millis

/** Obergrenzen und Zeitfenster der History.
  *
  * @param maxEntries
  *   Anzahl der Undo-Stufen.
  * @param maxRetainedBytes
  *   '''Geschaetztes''' Budget, siehe [[HistoryEntry.estimatedBytes]]. Keine Heapmessung.
  * @param mergeWindowMillis
  *   Wie lange zusammenhaengendes Tippen verschmelzen darf (§14). Danach beginnt eine neue Gruppe,
  *   auch wenn der Caret nahtlos weiterlaeuft -- eine Pause ist eine Absicht.
  */
final case class HistoryLimits(
    maxEntries: Int = 200,
    maxRetainedBytes: Int = 8 * 1024 * 1024,
    mergeWindowMillis: Long = 500L
):
  require(maxEntries >= 1, "Eine History ohne Eintrag ist keine History.")
  require(maxRetainedBytes >= 0, "Das Byte-Budget ist nicht negativ.")
  require(mergeWindowMillis >= 0, "Das Zeitfenster ist nicht negativ.")

object HistoryLimits:
  val default: HistoryLimits = HistoryLimits()

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

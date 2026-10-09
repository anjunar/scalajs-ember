package ember.editor.history

import ember.editor.core.*

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

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

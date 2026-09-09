package ember.editor.core

/** Eine aufkuendbare Registrierung.
  *
  * Absichtlich schmal: der Kern ist headless und soll `jfx.core.state.Disposable` weder nachbauen
  * noch importieren. Der JFX-Adapter aus P09 uebersetzt zwischen beiden -- das ist eine Zeile Code
  * und der Preis dafuer, dass der Kern ohne JFX auskommt (§7).
  *
  * [[dispose]] ist idempotent. Wer eine Registrierung zweimal aufkuendigt, hat kein Problem, und
  * wer sie beim Aufraeumen sicherheitshalber noch einmal aufkuendigt, auch nicht.
  */
trait Subscription:

  def dispose(): Unit

  /** Ob die Registrierung noch wirksam ist. */
  def isActive: Boolean

object Subscription:

  /** Eine bereits aufgekuendigte Registrierung. */
  val cancelled: Subscription = new Subscription:
    def dispose(): Unit   = ()
    def isActive: Boolean = false

  private[core] def apply(cancel: () => Unit): Subscription =
    new Subscription:
      private var active = true

      def isActive: Boolean = active

      def dispose(): Unit =
        if active then
          active = false
          cancel()

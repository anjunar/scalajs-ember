package ember.editor.core

/** Eine aufkuendbare Registrierung.
  *
  * Absichtlich schmal: der Kern ist headless und soll `ui.core.state.Disposable` weder nachbauen
  * noch importieren. Der UI-Adapter aus P09 uebersetzt zwischen beiden -- das ist eine Zeile Code
  * und der Preis dafuer, dass der Kern ohne UI auskommt (§7).
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

  /** Baut eine Registrierung aus ihrer Aufraeumaktion.
    *
    * Oeffentlich, weil auch Module ausserhalb des Kerns Registrierungen anbieten -- die
    * Dokumentansicht meldet ueber `onProjected`, spaeter der SelectionPort und die Toolbar. Sie
    * alle sollen dafuer denselben Vertrag verwenden und keinen eigenen nachbauen.
    */
  def apply(cancel: () => Unit): Subscription =
    new Subscription:
      private var active = true

      def isActive: Boolean = active

      def dispose(): Unit =
        if active then
          active = false
          cancel()

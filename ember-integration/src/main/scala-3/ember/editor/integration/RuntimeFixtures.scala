package ember.editor.integration

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.layout.TextComponent
import jfx.core.render.{Cursor, DomCursor}
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Fixtures fuer die JFX-Runtime-Vertraege, auf denen P09 aufbaut. Nur fuer Tests.
  *
  * ==Warum das hier noch einmal geprueft wird==
  *
  * `spliceText` und `Runtime.move` sind im Nachbar-Repo implementiert '''und''' getestet --
  * `HostEditingSpec` deckt UTF-16-Offsets, Zyklen, Kontextinvarianten und SSR-Duplikate ab. Diese
  * Fixtures verdoppeln das nicht. Sie pruefen zwei Dinge, die dort nicht pruefbar sind:
  *
  *   1. '''DOM-Knotenidentitaet.''' §15.1 verlangt, dass ein Splice denselben Textknoten behaelt
  *      und ein Move dasselbe Element bewegt statt es neu zu bauen. Ob das stimmt, kann nur ein
  *      echter Browser beantworten: es ist ein `===`-Vergleich auf DOM-Objekten. Fuer den Editor
  *      haengt daran alles -- ein neu erzeugter Textknoten nimmt Caret, Selection und eine laufende
  *      IME-Eingabe mit ins Grab.
  *   2. '''Diese Linkerausgabe.''' Der Nachbar testet seinen eigenen Build. Wir binden `jfx-core`
  *      als Quell-Abhaengigkeit ein und linken es mit unseren Einstellungen -- ESModule, ES2021,
  *      `fullLinkJS` mit optimierter Semantik. Ein Vertrag kann dort halten und hier brechen.
  *
  * ==Aufbau==
  *
  * {{{
  * div#runtime-root
  *   section#left
  *     p#movable   -> TextComponent("A😀BC")
  *   section#right
  * }}}
  */
@JSExportTopLevel("runtimeFixtures")
object RuntimeFixtures:

  private final class Box(val tagName: String, id: String) extends AbstractComponent:
    override def compose(cursor: Cursor): Unit = host.setAttribute("id", id)

  private final class Label(initial: String, id: String) extends AbstractComponent:
    val tagName   = "p"
    val text      = new TextComponent(initial)
    var disposals = 0

    override def compose(cursor: Cursor): Unit =
      host.setAttribute("id", id)
      Runtime.mount(text, cursor, Some(this))

    override def dispose(): Unit =
      disposals += 1
      super.dispose()

  private var root: Box      = null
  private var left: Box      = null
  private var right: Box     = null
  private var movable: Label = null

  @JSExport
  def mount(container: dom.Element): Unit =
    root = Runtime.mount(new Box("div", "runtime-root"), DomCursor.root(container))
    left = child(root, new Box("section", "left"))
    right = child(root, new Box("section", "right"))
    movable = child(left, new Label("A😀BC", "movable"))

  @JSExport
  def dispose(): Unit =
    if root != null then Runtime.unmount(root)
    root = null

  private def child[C <: AbstractComponent](owner: AbstractComponent, value: C): C =
    Runtime.mount(value, Runtime.contentCursor(owner), Some(owner))

  // -----------------------------------------------------------------------------------------
  // Text
  // -----------------------------------------------------------------------------------------

  /** UTF-16-Splice auf dem beweglichen Label. */
  @JSExport
  def splice(start: Int, deleteCount: Int, inserted: String): Unit =
    movable.text.spliceText(start, deleteCount, inserted)

  /** Setzt den Text vollstaendig. Fuer den No-op-Vertrag: gleicher Wert, kein Schreibzugriff. */
  @JSExport
  def setText(value: String): Unit = movable.text.setText(value)

  /** Der Text aus Sicht der Komponente. Gegenprobe zum DOM. */
  @JSExport
  def text(): String = movable.text.getText

  // -----------------------------------------------------------------------------------------
  // Move
  // -----------------------------------------------------------------------------------------

  /** Verschiebt das bewegliche Label nach `left` oder `right`. */
  @JSExport
  def move(target: String, index: Int): Unit =
    Runtime.move(movable, section(target), index)

  /** Montiert ein weiteres Label in einen Abschnitt.
    *
    * Der eigentliche Nachweis fuer die logische Reihenfolge: `contentCursor` setzt anhand der
    * Kindliste der Runtime an. Waere sie nach einem Move nicht mit dem DOM synchron, landete dieses
    * Label an der falschen Stelle -- ohne dass irgendetwas eine Fehlermeldung ergaebe.
    */
  @JSExport
  def addLabel(target: String, id: String, value: String): Unit =
    child(section(target), new Label(value, id)): Unit

  /** Wie oft `dispose` auf dem beweglichen Label lief. */
  @JSExport
  def disposals(): Int = if movable == null then -1 else movable.disposals

  private def section(name: String): Box = name match
    case "left"  => left
    case "right" => right
    case other   => throw new IllegalArgumentException(s"Unbekannter Abschnitt: $other")

  // -----------------------------------------------------------------------------------------
  // Abgewiesene Operationen
  // -----------------------------------------------------------------------------------------

  /** Fuehrt eine unzulaessige Operation aus und liefert die Fehlerklasse, oder `""`.
    *
    * §15.1: Zyklen, virtuelle Wurzeln und Text-Wurzeln werden abgewiesen -- und zwar '''vor'''
    * jeder Mutation. Dass danach nichts kaputt ist, prueft der Testtreiber am DOM.
    */
  @JSExport
  def attempt(action: String): String =
    try
      action match
        // Ein Abschnitt in eines seiner eigenen Kinder.
        case "cycle" => Runtime.move(left, movable, 0)
        // Ein Textknoten ist keine physische Elementkomponente.
        case "text" => Runtime.move(movable.text, right, 0)
        // Eine montierte Wurzel hat keinen Parent.
        case "root" => Runtime.move(root, right, 0)
        // Position ausserhalb der Kindliste.
        case "index" => Runtime.move(movable, right, 99)
        case other   => throw new IllegalArgumentException(s"Unbekannter Versuch: $other")
      ""
    catch case error: Throwable => error.getClass.getSimpleName

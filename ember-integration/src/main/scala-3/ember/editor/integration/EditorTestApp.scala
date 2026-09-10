package ember.editor.integration

import ember.editor.core.*
import ember.editor.richtext.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.layout.TextComponent
import jfx.core.render.{Cursor, DomCursor, DomNodes}
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Die Test-App der Browser-Harness. Nur fuer Tests, niemals publiziert.
  *
  * ==Was sie beweisen soll==
  *
  * Dass die Ember-Engine und die JFX-Runtime im '''selben, echt gelinkten''' Bundle
  * zusammenarbeiten -- in einem richtigen Browser, nicht gegen einen Stub. §24 haelt fest, warum
  * das noetig ist: "Viele npm-Core-Tests verwenden einen Stub; jsdom liefert keine belastbare
  * IME-/Selection-Engine. Der neue Browser-Harness muss den tatsaechlich gelinkten Scala-Editor
  * ausfuehren."
  *
  * ==Was sie ausdruecklich nicht ist==
  *
  * '''Keine Projektion.''' Diese App rendert nach jedem Commit stumpf neu: alle Bloecke weg, alle
  * Bloecke wieder hin. Das ist genau das Verfahren, das P09 ersetzt -- dort entsteht die keyed
  * `DocumentView`, die nur die betroffenen Knoten anfasst. Hier waere sie verfrueht und wuerde den
  * Nachweis vermengen, um den es geht.
  *
  * '''Keine Bridge-API.''' Die `@JSExport`-Methoden nehmen Strings, weil ein Testtreiber in
  * JavaScript nichts anderes hat. Die produktive Fassade aus §23 arbeitet mit opaken Handles und
  * validierten DTOs; hier wird davon nichts eingefroren.
  *
  * '''Kein `contenteditable`.''' Der Container faengt Tastendruecke ab und verhindert die native
  * Aktion. Eine echte Editierflaeche mit nativer Eingabe ist P21 bis P23 und braucht Composition,
  * Mutation-Observer und Recovery -- nichts davon existiert schon.
  *
  * ==Der Server-Import==
  *
  * Das Modul darf beim Laden weder `window` noch `document` lesen (§15.2). Deshalb steht hier kein
  * Initialisierungscode auf oberster Ebene; alles Browserabhaengige beginnt in [[mount]].
  */
@JSExportTopLevel("emberFixtures")
object EditorTestApp:

  /** Ein Block als JFX-Komponente: ein `p` mit genau einem Textknoten. */
  private final class Block(initial: String) extends AbstractComponent:
    val tagName = "p"
    val text    = new TextComponent(initial)

    override def compose(cursor: Cursor): Unit =
      host.setAttribute("data-block", "")
      Runtime.mount(text, cursor, Some(this))

  private final class Surface extends AbstractComponent:
    val tagName = "div"

    override def compose(cursor: Cursor): Unit =
      host.setAttribute("id", "surface")
      // Fokussierbar ohne contenteditable: der Browser soll den Container erreichen koennen,
      // aber nichts an ihm veraendern.
      host.setAttribute("tabindex", "0")

  private var surface: Surface                       = null
  private var blocks: Vector[Block]                  = Vector.empty
  private var session: EditorSession                 = null
  private var commits: Subscription                  = Subscription.cancelled
  private var keyListener: dom.KeyboardEvent => Unit = null
  private var keyTarget: dom.Element                 = null
  private var lastError: String                      = ""
  private var renderCount: Int                       = 0

  // -----------------------------------------------------------------------------------------
  // Lebenszyklus
  // -----------------------------------------------------------------------------------------

  /** Baut Sitzung und JFX-Baum in `container` auf. */
  @JSExport
  def mount(container: dom.Element): Unit =
    val generator = NodeIdGenerator.sequential("n")
    val resolved  = ExtensionResolver.resolve(Vector(RichText(generator))) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    val document = RichText.emptyDocument(resolved.schema, generator) match
      case Right(value)     => value
      case Left(violations) =>
        throw new IllegalStateException(violations.map(_.render).mkString("; "))

    session = EditorSession.create(
      document,
      resolved,
      resolved.sessionConfig(errorSink = error => lastError = error.render)
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    session.update(_.setSelection(RichText.caretAtStart(session.document))): Unit

    surface = Runtime.mount(new Surface, DomCursor.root(container))
    render()

    commits = session.onCommit(_ => render())
    attachKeys(container)

  /** Raeumt vollstaendig auf: Listener, Subscription, JFX-Baum, Sitzung. */
  @JSExport
  def dispose(): Unit =
    detachKeys()
    commits.dispose()
    if surface != null then Runtime.unmount(surface)
    if session != null then session.dispose()
    blocks = Vector.empty
    surface = null

  @JSExport
  def isDisposed(): Boolean = session != null && session.isDisposed

  // -----------------------------------------------------------------------------------------
  // Lesen
  // -----------------------------------------------------------------------------------------

  /** Die Absatztexte des '''Modells''', mit `\n` verbunden. */
  @JSExport
  def read(): String =
    val document = session.document
    document
      .childrenOf(document.rootId)
      .map(block =>
        document
          .childrenOf(block)
          .map(id => document.node(id).collect { case text: TextNode => text.text }.getOrElse(""))
          .mkString
      )
      .mkString("\n")

  /** Der Caret als `knoten:offset`, oder `-` ohne kollabierte Auswahl.
    *
    * Modell '''und''' Darstellung getrennt abfragbar zu haben ist der Kern des Nachweises: wenn
    * beide dasselbe sagen, arbeiten die zwei Runtimes tatsaechlich zusammen.
    */
  @JSExport
  def caret(): String =
    session.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => s"${node.value}:$offset" }
      .getOrElse("-")

  @JSExport
  def revision(): Int = session.state.revision.value.toInt

  /** Anzahl der montierten JFX-Bloecke. Fuer den Dispose-Nachweis. */
  @JSExport
  def mountedBlocks(): Int = blocks.length

  /** Der zuletzt in den Error-Sink gemeldete Fehler. Leer, wenn keiner auftrat. */
  @JSExport
  def error(): String = lastError

  /** Wie oft neu gerendert wurde. Macht sichtbar, dass die Commits tatsaechlich ankommen. */
  @JSExport
  def renders(): Int = renderCount

  // -----------------------------------------------------------------------------------------
  // Aendern
  // -----------------------------------------------------------------------------------------

  /** Fuehrt einen Editing-Command aus. Namen sind Teststrings, keine Dispatch-API (§12). */
  @JSExport
  def perform(command: String, payload: String): Boolean =
    val outcome = command match
      case "insert"    => session.dispatch(RichText.InsertText, payload)
      case "enter"     => session.dispatch(RichText.InsertParagraph)
      case "backspace" => session.dispatch(RichText.DeleteBackward)
      case "delete"    => session.dispatch(RichText.DeleteForward)
      case other       => throw new IllegalArgumentException(s"Unbekannter Testbefehl: $other")

    outcome match
      case Right(result) => result.wasHandled
      case Left(failure) =>
        lastError = failure.render
        false

  // -----------------------------------------------------------------------------------------
  // Native Ereignisse
  // -----------------------------------------------------------------------------------------

  /** Verbindet echte Tastendruecke mit den Commands.
    *
    * Ueber `DomNodes` -- die dafuer vorgesehene oeffentliche DOM-Anbindung des Kerns
    * (JFX_CORE_INTEGRATION.md). Ein Playwright-`keyboard.press` laeuft damit durch dieselbe Kette
    * wie eine echte Eingabe: DOM-Ereignis, Command, Transaktion, Commit, Projektion.
    *
    * `preventDefault` fuer alles Behandelte, weil es hier keine native Editierflaeche gibt, die die
    * Aktion sonst ausfuehren koennte. Die echte Abwaegung -- wann eine native Aktion verhindert
    * wird und wann nicht -- gehoert zu P22.
    */
  private def attachKeys(container: dom.Element): Unit =
    keyTarget = container
    keyListener = event =>
      val handled = event.key match
        case "Enter"     => perform("enter", "")
        case "Backspace" => perform("backspace", "")
        case "Delete"    => perform("delete", "")
        case key if key.length == 1 && !event.ctrlKey && !event.metaKey && !event.altKey =>
          perform("insert", key)
        case _ => false
      if handled then event.preventDefault()

    container.addEventListener("keydown", keyListener)

  private def detachKeys(): Unit =
    if keyTarget != null && keyListener != null then
      keyTarget.removeEventListener("keydown", keyListener)
    keyTarget = null
    keyListener = null

  // -----------------------------------------------------------------------------------------
  // Darstellung
  // -----------------------------------------------------------------------------------------

  /** Vollstaendiger Neuaufbau nach jedem Commit.
    *
    * Absichtlich die naive Variante. §15.1 verlangt fuer den echten Editor das Gegenteil --
    * unveraenderte Knoten werden nicht erneut komponiert, und ein `setAll` auf jedem Snapshot ist
    * ausgeschlossen. Genau das baut P09. Hier geht es nur darum, dass ueberhaupt etwas ankommt, und
    * dafuer ist die einfachste denkbare Projektion die ehrlichste.
    */
  private def render(): Unit =
    blocks.foreach(Runtime.unmount)
    val document = session.document
    blocks = document
      .childrenOf(document.rootId)
      .map { blockId =>
        val content = document
          .childrenOf(blockId)
          .map(id => document.node(id).collect { case text: TextNode => text.text }.getOrElse(""))
          .mkString
        Runtime.mount(new Block(content), Runtime.contentCursor(surface), Some(surface))
      }
    renderCount += 1

  /** Der reine DOM-Text der Flaeche. Gegenprobe zu [[read]]. */
  @JSExport
  def rendered(): String =
    if surface == null then ""
    else
      DomNodes.option(surface.host) match
        case Some(node: dom.Element) =>
          val children = node.querySelectorAll("[data-block]")
          (0 until children.length)
            .map(index => children(index).textContent)
            .mkString("\n")
        case _ => ""

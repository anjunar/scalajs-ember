package ember.editor.integration

import ember.editor.core.*
import ember.editor.html.RenderProfile
import ember.editor.ui.DocumentView
import ember.editor.richtext.*
import ember.editor.standard.ParagraphSupport
import ui.core.render.DomCursor
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Fixtures fuer die keyed [[DocumentView]] aus P09. Nur fuer Tests, niemals publiziert.
  *
  * ==Was hier geprueft wird und was nicht==
  *
  * `ProjectionSpec` deckt die Projektion headless gegen einen `SsrCursor` ab -- Index, Reihenfolge,
  * Renderprofile, Instanzerhalt auf Komponentenebene. Das ist die Mehrheit der Aussagen, und sie
  * braucht keinen Browser.
  *
  * Zwei Zusicherungen aus §15.1 lassen sich dort aber grundsaetzlich nicht belegen:
  *
  *   1. '''DOM-Identitaet.''' Dass ein Textedit denselben `Text`-Knoten behaelt und ein Move
  *      dasselbe `Element` bewegt statt es neu zu bauen, ist ein `===`-Vergleich auf DOM-Objekten.
  *      Fuer den Editor haengt daran alles -- ein neu erzeugter Textknoten nimmt Caret, Selection
  *      und eine laufende IME-Eingabe mit ins Grab.
  *   1. '''Der Umfang der Schreibzugriffe.''' "Unveraenderte Nodes werden nicht erneut komponiert"
  *      ist eine Aussage ueber das, was '''nicht''' passiert. Nur ein `MutationObserver` sieht das;
  *      ein gleicher Ausgabestring beweist es nicht.
  *
  * Dazu kommt die Abnahme "Gleiches initiales Rendering in SSR und Browser" -- vergleichbar erst,
  * wenn beide Seiten wirklich existieren.
  *
  * ==Das Dokument==
  *
  * {{{
  * root      article
  *   p0      p       t0  "Hallo"
  *   p1      p       t1  "Welt"
  * }}}
  *
  * Feste IDs, damit der Testtreiber sie benennen kann. Die Editieransicht traegt sie als
  * `data-ember-node`, ueber das der Test seine Elemente findet.
  */
@JSExportTopLevel("projectionFixtures")
object ProjectionFixtures:

  private val rootId = NodeId("root")

  private var session: EditorSession = null
  private var view: DocumentView     = null
  private var lastProjected: Long    = -1L
  private var lastError: String      = ""

  // -----------------------------------------------------------------------------------------
  // Lebenszyklus
  // -----------------------------------------------------------------------------------------

  @JSExport
  def mount(container: dom.Element): Unit =
    val generator = NodeIdGenerator.sequential("g")
    val resolved  = ExtensionResolver.resolve(Vector(RichText(generator))) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    val nodes = Vector(
      RootNode(rootId, Vector(NodeId("p0"), NodeId("p1"))),
      ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
      TextNode(NodeId("t0"), "Hallo"),
      ParagraphNode(NodeId("p1"), Vector(NodeId("t1"))),
      TextNode(NodeId("t1"), "Welt")
    )

    session = EditorSession.create(
      Document.unsafe(resolved.schema, rootId, nodes),
      resolved,
      resolved.sessionConfig(errorSink = error => lastError = error.render)
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    view = DocumentView.mount(
      session,
      DomCursor.root(container),
      ParagraphSupport.views,
      RenderProfile.Editor
    )
    view.onProjected(revision => lastProjected = revision.value): Unit

  @JSExport
  def dispose(): Unit =
    if view != null then view.dispose()
    if session != null then session.dispose()
    view = null
    session = null

  // -----------------------------------------------------------------------------------------
  // Lesen
  // -----------------------------------------------------------------------------------------

  /** Die Absatztexte des '''Modells''', mit `\n` verbunden. Gegenprobe zum DOM. */
  @JSExport
  def read(): String =
    val document = session.document
    document
      .childrenOf(document.rootId)
      .map(block =>
        document
          .childrenOf(block)
          .flatMap(id => document.node(id).collect { case text: TextNode => text.text })
          .mkString
      )
      .mkString("\n")

  @JSExport
  def revision(): Int = session.state.revision.value.toInt

  /** Die zuletzt '''projizierte''' Revision (§5). Vor der ersten Projektion `-1`. */
  @JSExport
  def projected(): Int = view.projectedRevision.value.toInt

  /** Was der `onProjected`-Zuhoerer gehoert hat. `-1`, solange nichts kam. */
  @JSExport
  def notified(): Int = lastProjected.toInt

  @JSExport
  def size(): Int = view.size

  @JSExport
  def error(): String = lastError

  /** Dasselbe Dokument ueber den SSR-Weg -- fuer die Abnahme "gleiches initiales Rendering". */
  @JSExport
  def ssrHtml(): String =
    DocumentView.renderToHtml(session.document, ParagraphSupport.views, RenderProfile.Editor)

  // -----------------------------------------------------------------------------------------
  // Aendern
  // -----------------------------------------------------------------------------------------

  @JSExport
  def splice(nodeId: String, start: Int, deleteCount: Int, inserted: String): Boolean =
    edit(_.spliceText(NodeId(nodeId), start, deleteCount, inserted))

  /** Setzt den Text ohne Splice -- fuer die Gegenprobe auf den No-op-Vertrag. */
  @JSExport
  def setText(nodeId: String, text: String): Boolean =
    val id = NodeId(nodeId)
    edit(tx =>
      session.document.node(id).collect { case run: TextNode => run }.foreach { run =>
        tx.replace(id, run.copy(text = text)): Unit
      }
    )

  @JSExport
  def move(nodeId: String, parentId: String, index: Int): Boolean =
    edit(_.move(NodeId(nodeId), NodeId(parentId), index))

  @JSExport
  def remove(nodeId: String): Boolean = edit(_.remove(NodeId(nodeId)))

  @JSExport
  def insertText(parentId: String, index: Int, nodeId: String, text: String): Boolean =
    edit(_.insert(NodeId(parentId), index, TextNode(NodeId(nodeId), text)))

  /** Inserts a run that carries a mark, so it will not merge with its neighbour.
    *
    * Since P12 adjacent runs with equal marks grow back together (§8.2). A test about ordering or
    * about DOM identity needs two runs that stay two -- and a difference in marks is the honest way
    * to get them, not a special case in the normalisation.
    */
  @JSExport
  def insertMarked(parentId: String, index: Int, nodeId: String, text: String): Boolean =
    edit(
      _.insert(
        NodeId(parentId),
        index,
        TextNode(NodeId(nodeId), text, MarkSet.of(StandardMarks.Strong))
      )
    )

  /** Gives an existing run a mark. Same purpose as [[insertMarked]]. */
  @JSExport
  def markRun(nodeId: String): Boolean =
    val id = NodeId(nodeId)
    edit(tx =>
      session.document.node(id).collect { case run: TextNode => run }.foreach { run =>
        tx.replace(id, run.copy(marks = MarkSet.of(StandardMarks.Strong))): Unit
      }
    )

  private def edit(body: Transaction => Unit): Boolean =
    session.update(body) match
      case Right(_)    => true
      case Left(error) =>
        lastError = error.render
        false

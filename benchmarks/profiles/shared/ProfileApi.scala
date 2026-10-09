package ember.editor.profiles

import ember.editor.core.*
import ember.editor.ui.*
import ember.editor.browser.*
import ember.editor.browsersupport.*
import org.scalajs.dom
import ui.core.render.DomCursor
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

/** Identical harness operations for three independently linked application entry points. */
class ProfileApi(
    name: String,
    resolved: ResolvedExtensions,
    views: ViewSupport,
    decode: String => Document,
    encode: Document => String,
    bindings: InputBindings,
    keys: KeyboardBindings
):
  private var session                             = Option.empty[EditorSession]
  private var view                                = Option.empty[DocumentView]
  private var selection                           = Option.empty[SelectionPort]
  private var input                               = Option.empty[BrowserInputController]
  @JSExport def registrations(): js.Array[String] = js.Array(resolved.order.map(_.value)*)
  @JSExport def profileName(): String             = name
  @JSExport def mount(host: dom.Element, source: String): Unit =
    dispose()
    val editor =
      EditorSession.create(decode(source), resolved, resolved.sessionConfig()).toOption.get
    session = Some(editor)
    view = Some(DocumentView.mount(editor, DomCursor.root(host), views))
    selection = Some(SelectionPort.attachTo(editor, view.get, host))
    input = Some(BrowserInputController.attachTo(editor, view.get, selection.get, bindings, keys))
  @JSExport def value(): String                = encode(session.get.document)
  @JSExport def render(source: String): String = DocumentView.renderToHtml(decode(source), views)
  @JSExport def dispose(): Unit                =
    input.foreach(_.dispose()); input = None
    selection.foreach(_.dispose()); selection = None
    view.foreach(_.dispose()); view = None
    session.foreach(_.dispose()); session = None

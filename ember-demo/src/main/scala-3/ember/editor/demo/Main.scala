package ember.editor.demo

import ui.core.component.Runtime
import ui.core.render.{DomCursor, HydratingCursor, SsrCursor}
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel

/** Demo entry points.
  *
  * No initialization at module scope: §15.2 requires that loading the bundle reads neither `window`
  * nor `document`. All browser-dependent work starts in [[boot]].
  *
  * [[renderForSsr]] runs in Node during the Pages build and produces the same component tree that
  * [[boot]] claims in the browser. Local development still falls back to a fresh DOM mount when
  * `#root` is empty.
  */
object Main:

  @JSExportTopLevel("renderForSsr")
  def renderForSsr(): String =
    val cursor = new SsrCursor()
    val app    = Runtime.mount(new DemoApp, cursor)
    try cursor.collectHtml()
    finally Runtime.unmount(app)

  @JSExportTopLevel("boot")
  def boot(): Unit =
    val root = dom.document.getElementById("root")
    if root == null then
      throw new IllegalStateException("Die Seite hat kein Element mit der id `root`.")

    val app = new DemoApp
    if root.childElementCount > 0 then
      val cursor = HydratingCursor.root(root)
      Runtime.mount(app, cursor): Unit
      cursor.completeHydration()
      root.setAttribute("data-rendering", "hydrated")
    else
      Runtime.mount(app, DomCursor.root(root)): Unit
      root.setAttribute("data-rendering", "client")

package ember.editor.demo

import ui.core.component.Runtime
import ui.core.render.DomCursor
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel

/** Einstiegspunkt der Demo.
  *
  * Kein Initialisierungscode auf oberster Ebene: §15.2 verlangt, dass ein Modul beim Laden weder
  * `window` noch `document` liest. Alles Browserabhaengige beginnt in [[boot]].
  *
  * Auch keine Hydration -- die ist P20. Die Seite rendert clientseitig in `#root`, und der Server
  * liefert nur die leere Huelle.
  */
object Main:

  @JSExportTopLevel("boot")
  def boot(): Unit =
    val root = dom.document.getElementById("root")
    if root == null then
      throw new IllegalStateException("Die Seite hat kein Element mit der id `root`.")
    Runtime.mount(new DemoApp, DomCursor.root(root)): Unit

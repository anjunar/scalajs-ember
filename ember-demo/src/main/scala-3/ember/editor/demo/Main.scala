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
  * Diese Showcase rendert bewusst clientseitig in `#root`. SSR und Hydration werden weiterhin im
  * separaten Integrationsmodul geprüft.
  */
object Main:

  @JSExportTopLevel("boot")
  def boot(): Unit =
    val root = dom.document.getElementById("root")
    if root == null then
      throw new IllegalStateException("Die Seite hat kein Element mit der id `root`.")
    Runtime.mount(new DemoApp, DomCursor.root(root)): Unit

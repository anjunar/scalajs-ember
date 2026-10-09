package ember.editor.demo

import org.scalajs.dom
import scala.scalajs.js
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.{classes, classIf}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.Anchor.*
import ui.core.layout.Button.button
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.Property
import ui.viewport.Viewport.viewport

/** Showcase shell. Example sessions survive navigation; the browser adapters follow the active
  * page.
  */
final class DemoApp extends AbstractComponent:
  val tagName         = "div"
  private val editors = DemoExample.all.map(example => example.id -> new DemoSession(example)).toMap
  private val active  = Property("article")
  private val dark    = Property(false)
  private var browserActivated = false

  override def compose(cursor: Cursor): Unit =
    addClass("demo-app")
    DslLayer.render(this, cursor) {
      viewport {
        div {
          classes = Seq("demo-shell")
          div {
            classes = Seq("demo-sidebar")
            div {
              classes = Seq("demo-brand")
              div { classes = Seq("demo-brand__symbol"); text("e") {} }
              div {
                div { classes = Seq("demo-brand__name"); text("ember") {} }
                div { classes = Seq("demo-brand__caption"); text("EDITOR SHOWCASE") {} }
              }
            }
            div { classes = Seq("nav-caption"); text("ENTDECKEN") {} }
            div {
              classes = Seq("demo-navigation")
              DemoExample.all.zipWithIndex.foreach { (example, index) =>
                val entry = anchor() {
                  href = s"#${example.id}"
                  classes = Seq("demo-nav-link")
                  classIf("is-active", active.map(_ == example.id))
                  div { classes = Seq("demo-nav-number"); text(f"${index + 1}%02d") {} }
                  text(example.label) {}
                }
                def mark(): Unit = if active.get == example.id then
                  entry.setAttribute("aria-current", "page")
                else entry.removeAttribute("aria-current")
                mark()
                entry.addDisposable(active.observe(_ => mark()))
              }
            }
            div {
              classes = Seq("demo-sidebar-note")
              div { classes = Seq("nav-caption"); text("DEIN DOKUMENT. DEIN TEMPO.") {} }
              text(
                "Alle Beispiele sind editierbar. Wechsle zwischen ihnen, ohne deine Änderungen zu verlieren."
              ) {}
              div {
                classes = Seq("demo-session-note"); text("Nur in dieser Sitzung · kein Upload") {}
              }
            }
          }
          div {
            classes = Seq("demo-main")
            div {
              classes = Seq("demo-topbar")
              div { text("Spielraum für Inhalte") {}; classes = Seq("demo-breadcrumb") }
              button("Hell / Dunkel") {
                classes = Seq("subtle-button")
                onClick(_ => dark.set(!dark.get))
              }
            }
            DemoExample.all.foreach { example =>
              when(active.map(_ == example.id)) {
                DslLayer.child(new DemoPage(editors(example.id))) {}
              }
            }
            div {
              classes = Seq("demo-footer")
              text("Mit Scala.js UI gebaut. Angetrieben von Ember.") {}
              anchor() { href = "https://github.com/anjunar/scalajs-ember"; text("Quellcode ↗") {} }
            }
          }
        }
      }
    }
    if cursor.isBrowser then cursor.afterHydration(() => activateBrowser())

  private def activateBrowser(): Unit =
    if !browserActivated then
      browserActivated = true
      val selected = dom.window.location.hash.stripPrefix("#")
      if editors.contains(selected) then active.set(selected)
      dark.set(dom.window.matchMedia("(prefers-color-scheme: dark)").matches)
      def theme(): Unit =
        dom.document.documentElement.setAttribute(
          "data-theme",
          if dark.get then "dark" else "light"
        )
      theme()
      addDisposable(dark.observe(_ => theme()))
      val navigate: js.Function1[dom.Event, Unit] = _ =>
        val id = dom.window.location.hash.stripPrefix("#")
        if editors.contains(id) then active.set(id)
      dom.window.addEventListener("hashchange", navigate)
      addDisposable(
        ui.core.state.Disposable(dom.window.removeEventListener("hashchange", navigate))
      )

  override def dispose(): Unit =
    // Unmount the view/controller trees before disposing their long-lived sessions.
    super.dispose()
    editors.values.foreach(_.dispose())

package ember.editor.demo

import ember.editor.browser.*
import ember.editor.browsersupport.{
  EditorBindings,
  HistoryBindings,
  TableBindings,
  TableSelectionView
}
import ember.editor.clipboard.{ClipboardCodec, BrowserClipboardController}
import ember.editor.codehighlighting.CodeDecorations
import ember.editor.core.*
import ember.editor.link.LinkUrlPolicy
import ember.editor.standard.{StandardHtmlImport, TableSupport}
import ember.editor.toolbar.*
import ember.editor.ui.DocumentView
import org.scalajs.dom
import scala.scalajs.js
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.ClassDsl.{classes, classIf}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.Button.button
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomNodes}
import ui.core.state.Property

final class DemoPage(editor: DemoSession) extends AbstractComponent:
  val tagName                                       = "main"
  private val inspect                               = Property(false)
  private val panel                                 = Property("Markdown")
  private val snapshot                              = Property("")
  private val stats                                 = Property("")
  private val notice                                = Property("")
  private val readOnly                              = Property(false)
  private var view: DocumentView                    = null
  private var selection: SelectionPort              = null
  private var input: BrowserInputController         = null
  private var clipboard: BrowserClipboardController = null
  private var decorations: CodeDecorations          = null
  private var cellSelection: TableSelectionView     = null
  private var ribbon: EditorToolbar                 = null
  private var menu: MenuToolbar                     = null
  private var floating: FloatingToolbar             = null
  private var dialogs: DemoDialogs                  = null

  private def available =
    input != null && input.mode == EditorMode.Editable && input.state == ControllerState.Ready

  override def compose(cursor: Cursor): Unit =
    addClass("demo-page")
    DslLayer.render(this, cursor) {
      div {
        classes = Seq("demo-intro")
        div { classes = Seq("eyebrow"); text(editor.example.eyebrow) {} }
        val title = new AbstractComponent:
          val tagName                                = "h1"
          override def compose(cursor: Cursor): Unit = Runtime.mount(
            new ui.core.layout.TextComponent(editor.example.title),
            cursor,
            Some(this)
          ): Unit
        DslLayer.child(title) {}
        div { classes = Seq("demo-description"); text(editor.example.description) {} }
      }
      div {
        classes = Seq("demo-workspace")
        classIf("has-inspector", inspect)
        div {
          classes = Seq("editor-card")
          div {
            classes = Seq("editor-card__heading")
            div { classes = Seq("editor-document-name"); text(editor.example.label) {} }
            div {
              classes = Seq("editor-view-options")
              val mode = button("Lesemodus") { onClick(_ => readOnly.set(!readOnly.get)) }
              def modeLabel(): Unit =
                mode.setAttribute("aria-pressed", readOnly.get.toString)
                mode.label(if readOnly.get then "Bearbeiten" else "Lesemodus")
              modeLabel()
              mode.addDisposable(readOnly.observe(_ => modeLabel()))
              val toggle = button("Quellansicht") { onClick(_ => inspect.set(!inspect.get)) }
              toggle.setAttribute("aria-expanded", "false")
              toggle.addDisposable(
                inspect.observe(value => toggle.setAttribute("aria-expanded", value.toString))
              )
            }
          }
          val menuHost    = div { classes = Seq("editor-menu-host") }
          val toolbarHost = div { classes = Seq("editor-ribbon-host") }
          div {
            classes = Seq("editor-paper")
            val host = div { classes = Seq("editor-surface") }
            host.setAttribute("aria-label", "Dokument bearbeiten")
            view = DocumentView.mount(
              editor.session,
              Runtime.contentCursor(host),
              TableSupport.views,
              parent = Some(host)
            )
            if cursor.isBrowser then
              val element = DomNodes.raw(host.host).asInstanceOf[dom.HTMLElement]
              selection = SelectionPort.detached(editor.session, view, element)
              input = BrowserInputController.detached(
                editor.session,
                view,
                selection,
                EditorBindings.everything,
                // Table Tab first: inside a cell it moves, and outside it passes to code and lists.
                TableBindings.tabNavigation ++ EditorBindings.tabIndentation ++
                  EditorBindings.everythingKeyboard ++ TableBindings.keyboard,
                TabPolicy.IndentsUntilEscape,
                EditorMode.Editable,
                Some(TableSupport.everything),
                BusyPolicy.Defer
              )
              editor.compositions.bind(input)
              val historyBinding = HistoryBindings.groupCompositions(input, editor.history)
              addDisposable(ui.core.state.Disposable(historyBinding.dispose()))
              val codec = new ClipboardCodec(
                "ember-demo/1",
                editor.session.document.schema,
                editor.codecs,
                TableSupport.everything,
                StandardHtmlImport
                  .everything(LinkUrlPolicy.default, editor.media)
                  .withRules(TableSupport.htmlImport*)
              )
              clipboard = new BrowserClipboardController(
                editor.session,
                selection,
                input,
                codec,
                report = result => result.left.foreach(error => notice.set(error.message))
              )
            dialogs = new DemoDialogs(editor, selection, () => available, DemoPage.this)
            val groups = DemoRibbon.groups(editor, () => available, dialogs, selection)
            ribbon = new EditorToolbar(editor.session, selection, Vector.empty, groups = groups)
            Runtime.mount(ribbon, Runtime.contentCursor(toolbarHost), Some(toolbarHost))
            menu = new MenuToolbar(editor.session, selection, groups)
            Runtime.mount(menu, Runtime.contentCursor(menuHost), Some(menuHost))
            if cursor.isBrowser then
              // Selection formatting: bold/italic/inline-code plus the link dialog -- the small,
              // fixed set a selection popup conventionally offers, not the whole ribbon.
              val floatingActions =
                groups.find(_.label == "Schrift").map(_.actions).getOrElse(Vector.empty) ++
                  groups
                    .find(_.label == "Einfügen")
                    .flatMap(_.actions.find(_.id == "link"))
                    .toVector
              floating =
                new FloatingToolbar(editor.session, selection, floatingActions, dom.document.body)
            addDisposable(readOnly.observe { value =>
              dialogs.close()
              if input != null then
                input.setMode(if value then EditorMode.ReadOnly else EditorMode.Editable)
              ribbon.refresh()
              menu.refresh()
            })
            if input != null then
              val composition = input.onComposition { _ =>
                ribbon.refresh()
                menu.refresh()
              }
              addDisposable(ui.core.state.Disposable(composition.dispose()))
              val outcomes = input.onOutcome {
                case InputOutcome.Refused(_, reason) => notice.set(reason.toString)
                case InputOutcome.Unimported(reason) => notice.set(reason.toString)
                case _                               => ()
              }
              addDisposable(ui.core.state.Disposable(outcomes.dispose()))
              cursor.afterHydration { () =>
                selection.attach()
                input.attach()
                // Colours are view state and write no DOM, but they paint on the text nodes the
                // hydration claimed -- so not before it has claimed them.
                decorations = CodeDecorations.attach(editor.session, view)
                cellSelection = TableSelectionView.attach(editor.session, view, selection)
                ribbon.refresh()
                menu.refresh()
              }
          }
          div {
            classes = Seq("editor-status")
            div { text(stats) {} }
            div { text("Änderungen nur in dieser Sitzung") {} }
          }
        }
        when(inspect) {
          div {
            classes = Seq("inspector-card")
            div { classes = Seq("inspector-heading"); text("Ein Dokument. Mehrere Ansichten.") {} }
            div {
              classes = Seq("inspector-tabs")
              Vector("Markdown", "JSON", "HTML", "Baum").foreach { label =>
                val tab = button(label) {
                  classIf("is-active", panel.map(_ == label))
                  onClick(_ => panel.set(label))
                }
                tab.setAttribute("aria-pressed", (panel.get == label).toString)
                tab.addDisposable(
                  panel.observe(value =>
                    tab.setAttribute("aria-pressed", (value == label).toString)
                  )
                )
              }
            }
            div {
              classes = Seq("inspector-description");
              text("Live aus dem Dokumentmodell. Die Quellansicht ist schreibgeschützt.") {}
            }
            val code = div { classes = Seq("inspector-code"); text(snapshot) {} }
            code.setAttribute("tabindex", "0")
            code.setAttribute("aria-label", "Dokumentquelle")
            div {
              classes = Seq("inspector-actions")
              button("JSON herunterladen") {
                onClick(_ => download(editor.json, "json", "application/json"))
              }
              button("Markdown herunterladen") {
                onClick(_ =>
                  editor.strictMarkdown.fold(
                    error => notice.set(error.message),
                    value => download(value, "md", "text/markdown")
                  )
                )
              }
            }
          }
        }
      }
      val message = div { classes = Seq("demo-notice"); text(notice) {} }
      message.setAttribute("role", "status")
      div {
        classes = Seq("demo-tips")
        div { text("Einfach ausprobieren") {}; classes = Seq("demo-tips__title") }
        div {
          text(
            "Text markieren → formatieren. Strg+Z → rückgängig. Link und Bild → im Fenster bearbeiten."
          ) {}
        }
        button("Beispiel zurücksetzen") {
          classes = Seq("subtle-button")
          onClick(_ =>
            dialogs.confirmReset(() =>
              editor
                .reset()
                .fold(
                  error => notice.set(error.message),
                  _ => notice.set("Beispiel zurückgesetzt.")
                )
            )
          )
        }
      }
    }
    def refresh(): Unit =
      stats.set(s"${editor.wordCount} Wörter · ${editor.session.document.size} Knoten")
      if inspect.get then
        snapshot.set(panel.get match
          case "JSON" => editor.json
          case "HTML" => editor.html
          case "Baum" => editor.outline
          case _      => editor.markdown)
    refresh()
    addDisposable(panel.observe(_ => refresh()))
    addDisposable(inspect.observe(_ => refresh()))
    val commits = editor.session.onCommit(_ => refresh())
    addDisposable(ui.core.state.Disposable(commits.dispose()))

  private def download(value: String, extension: String, mime: String): Unit =
    val url = dom.URL.createObjectURL(
      new dom.Blob(
        js.Array(value),
        js.Dynamic.literal(`type` = mime).asInstanceOf[dom.BlobPropertyBag]
      )
    )
    val anchor = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
    anchor.href = url
    anchor.download = s"ember-${editor.example.id}.$extension"
    dom.document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    scala.scalajs.js.timers.setTimeout(1000)(dom.URL.revokeObjectURL(url))

  override def dispose(): Unit =
    if floating != null then floating.dispose()
    if dialogs != null then dialogs.dispose()
    if clipboard != null then clipboard.dispose()
    if decorations != null then decorations.dispose()
    if cellSelection != null then cellSelection.dispose()
    if input != null then input.dispose()
    editor.compositions.release()
    if selection != null then selection.dispose()
    if view != null then view.dispose()
    super.dispose()

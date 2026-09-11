package ember.editor.demo

import ember.editor.core.*
import ember.editor.jfx.{DocumentView, EditorProperties}
import ember.editor.richtext.{BreakKind, HeadingLevel, StandardMarks}
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.AttributeDsl.setAttribute
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.Condition.when
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.{Disposable, Property, ReadOnlyProperty}
import org.scalajs.dom

/** The demo page.
  *
  * ==What it shows, and what it does not==
  *
  * It shows the state after P16: an immutable document model, atomic transactions, commands,
  * the keyed projection from P09, the versioned JSON from P10, and the block and inline types
  * P12 to P16 added -- all on the same document, side by side.
  *
  * It shows '''no''' editing surface in the full sense. There is no `contenteditable`, no DOM
  * selection and no native input; those are P20 to P23. The surface catches key presses and
  * translates them into commands -- the same chain a real input will take later, only without
  * the native part in front of it. The caret is a model value and is shown as one.
  *
  * ==Two runtimes, one tree==
  *
  * The page itself is an ordinary JFX component tree. The editing surface inside it is a
  * [[DocumentView]] -- as a child of the surface component, not as a second root: that way an
  * `Runtime.unmount` of the page clears the view away too.
  */
final class DemoApp extends AbstractComponent:

  val tagName = "div"

  private val editor = new DemoSession

  /** What stands in the status line. Kept apart from the session, because it is display. */
  private val status = Property("")

  private var view: DocumentView = null
  private var marked: Option[NodeId] = None
  private val bindings = collection.mutable.ArrayBuffer.empty[Disposable]

  override def compose(cursor: Cursor): Unit =
    setClasses(Seq("ember-demo"))

    val document = bind(EditorProperties.document(editor.session))

    DslLayer.render(this, cursor) {
      header()

      div {
        classes = Seq("ember-demo__columns")
        surface()
        Inspector.render(document, editor)
      }

      footer()
    }

  override def dispose(): Unit =
    bindings.foreach(_.dispose())
    bindings.clear()
    if view != null then view.dispose()
    editor.session.dispose()
    super.dispose()

  private def bind[A](entry: (ReadOnlyProperty[A], Disposable)): ReadOnlyProperty[A] =
    bindings += entry._2
    entry._1

  // -----------------------------------------------------------------------------------------
  // Header and footer
  // -----------------------------------------------------------------------------------------

  private def header()(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__header")
      div { classes = Seq("ember-demo__wordmark"); text("Ember") {} }
      div {
        classes = Seq("ember-demo__tagline")
        text("Ein modularer HTML-WYSIWYG-Editor fuer Scala.js") {}
      }
    }

  private def footer()(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__footer")
      text(
        "Stand P01-P16. Keine native Eingabe, keine DOM-Selection, kein contenteditable -- " +
          "das sind P20 bis P23."
      ) {}
    }

  // -----------------------------------------------------------------------------------------
  // The surface
  // -----------------------------------------------------------------------------------------

  private def surface()(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__panel", "ember-demo__panel--surface")

      panelTitle("Editierflaeche", "Anklicken und tippen")

      val host = div {
        classes = Seq("ember-demo__surface")
        // Focusable without contenteditable: the browser should be able to reach the surface,
        // but change nothing on it. What changes it is the projection.
        setAttribute("tabindex", "0")
        setAttribute("role", "textbox")
        setAttribute("aria-label", "Ember-Demodokument")
      }

      view = DocumentView.mount(
        editor.session,
        Runtime.contentCursor(host),
        editor.views,
        parent = Some(host)
      )

      keys(host)
      commandBar()
      statusLine()

      // Only now, so that the surface already exists.
      view.onProjected(_ => refreshStatus()): Unit
      refreshStatus()
    }

  /** Connects real key presses to the commands.
    *
    * `preventDefault` for everything handled, because there is no native editing surface here
    * that could carry the action out instead. The real judgement -- when a native action is
    * prevented and when it is not -- belongs to P22.
    */
  private def keys(host: AbstractComponent): Unit =
    host.onHandler("keydown") { event =>
      val native = event.raw.asInstanceOf[dom.KeyboardEvent]
      val handled = native.key match
        case "z" if native.ctrlKey || native.metaKey =>
          editor.perform(if native.shiftKey then DemoCommand.Redo else DemoCommand.Undo)
        case "y" if native.ctrlKey => editor.perform(DemoCommand.Redo)
        case "Tab" =>
          // Im Codeblock ruecken Tab und Shift+Tab die Zeile ein, sonst das Listenelement.
          // Beide Commands geben `Pass`, wenn sie nicht zustaendig sind (§12) -- die Demo
          // probiert deshalb erst den einen, dann den anderen.
          val code = editor.perform(
            if native.shiftKey then DemoCommand.OutdentCode else DemoCommand.IndentCode
          )
          if code then true
          else editor.perform(if native.shiftKey then DemoCommand.Outdent else DemoCommand.Indent)
        case "Enter"     => editor.perform(DemoCommand.Paragraph)
        case "Backspace" => editor.perform(DemoCommand.Backspace)
        case "Delete"    => editor.perform(DemoCommand.Delete)
        case key if key.length == 1 && !native.ctrlKey && !native.metaKey && !native.altKey =>
          editor.perform(DemoCommand.Insert(key))
        case _ => false
      if handled then native.preventDefault()
    }

  private def commandBar()(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__actions")

      action("Fett", DemoCommand.Mark(StandardMarks.Strong))
      action("Kursiv", DemoCommand.Mark(StandardMarks.Emphasis))
      action("Code", DemoCommand.Mark(StandardMarks.InlineCode))
      action("H2", DemoCommand.Heading(Some(HeadingLevel.H2)))
      action("Absatz", DemoCommand.Heading(None))
      action("Zitat", DemoCommand.Quote)
      action("Zitat aufheben", DemoCommand.Unquote)
      action("Umbruch", DemoCommand.HardBreak)
      action("Trenner", DemoCommand.Rule)
      action("Liste", DemoCommand.Bullets)
      action("Nummern", DemoCommand.Numbers)
      action("Einruecken", DemoCommand.Indent)
      action("Ausruecken", DemoCommand.Outdent)
      action("Link", DemoCommand.Link("https://github.com/anjunar/scalajs-ember"))
      action("Link weg", DemoCommand.Unlink)
      action("Codeblock", DemoCommand.Code)
      // Eine echte Datei unter einem relativen Pfad -- die Quelle, die `MediaUrlPolicy.default`
      // zulaesst. Einen Dateidialog gibt es hier nicht: Uploads sind laut §20 ein
      // Anwendungsservice, und der Command nimmt eine fertige Adresse.
      action("Bild", DemoCommand.Image("/ember.svg", "Das Ember-Logo"))
      action("Alt-Text", DemoCommand.Describe("Ein Logo mit Flamme"))
      action("Bild 96px", DemoCommand.Resize(96))
      action("Undo", DemoCommand.Undo)
      action("Redo", DemoCommand.Redo)
    }

  private def action(label: String, command: DemoCommand)(using AbstractComponent, Cursor): Unit =
    button(label) {
      classes = Seq("ember-demo__action")
      onClick(_ => editor.perform(command): Unit)
    }

  private def panelTitle(title: String, hint: String)(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__panel-title")
      div { classes = Seq("ember-demo__panel-name"); text(title) {} }
      div { classes = Seq("ember-demo__panel-hint"); text(hint) {} }
    }

  private def statusLine()(using AbstractComponent, Cursor): Unit =
    div { classes = Seq("ember-demo__status"); text(status) {} }

  /** Brings status line and caret marker up to date.
    *
    * The marker goes through [[DocumentView.componentFor]] -- the index §15.1 keeps as "eine
    * Zuordnung, keine zweite Ownership-Liste". The demo colours the run the model caret sits
    * in; it sets no DOM selection, because there is none yet.
    */
  private def refreshStatus(): Unit =
    val caret = editor.caret

    marked.filterNot(id => caret.exists(_._1 == id)).foreach { id =>
      view.componentFor(id).foreach(_.setClasses(Seq.empty))
    }
    marked = caret.map(_._1)
    marked.foreach(id => view.componentFor(id).foreach(_.setClasses(Seq("ember-caret-run"))))

    val position = caret match
      case Some((node, offset)) => s"Caret ${node.value}:$offset"
      case None                 => "kein Caret"

    val projected = view.projectedRevision.value
    val revision  = editor.session.state.revision.value
    val marks     = editor.activeMarks.markIds.map(_.value.split("/").head.split(Array(0x2e.toChar)).last)
    val undo      = editor.history.state.undo.length
    val redo      = editor.history.state.redo.length
    val problem   = editor.error.map(text => s"  ·  Fehler: $text").getOrElse("")

    status.set(
      s"$position  ·  Revision $revision, projiziert $projected  ·  " +
        s"${editor.session.document.size} Knoten  ·  History $undo/$redo" +
        (if marks.isEmpty then "" else marks.mkString("  ·  Marks ", ", ", "")) + problem
    )

/** The four views of the same document. */
private object Inspector:

  def render(
      document: ReadOnlyProperty[Document],
      editor: DemoSession
  )(using AbstractComponent, Cursor): Unit =
    val active = Property(Panel.Outline)

    div {
      classes = Seq("ember-demo__panel", "ember-demo__panel--inspector")

      div {
        classes = Seq("ember-demo__tabs")
        Panel.values.foreach(panel => tab(panel, active))
      }

      Panel.values.foreach { panel =>
        when(active.map(_ == panel)) {
          div { classes = Seq("ember-demo__panel-hint"); text(panel.hint) {} }
          // The Inhalt haengt am Document, not an a Timer: `EditorProperties` leads the
          // Property bei jedem Commit after (§10), and this Ableitung is the ganze Bindung.
          div {
            classes = Seq("ember-demo__code")
            text(document.map(_ => panel.contentOf(editor))) {}
          }
        }
      }
    }

  private def tab(panel: Panel, active: Property[Panel])(using AbstractComponent, Cursor): Unit =
    val entry = button(panel.label) {
      classes = Seq("ember-demo__tab")
      onClick(_ => active.set(panel))
    }
    // The active state lives in an attribute, not in a second class list: that way the
    // component's class set stays what its construction put there.
    entry.addDisposable(
      active.observe(current => entry.setAttribute("aria-selected", (current == panel).toString))
    )

/** Which view the inspector is showing. */
private enum Panel(val label: String, val hint: String):

  case Outline
      extends Panel(
        "Dokument",
        "Der Baum aus ember-core: referenzierte IDs, keine eingebetteten Objekte (§8.2)."
      )

  case Json
      extends Panel(
        "JSON",
        "ember-json, P10. Eingerueckt zum Lesen -- die Wire-Form ist kompakt und deterministisch."
      )

  case Content
      extends Panel(
        "HTML (Content)",
        "Was ein Leser bekommt. Ueber denselben Weg wie die Flaeche links, nur mit SsrCursor."
      )

  case Markdown
      extends Panel(
        "Markdown",
        "ember-markdown, P18. Kanonische Schreibweise -- der Export ist nicht quelltextgleich."
      )

  case Editor
      extends Panel(
        "HTML (Editor)",
        "Dieselbe Semantik plus data-ember-node. §19.1 laesst Editor-Attribute beim Austausch weg."
      )

  def contentOf(editor: DemoSession): String = this match
    case Outline => editor.outline
    case Json     => editor.json
    case Markdown => editor.markdown
    case Content => editor.html
    case Editor  => editor.editorHtml

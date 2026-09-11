package ember.editor.demo

import ember.editor.browser.*
import ember.editor.browsersupport.{CodeBindings, EditorBindings}
import ember.editor.core.*
import ember.editor.jfx.{DocumentView, EditorProperties}
import ember.editor.richtext.{BreakKind, HeadingLevel, StandardMarks}
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.Condition.when
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, DomNodes}
import jfx.core.state.{Disposable, Property, ReadOnlyProperty}
import org.scalajs.dom

/** The demo page.
  *
  * ==What it shows, and what it does not==
  *
  * It shows the state after P22: an immutable document model, atomic transactions, commands, the
  * keyed projection from P09, the versioned JSON from P10, the block and inline types P12 to P16
  * added -- and, since P21 and P22, a surface that can actually be typed into.
  *
  * The surface is a real editing host: `contenteditable`, a `SelectionPort` that reads and writes
  * the browser's selection, and the `BrowserInputController` that turns `beforeinput` into
  * commands. Nothing here imitates that any more; it is the same pipeline the integration harness
  * drives, against a session carrying every feature module in this repository.
  *
  * What is still open is P23: composition with its write lock, the observer reconciliation and
  * recovery. An IME works for simple cases and claims nothing beyond that.
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
  private var port: SelectionPort = null
  private var input: BrowserInputController = null
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
    if input != null then input.dispose()
    if port != null then port.dispose()
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
        "Stand P01-P22. Tippen, Auswahl und Tastatur laufen ueber die echte Pipeline; " +
          "Composition, Observer-Abgleich und Recovery sind P23."
      ) {}
    }

  // -----------------------------------------------------------------------------------------
  // The surface
  // -----------------------------------------------------------------------------------------

  private def surface()(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__panel", "ember-demo__panel--surface")

      panelTitle("Editierflaeche", "Anklicken und tippen -- Tab rueckt ein, Escape dann Tab geht raus")

      val host = div {
        classes = Seq("ember-demo__surface")
      }

      // `contenteditable`, `role` und `tabindex` setzt seit P22 der Controller -- sie gehoeren zur
      // Editierflaeche und nicht zu diesem Markup. Der zugaengliche Name bleibt hier, weil §22 ihn
      // ausdruecklich der Anwendung ueberlaesst.
      //
      // `host.setAttribute` und nicht die DSL-Erweiterung im Block: innerhalb einer
      // `AbstractComponent` verdeckt deren eigenes `setAttribute` die Erweiterung, und das
      // Attribut landete am Wurzel-Div der Seite statt an der Flaeche. Kein Compilefehler, nur ein
      // Attribut an der falschen Stelle -- derselbe Fall wie in P19b, hier von P22 aufgedeckt.
      host.setAttribute("aria-label", "Ember-Demodokument")

      view = DocumentView.mount(
        editor.session,
        Runtime.contentCursor(host),
        editor.views,
        parent = Some(host)
      )

      attachEditing(host)
      commandBar()
      statusLine()

      // Only now, so that the surface already exists.
      view.onProjected(_ => refreshStatus()): Unit
      refreshStatus()
    }

  /** Connects the real input pipeline (P22) to the surface.
    *
    * Until P22 this was a hand-written `keydown` bridge, and its own comment said what it was
    * waiting for: "The real judgement -- when a native action is prevented and when it is not --
    * belongs to P22." It does now, so the demo uses it instead of imitating it.
    *
    * What changed for the page is that this is no longer a stand-in. The same `SelectionPort`,
    * the same `BrowserInputController` and the same bindings the integration harness drives run
    * here, against a session that carries every feature module this repository has. That is what
    * the demo is for: the proof that the modules compose into an application.
    */
  private def attachEditing(host: AbstractComponent): Unit =
    DomNodes.option(host.host).foreach {
      case element: dom.Element =>
        port = SelectionPort.attachTo(editor.session, view, element)

        input = BrowserInputController.attachTo(
          editor.session,
          view,
          port,
          EditorBindings.everything,
          // Tab rueckt hier ein -- in einem Codeblock die Zeile, in einer Liste das Element.
          // §22 laesst das nur ausdruecklich aktiviert zu, und nur mit einem Ausgang: Escape,
          // dann Tab, und der Fokus geht weiter.
          EditorBindings.everythingKeyboard ++ CodeBindings.tabIndentation,
          TabPolicy.IndentsUntilEscape
        )

        // §15.4 verlangt bei Recovery eine verstaendliche Statusmeldung, §16 eine sichtbare
        // Ablehnung an der Formatgrenze. Beides laeuft hier in die Statuszeile.
        input.onOutcome {
          case InputOutcome.Refused(_, reason) => status.set(s"Abgelehnt: $reason")
          case InputOutcome.Unimported(reason) =>
            status.set(s"Native Aenderung nicht uebernommen: $reason")
          case _ => ()
        }: Unit

      case _ => ()
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

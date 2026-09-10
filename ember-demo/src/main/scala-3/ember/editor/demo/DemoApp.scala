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

/** The Demoseite.
  *
  * ==Was sie zeigt and was still not==
  *
  * Sie zeigt the Stand after P10: a unveraenderliches Document model, atomare Transaktionen,
  * Commands, the keyed Projektion from P09 and the versionierte JSON from P10 -- all am selben
  * Document, nebeneinander.
  *
  * Sie zeigt '''no''' Editierflaeche im vollen Sinn. It is no `contenteditable`, no
  * DOM-Selection and no native Eingabe; the are P20 until P23. The Flaeche faengt
  * Tastendruecke ab and translated sie in Commands -- dieselbe Kette, the a echte Eingabe
  * spaeter nimmt, nur ohne the nativen Part davor. The Caret is a Modellwert and is als
  * solcher angezeigt.
  *
  * ==Zwei Runtimes, a Baum==
  *
  * The Seite selbst is a gewoehnlicher JFX-Komponentenbaum. The Editierflaeche darin is
  * a [[DocumentView]] -- and zwar als Kind the Flaechenkomponente, not als zweite Wurzel:
  * damit raeumt a `Runtime.unmount` the Seite also the View ab.
  */
final class DemoApp extends AbstractComponent:

  val tagName = "div"

  private val editor = new DemoSession

  /** Was in the Statuszeile is. Getrennt von the Sitzung, weil it Anzeige is. */
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
  // Kopf and Fuss
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
        "Stand P01-P10. Keine native Eingabe, keine DOM-Selection, kein contenteditable -- " +
          "das sind P20 bis P23."
      ) {}
    }

  // -----------------------------------------------------------------------------------------
  // The Flaeche
  // -----------------------------------------------------------------------------------------

  private def surface()(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__panel", "ember-demo__panel--surface")

      panelTitle("Editierflaeche", "Anklicken und tippen")

      val host = div {
        classes = Seq("ember-demo__surface")
        // Fokussierbar ohne contenteditable: the Browser should the Flaeche erreichen can,
        // but nothing an ihr veraendern. Who sie veraendert, is the Projektion.
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

      // First jetzt, damit the Flaeche schon is.
      view.onProjected(_ => refreshStatus()): Unit
      refreshStatus()
    }

  /** Verbindet echte Tastendruecke with the Commands.
    *
    * `preventDefault` for all Behandelte, weil it here no native Editierflaeche is, the
    * the Aktion otherwise ausfuehren could. The echte Abwaegung -- wann a native Aktion
    * verhindert is and wann not -- gehoert to P22.
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

  /** Leads Statuszeile and Caretmarkierung after.
    *
    * The Markierung runs over [[DocumentView.componentFor]] -- the Index, the §15.1 als
    * "a Zuordnung, no zweite Ownership-List" leads. The Demo faerbt damit the Lauf a,
    * in the the Modellcaret is; sie setzt no DOM-Selection, denn the is it still not.
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

/** The drei Ansichten desselben Dokuments. */
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
    // The aktive State is im Attribut, not in a zweiten Klassenliste: so bleibt the
    // Klassenmenge the Komponente the, was the Construction gesetzt has.
    entry.addDisposable(
      active.observe(current => entry.setAttribute("aria-selected", (current == panel).toString))
    )

/** Which Sicht the Inspektor gerade zeigt. */
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

  case Editor
      extends Panel(
        "HTML (Editor)",
        "Dieselbe Semantik plus data-ember-node. §19.1 laesst Editor-Attribute beim Austausch weg."
      )

  def contentOf(editor: DemoSession): String = this match
    case Outline => editor.outline
    case Json    => editor.json
    case Content => editor.html
    case Editor  => editor.editorHtml

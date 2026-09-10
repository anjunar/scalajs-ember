package ember.editor.demo

import ember.editor.core.*
import ember.editor.jfx.{DocumentView, EditorProperties}
import ember.editor.standard.ParagraphSupport
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

/** Die Demoseite.
  *
  * ==Was sie zeigt und was noch nicht==
  *
  * Sie zeigt den Stand nach P10: ein unveraenderliches Dokumentmodell, atomare Transaktionen,
  * Commands, die keyed Projektion aus P09 und das versionierte JSON aus P10 -- alles am selben
  * Dokument, nebeneinander.
  *
  * Sie zeigt '''keine''' Editierflaeche im vollen Sinn. Es gibt kein `contenteditable`, keine
  * DOM-Selection und keine native Eingabe; das sind P20 bis P23. Die Flaeche faengt
  * Tastendruecke ab und uebersetzt sie in Commands -- dieselbe Kette, die eine echte Eingabe
  * spaeter nimmt, nur ohne den nativen Teil davor. Der Caret ist ein Modellwert und wird als
  * solcher angezeigt.
  *
  * ==Zwei Runtimes, ein Baum==
  *
  * Die Seite selbst ist ein gewoehnlicher JFX-Komponentenbaum. Die Editierflaeche darin ist
  * eine [[DocumentView]] -- und zwar als Kind der Flaechenkomponente, nicht als zweite Wurzel:
  * damit raeumt ein `Runtime.unmount` der Seite auch die Ansicht ab.
  */
final class DemoApp extends AbstractComponent:

  val tagName = "div"

  private val editor = new DemoSession

  /** Was in der Statuszeile steht. Getrennt von der Sitzung, weil es Anzeige ist. */
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
  // Kopf und Fuss
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
  // Die Flaeche
  // -----------------------------------------------------------------------------------------

  private def surface()(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("ember-demo__panel", "ember-demo__panel--surface")

      panelTitle("Editierflaeche", "Anklicken und tippen")

      val host = div {
        classes = Seq("ember-demo__surface")
        // Fokussierbar ohne contenteditable: der Browser soll die Flaeche erreichen koennen,
        // aber nichts an ihr veraendern. Wer sie veraendert, ist die Projektion.
        setAttribute("tabindex", "0")
        setAttribute("role", "textbox")
        setAttribute("aria-label", "Ember-Demodokument")
      }

      view = DocumentView.mount(
        editor.session,
        Runtime.contentCursor(host),
        ParagraphSupport.views,
        parent = Some(host)
      )

      keys(host)
      commandBar()
      statusLine()

      // Erst jetzt, damit die Flaeche schon steht.
      view.onProjected(_ => refreshStatus()): Unit
      refreshStatus()
    }

  /** Verbindet echte Tastendruecke mit den Commands.
    *
    * `preventDefault` fuer alles Behandelte, weil es hier keine native Editierflaeche gibt, die
    * die Aktion sonst ausfuehren koennte. Die echte Abwaegung -- wann eine native Aktion
    * verhindert wird und wann nicht -- gehoert zu P22.
    */
  private def keys(host: AbstractComponent): Unit =
    host.onHandler("keydown") { event =>
      val native = event.raw.asInstanceOf[dom.KeyboardEvent]
      val handled = native.key match
        case "z" if native.ctrlKey || native.metaKey =>
          editor.perform(if native.shiftKey then DemoCommand.Redo else DemoCommand.Undo)
        case "y" if native.ctrlKey => editor.perform(DemoCommand.Redo)
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

      action("Text einfuegen", DemoCommand.Insert("Ember "))
      action("Neuer Absatz", DemoCommand.Paragraph)
      action("Backspace", DemoCommand.Backspace)
      action("Delete", DemoCommand.Delete)
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

  /** Fuehrt Statuszeile und Caretmarkierung nach.
    *
    * Die Markierung laeuft ueber [[DocumentView.componentFor]] -- den Index, den §15.1 als
    * "eine Zuordnung, keine zweite Ownership-Liste" fuehrt. Die Demo faerbt damit den Lauf ein,
    * in dem der Modellcaret steht; sie setzt keine DOM-Auswahl, denn die gibt es noch nicht.
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
    val undo      = editor.history.state.undo.length
    val redo      = editor.history.state.redo.length
    val problem   = editor.error.map(text => s"  ·  Fehler: $text").getOrElse("")

    status.set(
      s"$position  ·  Revision $revision, projiziert $projected  ·  " +
        s"${editor.session.document.size} Knoten  ·  History $undo/$redo$problem"
    )

/** Die drei Ansichten desselben Dokuments. */
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
          // Der Inhalt haengt am Dokument, nicht an einem Timer: `EditorProperties` fuehrt die
          // Property bei jedem Commit nach (§10), und diese Ableitung ist die ganze Bindung.
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
    // Der aktive Zustand steht im Attribut, nicht in einer zweiten Klassenliste: so bleibt die
    // Klassenmenge der Komponente das, was der Aufbau gesetzt hat.
    entry.addDisposable(
      active.observe(current => entry.setAttribute("aria-selected", (current == panel).toString))
    )

/** Welche Sicht der Inspektor gerade zeigt. */
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

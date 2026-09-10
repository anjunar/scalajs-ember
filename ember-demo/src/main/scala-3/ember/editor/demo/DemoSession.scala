package ember.editor.demo

import ember.editor.core.*
import ember.editor.history.{History, HistoryConfig}
import ember.editor.html.RenderProfile
import ember.editor.jfx.{DocumentView, ViewSupport}
import ember.editor.json.*
import ember.editor.richtext.*
import ember.editor.standard.{ParagraphSupport, RichTextSupport}

/** The Sitzung the Demo samt all, was daran haengt.
  *
  * ==Warum the here zusammensteht==
  *
  * A Anwendung setzt the Module selbst zusammen: Kern, Profil, Persistenz, Semantik,
  * Projektion and Adapter. §6 haelt exactly the fest -- `standard` is a '''optionales'''
  * Integrationsmodul, and who nur JSON braucht, linkt weder JFX still HTML with. This Datei is
  * the einzige Stelle the Demo, an the all sechs Module vorkommen; the Rest kennt nur, was he
  * uses.
  */
final class DemoSession:

  private val generator = NodeIdGenerator.sequential("n")

  /** Undo and Redo (P11). A History gehoert exactly a Sitzung -- deshalb here and not
    * als globaler Wert.
    */
  val history: History = new History(HistoryConfig.default)

  private val resolved: ResolvedExtensions =
    ExtensionResolver.resolve(Vector(RichText(generator), history)) match
      case Right(value) => value
      case Left(errors) =>
        throw new IllegalStateException(errors.map(_.render).mkString("; "))

  /** The Codecs for the Persistenzansicht.
    *
    * Nur the the Kerns plus a for the Absatz. Letzterer is here and not in
    * `ember-json`: §6 stellt `json` beside the Node-Module, not over sie, and a
    * Absatz-Codec gehoert after P16/P18 ins Integrationsmodul. Until dahin is he the, was he
    * is -- a Zeile Anwendungscode.
    */
  private val paragraphCodec: NodeJsonCodec[ParagraphNode] = new NodeJsonCodec[ParagraphNode]:
    val nodeType: NodeType[ParagraphNode] = ParagraphNode

    def encode(
        node: ParagraphNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] = Right(Vector.empty)

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, ParagraphNode] = Right(ParagraphNode(id, Vector.empty))

  private val codecs: JsonSupport = CoreJsonSupport.all ++ JsonSupport.of(paragraphCodec)

  /** The adapter set: root, paragraph and text plus the block types P12 added. */
  val views: ViewSupport = RichTextSupport.views

  private var lastError: Option[String] = None

  val session: EditorSession =
    EditorSession.create(
      startingDocument,
      resolved,
      resolved.sessionConfig(errorSink = error => lastError = Some(error.render))
    ) match
      case Right(value) => value
      case Left(errors) =>
        throw new IllegalStateException(errors.map(_.render).mkString("; "))

  session.update(_.setSelection(RichText.caretAtStart(session.document))): Unit

  /** A Document with Inhalt statt a leeren Absatzes -- it should etwas to sehen give. */
  private def startingDocument: Document =
    val paragraphs = Vector(
      "Ember ist ein modularer HTML-WYSIWYG-Editor fuer Scala.js.",
      "Was hier steht, ist ein Dokumentmodell -- kein HTML-String. Tippen Sie in die Flaeche " +
        "links; die Panels rechts zeigen denselben Stand als Baum, als JSON und als " +
        "ausgeliefertes HTML."
    )

    val nodes = paragraphs.zipWithIndex.flatMap { (content, index) =>
      Vector(
        ParagraphNode(NodeId(s"p$index"), Vector(NodeId(s"t$index"))),
        TextNode(NodeId(s"t$index"), content)
      )
    }

    Document.unsafe(
      resolved.schema,
      NodeId("root"),
      RootNode(NodeId("root"), paragraphs.indices.map(index => NodeId(s"p$index")).toVector) +:
        nodes
    )

  // -----------------------------------------------------------------------------------------
  // Was the Oberflaeche braucht
  // -----------------------------------------------------------------------------------------

  /** Leads a Editing-Command from. The Rueckgabewert says, if he zustaendig war.
    *
    * Undo and Redo laufen over the History selbst and not over a Dispatch: sie setzen
    * with it `Origin.History`, and the Rekorder ueberspringt exactly this Herkunft (§14).
    */
  def perform(command: DemoCommand): Boolean =
    val outcome = command match
      case DemoCommand.Insert(text) => session.dispatch(RichText.InsertText, text)
      case DemoCommand.Paragraph    => session.dispatch(RichText.InsertParagraph)
      case DemoCommand.Backspace    => session.dispatch(RichText.DeleteBackward)
      case DemoCommand.Delete       => session.dispatch(RichText.DeleteForward)
      case DemoCommand.Undo         => return handled(history.undo())
      case DemoCommand.Redo         => return handled(history.redo())
      case DemoCommand.Mark(mark)   => session.dispatch(RichText.ToggleMark, mark)
      case DemoCommand.Heading(level) => session.dispatch(RichText.SetHeading, level)
      case DemoCommand.Quote        => session.dispatch(RichText.Quote)
      case DemoCommand.Unquote      => session.dispatch(RichText.Unquote)
      case DemoCommand.HardBreak    => session.dispatch(RichText.InsertBreak, BreakKind.Hard)
      case DemoCommand.Rule         => session.dispatch(RichText.InsertThematicBreak)

    outcome match
      case Right(result) => result.wasHandled
      case Left(failure) =>
        lastError = Some(failure.render)
        false

  private def handled(outcome: Either[UpdateError, Boolean]): Boolean = outcome match
    case Right(changed) => changed
    case Left(failure) =>
      lastError = Some(failure.render)
      false

  def error: Option[String] = lastError

  /** The marks a toolbar would show as active (§11). */
  def activeMarks: MarkSet = RangeFormatting.activeMarks(session.state)

  /** The Node, in the the Modellcaret is, and sein Offset.
    *
    * '''It is still no DOM-Selection.''' The Caret is a Modellwert (§11); ihn in a
    * echte Browserauswahl to uebersetzen is the `SelectionPort` from P21. Until dahin zeigt the
    * Demo, was the Modell weiss, and behauptet nothing about it hinaus.
    */
  def caret: Option[(NodeId, Int)] =
    session.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => (node, offset) }

  /** The Document tree als eingerueckter Text. */
  def outline: String =
    val document = session.document

    def walk(id: NodeId, depth: Int): Vector[String] =
      val indent = "  " * depth
      document.node(id) match
        case Some(text: TextNode) =>
          Vector(s"$indent${id.value}  \"${text.text}\"")
        case Some(element: ElementNode) =>
          val name = document.schema.descriptorFor(element).map(_.typeId.value).getOrElse("?")
          s"$indent${id.value}  $name" +:
            element.children.flatMap(walk(_, depth + 1))
        case _ => Vector.empty

    walk(document.rootId, 0).mkString("\n")

  /** Derselbe Stand als JSON (P10). */
  def json: String =
    DocumentJson.encode(session.document, codecs) match
      case Right(value) => JsonText.renderPretty(value)
      case Left(errors) => errors.map(_.render).mkString("\n")

  /** Derselbe Stand als ausgeliefertes HTML (P09, Content-Profil).
    *
    * Over denselben Weg wie the Flaeche links -- `DocumentView` nimmt a beliebigen Cursor,
    * here a `SsrCursor`. Dass beide dasselbe liefern, is deshalb no Absprache between
    * zwei Implementierungen.
    */
  def html: String =
    DocumentView.renderToHtml(session.document, views, RenderProfile.Content)

  /** And the Editierfassung, with the Node-IDs als Attribute (§19.1). */
  def editorHtml: String =
    DocumentView.renderToHtml(session.document, views, RenderProfile.Editor)

/** Was the Demo trigger can. No Dispatch-API -- the Commands are Werte (§12). */
enum DemoCommand:
  case Insert(text: String)
  case Paragraph
  case Backspace
  case Delete
  case Undo
  case Redo
  case Mark(mark: TextMark)
  case Heading(level: Option[HeadingLevel])
  case Quote
  case Unquote
  case HardBreak
  case Rule

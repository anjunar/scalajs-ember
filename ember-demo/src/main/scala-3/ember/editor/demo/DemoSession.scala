package ember.editor.demo

import ember.editor.core.*
import ember.editor.code.{CodeCommands, CodeExtension, CodeInfo}
import ember.editor.history.{History, HistoryConfig}
import ember.editor.link.{LinkCommands, LinkExtension, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListCommands, ListExtension, ListKind}
import ember.editor.html.RenderProfile
import ember.editor.jfx.{DocumentView, ViewSupport}
import ember.editor.json.*
import ember.editor.richtext.*
import ember.editor.standard.CodeSupport

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
    ExtensionResolver.resolve(
      Vector(
        RichText(generator),
        ListExtension(generator),
        LinkExtension(generator),
        CodeExtension(generator),
        history
      )
    ) match
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
  val views: ViewSupport = CodeSupport.views

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
      case DemoCommand.Bullets      => session.dispatch(ListCommands.ToggleList, ListKind.Unordered)
      case DemoCommand.Numbers      => session.dispatch(ListCommands.ToggleList, ListKind.Ordered)
      case DemoCommand.Indent       => session.dispatch(ListCommands.Indent)
      case DemoCommand.Outdent      => session.dispatch(ListCommands.Outdent)
      case DemoCommand.Unlink       => session.dispatch(LinkCommands.RemoveLink)
      case DemoCommand.Link(url) => return linkRunAtCaret(url)
      case DemoCommand.Code        => session.dispatch(CodeCommands.ToggleCodeBlock, CodeInfo.of("scala"))
      case DemoCommand.IndentCode  => session.dispatch(CodeCommands.IndentLine)
      case DemoCommand.OutdentCode => session.dispatch(CodeCommands.OutdentLine)

    outcome match
      case Right(result) => result.wasHandled
      case Left(failure) =>
        lastError = Some(failure.render)
        false

  /** Legt den ganzen Textlauf am Caret in einen Link.
    *
    * ==Warum die Demo den Bereich selbst setzt==
    *
    * `SetLink` braucht eine Auswahl -- es gibt nichts zu umschliessen, wenn nichts ausgewaehlt
    * ist. Eine DOM-Auswahl gibt es aber noch nicht: der `SelectionPort` ist P21. Die Demo waehlt
    * deshalb im Modell aus, und zwar den ganzen Lauf am Caret: vorhersagbar, erklaerbar
    * und ohne so zu tun, als koennte man hier schon mit der Maus markieren.
    *
    * Auswahl und Command laufen in einer Transaktion. Zwei waeren zwei History-Stufen,
    * und ein Undo nach dem Verlinken naehme dann nur die Auswahl zurueck.
    */
  private def linkRunAtCaret(url: String): Boolean =
    // Die Policy ist die einzige Tuer zu einer `LinkUrl` -- eine unsichere Adresse kommt gar
    // nicht erst bis zum Command (§19.1, P14).
    LinkUrlPolicy.default.parse(url) match
      case Left(error) =>
        lastError = Some(error.render)
        false
      case Right(value) =>
        caret.map(_._1).flatMap(id => session.document.node(id).collect { case run: TextNode => run }) match
          case None => false
          case Some(run) =>
            handled(
              session
                .update { transaction =>
                  transaction.select(
                    RangeSelection(
                      Point.textBefore(run.id, 0),
                      Point.textBefore(run.id, run.text.length)
                    )
                  ): Unit
                  transaction.dispatch(LinkCommands.SetLink, LinkTarget(value)): Unit
                }
                .map(_ => true)
            )

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
  case Bullets
  case Numbers
  case Indent
  case Outdent
  case Link(url: String)
  case Unlink
  case Code
  case IndentCode
  case OutdentCode

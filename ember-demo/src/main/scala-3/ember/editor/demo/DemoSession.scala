package ember.editor.demo

import ember.editor.core.*
import ember.editor.code.{CodeCommands, CodeExtension, CodeInfo}
import ember.editor.history.{History, HistoryConfig}
import ember.editor.image.{
  ImageCommands,
  ImageExtension,
  ImageNode,
  MediaReference,
  MediaUrlPolicy,
  PositivePixels
}
import ember.editor.link.{LinkCommands, LinkExtension, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListCommands, ListExtension, ListKind}
import ember.editor.html.RenderProfile
import ember.editor.jfx.{DocumentView, ViewSupport}
import ember.editor.json.*
import ember.editor.richtext.*
import ember.editor.markdown.{LossPolicy, MarkdownCodec}
import ember.editor.standard.{ImageJsonSupport, ImageSupport, MarkdownSupports}

/** The session of the demo and everything hanging off it.
  *
  * ==Why it all stands here==
  *
  * An application assembles the modules itself: core, profile, persistence, semantics,
  * projection and adapters. §6 says as much -- `standard` is an '''optional''' integration
  * module, and whoever needs only JSON links neither JFX nor HTML. This file is the one place
  * in the demo where all six modules appear; the rest knows only what it uses.
  */
final class DemoSession:

  private val generator = NodeIdGenerator.sequential("n")

  /** Which sources this demo accepts (§20).
    *
    * The default profile, unchanged: https and relative paths, nothing else. The demo's own
    * picture is a relative path -- exactly the case `allowRelative` covers -- and a `data:` URL
    * would be more convenient and is refused, because it is not a lasting MediaReference.
    */
  private val media: MediaUrlPolicy = MediaUrlPolicy.default

  /** Undo and redo (P11). A history belongs to exactly one session -- hence here, and not as a
    * global value.
    */
  val history: History = new History(HistoryConfig.default)

  private val resolved: ResolvedExtensions =
    ExtensionResolver.resolve(
      Vector(
        RichText(generator),
        ListExtension(generator),
        LinkExtension(generator),
        CodeExtension(generator),
        ImageExtension(generator, media),
        history
      )
    ) match
      case Right(value) => value
      case Left(errors) =>
        throw new IllegalStateException(errors.map(_.render).mkString("; "))

  /** The codec for the paragraph, which no module supplies.
    *
    * §6 puts `json` beside the node modules, not above them, so a paragraph codec is an
    * application's business until P18 moves it into the integration module. Until then it is
    * what it is -- a line of application code. Everything else comes from the modules:
    * [[ImageJsonSupport.support]] brings the core codecs and the one for pictures.
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

  private val codecs: JsonSupport =
    ImageJsonSupport.support(media) ++ JsonSupport.of(paragraphCodec)

  /** Every standard adapter: rich text, lists, links, code and images. */
  val views: ViewSupport = ImageSupport.views

  /** The Markdown rules, with this demo's policies -- the same two the commands use. */
  private val markdownRules = MarkdownSupports.everything(LinkUrlPolicy.default, media)

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

  /** A document with content instead of an empty paragraph -- there should be something to see.
    *
    * The last paragraph carries a picture, so the inline atom is visible without a click: it
    * sits '''between''' two text runs, not next to the paragraph, and the tree panel shows
    * exactly that (§20).
    */
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

    val picture = Vector(
      ParagraphNode(NodeId("pBild"), Vector(NodeId("tBild"), NodeId("bild"), NodeId("tBildEnde"))),
      TextNode(NodeId("tBild"), "Bilder sind Inline-Atome: "),
      ImageNode(
        NodeId("bild"),
        MediaReference(media.unsafe("/ember.svg")),
        "Das Ember-Logo",
        width = PositivePixels.parse(48),
        height = PositivePixels.parse(48)
      ),
      TextNode(NodeId("tBildEnde"), " -- dieses steht mitten im Absatz.")
    )

    Document.unsafe(
      resolved.schema,
      NodeId("root"),
      RootNode(
        NodeId("root"),
        paragraphs.indices.map(index => NodeId(s"p$index")).toVector :+ NodeId("pBild")
      ) +: (nodes ++ picture)
    )

  // -----------------------------------------------------------------------------------------
  // Was the Oberflaeche braucht
  // -----------------------------------------------------------------------------------------

  /** Runs one editing command. The return value says whether it was responsible.
    *
    * Undo and redo go through the history itself and not through a dispatch: they set
    * `Origin.History` while doing so, and the recorder skips exactly that origin (§14).
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
      case DemoCommand.Image(url, alt) => return insertImage(url, alt)
      case DemoCommand.Describe(alt)   => return changeImage(_.copy(alt = alt))
      case DemoCommand.Resize(width) =>
        return changeImage(_.copy(width = PositivePixels.parse(width), height = None))

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

  /** Inserts a picture at the caret (P16).
    *
    * The policy is the only door to a [[MediaUrl]], exactly as it is for links: a source the
    * profile refuses never reaches the command, and there is no second path that could let one
    * past (§20). The demo has no picker and no upload -- §20 puts both in an application
    * service, and what the command takes is a finished address.
    */
  private def insertImage(url: String, alt: String): Boolean =
    media.parse(url) match
      case Left(error) =>
        lastError = Some(error.render)
        false
      case Right(source) =>
        val image = ImageNode(generator.nextFor(session.document), MediaReference(source), alt)
        session.dispatch(ImageCommands.InsertImage, image) match
          case Right(result) => result.wasHandled
          case Left(failure) =>
            lastError = Some(failure.render)
            false

  /** Rewrites the picture next to the caret.
    *
    * ==Why the demo picks the selection itself==
    *
    * An atom has no text position inside it, so a caret cannot stand '''in''' a picture; what a
    * selection can do is name it (§11). Naming it with the mouse is the `SelectionPort` from
    * P21, so the demo does in the model what a click will later do in the DOM, and takes the
    * neighbour of the caret -- predictable, and without pretending one could already point at
    * it.
    *
    * Selection, change and the caret afterwards run in '''one''' transaction. Three would be
    * three history steps, and an undo would then only take back the selection.
    */
  private def changeImage(change: ImageNode => ImageNode): Boolean =
    (caret, imageNextToCaret) match
      case (Some((run, offset)), Some(image)) =>
        handled(
          session
            .update { transaction =>
              transaction.select(NodeSelection(Set(image.id))): Unit
              transaction.dispatch(ImageCommands.UpdateImage, change): Unit
              transaction.select(RangeSelection.caret(Point.textBefore(run, offset))): Unit
            }
            .map(_ => true)
        )
      case _ => false

  /** The picture directly after the caret's run, or directly before it. */
  private def imageNextToCaret: Option[ImageNode] =
    for
      (run, _)  <- caret
      parent    <- session.document.parentOf(run)
      index     <- session.document.indexOfChild(run)
      siblings   = session.document.childrenOf(parent)
      image     <- Vector(index + 1, index - 1)
                     .flatMap(siblings.lift)
                     .flatMap(session.document.node)
                     .collectFirst { case image: ImageNode => image }
    yield image

  private def handled(outcome: Either[UpdateError, Boolean]): Boolean = outcome match
    case Right(changed) => changed
    case Left(failure) =>
      lastError = Some(failure.render)
      false

  def error: Option[String] = lastError

  /** The marks a toolbar would show as active (§11). */
  def activeMarks: MarkSet = RangeFormatting.activeMarks(session.state)

  /** The node the model caret sits in, and its offset.
    *
    * '''This is still not a DOM selection.''' The caret is a model value (§11); translating it
    * into a real browser selection is the `SelectionPort` from P21. Until then the demo shows
    * what the model knows and claims nothing beyond it.
    */
  def caret: Option[(NodeId, Int)] =
    session.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => (node, offset) }

  /** The document tree as indented text. */
  def outline: String =
    val document = session.document

    def typeOf(node: EditorNode): String =
      document.schema.descriptorFor(node).map(_.typeId.value).getOrElse("?")

    def walk(id: NodeId, depth: Int): Vector[String] =
      val indent = "  " * depth
      document.node(id) match
        case Some(text: TextNode) =>
          Vector(s"$indent${id.value}  \"${text.text}\"")
        case Some(element: ElementNode) =>
          s"$indent${id.value}  ${typeOf(element)}" +:
            element.children.flatMap(walk(_, depth + 1))
        // An atom is neither: no children and no text, but it is a node and belongs in the
        // tree. Leaving it out would make the panel disagree with the document about how many
        // children a paragraph has.
        case Some(atom) => Vector(s"$indent${id.value}  ${typeOf(atom)}")
        case None       => Vector.empty

    walk(document.rootId, 0).mkString("\n")

  /** The same state as Markdown (P18).
    *
    * '''AllowLossy''', and that is the interesting part: a panel that showed an error
    * instead of a document whenever something has no Markdown spelling would be useless. §18.2
    * makes the choice explicit, and a viewer legitimately chooses to see what Markdown can
    * carry -- the demo prints what it could not underneath, so the loss is visible rather than
    * silent.
    */
  def markdown: String =
    MarkdownCodec.encode(session.document, markdownRules, LossPolicy.AllowLossy) match
      case Right(written) =>
        val losses = written.losses.map(loss => s"-- ${loss.message}")
        (written.source +: losses).mkString("\n")
      case Left(error) => error.render

  /** The same state as JSON (P10). */
  def json: String =
    DocumentJson.encode(session.document, codecs) match
      case Right(value) => JsonText.renderPretty(value)
      case Left(errors) => errors.map(_.render).mkString("\n")

  /** The same state as delivered HTML (P09, content profile).
    *
    * Through the same path as the surface on the left -- `DocumentView` takes any cursor, here
    * an `SsrCursor`. That both produce the same output is therefore not an agreement between
    * two implementations.
    */
  def html: String =
    DocumentView.renderToHtml(session.document, views, RenderProfile.Content)

  /** And the editing version, with the node ids as attributes (§19.1). */
  def editorHtml: String =
    DocumentView.renderToHtml(session.document, views, RenderProfile.Editor)

/** What the demo can trigger. No dispatch API -- commands are values (§12). */
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
  case Image(url: String, alt: String)
  case Describe(alt: String)
  case Resize(width: Int)

package ember.editor.demo

import ember.editor.core.*
import ember.editor.history.{History, HistoryConfig}
import ember.editor.html.RenderProfile
import ember.editor.jfx.DocumentView
import ember.editor.json.*
import ember.editor.richtext.*
import ember.editor.standard.ParagraphSupport

/** Die Sitzung der Demo samt allem, was daran haengt.
  *
  * ==Warum das hier zusammensteht==
  *
  * Eine Anwendung setzt die Module selbst zusammen: Kern, Profil, Persistenz, Semantik,
  * Projektion und Adapter. §6 haelt genau das fest -- `standard` ist ein '''optionales'''
  * Integrationsmodul, und wer nur JSON braucht, linkt weder JFX noch HTML mit. Diese Datei ist
  * die einzige Stelle der Demo, an der alle sechs Module vorkommen; der Rest kennt nur, was er
  * benutzt.
  */
final class DemoSession:

  private val generator = NodeIdGenerator.sequential("n")

  /** Undo und Redo (P11). Eine History gehoert genau einer Sitzung -- deshalb hier und nicht
    * als globaler Wert.
    */
  val history: History = new History(HistoryConfig.default)

  private val resolved: ResolvedExtensions =
    ExtensionResolver.resolve(Vector(RichText(generator), history)) match
      case Right(value) => value
      case Left(errors) =>
        throw new IllegalStateException(errors.map(_.render).mkString("; "))

  /** Die Codecs fuer die Persistenzansicht.
    *
    * Nur die des Kerns plus einer fuer den Absatz. Letzterer steht hier und nicht in
    * `ember-json`: §6 stellt `json` neben die Node-Module, nicht ueber sie, und ein
    * Absatz-Codec gehoert nach P16/P18 ins Integrationsmodul. Bis dahin ist er das, was er
    * ist -- eine Zeile Anwendungscode.
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

  /** Ein Dokument mit Inhalt statt eines leeren Absatzes -- es soll etwas zu sehen geben. */
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
  // Was die Oberflaeche braucht
  // -----------------------------------------------------------------------------------------

  /** Fuehrt einen Editing-Command aus. Der Rueckgabewert sagt, ob er zustaendig war.
    *
    * Undo und Redo laufen ueber die History selbst und nicht ueber einen Dispatch: sie setzen
    * dabei `Origin.History`, und der Rekorder ueberspringt genau diese Herkunft (§14).
    */
  def perform(command: DemoCommand): Boolean =
    val outcome = command match
      case DemoCommand.Insert(text) => session.dispatch(RichText.InsertText, text)
      case DemoCommand.Paragraph    => session.dispatch(RichText.InsertParagraph)
      case DemoCommand.Backspace    => session.dispatch(RichText.DeleteBackward)
      case DemoCommand.Delete       => session.dispatch(RichText.DeleteForward)
      case DemoCommand.Undo         => return handled(history.undo())
      case DemoCommand.Redo         => return handled(history.redo())

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

  /** Der Knoten, in dem der Modellcaret steht, und sein Offset.
    *
    * '''Es gibt noch keine DOM-Selection.''' Der Caret ist ein Modellwert (§11); ihn in eine
    * echte Browserauswahl zu uebersetzen ist der `SelectionPort` aus P21. Bis dahin zeigt die
    * Demo, was das Modell weiss, und behauptet nichts darueber hinaus.
    */
  def caret: Option[(NodeId, Int)] =
    session.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => (node, offset) }

  /** Der Dokumentbaum als eingerueckter Text. */
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
    * Ueber denselben Weg wie die Flaeche links -- `DocumentView` nimmt einen beliebigen Cursor,
    * hier einen `SsrCursor`. Dass beide dasselbe liefern, ist deshalb keine Absprache zwischen
    * zwei Implementierungen.
    */
  def html: String =
    DocumentView.renderToHtml(session.document, ParagraphSupport.views, RenderProfile.Content)

  /** Und die Editierfassung, mit den Knoten-IDs als Attribute (§19.1). */
  def editorHtml: String =
    DocumentView.renderToHtml(session.document, ParagraphSupport.views, RenderProfile.Editor)

/** Was die Demo ausloesen kann. Keine Dispatch-API -- die Commands sind Werte (§12). */
enum DemoCommand:
  case Insert(text: String)
  case Paragraph
  case Backspace
  case Delete
  case Undo
  case Redo

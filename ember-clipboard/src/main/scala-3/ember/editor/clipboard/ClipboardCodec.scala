package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.json.*
import ember.editor.richtext.*

final case class ClipboardData(formats: Map[String, String]):
  def get(mime: String): Option[String] = formats.get(mime)

object ClipboardMime:
  val Internal = "application/x-ember-editor+json"
  val Html     = "text/html"
  val Text     = "text/plain"
  val priority = Vector(Internal, Html, Text)

final case class ClipboardDecoded(
    fragment: DocumentFragment,
    mime: String,
    diagnostics: Vector[String]
)

/** Profile identity and every format's validators belong to the receiving app. No browser API is
  * used by extraction, encoding or decoding.
  */
final class ClipboardCodec(
    val profile: String,
    val schema: Schema,
    json: JsonSupport,
    html: HtmlSupport,
    htmlImport: HtmlImportSupport,
    val maxSourceChars: Int = 1024 * 1024,
    atomText: EditorNode => String = _ => "\uFFFC"
):
  require(profile.nonEmpty && maxSourceChars > 0)
  private val limits = DecodeLimits.default.copy(
    maxSourceChars = maxSourceChars,
    maxNodes = 20000,
    maxDocumentDepth = 64
  )

  def encode(fragment: DocumentFragment): Either[ClipboardError, ClipboardData] =
    val plain    = text(fragment.document)
    val internal = DocumentJson.encode(fragment.document, json).toOption.map { document =>
      JsonText.render(
        JsonValue.obj(
          "format"    -> JsonValue.Str("ember-fragment"),
          "version"   -> JsonValue.num(1),
          "profile"   -> JsonValue.Str(profile),
          "openStart" -> JsonValue.num(fragment.openStart),
          "openEnd"   -> JsonValue.num(fragment.openEnd),
          "document"  -> document
        )
      )
    }
    val formats = Map(ClipboardMime.Text -> plain) ++
      internal.map(ClipboardMime.Internal -> _) ++ render(fragment.document).map(
        ClipboardMime.Html -> _
      )
    if formats.values.exists(_.length > maxSourceChars) then
      Left(ClipboardError("Clipboard-Inhalt überschreitet das Größenlimit."))
    else Right(ClipboardData(formats))

  def decode(data: ClipboardData): Either[ClipboardError, ClipboardDecoded] =
    var diagnostics = Vector.empty[String]
    var chosen      = Option.empty[ClipboardDecoded]
    ClipboardMime.priority.foreach { mime =>
      if chosen.isEmpty then
        data.get(mime).foreach { source =>
          val result = if source.length > maxSourceChars then
            Left(ClipboardError("Clipboard-Inhalt überschreitet das Größenlimit."))
          else
            mime match
              case ClipboardMime.Internal => decodeInternal(source).map(_ -> Vector.empty[String])
              case ClipboardMime.Html     =>
                HtmlImport
                  .imported(
                    source,
                    schema,
                    htmlImport,
                    NodeIdGenerator.sequential("html"),
                    NodeId("root"),
                    HtmlImportPolicy.default
                      .copy(limits = HtmlLimits(maxSourceChars = maxSourceChars, maxDepth = 64))
                  )
                  .left
                  .map(error => ClipboardError(error.render))
                  .flatMap { parsed =>
                    val roots = parsed.document.childrenOf(parsed.document.rootId)
                    def open(id: Option[NodeId]): Int =
                      if id.flatMap(parsed.document.node).exists(_.isInstanceOf[ParagraphNode]) then
                        1
                      else 0
                    DocumentFragment
                      .create(parsed.document, open(roots.headOption), open(roots.lastOption))
                      .map(_ -> parsed.diagnostics.map(_.render))
                  }
              case _ => plain(source).map(_ -> Vector.empty[String])
          result match
            case Right((fragment, notes)) =>
              chosen = Some(ClipboardDecoded(fragment, mime, diagnostics ++ notes))
            case Left(error) => diagnostics :+= s"$mime: ${error.message}"
        }
    }
    chosen.toRight(ClipboardError(if diagnostics.isEmpty then "Kein unterstütztes Clipboard-Format."
    else diagnostics.mkString("; ")))

  private def decodeInternal(source: String): Either[ClipboardError, DocumentFragment] =
    val at       = DiagnosticPath.Root
    val envelope = for
      value        <- JsonText.parse(source, limits)
      obj          <- value.asObject(at)
      format       <- obj.string("format", at)
      version      <- obj.int("version", at)
      givenProfile <- obj.string("profile", at)
      start        <- obj.int("openStart", at)
      end          <- obj.int("openEnd", at)
      document     <- obj.required("document", at)
    yield (format, version, givenProfile, start, end, document)
    envelope.left.map(error => ClipboardError(error.render)).flatMap {
      (format, version, givenProfile, start, end, document) =>
        if format != "ember-fragment" || version != 1 || givenProfile != profile then
          Left(ClipboardError("Inkompatibles internes Fragmentformat oder Profil."))
        else
          DocumentJson
            .decode(document, schema, json, DecodeConfig(limits = limits))
            .left
            .map(errors => ClipboardError(errors.map(_.render).mkString("; ")))
            .flatMap(decoded => DocumentFragment.create(decoded.document, start, end))
    }

  private def plain(source: String): Either[ClipboardError, DocumentFragment] =
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    if 1L + 2L * (normalized.count(_ == '\n') + 1L) > limits.maxNodes then
      return Left(ClipboardError("Clipboard-Inhalt überschreitet das Knotenlimit."))
    val generator = NodeIdGenerator.sequential("plain")
    val root      = generator.next(_ => false)
    val blocks    = normalized.split("\n", -1).toVector.map { line =>
      val p = generator.next(_ => false)
      val t = generator.next(_ => false)
      Vector[EditorNode](ParagraphNode(p, Vector(t)), TextNode(t, line))
    }
    Document
      .build(schema, root, RootNode(root, blocks.map(_.head.id)) +: blocks.flatten)
      .left
      .map(errors => ClipboardError(errors.map(_.render).mkString("; ")))
      .flatMap(DocumentFragment.create(_, 1, 1))

  def text(document: Document): String =
    val values = scala.collection.mutable.Map.empty[NodeId, String]
    document.inDocumentOrder.toVector.reverse.foreach { node =>
      values(node.id) = node match
        case text: TextNode       => text.text
        case _: BreakNode         => "\n"
        case element: ElementNode =>
          val blocks = element.children.exists(id =>
            document.node(id).exists {
              case _: InlineElementNode => false
              case _: ElementNode       => true
              case _                    => false
            }
          )
          element.children.map(values).mkString(if blocks then "\n" else "")
        case atom => atomText(atom)
    }
    values(document.rootId)

  private def render(document: Document): Option[String] =
    val values   = scala.collection.mutable.Map.empty[NodeId, HtmlFragment]
    var complete = true
    document.inDocumentOrder.toVector.reverse.filterNot(_.id == document.rootId).foreach { node =>
      html.shapeOf(node, RenderProfile.Content) match
        case Some(HtmlShape.TextRun(_, value, _, marks)) =>
          values(node.id) = marks.foldRight[HtmlFragment](HtmlFragment.Text(value))((tag, inner) =>
            HtmlFragment.Element(tag, children = Vector(inner))
          )
        case Some(HtmlShape.Element(tag, attributes, inner)) =>
          val content = document.childrenOf(node.id).flatMap(values.get)
          val wrapped = inner.foldRight(content)((tag, children) =>
            Vector(HtmlFragment.Element(tag, children = children))
          )
          values(node.id) = HtmlFragment.Element(
            tag,
            attributes.filterNot(_.name.startsWith("data-ember-")),
            wrapped
          )
        case None => complete = false
    }
    Option.when(complete)(
      document.childrenOf(document.rootId).flatMap(values.get).map(HtmlFragment.render).mkString
    )

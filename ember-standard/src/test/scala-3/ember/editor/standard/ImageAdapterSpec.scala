package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.*
import ember.editor.jfx.*
import ember.editor.json.*
import ember.editor.richtext.*
import jfx.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Images in SSR and in JSON (P16). */
final class ImageAdapterSpec extends AnyFlatSpec with Matchers {

  private val root   = NodeId("root")
  private val policy = MediaUrlPolicy.default

  private def open(image: ImageNode): EditorSession =
    val generator = NodeIdGenerator.sequential("g")
    val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), ImageExtension(generator, policy)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          root,
          Vector(
            RootNode(root, Vector(NodeId("p0"))),
            ParagraphNode(NodeId("p0"), Vector(NodeId("t0"), image.id)),
            TextNode(NodeId("t0"), "Davor"),
            image
          )
        ),
        resolved,
        resolved.sessionConfig(errorSink = error => fail(s"Projektion: ${error.render}"))
      )
      .getOrElse(fail("Sitzung nicht erzeugbar"))

  private def picture(
      url: String = "https://example.com/bild.png",
      alt: String = "Ein Bild",
      title: Option[String] = None,
      width: Option[Int] = None,
      height: Option[Int] = None,
      mediaId: Option[String] = None
  ): ImageNode =
    ImageNode(
      NodeId("bild"),
      MediaReference(policy.unsafe(url), mediaId.map(MediaId(_))),
      alt,
      title,
      width.flatMap(PositivePixels.parse),
      height.flatMap(PositivePixels.parse)
    )

  private def html(editor: EditorSession): String =
    DocumentView.renderToHtml(editor.document, ImageSupport.views)

  // ---------------------------------------------------------------------------------------
  // SSR
  // ---------------------------------------------------------------------------------------

  "An image" should "render as img with src and alt" in {
    val editor = open(picture())

    html(editor) should include("<img src=\"https://example.com/bild.png\" alt=\"Ein Bild\">")
  }

  it should "keep an empty alt attribute" in {
    // §20: ein dekoratives Bild hat ausdruecklich leeren Alt-Text. Das Attribut wegzulassen
    // liesse einen Screenreader stattdessen den Dateinamen vorlesen.
    val editor = open(picture(alt = ""))

    html(editor) should include("alt=\"\"")
  }

  it should "write width and height when the document has them" in {
    // §20: absolute Werte "koennen Layoutspruenge reduzieren".
    val editor = open(picture(width = Some(640), height = Some(480)))

    html(editor) should include("width=\"640\"")
    html(editor) should include("height=\"480\"")
  }

  it should "leave them out when it has none" in {
    val editor = open(picture())

    html(editor) should not include "width="
    html(editor) should not include "height="
  }

  it should "render an internal source unchanged" in {
    val editor = open(picture(url = "/medien/bild.png"))

    html(editor) should include("src=\"/medien/bild.png\"")
  }

  it should "sit inline, next to the text" in {
    // §20: "Image ist ein Inline-Atom und damit auch in Paragraphen/Links verwendbar."
    val editor = open(picture())

    html(editor) should include("Davor")
    html(editor).indexOf("Davor") should be < html(editor).indexOf("<img")
  }

  it should "be a void element, with no closing tag" in {
    // `<img></img>` ist kein gueltiges HTML -- der Parser schliesst das Element beim Starttag,
    // und der Endtag waere ein Fehler, den jeder Validator meldet.
    val editor = open(picture())

    html(editor) should not include "</img>"
  }

  "The rendering" should "be the same through a session and through renderToHtml" in {
    val editor = open(picture())
    val cursor = new SsrCursor()
    DocumentView.mount(editor, cursor, ImageSupport.views, RenderProfile.Content)

    cursor.collectHtml() shouldBe html(editor)
  }

  // ---------------------------------------------------------------------------------------
  // JSON
  // ---------------------------------------------------------------------------------------

  private def support = ImageJsonSupport.support(policy) ++ JsonSupport.of(paragraphCodec)

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

  private def roundTrip(editor: EditorSession): Document =
    val text = DocumentJson
      .encodeToString(editor.document, support)
      .getOrElse(fail("nicht kodierbar"))

    DocumentJson
      .decodeString(text, editor.document.schema, support)
      .map(_.document)
      .getOrElse(fail(s"nicht dekodierbar: $text"))

  "An image" should "survive a JSON round trip" in {
    val original = open(picture(title = Some("Titel"), width = Some(640), mediaId = Some("4711")))

    roundTrip(original) shouldBe original.document
  }

  it should "survive with an internal source" in {
    val original = open(picture(url = "/medien/bild.png"))

    roundTrip(original) shouldBe original.document
  }

  it should "survive with an empty alt text" in {
    val original = open(picture(alt = ""))

    roundTrip(original) shouldBe original.document
  }

  it should "write no field it has no value for" in {
    val editor = open(picture())
    val text   = DocumentJson.encodeToString(editor.document, support).getOrElse(fail("nope"))

    text should not include "title"
    text should not include "width"
    text should not include "mediaId"
  }

  // ---------------------------------------------------------------------------------------
  // Decoding is as strict as the command
  // ---------------------------------------------------------------------------------------

  private def decodeWith(src: String): Either[Vector[DecodeError], DecodeResult] =
    val editor = open(picture())
    val text = DocumentJson
      .encodeToString(editor.document, support)
      .getOrElse(fail("nicht kodierbar"))
      .replace("https://example.com/bild.png", src)

    DocumentJson.decodeString(text, editor.document.schema, support)

  "A payload with a data source" should "not decode" in {
    // §20: `data:` ist keine dauerhafte MediaReference -- und eine Quelle aus einem Payload ist
    // genauso ungeprueft wie eine aus einem Dialog.
    decodeWith("data:image/png;base64,AAAA") should matchPattern { case Left(_) => }
  }

  it should "not decode a javascript source" in {
    decodeWith("javascript:alert(1)") should matchPattern { case Left(_) => }
  }

  it should "not decode a protocol-relative source" in {
    decodeWith("//example.com/bild.png") should matchPattern { case Left(_) => }
  }

  it should "not decode plain http under the default profile" in {
    decodeWith("http://example.com/bild.png") should matchPattern { case Left(_) => }
  }

  it should "decode it once the application allows http" in {
    val editor = open(picture())
    val allowing = ImageJsonSupport.support(MediaUrlPolicy.allowingHttp) ++
      JsonSupport.of(paragraphCodec)

    val text = DocumentJson
      .encodeToString(editor.document, support)
      .getOrElse(fail("nicht kodierbar"))
      .replace("https://example.com/bild.png", "http://example.com/bild.png")

    DocumentJson.decodeString(text, editor.document.schema, allowing) should
      matchPattern { case Right(_) => }
  }

  "A payload with an impossible size" should "not decode" in {
    // §19.2 verlangt die Pruefung von Zahlenbereichen. Ein stilles Wegwerfen erzeugte ein
    // Dokument, das vom Payload abweicht, ohne es zu sagen.
    val editor = open(picture(width = Some(640)))
    val text = DocumentJson
      .encodeToString(editor.document, support)
      .getOrElse(fail("nicht kodierbar"))
      .replace("\"width\":640", "\"width\":0")

    DocumentJson.decodeString(text, editor.document.schema, support) should
      matchPattern { case Left(_) => }
  }

  it should "not decode a fractional size" in {
    val editor = open(picture(width = Some(640)))
    val text = DocumentJson
      .encodeToString(editor.document, support)
      .getOrElse(fail("nicht kodierbar"))
      .replace("\"width\":640", "\"width\":1.5")

    DocumentJson.decodeString(text, editor.document.schema, support) should
      matchPattern { case Left(_) => }
  }
}

package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.image.*
import ember.editor.json.*
import ember.editor.link.*
import ember.editor.list.{ListExtension, ListItemNode, ListNode, ListKind as DocumentListKind}
import ember.editor.markdown.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The built-in JSON codecs (P18).
  *
  * ==Why a round trip and not an assertion on the payload==
  *
  * §19.2 asks for a versioned, lossless wire format. A test that spells out the expected JSON
  * proves that '''this''' encoder writes what the test author expected; a round trip proves the
  * encoder and the decoder agree, which is the property a stored document actually depends on.
  *
  * The payload is asserted in exactly the places where its shape is a decision -- an omitted
  * field, a refused value -- and nowhere else.
  */
final class StandardJsonRoundTripSpec extends AnyFlatSpec with Matchers {

  private val root  = NodeId("root")
  private val links = LinkUrlPolicy.default
  private val media = MediaUrlPolicy.default

  private val support = StandardJsonSupport.everything(links, media)

  private val schema: Schema =
    ExtensionResolver
      .resolve(
        Vector(
          RichText(NodeIdGenerator.sequential("x")),
          ListExtension(NodeIdGenerator.sequential("y")),
          LinkExtension(NodeIdGenerator.sequential("z")),
          CodeExtension(NodeIdGenerator.sequential("c")),
          ImageExtension(NodeIdGenerator.sequential("i"), media)
        )
      )
      .map(_.schema)
      .getOrElse(fail("Extensions nicht aufloesbar"))

  /** A document built from Markdown -- the shortest way to a realistic one. */
  private def fromMarkdown(source: String): Document =
    MarkdownCodec
      .decode(
        source,
        schema,
        MarkdownSupports.everything(links, media),
        NodeIdGenerator.sequential("m"),
        root
      )
      .map(_.document)
      .getOrElse(fail(s"Markdown nicht dekodierbar: $source"))

  private def written(document: Document, using: JsonSupport = support): String =
    DocumentJson.encodeToString(document, using).getOrElse(fail("nicht kodierbar"))

  private def roundTrip(document: Document): Document =
    DocumentJson
      .decodeString(written(document), schema, support)
      .map(_.document)
      .getOrElse(fail(s"nicht dekodierbar: ${written(document)}"))

  private def survives(source: String): Unit =
    val original = fromMarkdown(source)
    withClue(s"\nJSON:\n${written(original)}\n") { roundTrip(original) shouldBe original }

  // ---------------------------------------------------------------------------------------
  // Round-Trips
  // ---------------------------------------------------------------------------------------

  "A paragraph" should "survive" in {
    survives("Ein Absatz.\n")
  }

  "A heading" should "survive with its level" in {
    survives("# eins\n\n###### sechs\n")
  }

  "A quote" should "survive with its blocks" in {
    survives("> eins\n>\n> zwei\n")
  }

  "Marks" should "survive, including several on one run" in {
    survives("*kursiv* **fett** ***beides*** `code`\n")
  }

  "A hard break" should "survive as a hard break" in {
    survives("eins\\\nzwei\n")
  }

  "A thematic break" should "survive" in {
    survives("---\n")
  }

  "A list" should "survive with its kind, start number and tightness" in {
    survives("- eins\n- zwei\n")
    survives("5. fuenf\n\n6. sechs\n")
  }

  "A nested list" should "survive" in {
    survives("- aussen\n  - innen\n")
  }

  "A link" should "survive with target and title" in {
    survives("""[Text](/ziel "Titel")""" + "\n")
  }

  "An image" should "survive" in {
    survives("""![Alt](/b.png "Titel")""" + "\n")
  }

  "A code block" should "survive with its language" in {
    survives("```scala\nval x = 1\n\nval y = 2\n```\n")
  }

  it should "survive an info string that names no language" in {
    survives("```nicht-echt!\ncode\n```\n")
  }

  "A whole document" should "survive at once" in {
    survives(
      "# Titel\n\nEin Absatz mit *kursiv*, `code` und [einem Link](/z).\n\n" +
        "> Ein Zitat\n\n- eins\n- zwei\n\n```scala\nval x = 1\n```\n\n---\n\n![Bild](/b.png)\n"
    )
  }

  // ---------------------------------------------------------------------------------------
  // Was der Payload sagt, und was nicht
  // ---------------------------------------------------------------------------------------

  "A list" should "write no start number when it means nothing" in {
    // Eine `"start":1` in jeder Aufzaehlung waere Rauschen ohne Aussage -- dieselbe Ueberlegung
    // wie beim leeren `marks` im Kern.
    written(fromMarkdown("- eins\n")) should not include "start"
  }

  it should "write one when it does" in {
    written(fromMarkdown("5. fuenf\n")) should include("\"start\":5")
  }

  "A code block" should "write no language field when there is none" in {
    written(fromMarkdown("```\ncode\n```\n")) should not include "language"
  }

  "A link without a title" should "write no title field" in {
    written(fromMarkdown("[t](/z)\n")) should not include "title"
  }

  // ---------------------------------------------------------------------------------------
  // Dekodieren ist so streng wie der Command
  // ---------------------------------------------------------------------------------------

  private def decodeWith(source: String, replace: (String, String)): Either[Vector[DecodeError], DecodeResult] =
    val text = written(fromMarkdown(source)).replace(replace._1, replace._2)
    DocumentJson.decodeString(text, schema, support)

  "A payload with a javascript link" should "not decode" in {
    // §19.1: dieselbe Policy wie der Command, und es gibt nur eine Tuer zu einem `LinkUrl`.
    decodeWith("[t](/z)\n", "\"/z\"" -> "\"javascript:alert(1)\"") should matchPattern {
      case Left(_) =>
    }
  }

  "A payload with a data image source" should "not decode" in {
    decodeWith("![a](/b.png)\n", "\"/b.png\"" -> "\"data:image/png;base64,AAA\"") should
      matchPattern { case Left(_) => }
  }

  "A payload with an impossible heading level" should "not decode" in {
    // §19.2 verlangt die Pruefung von Zahlenbereichen. Auf H6 zu klemmen erzeugte ein Dokument,
    // das vom Payload abweicht, ohne es zu sagen.
    decodeWith("# t\n", "\"level\":1" -> "\"level\":9") should matchPattern { case Left(_) => }
  }

  "A payload with an unknown list kind" should "not decode" in {
    decodeWith("- a\n", "\"unordered\"" -> "\"triangular\"") should matchPattern { case Left(_) => }
  }

  "A payload with an unknown break kind" should "not decode" in {
    decodeWith("a\\\nb\n", "\"hard\"" -> "\"diagonal\"") should matchPattern { case Left(_) => }
  }

  "A payload with an invalid language" should "not decode" in {
    decodeWith("```scala\nx\n```\n", "\"scala\"" -> "\"mit leerzeichen\"") should
      matchPattern { case Left(_) => }
  }

  // ---------------------------------------------------------------------------------------
  // Einzeln waehlbar
  // ---------------------------------------------------------------------------------------

  "A profile without the underline codec" should "refuse a document that carries one" in {
    // §6: getrennt waehlbar heisst auch, dass ein Profil ohne Unterstreichung keine lesen kann.
    val withoutUnderline = (CoreJsonSupport.all ++ JsonSupport.of(StandardJsonCodecs.paragraph))
      .withMarks(MarkSupport.of(StandardJsonCodecs.strong))

    val document = Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("p"))),
        ParagraphNode(NodeId("p"), Vector(NodeId("t"))),
        TextNode(NodeId("t"), "Text", MarkSet.of(StandardMarks.Underline))
      )
    )

    DocumentJson.encodeToString(document, withoutUnderline) should matchPattern { case Left(_) => }
  }

  it should "be enough for a document that does not" in {
    val minimal = (CoreJsonSupport.all ++ JsonSupport.of(StandardJsonCodecs.paragraph))
      .withMarks(MarkSupport.of(StandardJsonCodecs.strong))

    val document = Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("p"))),
        ParagraphNode(NodeId("p"), Vector(NodeId("t"))),
        TextNode(NodeId("t"), "Text", MarkSet.of(StandardMarks.Strong))
      )
    )

    DocumentJson.encodeToString(document, minimal) should matchPattern { case Right(_) => }
  }

  "The rich-text bundle" should "not require list, link, code or image codecs" in {
    // Die Abnahmezeile von §6 in einem Test: wer nur Absaetze braucht, linkt nur Absaetze.
    val document = fromMarkdown("# Titel\n\nEin *Absatz*.\n")

    DocumentJson.encodeToString(document, StandardJsonSupport.richText) should
      matchPattern { case Right(_) => }
  }

  it should "refuse a document with a list" in {
    DocumentJson.encodeToString(fromMarkdown("- eins\n"), StandardJsonSupport.richText) should
      matchPattern { case Left(_) => }
  }
}

package ember.editor.json

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Der Persistenznachweis aus P10 (Architektur §19.2). */
final class DocumentJsonSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  private val schema =
    Schema.unsafe(RootNode, TextNode, BlockNode, BrokenNode)

  private val support =
    (CoreJsonSupport.all ++ JsonSupport.of(TestCodecs.block, TestCodecs.broken))
      .withMarks(MarkSupport.of(TestCodecs.highlight, TestCodecs.marker))

  /** Dieselben Knotencodecs, aber ohne Markierungscodecs. */
  private val withoutMarks =
    CoreJsonSupport.all ++ JsonSupport.of(TestCodecs.block, TestCodecs.broken)

  /** `root > b0 > t0, t1` */
  private def sample(
      first: TextNode = TextNode(NodeId("t0"), "Hallo"),
      second: TextNode = TextNode(NodeId("t1"), "Welt")
  ): Document =
    Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("b0"))),
        BlockNode(NodeId("b0"), Vector(first.id, second.id), "Abschnitt"),
        first,
        second
      )
    )

  private def encoded(document: Document = sample()): String =
    DocumentJson.encodeToString(document, support) match
      case Right(text) => text
      case Left(errors) => fail(errors.map(_.render).mkString("; "))

  private def decode(
      source: String,
      config: DecodeConfig = DecodeConfig()
  ): Either[Vector[DecodeError], DecodeResult] =
    DocumentJson.decodeString(source, schema, support, config)

  private def decoded(source: String, config: DecodeConfig = DecodeConfig()): DecodeResult =
    decode(source, config) match
      case Right(result) => result
      case Left(errors)  => fail(errors.map(_.render).mkString("; "))

  private def failure(source: String, config: DecodeConfig = DecodeConfig()): Vector[DecodeError] =
    decode(source, config) match
      case Left(errors)  => errors
      case Right(result) => fail(s"unerwartet angenommen: $result")

  // ---------------------------------------------------------------------------------------
  // Roundtrip
  // ---------------------------------------------------------------------------------------

  "A document" should "survive a roundtrip unchanged" in {
    val original = sample()

    decoded(encoded(original)).document shouldBe original
  }

  it should "keep every node id" in {
    // §19.2: "IDs werden fuer Persistenz/SSR erhalten." Daran haengt die Hydration -- ein
    // remappter Server-Snapshot passte zu keinem Browserstand mehr.
    val restored = decoded(encoded()).document

    restored.ids.toVector.map(_.value).sorted shouldBe Vector("b0", "root", "t0", "t1")
    restored.rootId shouldBe root
  }

  it should "encode deterministically" in {
    // Ohne das sind Roundtrip-Fixtures und ein Vergleich zwischen Server- und Browserstand
    // wertlos.
    encoded() shouldBe encoded()
  }

  it should "write its nodes in document order" in {
    val text = encoded()

    text.indexOf("\"root\"") should be < text.indexOf("\"b0\"")
    text.indexOf("\"b0\"") should be < text.indexOf("\"t0\"")
    text.indexOf("\"t0\"") should be < text.indexOf("\"t1\"")
  }

  it should "carry neither selection nor history" in {
    // §9: was gespeichert wird, ist das Dokument -- Auswahl, History und View-State sind
    // Zustand einer Bearbeitung (§5).
    val text = encoded()

    text should not include "selection"
    text should not include "history"
    text should not include "revision"
  }

  it should "name format, both versions and the root" in {
    val text = encoded()

    text should include("\"format\":\"ember-document\"")
    text should include("\"formatVersion\":1")
    text should include("\"schemaVersion\":1")
    text should include("\"root\":\"root\"")
  }

  // ---------------------------------------------------------------------------------------
  // Markierungen
  // ---------------------------------------------------------------------------------------

  "Marks" should "survive a roundtrip" in {
    val marked = TextNode(NodeId("t0"), "Hallo", MarkSet.of(Highlight("gelb"), Marker))
    val original = sample(first = marked)

    val restored = decoded(encoded(original)).document
    restored.node(NodeId("t0")) shouldBe Some(marked)
  }

  it should "stay out of the payload when there are none" in {
    encoded() should not include "marks"
  }

  it should "be rejected when no codec knows them" in {
    val marked = sample(first = TextNode(NodeId("t0"), "Hallo", MarkSet.of(Highlight("gelb"))))
    val text   = encoded(marked)

    val bare = DocumentJson.decodeString(text, schema, withoutMarks, DecodeConfig())

    inside(bare) { case Left(Vector(error: DecodeError.UnknownMark)) =>
      error.markId shouldBe "test.highlight/1"
    }
  }

  it should "fail to encode without a codec" in {
    val marked = sample(first = TextNode(NodeId("t0"), "Hallo", MarkSet.of(Highlight("gelb"))))

    val outcome = DocumentJson.encode(marked, withoutMarks)

    inside(outcome) { case Left(Vector(error: EncodeError.NoMarkCodec)) =>
      error.nodeId shouldBe NodeId("t0")
    }
  }

  // ---------------------------------------------------------------------------------------
  // Ungueltige Eingaben
  // ---------------------------------------------------------------------------------------

  "Invalid JSON" should "be reported as such" in {
    failure("{nicht wirklich json") should matchPattern {
      case Vector(_: DecodeError.MalformedJson) =>
    }
  }

  "A foreign format" should "be rejected by name" in {
    val text = encoded().replace("ember-document", "something-else")

    inside(failure(text)) { case Vector(error: DecodeError.UnknownFormat) =>
      error.found shouldBe "something-else"
    }
  }

  it should "be rejected by version" in {
    val text = encoded().replace("\"formatVersion\":1", "\"formatVersion\":2")

    inside(failure(text)) { case Vector(error: DecodeError.UnsupportedFormatVersion) =>
      error.found shouldBe 2
      error.supported shouldBe 1
    }
  }

  "A wrong type" should "be reported with its path" in {
    val text = encoded().replace("\"nodes\":[", "\"nodes\":{\"a\":[")
      .replace("}]}", "}]}}")

    inside(failure(text)) { case Vector(error: DecodeError.TypeMismatch) =>
      error.expected shouldBe "array"
      error.path.render shouldBe "<root>.nodes"
    }
  }

  it should "reject a number where an integer belongs" in {
    val text = encoded().replace("\"formatVersion\":1", "\"formatVersion\":1.5")

    inside(failure(text)) { case Vector(error: DecodeError.InvalidValue) =>
      error.path.render shouldBe "<root>.formatVersion"
    }
  }

  "A duplicate node id" should "be an error" in {
    // Der Grund, warum die Knotenliste ein Array ist: als Objekt haette `js.JSON.parse` den
    // zweiten Eintrag ueber den ersten geschrieben, und das Dokument saehe gueltig aus.
    val text = encoded().replace("\"id\":\"t1\"", "\"id\":\"t0\"")

    inside(failure(text)) { case Vector(error: DecodeError.DuplicateNodeId) =>
      error.nodeId shouldBe "t0"
      error.path.render should include("#t0")
    }
  }

  "A duplicate object key" should "collapse to its last value" in {
    // Was `js.JSON.parse` zusammenfasst, kann dieses Modul nicht mehr sehen. Der Test haelt
    // die dokumentierte Regel fest, statt eine Pruefung zu behaupten, die es nicht gibt.
    val parsed = JsonText.parse("""{"a":1,"a":2}""", DecodeLimits.default)

    inside(parsed) { case Right(value: JsonValue.Obj) =>
      value.get("a") shouldBe Some(JsonValue.Num(2))
      value.fields.length shouldBe 1
    }
  }

  "A broken reference" should "be reported by the core validator" in {
    val text = encoded().replace("\"t1\"]", "\"fehlt\"]")

    inside(failure(text)) { case Vector(error: DecodeError.InvalidDocument) =>
      error.violations should not be empty
    }
  }

  "A leaf with children" should "be rejected" in {
    val text = encoded().replace(
      "\"type\":\"ember.core.text/1\",\"codecVersion\":1,\"text\":\"Welt\"",
      "\"type\":\"ember.core.text/1\",\"codecVersion\":1,\"text\":\"Welt\",\"children\":[\"x\"]"
    )

    inside(failure(text)) { case Vector(error: DecodeError.InvalidValue) =>
      error.message should include("Blatt")
    }
  }

  "An element without an element descriptor" should "be reported, not silently flattened" in {
    // Von Hand geschrieben: ein solches Dokument laesst sich gar nicht erst bauen -- der
    // `DocumentValidator` fuehrt den Fall als `MissingElementDescriptor` (§8.1). Genau deshalb
    // muss das Dekodieren ihn melden, statt die Kindliste stillschweigend fallen zu lassen.
    val text =
      """{"format":"ember-document","formatVersion":1,"schemaVersion":1,"root":"root","nodes":[""" +
        """{"id":"root","type":"ember.core.root/1","codecVersion":1,"children":["x"]},""" +
        """{"id":"x","type":"test.broken/1","codecVersion":1,"children":["y"]},""" +
        """{"id":"y","type":"ember.core.text/1","codecVersion":1,"text":"a"}]}"""

    inside(failure(text)) { case Vector(error: DecodeError.InvalidValue) =>
      error.message should include("ElementNodeType")
    }
  }

  it should "report every node error at once" in {
    // Wer einen fremden Payload debuggt, will nicht zwanzig Laeufe fuer zwanzig Tippfehler.
    val text = encoded()
      .replace("\"type\":\"ember.core.text/1\",\"codecVersion\":1,\"text\":\"Hallo\"",
        "\"type\":\"fremd.a/1\",\"codecVersion\":1")
      .replace("\"type\":\"ember.core.text/1\",\"codecVersion\":1,\"text\":\"Welt\"",
        "\"type\":\"fremd.b/1\",\"codecVersion\":1")

    failure(text) should have length 2
  }

  // ---------------------------------------------------------------------------------------
  // Unbekannte Knoten
  // ---------------------------------------------------------------------------------------

  private def withUnknown: String =
    encoded().replace("\"type\":\"test.block/1\"", "\"type\":\"fremd.section/2\"")

  "An unknown node type" should "be rejected by default" in {
    inside(failure(withUnknown)) { case Vector(error: DecodeError.UnknownNodeType) =>
      error.typeId shouldBe "fremd.section/2"
      error.path.render should include("#b0")
    }
  }

  it should "be preserved with an explicit policy" in {
    val result = decoded(withUnknown, DecodeConfig(unknownNodes = UnknownNodePolicy.Preserve))

    inside(result.document.node(NodeId("b0"))) { case Some(node: UnsupportedNode) =>
      node.typeId.value shouldBe "fremd.section/2"
      node.payload.get("label") shouldBe Some(JsonValue.Str("Abschnitt"))
      node.children shouldBe Vector(NodeId("t0"), NodeId("t1"))
    }
    result.diagnostics should have length 1
  }

  it should "keep its children reachable" in {
    // Ohne Kindliste am UnsupportedNode waeren die bekannten Textlaeufe darunter unerreichbar,
    // und der Validator lehnte das Dokument ab -- die Erhaltung haette zerstoert, wozu es sie
    // gibt.
    val result = decoded(withUnknown, DecodeConfig(unknownNodes = UnknownNodePolicy.Preserve))

    result.document.size shouldBe 4
    result.document.node(NodeId("t0")) shouldBe Some(TextNode(NodeId("t0"), "Hallo"))
  }

  it should "round-trip byte-identically" in {
    val config = DecodeConfig(unknownNodes = UnknownNodePolicy.Preserve)
    val once   = decoded(withUnknown, config).document

    DocumentJson.encodeToString(once, support) shouldBe Right(withUnknown)
  }

  it should "offer a text fallback, never markup" in {
    val text = encoded().replace(
      "\"type\":\"ember.core.text/1\",\"codecVersion\":1,\"text\":\"Hallo\"",
      "\"type\":\"fremd.note/1\",\"codecVersion\":1,\"text\":\"<b>fett</b>\""
    )

    val result = decoded(text, DecodeConfig(unknownNodes = UnknownNodePolicy.Preserve))

    inside(result.document.node(NodeId("t0"))) { case Some(node: UnsupportedNode) =>
      node.fallbackText shouldBe "<b>fett</b>"
    }
  }

  "A known type without a codec" should "be an error even under Preserve" in {
    // Ein Verdrahtungsfehler der Anwendung, kein unbekanntes Datum. Ihn zu erhalten hiesse, ein
    // Dokument als fremd auszugeben, das dieser Editor sehr wohl versteht.
    val outcome = DocumentJson.decodeString(
      encoded(),
      schema,
      CoreJsonSupport.all,
      DecodeConfig(unknownNodes = UnknownNodePolicy.Preserve)
    )

    inside(outcome) { case Left(Vector(error: DecodeError.NoCodec)) =>
      error.typeId shouldBe "test.block/1"
    }
  }

  // ---------------------------------------------------------------------------------------
  // Codec-Versionen
  // ---------------------------------------------------------------------------------------

  "A codec version" should "be independent of the format version" in {
    val versioned = JsonSupport.of(CoreJsonSupport.root, CoreJsonSupport.text, TestCodecs.blockV2)
    val document  = sample()

    val text = DocumentJson.encodeToString(document, versioned).getOrElse(fail("nicht kodierbar"))
    text should include("\"formatVersion\":1")
    text should include("\"type\":\"test.block/1\",\"codecVersion\":2")

    DocumentJson.decodeString(text, schema, versioned, DecodeConfig())
      .map(_.document) shouldBe Right(document)
  }

  it should "let a codec read an older payload" in {
    val versioned = JsonSupport.of(CoreJsonSupport.root, CoreJsonSupport.text, TestCodecs.blockV2)
    val old = encoded()
      .replace("\"codecVersion\":1,\"label\"", "\"codecVersion\":1,\"caption\"")

    val result = DocumentJson.decodeString(old, schema, versioned, DecodeConfig())

    inside(result) { case Right(DecodeResult(document, _)) =>
      document.node(NodeId("b0")) shouldBe Some(BlockNode(NodeId("b0"),
        Vector(NodeId("t0"), NodeId("t1")), "Abschnitt"))
    }
  }

  it should "refuse a newer payload" in {
    // Ein neuerer Stand kann Felder tragen, deren Bedeutung dieser Codec nicht kennt. Ihn als
    // alten zu lesen waere stiller Datenverlust.
    val text = encoded().replace("\"type\":\"test.block/1\",\"codecVersion\":1",
      "\"type\":\"test.block/1\",\"codecVersion\":7")

    inside(failure(text)) { case Vector(error: DecodeError.UnsupportedCodecVersion) =>
      error.found shouldBe 7
      error.supported shouldBe 1
    }
  }

  // ---------------------------------------------------------------------------------------
  // Grenzen
  // ---------------------------------------------------------------------------------------

  "The limits" should "cap the source length before parsing" in {
    inside(failure(encoded(), DecodeConfig(limits = DecodeLimits(maxSourceChars = 10)))) {
      case Vector(error: DecodeError.LimitExceeded) => error.limit shouldBe "maxSourceChars"
    }
  }

  it should "cap the node count" in {
    inside(failure(encoded(), DecodeConfig(limits = DecodeLimits(maxNodes = 2)))) {
      case Vector(error: DecodeError.LimitExceeded) => error.limit shouldBe "maxNodes"
    }
  }

  it should "cap the child count" in {
    inside(failure(encoded(), DecodeConfig(limits = DecodeLimits(maxChildren = 1)))) {
      case Vector(error: DecodeError.LimitExceeded) => error.limit shouldBe "maxChildren"
    }
  }

  it should "cap the text length" in {
    // Beide Textlaeufe sind zu lang, und beide werden gemeldet -- ein Payload wird vollstaendig
    // beurteilt, nicht bis zum ersten Befund.
    val errors = failure(encoded(), DecodeConfig(limits = DecodeLimits(maxTextChars = 2)))

    errors should have length 2
    errors.foreach {
      case exceeded: DecodeError.LimitExceeded => exceeded.limit shouldBe "maxTextChars"
      case other                               => fail(s"unerwartet: $other")
    }
  }

  it should "cap the document depth" in {
    inside(failure(encoded(), DecodeConfig(limits = DecodeLimits(maxDocumentDepth = 2)))) {
      case Vector(error: DecodeError.LimitExceeded) => error.limit shouldBe "maxDocumentDepth"
    }
  }

  it should "cap the json depth" in {
    val deep = (1 to 40).foldLeft("1")((inner, _) => s"[$inner]")

    inside(JsonText.parse(deep, DecodeLimits(maxJsonDepth = 8))) {
      case Left(error: DecodeError.LimitExceeded) => error.limit shouldBe "maxJsonDepth"
    }
  }

  // ---------------------------------------------------------------------------------------
  // Einbettbarkeit
  // ---------------------------------------------------------------------------------------

  "The output" should "survive inside a script tag" in {
    // §16: der Payload landet spaeter in einem `<script>`. Ein `</script>` im Text wuerde ihn
    // dort beenden -- ein Skript-Injektionsweg, der nichts mit dem Editor zu tun haette.
    val document = sample(first = TextNode(NodeId("t0"), "</script><script>alert(1)</script>"))
    val text     = encoded(document)

    text should not include "<"
    text should not include ">"
    text should include("\\u003c/script")
    decoded(text).document shouldBe document
  }

  it should "escape the javascript line separators" in {
    val document = sample(first = TextNode(NodeId("t0"), "a\u2028b\u2029c"))
    val text     = encoded(document)

    text should include("\\u2028")
    text should include("\\u2029")
    decoded(text).document shouldBe document
  }

  it should "render whole numbers without a fraction" in {
    JsonText.render(JsonValue.num(1)) shouldBe "1"
    JsonText.render(JsonValue.Num(1.5)) shouldBe "1.5"
  }

  private def inside[A](value: A)(check: PartialFunction[A, Any]): Unit =
    if check.isDefinedAt(value) then check(value): Unit
    else fail(s"unerwartet: $value")
}

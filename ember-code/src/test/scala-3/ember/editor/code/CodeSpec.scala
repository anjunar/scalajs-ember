package ember.editor.code

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.richtext.StandardMarks.Strong
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Code as a document feature, independent of highlighting (§8.2, §18.2). */
final class CodeSpec extends AnyFlatSpec with Matchers {

  private final class Fixture(paragraphs: String*):
    val generator: NodeIdGenerator = NodeIdGenerator.sequential("n")

    private val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), CodeExtension(generator)))
      .getOrElse(throw new AssertionError("Extensions nicht aufloesbar"))

    val root: NodeId = NodeId("root")

    private val blocks =
      (if paragraphs.isEmpty then Vector("") else paragraphs.toVector).zipWithIndex

    val session: EditorSession = EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          root,
          RootNode(root, blocks.map((_, index) => NodeId(s"p$index"))) +:
            blocks.flatMap { (content, index) =>
              Vector(
                ParagraphNode(NodeId(s"p$index"), Vector(NodeId(s"t$index"))),
                TextNode(NodeId(s"t$index"), content)
              )
            }
        ),
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(throw new AssertionError("Sitzung nicht erzeugbar"))

    def document: Document = session.document

    /** The document as `code(info)"…"` and `p"…"`, which is what a code test is about. */
    def outline: Vector[String] =
      document.childrenOf(root).flatMap { id =>
        document.node(id) match
          case Some(code: CodeBlockNode) =>
            val info = code.info.render
            val tag  = if info.isEmpty then "code" else s"code($info)"
            Vector(s"""$tag"${CodeBlockNode.textOf(code, document)}"""")
          case Some(element: ElementNode) => Vector(s"""p"${textOf(id)}"""")
          case _                          => Vector.empty
      }

    def textOf(id: NodeId): String = document.node(id) match
      case Some(run: TextNode)        => run.text
      case Some(element: ElementNode) => element.children.map(textOf).mkString
      case _                          => ""

    def codeBlock: Option[CodeBlockNode] =
      document.inDocumentOrder.collectFirst { case code: CodeBlockNode => code }

    def codeRun: Option[TextNode] =
      codeBlock
        .flatMap(_.children.headOption)
        .flatMap(document.node)
        .collect { case run: TextNode => run }

    def caret: Option[(String, Int)] =
      session.selection
        .collect { case range: RangeSelection if range.isCollapsed => range.focus }
        .collect { case Point.Text(node, offset, _) => (node.value, offset) }

    def edit(body: Transaction => Unit): Unit =
      session.update(body) match
        case Right(_)    => ()
        case Left(error) => throw new AssertionError(s"abgewiesen: ${error.render}")

    def caretAt(node: String, offset: Int): Unit =
      edit(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), offset))): Unit)

    /** Puts the caret at `offset` inside the code block's run. */
    def caretInCode(offset: Int): Unit =
      codeRun.foreach(run => caretAt(run.id.value, offset))

    def dispatch[A](command: EditorCommand[A], payload: A): Boolean =
      session.dispatch(command, payload) match
        case Right(outcome) => outcome.wasHandled
        case Left(error)    => throw new AssertionError(s"abgewiesen: ${error.render}")

    def dispatch(command: EditorCommand[Unit]): Boolean = dispatch(command, ())

    def toCode(info: CodeInfo = CodeInfo.empty): Boolean =
      dispatch(CodeCommands.ToggleCodeBlock, info)

    def enter(): Boolean = dispatch(RichText.InsertParagraph)

  private def coded(text: String = "val x = 1"): Fixture =
    val f = new Fixture(text)
    f.caretAt("t0", 0)
    f.toCode(CodeInfo.of("scala")): Unit
    f

  // ---------------------------------------------------------------------------------------
  // Info strings
  // ---------------------------------------------------------------------------------------

  "CodeInfo" should "split the first word off as the language" in {
    // §18.2: "Info-/Sprachmetadaten typisiert behandeln."
    val info = CodeInfo.parse("scala {highlight=3-5}")

    info.language.map(_.value) shouldBe Some("scala")
    info.meta shouldBe Some("{highlight=3-5}")
  }

  it should "round-trip an info string" in {
    CodeInfo.parse("scala {a=1}").render shouldBe "scala {a=1}"
    CodeInfo.parse("scala").render shouldBe "scala"
    CodeInfo.parse("").render shouldBe ""
  }

  it should "keep an info string that names no valid language" in {
    // Kein Fehler, sondern ein Info-String ohne Sprache. Ihn wegzuwerfen waere Verlust (§18.2).
    val info = CodeInfo.parse("`nicht erlaubt`")

    info.language shouldBe None
    info.meta shouldBe Some("`nicht erlaubt`")
    info.render shouldBe "`nicht erlaubt`"
  }

  "CodeLanguage" should "refuse what a fence cannot write" in {
    // Ein Backtick im Info-String eines Backtick-Fence ist nach CommonMark verboten; ein Wert,
    // der sich nicht zurueckschreiben laesst, gehoert nicht ins Dokument.
    CodeLanguage.parse("scala") should not be empty
    CodeLanguage.parse("") shouldBe None
    CodeLanguage.parse("zwei woerter") shouldBe None
    CodeLanguage.parse("mit`backtick") shouldBe None
  }

  // ---------------------------------------------------------------------------------------
  // Converting
  // ---------------------------------------------------------------------------------------

  "ToggleCodeBlock" should "turn the paragraph at the caret into code" in {
    val f = coded()

    f.outline shouldBe Vector("code(scala)\"val x = 1\"")
  }

  it should "keep the block id" in {
    // `Replace` erhaelt Identitaet und Kinder (§10) -- jeder Punkt im Block ueberlebt.
    val f = new Fixture("val x = 1")
    f.caretAt("t0", 0)

    f.toCode(): Unit

    f.codeBlock.map(_.id) shouldBe Some(NodeId("p0"))
  }

  it should "turn it back into a paragraph" in {
    val f = coded()
    f.caretInCode(0)

    f.toCode() shouldBe true

    f.outline shouldBe Vector("p\"val x = 1\"")
  }

  it should "make one paragraph per line" in {
    // Ein `\n` in einem Absatzlauf waere ein Dokument, das kein Renderer richtig zeigt: HTML
    // macht daraus ein Leerzeichen, und der Inhalt aenderte still seine Bedeutung.
    val f = coded("eins\nzwei\ndrei")
    f.caretInCode(0)

    f.toCode(): Unit

    f.outline shouldBe Vector("p\"eins\"", "p\"zwei\"", "p\"drei\"")
  }

  "SetCodeInfo" should "change the language" in {
    val f = coded()
    f.caretInCode(0)

    f.dispatch(CodeCommands.SetCodeInfo, CodeInfo.of("rust")) shouldBe true

    f.codeBlock.flatMap(_.info.language).map(_.value) shouldBe Some("rust")
  }

  it should "pass outside a code block" in {
    val f = new Fixture("Text")
    f.caretAt("t0", 0)

    f.dispatch(CodeCommands.SetCodeInfo, CodeInfo.of("rust")) shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // The content model
  // ---------------------------------------------------------------------------------------

  "A code block" should "hold exactly one run" in {
    val f = coded()

    f.codeBlock.map(_.children.length) shouldBe Some(1)
  }

  it should "collapse several runs into one" in {
    val f = coded("eins")

    f.edit(_.insert(f.codeBlock.get.id, 1, TextNode(NodeId("zwei"), "zwei")): Unit)

    // Ohne Trenner: zwei Laeufe nebeneinander waren eine Zeile. Ein Umbruch dazwischen waere
    // einer, den niemand getippt hat.
    f.codeBlock.map(_.children.length) shouldBe Some(1)
    f.outline shouldBe Vector("code(scala)\"einszwei\"")
  }

  it should "keep a pasted block on its own line" in {
    // Zwei Absaetze, die hineinwandern, waren zwei Zeilen. Sie zusammenzuziehen verlöre eine.
    val f = coded("eins")

    f.edit(
      _.insert(
        f.codeBlock.get.id,
        1,
        ParagraphNode(NodeId("zeile"), Vector(NodeId("zt"))),
        Vector(TextNode(NodeId("zt"), "zwei"))
      ): Unit
    )

    f.outline shouldBe Vector("code(scala)\"eins\nzwei\"")
  }

  it should "strip marks" in {
    // Fetter Code ist nichts, was ein Fence schreiben kann.
    val f = coded()
    val run = f.codeRun.getOrElse(fail("kein Lauf"))

    f.edit(_.replace(run.id, run.copy(marks = MarkSet.of(Strong))): Unit)

    f.codeRun.map(_.marks.isEmpty) shouldBe Some(true)
    f.outline shouldBe Vector("code(scala)\"val x = 1\"")
  }

  it should "keep the text of a block pasted into it" in {
    // Inhalt, der in einen Codeblock wandert, soll Code *werden* -- nicht scheitern und nicht
    // verschwinden.
    val f = coded("eins")

    f.edit(
      _.insert(
        f.codeBlock.get.id,
        1,
        ParagraphNode(NodeId("frei"), Vector(NodeId("ft"))),
        Vector(TextNode(NodeId("ft"), "zwei"))
      ): Unit
    )

    f.outline shouldBe Vector("code(scala)\"eins\nzwei\"")
    f.document.node(NodeId("frei")) shouldBe None
  }

  it should "get a run when it has none" in {
    val f = coded()

    f.edit(_.remove(f.codeRun.get.id): Unit)

    f.codeBlock.map(_.children.length) shouldBe Some(1)
    f.outline shouldBe Vector("code(scala)\"\"")
  }

  it should "be a no-op once it is normal" in {
    val f = coded()
    val before = f.session.state.documentRevision

    f.caretInCode(3)

    f.session.state.documentRevision shouldBe before
  }

  // ---------------------------------------------------------------------------------------
  // Content is kept verbatim
  // ---------------------------------------------------------------------------------------

  "The content" should "keep inner blank lines" in {
    // §18.2: "Inhalt einschliesslich innerer Leerzeilen erhalten." Die Grundlage fuer Fences.
    val f = coded("eins\n\n\ndrei")

    f.codeRun.map(_.text) shouldBe Some("eins\n\n\ndrei")
  }

  it should "keep leading and trailing blank lines" in {
    val f = coded("\nmitte\n")

    f.codeRun.map(_.text) shouldBe Some("\nmitte\n")
  }

  it should "keep indentation exactly" in {
    val f = coded("def f():\n    return 1")

    f.codeRun.map(_.text) shouldBe Some("def f():\n    return 1")
  }

  // ---------------------------------------------------------------------------------------
  // Enter
  // ---------------------------------------------------------------------------------------

  "Enter inside code" should "insert a newline" in {
    val f = coded("eins")
    f.caretInCode(4)

    f.enter() shouldBe true

    f.codeRun.map(_.text) shouldBe Some("eins\n")
    f.document.childrenOf(f.root) should have length 1
  }

  it should "leave the caret after it" in {
    val f = coded("eins")
    f.caretInCode(2)

    f.enter(): Unit

    f.caret.map(_._2) shouldBe Some(3)
    f.codeRun.map(_.text) shouldBe Some("ei\nns")
  }

  it should "leave the block on an empty last line" in {
    // Ein Codeblock hat keine Kante, ueber die ein Caret treten koennte -- ohne diese
    // Konvention gaebe es keinen Weg heraus.
    val f = coded("eins")
    f.caretInCode(4)
    f.enter(): Unit

    f.enter() shouldBe true

    f.outline shouldBe Vector("code(scala)\"eins\"", "p\"\"")
  }

  it should "put the caret in the new paragraph" in {
    val f = coded("eins")
    f.caretInCode(4)
    f.enter(): Unit
    f.enter(): Unit

    f.caret.map(_._2) shouldBe Some(0)
    f.caret.map(_._1) should not be f.codeRun.map(_.id.value)
  }

  it should "pass outside a code block" in {
    val f = new Fixture("Text")
    f.caretAt("t0", 2)

    f.enter() shouldBe true

    f.outline shouldBe Vector("p\"Te\"", "p\"xt\"")
  }

  // ---------------------------------------------------------------------------------------
  // Indent
  // ---------------------------------------------------------------------------------------

  "IndentLine" should "add two spaces at the start of the line" in {
    val f = coded("eins\nzwei")
    f.caretInCode(7)

    f.dispatch(CodeCommands.IndentLine) shouldBe true

    f.codeRun.map(_.text) shouldBe Some("eins\n  zwei")
    f.caret.map(_._2) shouldBe Some(9)
  }

  it should "work on the first line" in {
    val f = coded("eins")
    f.caretInCode(2)

    f.dispatch(CodeCommands.IndentLine): Unit

    f.codeRun.map(_.text) shouldBe Some("  eins")
  }

  "OutdentLine" should "remove one unit" in {
    val f = coded("    eins")
    f.caretInCode(6)

    f.dispatch(CodeCommands.OutdentLine): Unit

    f.codeRun.map(_.text) shouldBe Some("  eins")
  }

  it should "remove only what is there" in {
    // Eine um drei Leerzeichen eingerueckte Zeile verliert zwei, nicht drei -- sonst koennte
    // der Befehl einen einzelnen Druck seines Gegenstuecks nicht rueckgaengig machen.
    val f = coded(" eins")
    f.caretInCode(3)

    f.dispatch(CodeCommands.OutdentLine): Unit

    f.codeRun.map(_.text) shouldBe Some("eins")
  }

  it should "do nothing on a line without indentation" in {
    val f = coded("eins")
    f.caretInCode(2)

    f.dispatch(CodeCommands.OutdentLine): Unit

    f.codeRun.map(_.text) shouldBe Some("eins")
  }

  it should "pass outside a code block" in {
    val f = new Fixture("Text")
    f.caretAt("t0", 2)

    f.dispatch(CodeCommands.IndentLine) shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // No highlighter
  // ---------------------------------------------------------------------------------------

  "The module" should "leave the code as one run whatever happens" in {
    // P15, Risiko: "Sichtbares Highlighting darf spaeter keine persistente Mark-Zerlegung jeder
    // Codezeile erzwingen." Das Dokument haelt genau einen unmarkierten Lauf -- ein Highlighter
    // faerbt spaeter in einer Ansicht, nicht im Dokument.
    val f = coded("def f():\n  return 1")
    f.caretInCode(0)
    f.dispatch(CodeCommands.IndentLine): Unit
    f.caretInCode(5)
    f.enter(): Unit

    f.codeBlock.map(_.children.length) shouldBe Some(1)
    f.codeRun.map(_.marks.isEmpty) shouldBe Some(true)
  }

  it should "leave a valid document after every command" in {
    val f = coded("eins\nzwei")
    f.caretInCode(0)
    f.dispatch(CodeCommands.IndentLine): Unit
    f.dispatch(CodeCommands.SetCodeInfo, CodeInfo.of("python")): Unit
    f.caretInCode(0)
    f.toCode(): Unit

    Document.build(
      f.document.schema,
      f.document.rootId,
      f.document.ids.flatMap(f.document.node).toVector
    ) should matchPattern { case Right(_) => }
  }
}

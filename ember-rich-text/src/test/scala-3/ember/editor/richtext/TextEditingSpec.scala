package ember.editor.richtext

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Die erste vollstaendige vertikale Funktion: Text ohne DOM bearbeiten (P06).
  *
  * §24 verlangt fuer den Kern "Invarianten, atomarer Rollback, Revisionen" -- hier kommt die
  * Abnahme der Phase dazu: eine reine Scala.js-Test-App kann Text editieren, die Loeschbefehle
  * verletzen keine Graphemgrenze, und Operationen und Auswahl bleiben gemeinsam gueltig.
  */
final class TextEditingSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  /** Eine Sitzung ueber `root > paragraph* > text*` mit den angegebenen Absatztexten.
    *
    * Fixture-IDs heissen `p0`/`t0`, generierte `g1`, `g2`, ... -- so ist in jeder Zusicherung
    * ablesbar, was vorgegeben war und was der Editor selbst angelegt hat.
    */
  private def open(paragraphs: String*): EditorSession =
    val generator = NodeIdGenerator.sequential("g")
    val resolved  = ExtensionResolver
      .resolve(Vector(RichText(generator)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    val blocks =
      paragraphs.zipWithIndex.map((text, index) => (NodeId(s"p$index"), NodeId(s"t$index"), text))
    val nodes = RootNode(root, blocks.map(_._1).toVector) +:
      blocks.flatMap((block, text, content) =>
        Vector(ParagraphNode(block, Vector(text)), TextNode(text, content))
      )

    val document = Document.unsafe(resolved.schema, root, nodes.toVector)
    EditorSession
      .create(document, resolved, resolved.sessionConfig())
      .getOrElse(fail("Sitzung nicht erzeugbar"))

  /** Der Inhalt je Absatz, Textlaeufe aneinandergehaengt. */
  private def paragraphs(editor: EditorSession): Vector[String] =
    val document = editor.document
    document
      .childrenOf(document.rootId)
      .map(block => document.childrenOf(block).map(textOf(editor, _)).mkString)

  private def textOf(editor: EditorSession, id: NodeId): String =
    editor.document.node(id).collect { case text: TextNode => text.text }.getOrElse("")

  /** Der Caret als `(Knoten, Offset)`, sofern die Auswahl kollabiert ist. */
  private def caret(editor: EditorSession): Option[(String, Int)] =
    editor.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => (node.value, offset) }

  private def place(editor: EditorSession, node: String, offset: Int): Unit =
    editor.update(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), offset)))) match
      case Right(_)    => ()
      case Left(error) => fail(s"Caret nicht setzbar: ${error.render}")

  private def selectRange(editor: EditorSession, range: RangeSelection): Unit =
    editor.update(_.select(range)) match
      case Right(_)    => ()
      case Left(error) => fail(s"Auswahl nicht setzbar: ${error.render}")

  private def run(editor: EditorSession, outcome: Either[UpdateError, DispatchOutcome]): Unit =
    outcome match
      case Right(_)    => ()
      case Left(error) => fail(s"Command abgewiesen: ${error.render}")

  private def type_(editor: EditorSession, text: String): Unit =
    run(editor, editor.dispatch(RichText.InsertText, text))

  private def enter(editor: EditorSession): Unit =
    run(editor, editor.dispatch(RichText.InsertParagraph))

  private def backspace(editor: EditorSession): Unit =
    run(editor, editor.dispatch(RichText.DeleteBackward))

  private def delete(editor: EditorSession): Unit =
    run(editor, editor.dispatch(RichText.DeleteForward))

  /** Unabhaengige Vollvalidierung nach §8.2. */
  private def revalidate(editor: EditorSession): Unit =
    Document.build(
      editor.document.schema,
      editor.document.rootId,
      editor.document.nodes.toVector
    ) match
      case Right(_)         => ()
      case Left(violations) => fail(violations.map(_.render).mkString("ungueltig:\n", "\n", ""))

  // ---------------------------------------------------------------------------------------
  // Einfuegen
  // ---------------------------------------------------------------------------------------

  "Typing at the caret" should "insert and move the caret along" in {
    val editor = open("Hallo")
    place(editor, "t0", 5)

    type_(editor, " Welt")

    paragraphs(editor) shouldBe Vector("Hallo Welt")
    caret(editor) shouldBe Some(("t0", 10))
    revalidate(editor)
  }

  it should "insert in the middle" in {
    val editor = open("Halo")
    place(editor, "t0", 3)

    type_(editor, "l")

    paragraphs(editor) shouldBe Vector("Hallo")
    caret(editor) shouldBe Some(("t0", 4))
  }

  it should "work in an empty document" in {
    // §8.2: Der Kern erlaubt eine leere Wurzel, das Profil macht daraus eine editierbare
    // Flaeche. Ohne Absatz und Textlauf gaebe es keine gueltige Caretposition.
    val generator = NodeIdGenerator.sequential("g")
    val resolved  = ExtensionResolver
      .resolve(Vector(RichText(generator)))
      .getOrElse(fail("Extensions nicht aufloesbar"))
    val document = RichText.emptyDocument(resolved.schema, generator).getOrElse(fail("leer?"))
    val editor   = EditorSession
      .create(document, resolved, resolved.sessionConfig())
      .getOrElse(fail("Sitzung nicht erzeugbar"))

    editor.update(_.setSelection(RichText.caretAtStart(editor.document)))
    type_(editor, "Erstes Zeichen")

    paragraphs(editor) shouldBe Vector("Erstes Zeichen")
    revalidate(editor)
  }

  it should "do nothing without a selection" in {
    val editor = open("Hallo")

    type_(editor, "X")

    paragraphs(editor) shouldBe Vector("Hallo")
    editor.state.revision.value shouldBe 0
  }

  "Typing over a selection" should "replace it" in {
    val editor = open("Hallo Welt")
    selectRange(
      editor,
      RangeSelection(Point.textBefore(NodeId("t0"), 0), Point.textBefore(NodeId("t0"), 5))
    )

    type_(editor, "Servus")

    paragraphs(editor) shouldBe Vector("Servus Welt")
    caret(editor) shouldBe Some(("t0", 6))
  }

  it should "replace a selection spanning several paragraphs" in {
    // Der Mehrnode-Fall: Anfang im ersten Absatz, Ende im dritten. Was dazwischen liegt,
    // verschwindet vollstaendig, und die beiden Raender wachsen zu einem Absatz zusammen.
    val editor = open("Erster Absatz", "Mittlerer", "Letzter Absatz")
    selectRange(
      editor,
      RangeSelection(Point.textBefore(NodeId("t0"), 7), Point.textBefore(NodeId("t2"), 8))
    )

    type_(editor, "X")

    paragraphs(editor) shouldBe Vector("Erster XAbsatz")
    revalidate(editor)
  }

  // ---------------------------------------------------------------------------------------
  // Loeschen: Graphemgrenzen
  // ---------------------------------------------------------------------------------------

  "Backspace" should "remove one character" in {
    val editor = open("Hallo")
    place(editor, "t0", 5)

    backspace(editor)

    paragraphs(editor) shouldBe Vector("Hall")
    caret(editor) shouldBe Some(("t0", 4))
  }

  it should "remove a surrogate pair as a whole" in {
    // Die Akzeptanzzeile der Phase: Loeschbefehle verletzen keine Graphemgrenze. Ein Backspace,
    // der hier eine UTF-16-Einheit entfernt, hinterlaesst einen halben Codepunkt.
    val editor = open("a😀")
    place(editor, "t0", 3)

    backspace(editor)

    paragraphs(editor) shouldBe Vector("a")
  }

  it should "remove a ZWJ emoji sequence as a whole" in {
    val family = "👨‍👩‍👧"
    val editor = open("a" + family)
    place(editor, "t0", 1 + family.length)

    backspace(editor)

    paragraphs(editor) shouldBe Vector("a")
  }

  it should "remove a combining mark together with its base" in {
    // Ausdruecklich als Escape: `e` + COMBINING ACUTE ACCENT, fuenf Zeichen. Als Literal
    // geschrieben waere nicht zu sehen, ob die vorkomponierte oder die zerlegte Schreibweise
    // gemeint ist -- und nur die zerlegte prueft ueberhaupt eine Graphemgrenze.
    val editor = open("café")
    place(editor, "t0", 5)

    backspace(editor)

    paragraphs(editor) shouldBe Vector("caf")
  }

  it should "do nothing at the very beginning of the document" in {
    val editor = open("Hallo")
    place(editor, "t0", 0)

    backspace(editor)

    paragraphs(editor) shouldBe Vector("Hallo")
    editor.state.documentRevision.value shouldBe 0
  }

  "Delete" should "remove the character ahead" in {
    val editor = open("Hallo")
    place(editor, "t0", 0)

    delete(editor)

    paragraphs(editor) shouldBe Vector("allo")
  }

  it should "remove a whole cluster ahead" in {
    val editor = open("😀a")
    place(editor, "t0", 0)

    delete(editor)

    paragraphs(editor) shouldBe Vector("a")
  }

  it should "do nothing at the very end of the document" in {
    val editor = open("Hallo")
    place(editor, "t0", 5)

    delete(editor)

    paragraphs(editor) shouldBe Vector("Hallo")
    editor.state.documentRevision.value shouldBe 0
  }

  // ---------------------------------------------------------------------------------------
  // Loeschen ueber Blockgrenzen
  // ---------------------------------------------------------------------------------------

  "Backspace at the start of a paragraph" should "join it with the one before" in {
    val editor = open("Erster", "Zweiter")
    place(editor, "t1", 0)

    backspace(editor)

    paragraphs(editor) shouldBe Vector("ErsterZweiter")
    caret(editor) shouldBe Some(("t0", 6))
    revalidate(editor)
  }

  it should "leave the caret at the junction" in {
    val editor = open("Erster", "Zweiter")
    place(editor, "t1", 0)

    backspace(editor)
    type_(editor, "-")

    paragraphs(editor) shouldBe Vector("Erster-Zweiter")
  }

  "Delete at the end of a paragraph" should "pull the next one up" in {
    val editor = open("Erster", "Zweiter")
    place(editor, "t0", 6)

    delete(editor)

    paragraphs(editor) shouldBe Vector("ErsterZweiter")
    revalidate(editor)
  }

  // ---------------------------------------------------------------------------------------
  // Absaetze teilen
  // ---------------------------------------------------------------------------------------

  "Enter in the middle" should "split the paragraph" in {
    val editor = open("HalloWelt")
    place(editor, "t0", 5)

    enter(editor)

    paragraphs(editor) shouldBe Vector("Hallo", "Welt")
    caret(editor).map(_._2) shouldBe Some(0)
    revalidate(editor)
  }

  it should "leave the caret in the new paragraph" in {
    val editor = open("HalloWelt")
    place(editor, "t0", 5)

    enter(editor)
    type_(editor, "X")

    paragraphs(editor) shouldBe Vector("Hallo", "XWelt")
  }

  "Enter at the end" should "open an empty paragraph below" in {
    val editor = open("Hallo")
    place(editor, "t0", 5)

    enter(editor)

    paragraphs(editor) shouldBe Vector("Hallo", "")
    revalidate(editor)
  }

  it should "give the empty paragraph a usable caret" in {
    val editor = open("Hallo")
    place(editor, "t0", 5)

    enter(editor)
    type_(editor, "Welt")

    paragraphs(editor) shouldBe Vector("Hallo", "Welt")
  }

  "Enter at the start" should "push an empty paragraph above" in {
    val editor = open("Hallo")
    place(editor, "t0", 0)

    enter(editor)

    paragraphs(editor) shouldBe Vector("", "Hallo")
    revalidate(editor)
  }

  it should "keep the caret with the text that moved down" in {
    val editor = open("Hallo")
    place(editor, "t0", 0)

    enter(editor)
    type_(editor, "X")

    paragraphs(editor) shouldBe Vector("", "XHallo")
  }

  "Enter over a selection" should "delete it first" in {
    val editor = open("HalloWelt")
    selectRange(
      editor,
      RangeSelection(Point.textBefore(NodeId("t0"), 5), Point.textBefore(NodeId("t0"), 9))
    )

    enter(editor)

    paragraphs(editor) shouldBe Vector("Hallo", "")
    revalidate(editor)
  }

  // ---------------------------------------------------------------------------------------
  // Normalisierung
  // ---------------------------------------------------------------------------------------

  "Emptying the document" should "leave an editable paragraph behind" in {
    // Ohne die Normalisierung waere ein leergeloeschtes Dokument nicht mehr bearbeitbar: keine
    // Bloecke, kein Textlauf, keine gueltige Caretposition.
    val editor = open("Hallo")
    selectRange(
      editor,
      RangeSelection(Point.textBefore(NodeId("t0"), 0), Point.textBefore(NodeId("t0"), 5))
    )

    backspace(editor)

    paragraphs(editor) shouldBe Vector("")
    editor.document.childrenOf(root) should have size 1
    revalidate(editor)
  }

  it should "stay editable afterwards" in {
    val editor = open("Hallo")
    selectRange(
      editor,
      RangeSelection(Point.textBefore(NodeId("t0"), 0), Point.textBefore(NodeId("t0"), 5))
    )

    backspace(editor)
    type_(editor, "Neu")

    paragraphs(editor) shouldBe Vector("Neu")
  }

  "Joining two paragraphs" should "not leave an empty text run behind" in {
    // Beim Zusammenfuehren bleibt regelmaessig ein leer gewordener Lauf uebrig. Er ist
    // unsichtbar, verschiebt aber Kindpositionen -- die Normalisierung raeumt ihn weg, solange
    // der Caret nicht darauf steht.
    val editor = open("Erster", "")
    place(editor, "t1", 0)

    backspace(editor)

    val block = editor.document.childrenOf(root).head
    editor.document.childrenOf(block) should have size 1
    revalidate(editor)
  }

  "Normalisation" should "settle without exhausting the transform budget" in {
    // Die beiden Regeln "leerer Block braucht Text" und "leere Laeufe weg" koennten einander
    // ins Gehege kommen. Dass sie es nicht tun, ist der eigentliche Test: waere es so, endete
    // jede Aenderung nach 32 Runden in TransformBudgetExhausted.
    val editor = open("Hallo", "Welt")
    place(editor, "t1", 0)

    backspace(editor)
    place(editor, "t0", 5)
    enter(editor)
    backspace(editor)

    revalidate(editor)
    editor.state.revision.value should be > 0L
  }

  // ---------------------------------------------------------------------------------------
  // Zusammenspiel mit dem Kern
  // ---------------------------------------------------------------------------------------

  "Every edit" should "keep document and selection valid together" in {
    // §10, Akzeptanz aus P04: Index und Auswahl passen zu derselben Revision. Hier ueber eine
    // ganze Folge echter Bearbeitungen statt an einem Einzelfall.
    val editor = open("Hallo Welt")
    place(editor, "t0", 5)

    Vector(
      () => type_(editor, " liebe"),
      () => enter(editor),
      () => type_(editor, "Zweite Zeile"),
      () => backspace(editor),
      () => backspace(editor),
      () => delete(editor),
      () => enter(editor)
    ).foreach { step =>
      step()
      revalidate(editor)
      val selection = editor.selection.getOrElse(fail("Auswahl verloren"))
      SelectionSupport.core.validate(selection, editor.document) shouldBe empty
    }

    // Nachgerechnet, Schritt fuer Schritt: " liebe" bei 5 eingefuegt ergibt
    // "Hallo liebe Welt" mit Caret bei 11. Enter teilt dort, also wandert " Welt" in den
    // zweiten Absatz -- nicht, wie man beim Ueberfliegen vermutet, in den ersten.
    paragraphs(editor) shouldBe Vector("Hallo liebe", "Zweite Zei", "Welt")
  }

  "A whole editing session" should "produce exactly one commit per command" in {
    val editor = open("Hallo")
    place(editor, "t0", 5)
    val before = editor.state.revision.value

    type_(editor, "!")

    editor.state.revision.value shouldBe before + 1
  }

  "The rich text profile" should "run entirely without a DOM" in {
    // Die Akzeptanzzeile der Phase. Dass diese Suite ueberhaupt laeuft, ist der Beleg -- der
    // Grenz-Lint des Moduls verbietet `org.scalajs.dom` ohnehin beim Compile.
    val editor = open("Hallo")
    place(editor, "t0", 5)
    type_(editor, " Welt")

    paragraphs(editor) shouldBe Vector("Hallo Welt")
  }
}

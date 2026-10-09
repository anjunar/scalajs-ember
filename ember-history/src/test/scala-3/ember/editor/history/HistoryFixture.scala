package ember.editor.history

import ember.editor.core.*

/** Eine Sitzung mit installierter History und einem Absatz voller Text.
  *
  * `root > b0 > t0`. Der Caret steht am Anfang von `t0`.
  */
final class HistoryFixture(config: HistoryConfig = HistoryConfig.default):

  val clock: HistoryClock.Fake = new HistoryClock.Fake(1_000L)
  val history: History         = new History(config, clock)

  private val resolved: ResolvedExtensions =
    ExtensionResolver.resolve(Vector(TestNodes, history)) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

  val root: NodeId  = NodeId("root")
  val block: NodeId = NodeId("b0")
  val text: NodeId  = NodeId("t0")

  private val document = Document.unsafe(
    resolved.schema,
    root,
    Vector(
      RootNode(root, Vector(block)),
      BlockNode(block, Vector(text)),
      TextNode(text, "Hallo")
    )
  )

  val session: EditorSession =
    EditorSession.create(document, resolved, resolved.sessionConfig()) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

  caretAt(text, 0)

  // -----------------------------------------------------------------------------------------
  // Bequemlichkeiten
  // -----------------------------------------------------------------------------------------

  def caretAt(node: NodeId, offset: Int): Unit =
    session.update(
      _.select(RangeSelection.caret(Point.textBefore(node, offset))): Unit
    ): Unit

  def caret: Option[Int] =
    session.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(_, offset, _) => offset }

  def textOf(node: NodeId = text): String =
    session.document.node(node).collect { case run: TextNode => run.text }.getOrElse("")

  /** Tippt ein Zeichen am Caret -- Splice plus nachgezogener Caret, wie ein Command es taete. */
  def typeChar(character: String, meta: TransactionMeta = TransactionMeta.user): Unit =
    val at = caret.getOrElse(0)
    edit(meta) { transaction =>
      transaction.spliceText(text, at, 0, character): Unit
      transaction.select(RangeSelection.caret(Point.textBefore(text, at + character.length))): Unit
    }

  /** Backspace: loescht vor dem Caret und zieht ihn mit. */
  def backspace(meta: TransactionMeta = TransactionMeta.user): Unit =
    val at = caret.getOrElse(0)
    if at > 0 then
      edit(meta) { transaction =>
        transaction.spliceText(text, at - 1, 1, ""): Unit
        transaction.select(RangeSelection.caret(Point.textBefore(text, at - 1))): Unit
      }

  /** Delete: loescht hinter dem Caret, der Caret bleibt stehen. */
  def delete(meta: TransactionMeta = TransactionMeta.user): Unit =
    val at = caret.getOrElse(0)
    if at < textOf().length then edit(meta)(_.spliceText(text, at, 1, ""): Unit)

  def edit(meta: TransactionMeta = TransactionMeta.user)(body: Transaction => Unit): Unit =
    session.update(meta)(body) match
      case Right(_)    => ()
      case Left(error) => throw new AssertionError(s"abgewiesen: ${error.render}")

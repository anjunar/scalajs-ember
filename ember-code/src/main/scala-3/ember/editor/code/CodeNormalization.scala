package ember.editor.code

import ember.editor.core.*
import ember.editor.richtext.*

/** Keeping a code block's content to one plain run.
  *
  * §8.2: "CodeBlock erlaubt Text mit Zeilenumbruechen, aber keine beliebigen Rich-Text-Kinder." One
  * rule enforces the whole sentence.
  */
private[code] object CodeNormalization:

  /** Collapses whatever is inside a code block into a single run.
    *
    * ==What it repairs==
    *
    *   - several children become one run,
    *   - a paragraph or a link that was pasted in loses its wrapper and keeps its text,
    *   - an empty block gets a run, so that there is a caret position.
    *
    * ==Why it collapses rather than rejects==
    *
    * Content moved into a code block should '''become''' code. Rejecting would fail the whole
    * transaction over a paste; dropping would lose text. Taking the text and leaving the structure
    * behind is what the author meant by dropping it in there.
    *
    * ==What it does not do==
    *
    * Strip marks. That is [[runInCodeIsPlain]]'s job, and the split is not cosmetic -- see there.
    *
    * ==Idempotent==
    *
    * Once the block holds exactly one run with the collapsed text, nothing happens. §10's budget
    * depends on it; a rule that rewrote the run every round would exhaust it in thirty-two.
    */
  def contentIsOneRun(generator: NodeIdGenerator): Transform[CodeBlockNode] =
    new Transform[CodeBlockNode]:
      val name           = "code.content-is-one-run"
      val nodeType       = CodeBlockNode
      override val phase = TransformPhase.Late

      def transform(node: CodeBlockNode, scope: TransformScope): Unit =
        val document = scope.document
        val wanted   = collapsed(document, node)

        node.children.headOption.flatMap(document.node) match
          case Some(run: TextNode) if node.children.length == 1 && run.text == wanted => ()

          // One run holding the wrong text. Rewriting it keeps its id, and with it every point
          // inside.
          case Some(run: TextNode) if node.children.length == 1 =>
            scope.replace(run.id, run.copy(text = wanted)): Unit

          // Anything else: build the single run and drop what was there.
          case _ =>
            val kept = generator.nextFor(document)
            scope.insert(node.id, 0, TextNode(kept, wanted)): Unit
            node.children.foreach(child => scope.remove(child): Unit)

  /** A run inside a code block carries no marks.
    *
    * Bold code is not a thing a fence can write, and §8.2 keeps rich-text children out of a code
    * block for the same reason.
    *
    * ==Why this is its own rule, on the run==
    *
    * Because a mark change touches the '''run''', not the block. §3.4 is explicit that an ancestor
    * merely on the path of a change is not a transform candidate -- `ChangeSet.touchedAncestors`
    * exists to keep it out -- so a rule bound to the code block would never be asked. That is now
    * the third time the same shape has come up, after the text run merge in P12 and the adjacent
    * lists in P13: '''the rule belongs on the node that changes, not on the one that owns it.'''
    */
  val runInCodeIsPlain: Transform[TextNode] = new Transform[TextNode]:
    val name           = "code.run-in-code-is-plain"
    val nodeType       = TextNode
    override val phase = TransformPhase.Late

    def transform(node: TextNode, scope: TransformScope): Unit =
      val insideCode = scope.document
        .parentOf(node.id)
        .flatMap(scope.document.node)
        .exists(_.isInstanceOf[CodeBlockNode])

      if insideCode && node.marks.nonEmpty then
        scope.replace(node.id, node.copy(marks = MarkSet.empty)): Unit

  /** The text of everything inside, in order.
    *
    * '''Runs join without a separator, blocks with a newline.''' Two runs next to each other were
    * one line -- a formatting split, a paste of inline content -- and putting a newline between
    * them would invent a line break the author never typed. Two paragraphs dropped in were two
    * lines, and running them together would lose one.
    */
  private def collapsed(document: DocumentRead, node: CodeBlockNode): String =
    node.children.foldLeft("") { (text, child) =>
      document.node(child) match
        case Some(run: TextNode)        => text + run.text
        case Some(element: ElementNode) =>
          val inner = element.children.map(blockText(document, _)).mkString
          if text.isEmpty then inner else s"$text\n$inner"
        case _ => text
    }

  private def blockText(document: DocumentRead, node: NodeId): String =
    document.node(node) match
      case Some(run: TextNode)        => run.text
      case Some(element: ElementNode) => element.children.map(blockText(document, _)).mkString
      case _                          => ""

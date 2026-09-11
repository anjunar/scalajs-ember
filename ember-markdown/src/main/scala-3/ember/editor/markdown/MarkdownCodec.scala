package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** Markdown to document and back (§18.1).
  *
  * ==No HTML in between==
  *
  * P18's acceptance: "Kein HTML-/DOM-Zwischenschritt." The path is
  * `source → syntax → nodes` and `nodes → syntax → source`, and neither direction touches an
  * HTML string or a DOM. The middle step is [[MarkdownBlock]], which is an import value and not
  * a second editor state (§18.1).
  *
  * ==What the two directions promise==
  *
  *   - `decode(encode(document)) ≃ normalize(document)` for what the profile supports.
  *   - `encode(decode(source)) == source` is '''not''' promised, and §18.2 says so outright.
  *
  * IDs, selection and the original Markdown spelling are not part of that equivalence. A node
  * keeps its identity through an edit, not through an export and a re-import -- decoding builds
  * a fresh document, and it says so by taking a [[NodeIdGenerator]].
  */
object MarkdownCodec:

  // -----------------------------------------------------------------------------------------
  // Lesen
  // -----------------------------------------------------------------------------------------

  /** Parses a source and turns it into a document.
    *
    * @param rootId
    *   the id of the root node. Chosen by the caller because a document's root id is often
    *   fixed by the application, and inventing one here would make two imports of the same
    *   source differ in a way nothing else does.
    */
  def decode(
      source: String,
      schema: Schema,
      support: MarkdownSupport,
      generator: NodeIdGenerator,
      rootId: NodeId,
      profile: MarkdownProfile = MarkdownProfile.commonMarkSafe
  ): Either[MarkdownError, DecodedDocument] =
    support.duplicates match
      case Vector() =>
        Markdown.parseSyntax(source, profile) match
          case Left(error) => Left(MarkdownError.Unparseable(error))
          case Right(parsed) => decodeSyntax(parsed, schema, support, generator, rootId)
      case repeated => Left(MarkdownError.DuplicateRules(repeated))

  /** The same from an already-parsed tree. For a caller that needs the syntax as well. */
  def decodeSyntax(
      parsed: ParseResult,
      schema: Schema,
      support: MarkdownSupport,
      generator: NodeIdGenerator,
      rootId: NodeId
  ): Either[MarkdownError, DecodedDocument] =
    val sink    = new NodeSink(generator)
    val decoder = new Decoder(support, sink)

    val children = decoder.blocks(parsed.document.children)

    decoder.failure match
      case Some(problem) => Left(problem)
      case None =>
        val nodes = RootNode(rootId, children) +: sink.collected
        Document.build(schema, rootId, nodes) match
          case Left(violations) => Left(MarkdownError.InvalidDocument(violations))
          case Right(document) =>
            Right(
              DecodedDocument(
                document,
                DocumentSourceMap(sink.sourceSpans :+ (rootId -> parsed.document.span)),
                parsed,
                sink.diagnostics
              )
            )

  // -----------------------------------------------------------------------------------------
  // Schreiben
  // -----------------------------------------------------------------------------------------

  /** Turns a document into Markdown.
    *
    * §18.2: "Export liefert `EncodeResult(value, diagnostics)` oder einen Fehler; `Strict`
    * verweigert Informationsverlust, `AllowLossy` muss die Anwendung bewusst waehlen."
    */
  def encode(
      document: Document,
      support: MarkdownSupport,
      policy: LossPolicy = LossPolicy.Strict
  ): Either[MarkdownError, EncodedMarkdown] =
    support.duplicates match
      case repeated if repeated.nonEmpty => Left(MarkdownError.DuplicateRules(repeated))
      case _ =>
        val sink    = new SyntaxSink
        val encoder = new Encoder(support, document, sink)

        val blocks = document.childrenOf(document.rootId).flatMap(encoder.block)

        encoder.failure match
          case Some(problem) => Left(problem)
          case None =>
            val diagnostics = sink.diagnostics
            val losses      = diagnostics.filter(_.loss)

            if policy == LossPolicy.Strict && losses.nonEmpty then
              Left(MarkdownError.WouldLose(losses))
            else
              val tree = MarkdownDocument(sink.fresh(), sink.nowhere, blocks)
              Right(EncodedMarkdown(MarkdownWriter.write(tree), diagnostics))

  // -----------------------------------------------------------------------------------------
  // Der Abstieg
  // -----------------------------------------------------------------------------------------

  /** Drives the decoding rules. One instance per decode; the mutation is the failure slot. */
  private final class Decoder(support: MarkdownSupport, sink: NodeSink):

    var failure: Option[MarkdownError] = None

    def blocks(from: Vector[MarkdownBlock]): Vector[NodeId] =
      from.flatMap(block => if failure.isDefined then None else this.block(block))

    private def block(value: MarkdownBlock): Option[NodeId] =
      // Kinder zuerst: eine Regel bekommt fertige IDs und muss den Abstieg nicht selbst fahren.
      // Das ist derselbe Grund, aus dem `materialise` im Parser von unten baut -- eine Regel,
      // die rekursiert, koennte die Reihenfolge anders waehlen als die naechste.
      val children = value match
        case MarkdownBlock.Paragraph(_, _, _) | MarkdownBlock.Heading(_, _, _, _, _) =>
          inlineChildren(value)
        case other => blocks(other.children)

      if failure.isDefined then None
      else
        support.blocks.iterator.map(_.decode(value, children, sink)).collectFirst {
          case Some(id) => id
        } match
          case Some(id) => Some(id)
          case None =>
            failure = Some(MarkdownError.NoRule(describe(value), value.span))
            None

    private def inlineChildren(value: MarkdownBlock): Vector[NodeId] = value match
      case MarkdownBlock.Paragraph(_, _, content)       => inlines(content, MarkSet.empty)
      case MarkdownBlock.Heading(_, _, _, _, content)   => inlines(content, MarkSet.empty)
      case _                                            => Vector.empty

    def inlines(from: Vector[MarkdownInline], marks: MarkSet): Vector[NodeId] =
      from.flatMap(value => if failure.isDefined then Vector.empty else oneInline(value, marks))

    /** Ein Inline. Nicht `inline` genannt -- das ist in Scala 3 ein Soft Keyword. */
    private def oneInline(value: MarkdownInline, marks: MarkSet): Vector[NodeId] =
      // Erst fragen, ob dieses Inline eine Mark ist. Wenn ja, steigt der Abstieg mit der
      // erweiterten Menge weiter -- es entsteht '''kein''' Knoten dafuer (§8.2).
      support.marks.iterator.flatMap(rule => rule.markFor(value)).nextOption() match
        case Some(mark) => inlines(value.children, marks + mark)
        case None =>
          val children = inlines(value.children, marks)
          if failure.isDefined then Vector.empty
          else
            support.inlines.iterator.map(_.decode(value, marks, children, sink)).collectFirst {
              case Some(produced) => produced
            } match
              case Some(produced) => produced
              case None =>
                failure = Some(MarkdownError.NoRule(describe(value), value.span))
                Vector.empty

  /** Drives the encoding rules. */
  private final class Encoder(support: MarkdownSupport, document: Document, sink: SyntaxSink):

    var failure: Option[MarkdownError] = None

    def block(id: NodeId): Option[MarkdownBlock] =
      document.node(id).flatMap { node =>
        val children = node match
          case element: ElementNode if holdsBlocks(element) =>
            MarkdownChildren(blocks = element.children.flatMap(block))
          case element: ElementNode =>
            MarkdownChildren(inlines = inlines(element.id))
          case _ => MarkdownChildren()

        if failure.isDefined then None
        else
          support.blocks.iterator
            .filter(_.handles(node))
            .map(_.encode(node, children, sink))
            .collectFirst { case Some(produced) => produced } match
            case Some(produced) => Some(produced)
            case None =>
              failure = Some(MarkdownError.NoNodeRule(node.id, node.getClass.getSimpleName))
              None
      }

    /** Whether a node's children are blocks rather than inline content.
      *
      * Decided by asking the '''rules''', not by naming types: this module may not know that a
      * `ParagraphNode` holds runs and a `QuoteNode` holds paragraphs. A child that some block
      * rule claims is a block; everything else is inline content.
      */
    private def holdsBlocks(element: ElementNode): Boolean =
      element.children
        .flatMap(document.node)
        .exists(child => support.blocks.exists(_.handles(child)))

    /** The inline content of a block, with marks turned back into nesting. */
    def inlines(parent: NodeId): Vector[MarkdownInline] =
      val runs = document.childrenOf(parent).flatMap { id =>
        document.node(id).map(node => (node, marksOf(node)))
      }

      // Gleiche Markmengen zusammenfassen, '''bevor''' geschachtelt wird. Zwei benachbarte
      // kursive Laeufe einzeln zu schreiben ergaebe `*a**b*`, und das liest sich als etwas
      // ganz anderes zurueck.
      groupByMarks(runs).flatMap((marks, nodes) => nest(marks.marks, nodes))

    private def marksOf(node: EditorNode): MarkSet = node match
      case run: TextNode => run.marks
      case _             => MarkSet.empty

    private def groupByMarks(
        runs: Vector[(EditorNode, MarkSet)]
    ): Vector[(MarkSet, Vector[EditorNode])] =
      val grouped = mutable.ArrayBuffer.empty[(MarkSet, mutable.ArrayBuffer[EditorNode])]
      runs.foreach { (node, marks) =>
        grouped.lastOption match
          case Some((previous, bucket)) if previous == marks => bucket += node
          case _ => grouped += ((marks, mutable.ArrayBuffer(node)))
      }
      grouped.toVector.map((marks, bucket) => (marks, bucket.toVector))

    /** Writes a group of runs, wrapped in the syntax for its marks. */
    private def nest(marks: Vector[TextMark], nodes: Vector[EditorNode]): Vector[MarkdownInline] =
      val inner = nodes.flatMap(plainInline)

      // Nach `nesting` sortiert, damit die Ausgabe deterministisch ist: `**_a_**` und `_**a**_`
      // sind dasselbe Dokument, aber nicht derselbe String, und ein Export, der zwischen beiden
      // schwankt, macht jeden Diff unbrauchbar.
      val ordered = marks
        .flatMap { mark =>
          support.marks.find(_.owns(mark)) match
            case Some(rule) => Some(rule -> mark)
            case None =>
              sink.lost(s"Fuer die Markierung `${mark.markId.value}` ist keine MarkdownRule registriert.")
              None
        }
        .sortBy((rule, _) => rule.nesting)

      ordered.foldRight(inner) { case ((rule, mark), wrapped) =>
        rule.inlineFor(mark, wrapped, sink) match
          case Some(value) => Vector(value)
          case None =>
            sink.lost(s"Die Markierung `${mark.markId.value}` hat keine Markdown-Schreibweise.")
            wrapped
      }

    private def plainInline(node: EditorNode): Vector[MarkdownInline] =
      val children = node match
        case element: ElementNode => inlines(element.id)
        case _                    => Vector.empty

      if failure.isDefined then Vector.empty
      else
        support.inlines.iterator
          .filter(_.handles(node))
          .map(_.encode(node, children, sink))
          .collectFirst { case Some(produced) => produced } match
          case Some(produced) => produced
          case None =>
            failure = Some(MarkdownError.NoNodeRule(node.id, node.getClass.getSimpleName))
            Vector.empty

  private def describe(block: MarkdownBlock): String = block.getClass.getSimpleName
  private def describe(inline: MarkdownInline): String = inline.getClass.getSimpleName

/** A document built from Markdown, with the two source maps §18.2 asks for. */
final case class DecodedDocument(
    document: Document,
    sourceMap: DocumentSourceMap,
    syntax: ParseResult,
    diagnostics: Vector[MarkdownDiagnostic] = Vector.empty
)

/** Markdown written from a document, with what it could not carry (§18.2). */
final case class EncodedMarkdown(
    source: String,
    diagnostics: Vector[MarkdownDiagnostic] = Vector.empty
):
  /** What was dropped. Empty under [[LossPolicy.Strict]], by construction. */
  def losses: Vector[MarkdownDiagnostic] = diagnostics.filter(_.loss)

/** A conversion that produced nothing. */
sealed trait MarkdownError extends EditorError

object MarkdownError:

  final case class Unparseable(cause: ParseError) extends MarkdownError:
    def message: String = s"Der Quelltext liess sich nicht parsen: ${cause.render}"

  final case class DuplicateRules(ids: Vector[String]) extends MarkdownError:
    def message: String =
      ids.mkString("Mehrfach registrierte Regeln: ", ", ", ". Eine haette still gewonnen.")

  final case class NoRule(syntax: String, at: SourceSpan) extends MarkdownError:
    def message: String =
      s"Fuer `$syntax` bei ${at.render} ist keine MarkdownRule registriert. " +
        "Ein stilles Weglassen waere ein Dokument, das der Quelltext nicht meint."

  final case class NoNodeRule(node: NodeId, nodeClass: String) extends MarkdownError:
    def message: String =
      s"Fuer `${node.value}` ($nodeClass) ist keine MarkdownRule registriert."

  final case class WouldLose(losses: Vector[MarkdownDiagnostic]) extends MarkdownError:
    def message: String =
      losses
        .map(_.render)
        .mkString(
          "Der Export verloere Information (LossPolicy.Strict): ",
          "; ",
          ". Mit `LossPolicy.AllowLossy` ist es eine Diagnose statt eines Fehlers."
        )

  final case class InvalidDocument(violations: Vector[Violation]) extends MarkdownError:
    def message: String =
      violations.map(_.render).mkString("Das dekodierte Dokument ist ungueltig: ", "; ", "")

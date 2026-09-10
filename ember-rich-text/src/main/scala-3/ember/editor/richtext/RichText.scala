package ember.editor.richtext

import ember.editor.core.*

/** Das Rich-Text-Profil als Extension.
  *
  * ==Was es beitraegt==
  *
  * Die Knotenarten `RootNode`, `TextNode` und [[ParagraphNode]], die vier Editing-Commands und die
  * Normalisierung, die eine editierbare Flaeche bewohnbar haelt.
  *
  * Dass Wurzel und Textlauf hier mitgebracht werden, obwohl sie im Kern definiert sind, ist
  * Absicht: der Kern ist ein Modell, kein Profil. Er registriert nichts von selbst. Ein Modul, das
  * auf rich-text aufbaut (P13 Listen, P14 Links), traegt sie nicht erneut bei -- es deklariert
  * `dependsOn` und bekommt sie ueber die Aufloesung.
  *
  * ==Was es nicht beitraegt==
  *
  * Marks. §8.2 nennt Strong, Emphasis, Underline, Strike und InlineCode als eingebaute Marks des
  * Profils, aber sie gehoeren mit Bereichsformatierung, `TypingMarks` und der
  * Textlauf-Normalisierung zusammen -- und das ist P12. Ein halber Mark-Vertrag jetzt waere eine
  * API, die gleich wieder umgebaut wuerde.
  *
  * @param generator
  *   Quelle neuer Knoten-IDs. Injiziert, damit Tests deterministische IDs bekommen (§8.3).
  * @param boundaries
  *   Segmentierung fuer die Loeschbefehle. Austauschbar, damit spaeter ein `Intl.Segmenter`-Adapter
  *   danebentreten kann (§11).
  */
final class RichText private (
    generator: NodeIdGenerator,
    boundaries: TextBoundaryService
) extends Extension:

  val id: ExtensionId = ExtensionId("ember.rich-text")

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      nodeTypes = Vector(RootNode, TextNode, ParagraphNode),
      transforms = Vector(
        RichText.RootNeedsBlock(generator),
        RichText.BlockNeedsText(generator),
        RichText.DropRedundantEmptyText
      ),
      commands = Vector(
        CommandRegistration(RichText.InsertText) { (scope, text) =>
          TextEditing.insertText(scope, generator, text)
          CommandResult.Handled
        },
        CommandRegistration(RichText.InsertParagraph) { (scope, _) =>
          TextEditing.insertParagraph(scope, generator)
          CommandResult.Handled
        },
        CommandRegistration(RichText.DeleteBackward) { (scope, _) =>
          TextEditing.deleteBackward(scope, boundaries)
          CommandResult.Handled
        },
        CommandRegistration(RichText.DeleteForward) { (scope, _) =>
          TextEditing.deleteForward(scope, boundaries)
          CommandResult.Handled
        }
      )
    )

object RichText:

  /** Fuegt Text an der Auswahl ein. Payload ist der einzufuegende Text. */
  val InsertText: EditorCommand[String] = EditorCommand.of[String]("rich-text.insert-text")

  /** Teilt den Block an der Auswahl. Enter. */
  val InsertParagraph: EditorCommand[Unit] = EditorCommand.unit("rich-text.insert-paragraph")

  /** Entfernt ein Graphemcluster vor der Auswahl. Backspace. */
  val DeleteBackward: EditorCommand[Unit] = EditorCommand.unit("rich-text.delete-backward")

  /** Entfernt ein Graphemcluster hinter der Auswahl. Entfernen. */
  val DeleteForward: EditorCommand[Unit] = EditorCommand.unit("rich-text.delete-forward")

  def apply(
      generator: NodeIdGenerator,
      boundaries: TextBoundaryService = UnicodeTextBoundaries
  ): RichText = new RichText(generator, boundaries)

  /** Ein editierbares leeres Dokument: Wurzel, ein Absatz, ein leerer Textlauf.
    *
    * §8.2: "Der Kern erlaubt eine leere Root. Das Rich-Text-Profil stellt fuer eine editierbare
    * leere Flaeche einen Paragraph und eine gueltige Caretposition her." Genau diese Zeile.
    *
    * Die Normalisierungs-Transforms halten diese Form waehrend des Editierens aufrecht; hier wird
    * sie hergestellt, weil beim Anlegen noch nichts schmutzig ist und kein Transform laufen wuerde.
    */
  def emptyDocument(
      schema: Schema,
      generator: NodeIdGenerator
  ): Either[Vector[Violation], Document] =
    val rootId      = generator.next(_ => false)
    val paragraphId = generator.next(_ == rootId)
    val textId      = generator.next(id => id == rootId || id == paragraphId)

    Document.build(
      schema,
      rootId,
      Vector(
        RootNode(rootId, Vector(paragraphId)),
        ParagraphNode(paragraphId, Vector(textId)),
        TextNode(textId, "")
      )
    )

  /** Die Caretposition am Anfang des Dokuments, sofern es einen Textlauf gibt. */
  def caretAtStart(document: DocumentRead): Option[Selection] =
    document
      .subtreeOf(document.rootId)
      .find(id => document.node(id).exists(_.isInstanceOf[TextNode]))
      .map(id => RangeSelection.caret(Point.textBefore(id, 0)))

  // -----------------------------------------------------------------------------------------
  // Normalisierung
  // -----------------------------------------------------------------------------------------

  /** Eine leergeraeumte Wurzel bekommt wieder einen Block.
    *
    * Ohne diese Regel liesse sich ein Dokument leerloeschen und danach nicht mehr bearbeiten -- es
    * gaebe keine gueltige Caretposition mehr.
    */
  private[richtext] final case class RootNeedsBlock(generator: NodeIdGenerator)
      extends Transform[RootNode]:
    val name           = "rich-text.root-needs-block"
    val nodeType       = RootNode
    override val phase = TransformPhase.Late

    def transform(node: RootNode, scope: TransformScope): Unit =
      if node.children.isEmpty then
        scope.insert(node.id, 0, ParagraphNode.empty(generator.nextFor(scope.document))): Unit

  /** Ein leerer Block bekommt einen leeren Textlauf.
    *
    * Ein Block ohne Kinder haette nur eine Kindposition als Caretort -- editierbar ist er damit
    * nicht, denn Text schreibt man in einen Textlauf.
    */
  private[richtext] final case class BlockNeedsText(generator: NodeIdGenerator)
      extends Transform[ParagraphNode]:
    val name           = "rich-text.block-needs-text"
    val nodeType       = ParagraphNode
    override val phase = TransformPhase.Late

    def transform(node: ParagraphNode, scope: TransformScope): Unit =
      if node.children.isEmpty then
        scope.insert(node.id, 0, TextNode(generator.nextFor(scope.document), "")): Unit

  /** Entfernt leere Textlaeufe neben nicht leeren.
    *
    * Beim Zusammenfuehren zweier Bloecke bleibt regelmaessig ein leer gewordener Lauf uebrig. Er
    * ist unsichtbar, aber er verschiebt Kindpositionen und erschwert jeden spaeteren Vergleich.
    *
    * Zwei Einschraenkungen, beide notwendig:
    *
    *   - Nur wenn der Block noch einen '''nicht leeren''' Lauf hat. Sonst wuerde diese Regel mit
    *     [[BlockNeedsText]] in eine Endlosschleife laufen, die das Arbeitsbudget erst nach 32
    *     Runden abbricht.
    *   - Nicht der Lauf, auf den die Auswahl zeigt. Ein aufgeraeumter Baum ist keinen verlorenen
    *     Cursor wert.
    */
  private[richtext] object DropRedundantEmptyText extends Transform[ParagraphNode]:
    val name           = "rich-text.drop-redundant-empty-text"
    val nodeType       = ParagraphNode
    override val phase = TransformPhase.Late

    def transform(node: ParagraphNode, scope: TransformScope): Unit =
      val document = scope.document
      val texts = node.children.flatMap(id => document.node(id).collect { case t: TextNode => t })

      if texts.exists(_.text.nonEmpty) then
        val selected = scope.selection.collect { case range: RangeSelection => range.focus.owner }
        texts
          .filter(text => text.text.isEmpty && !selected.contains(text.id))
          .foreach(text => scope.remove(text.id): Unit)

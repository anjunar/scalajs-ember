package ember.editor.image

import ember.editor.core.*

/** A pixel measurement that is actually a measurement.
  *
  * §20 asks for "validierte positive Pixelmasse", and the type is where that lives. A width of zero
  * or minus one is not a small picture -- it is a document that no renderer can lay out, and one
  * that reached HTML would produce `width="-1"` for every reader to see.
  *
  * The upper bound is deliberate too. A stated size is meant to reserve layout space and stop the
  * page jumping (§20); a number in the millions reserves nothing and only says that something has
  * gone wrong upstream.
  */
opaque type PositivePixels = Int

object PositivePixels:

  /** Bigger than any real image and small enough to be obviously a mistake. */
  val max: Int = 100_000

  def parse(value: Int): Option[PositivePixels] =
    if value > 0 && value <= max then Some(value) else None

  def apply(value: Int): PositivePixels =
    parse(value).getOrElse(
      throw EditorContractViolation(
        s"Keine gueltige Pixelangabe: $value. Erlaubt ist 1 bis $max."
      )
    )

  given Ordering[PositivePixels] = Ordering.Int

  extension (pixels: PositivePixels) def value: Int = pixels

/** An image.
  *
  * ==An inline atom==
  *
  * §20: "Image ist ein Inline-Atom und damit auch in Paragraphen/Links verwendbar." That is why it
  * is an [[AtomNode]] and not a block: a picture inside a sentence, or inside a link, is ordinary
  * and would need a second node type if this one could only stand alone. A block-like presentation
  * gets a `Figure` container later, which §20 also says.
  *
  * §8.1 keeps atoms narrower than Lexical's `DecoratorNode` -- "keine Slots, keine editierbaren
  * Teilbereiche". An image has no inside to edit: its caption, when it gets one, will be a sibling
  * in a figure, not a slot in here.
  *
  * ==Alt text is a String, and may be empty==
  *
  * §20: "Alt-Text ist editierbar; ein dekoratives Bild verwendet ausdruecklich leeren Alt-Text,
  * nicht automatisch den Dateinamen." So `alt` is not optional -- every image has one, and the
  * empty one '''means something''': this picture carries no information a reader needs. Making it
  * an `Option` would blur the difference between "decorative" and "nobody said", and filling it
  * with a file name would put `IMG_4021.jpg` into a screen reader.
  *
  * ==What is not in here==
  *
  * File data, an object URL, an upload handle, a progress value, a `Map[String, Any]` for whatever
  * comes next. P16's acceptance names the first two; §20 names the last: "generische Media-Typen
  * benoetigen kein `Map[String, Any]`". A different kind of media is a different node type using
  * the same atom and view SPI.
  */
final case class ImageNode(
    id: NodeId,
    source: MediaReference,
    alt: String = "",
    title: Option[String] = None,
    width: Option[PositivePixels] = None,
    height: Option[PositivePixels] = None
) extends AtomNode:

  def src: MediaUrl = source.src

object ImageNode extends NodeType[ImageNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.image.image/1")

  def project(node: EditorNode): Option[ImageNode] = node match
    case value: ImageNode => Some(value)
    case _                => None

  def rekey(node: ImageNode, id: NodeId): ImageNode = node.copy(id = id)

  /** A title made of whitespace is not a title -- it renders as an empty tooltip. */
  override def validate(node: ImageNode, document: DocumentRead): Vector[Violation] =
    node.title match
      case Some(title) if title.trim.isEmpty =>
        Vector(
          Violation.NodeRejected(
            node.id,
            "Ein Bildtitel aus Leerzeichen ist kein Titel. Weglassen statt leer setzen.",
            DiagnosticPath.node(node.id.value).field("title")
          )
        )
      case _ => Vector.empty

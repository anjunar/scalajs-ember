package ember.editor.image

import ember.editor.core.*

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

package ember.editor.image

import ember.editor.core.*

/** The commands an image contributes. */
object ImageCommands:

  /** Inserts an image at the caret. */
  val InsertImage: EditorCommand[ImageNode] = EditorCommand.of[ImageNode]("image.insert")

  /** Replaces the image at the caret -- new alt text, new size, new source. */
  val UpdateImage: EditorCommand[ImageNode => ImageNode] =
    EditorCommand.of[ImageNode => ImageNode]("image.update")

/** Putting images into a document.
  *
  * ==What is missing here, on purpose==
  *
  * A picker, an upload, a progress bar, an `AbortSignal`. §20 puts all of them somewhere else:
  * "Uploads sind ein Anwendungsservice. Browser-Datei, Progress und AbortSignal gehoeren zu einem
  * Browser-/Forms-Port, nicht zum Core-Node."
  *
  * What this module takes is a finished [[MediaReference]] -- something that already exists at an
  * address. §20: "Ein Upload liefert erst nach dauerhafter Speicherung eine validierte
  * MediaReference", and, for the case people forget, "auch bei direkter externen URL wird kein
  * Upload erzwungen".
  *
  * The consequence is worth stating plainly: inserting an image is an ordinary document change. It
  * is one history step, it needs no lifecycle, and an undo removes the node without touching a file
  * anywhere (§20).
  */
object ImageEditing:

  /** Inserts an image where the caret is.
    *
    * At a text position the run is split, and the image goes between the halves -- it is an inline
    * atom, so it belongs '''between''' characters and not next to a paragraph.
    */
  def insert(
      scope: TransformScope,
      generator: NodeIdGenerator,
      image: ImageNode
  ): CommandResult =
    caretOf(scope) match
      case Some(Point.Text(run, offset, _)) =>
        (for
          block <- scope.document.parentOf(run)
          index <- scope.document.indexOfChild(run)
        yield (block, index)) match
          case None                 => CommandResult.Pass
          case Some((block, index)) =>
            val text = textOf(scope.document, run)

            if offset > 0 && offset < text.length then
              scope.splitText(run, offset, generator.nextFor(scope.document)): Unit

            val at = if offset == 0 then index else index + 1
            scope.insert(block, at, image.copy(id = freshId(scope, generator, image))): Unit
            CommandResult.Handled

      case Some(Point.Children(parent, index, _)) =>
        scope.insert(parent, index, image.copy(id = freshId(scope, generator, image))): Unit
        CommandResult.Handled

      case _ => CommandResult.Pass

  /** Keeps the caller's id when it is free, and picks one when it is not.
    *
    * A caller that built the node itself usually has no id worth keeping; one that is restoring a
    * known image does. Refusing the second case would make the command useless for a paste.
    */
  private def freshId(
      scope: TransformScope,
      generator: NodeIdGenerator,
      image: ImageNode
  ): NodeId =
    if scope.document.contains(image.id) then generator.nextFor(scope.document) else image.id

  /** Rewrites the image at the caret.
    *
    * `Replace` keeps identity (§10), so a bookmark on the picture survives a change of its alt text
    * -- which is the common edit and the one §20 asks to be editable.
    */
  def update(scope: TransformScope, change: ImageNode => ImageNode): CommandResult =
    imageAt(scope) match
      case None        => CommandResult.Pass
      case Some(image) =>
        val next = change(image)
        if next == image then CommandResult.Handled
        else
          scope.replace(image.id, next.copy(id = image.id)): Unit
          CommandResult.Handled

  /** The image the selection points at.
    *
    * An atom has no text position inside it, so a caret cannot stand '''in''' one. What a selection
    * can do is name it -- as a [[NodeSelection]], or as a child position whose neighbour it is.
    */
  def imageAt(scope: TransformScope): Option[ImageNode] =
    val document = scope.document

    scope.selection match
      case Some(selection: NodeSelection) =>
        selection.nodes.toVector.flatMap(document.node).collectFirst { case image: ImageNode =>
          image
        }
      case Some(range: RangeSelection) =>
        range.focus match
          case Point.Children(parent, index, _) =>
            document
              .childrenOf(parent)
              .lift(index)
              .flatMap(document.node)
              .collect { case image: ImageNode => image }
          case _ => None
      case _ => None

  private def caretOf(scope: TransformScope): Option[Point] =
    scope.selection.collect { case range: RangeSelection if range.isCollapsed => range.focus }

  private def textOf(document: DocumentRead, run: NodeId): String =
    document.node(run).collect { case value: TextNode => value.text }.getOrElse("")

/** Images as an extension.
  *
  * Contributes a node type and two commands, and nothing else -- no transform. There is no
  * invariant to repair: an image has no children to go wrong, and its fields are already typed so
  * that a wrong one cannot be built.
  *
  * @param policy
  *   which sources this profile accepts. Injected for the same reason as the link policy: what
  *   counts as an acceptable host is an application's decision.
  */
final class ImageExtension private (
    generator: NodeIdGenerator,
    val policy: MediaUrlPolicy
) extends Extension:

  val id: ExtensionId = ExtensionId("ember.image")

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      nodeTypes = Vector(ImageNode),
      commands = Vector(
        CommandRegistration(ImageCommands.InsertImage) { (scope, image) =>
          ImageEditing.insert(scope, generator, image)
        },
        CommandRegistration(ImageCommands.UpdateImage) { (scope, change) =>
          ImageEditing.update(scope, change)
        }
      )
    )

object ImageExtension:

  def apply(
      generator: NodeIdGenerator,
      policy: MediaUrlPolicy = MediaUrlPolicy.default
  ): ImageExtension = new ImageExtension(generator, policy)

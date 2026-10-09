package ember.editor.image

import ember.editor.core.*

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

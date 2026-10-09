package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*

/** Links as an extension.
  *
  * ==Why no dialog is needed==
  *
  * P14's acceptance: "Linkdialog nicht noetig, headless nutzbar." Everything here takes a
  * [[LinkTarget]] and returns a document change; where that target comes from -- a dialog, a paste,
  * a Markdown import, a test -- is somebody else's question. `ember-ui` (P27) will put a dialog in
  * front of it, and nothing in this module will have to change.
  *
  * @param policy
  *   which targets this profile accepts. Injected, because "erlaubt" is an application's decision
  *   and not a library's: an intranet editor and a public one disagree about `http`.
  */
final class LinkExtension private (
    generator: NodeIdGenerator,
    val policy: LinkUrlPolicy
) extends Extension:

  val id: ExtensionId = ExtensionId("ember.link")

  override val dependsOn: Vector[ExtensionId] = Vector(ExtensionId("ember.rich-text"))

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      nodeTypes = Vector(LinkNode),
      transforms = LinkNormalization.all,
      commands = Vector(
        CommandRegistration(LinkCommands.SetLink) { (scope, target) =>
          LinkEditing.setLink(scope, generator, target)
        },
        CommandRegistration(LinkCommands.RemoveLink) { (scope, _) =>
          LinkEditing.removeLink(scope)
        }
      )
    )

object LinkExtension:

  def apply(
      generator: NodeIdGenerator,
      policy: LinkUrlPolicy = LinkUrlPolicy.default
  ): LinkExtension = new LinkExtension(generator, policy)

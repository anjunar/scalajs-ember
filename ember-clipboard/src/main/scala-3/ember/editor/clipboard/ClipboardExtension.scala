package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.richtext.*

final class ClipboardExtension(generator: NodeIdGenerator) extends Extension:
  val id                                          = ExtensionId("ember.clipboard")
  override val dependsOn                          = Vector(ExtensionId("ember.rich-text"))
  override def contribute: ExtensionContributions = ExtensionContributions(commands =
    Vector(
      CommandRegistration(ClipboardCommands.Paste) { (scope, fragment) =>
        FragmentEditing.paste(scope, fragment, generator): Unit
        CommandResult.Handled
      },
      CommandRegistration(ClipboardCommands.DeleteSelection) { (scope, _) =>
        FragmentEditing.delete(scope): Unit
        CommandResult.Handled
      },
      CommandRegistration(ClipboardCommands.Move) { (scope, move) =>
        FragmentEditing.move(scope, move, generator): Unit
        CommandResult.Handled
      }
    )
  )

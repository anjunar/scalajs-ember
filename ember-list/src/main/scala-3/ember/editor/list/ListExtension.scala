package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*

/** Lists as an extension.
  *
  * ==Why it depends on rich-text and not the other way round==
  *
  * §6 puts `list` above `rich-text`, and §8.2 says why: "ein ListItem Blockinhalte." The most
  * common block is a paragraph, so a list needs paragraphs to be worth anything -- while the
  * rich-text profile is perfectly useful without lists. An application that only edits prose never
  * links this module.
  *
  * ==Where it takes precedence, and where it does not==
  *
  * Enter and Backspace mean something different inside a list, and §12's priority chain is exactly
  * the mechanism for that: both handlers register at [[CommandPriority.High]], above the rich-text
  * ones, and return [[CommandResult.Pass]] when the caret is not in a list. The rich-text handler
  * then does the ordinary thing. This module does not replace paragraph splitting; it takes
  * precedence where lists are involved and steps aside everywhere else.
  *
  * That is also why splitting an item calls `RichText.InsertParagraph` rather than reimplementing
  * it. The block split already handles marks, empty runs and caret placement -- a second
  * implementation would drift.
  */
final class ListExtension private (generator: NodeIdGenerator) extends Extension:

  val id: ExtensionId = ExtensionId("ember.list")

  /** Resolution order matters: the rich-text commands must exist before these sit above them. */
  override val dependsOn: Vector[ExtensionId] = Vector(ExtensionId("ember.rich-text"))

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      nodeTypes = Vector(ListNode, ListItemNode),
      transforms = ListNormalization.all(generator),
      commands = Vector(
        CommandRegistration(ListCommands.ToggleList) { (scope, kind) =>
          ListEditing.toggle(scope, generator, kind)
          CommandResult.Handled
        },
        CommandRegistration(ListCommands.Indent) { (scope, _) =>
          ListEditing.indent(scope, generator)
          CommandResult.Handled
        },
        CommandRegistration(ListCommands.Outdent) { (scope, _) =>
          ListEditing.outdent(scope, generator)
          CommandResult.Handled
        },
        CommandRegistration(
          RichText.InsertParagraph,
          CommandPriority.High,
          (scope, _) => ListEditing.insertParagraph(scope, generator)
        ),
        CommandRegistration(
          RichText.DeleteBackward,
          CommandPriority.High,
          (scope, _) => ListEditing.deleteBackward(scope, generator)
        )
      )
    )

object ListExtension:

  def apply(generator: NodeIdGenerator): ListExtension = new ListExtension(generator)

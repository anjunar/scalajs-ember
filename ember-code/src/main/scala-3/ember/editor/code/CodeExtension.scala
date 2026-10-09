package ember.editor.code

import ember.editor.core.*
import ember.editor.richtext.*

/** Code blocks as an extension.
  *
  * ==What it takes precedence over==
  *
  * Enter, at [[CommandPriority.High]] like the list module. Inside a code block Enter inserts a
  * newline; everywhere else the handler passes and the rich-text one splits the block. §12's
  * priority chain is the mechanism, and stepping aside is half of using it correctly.
  *
  * ==What it deliberately does not contribute==
  *
  * A highlighter. P15's acceptance: "Kein Syntax-Highlighter und kein CodeMirror als
  * Produktionsabhaengigkeit." What this module guarantees is the thing a highlighter would need and
  * the thing Markdown fences need -- the content, verbatim, including its blank lines. A later
  * `ember-code-highlighting` (§6) can colour it in a view without touching the document, which is
  * exactly what the risk line demands.
  */
final class CodeExtension private (generator: NodeIdGenerator) extends Extension:

  val id: ExtensionId = ExtensionId("ember.code")

  override val dependsOn: Vector[ExtensionId] = Vector(ExtensionId("ember.rich-text"))

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      nodeTypes = Vector(CodeBlockNode),
      transforms = Vector(
        CodeNormalization.contentIsOneRun(generator),
        CodeNormalization.runInCodeIsPlain
      ),
      commands = Vector(
        CommandRegistration(CodeCommands.ToggleCodeBlock) { (scope, info) =>
          CodeEditing.toggle(scope, generator, info)
        },
        CommandRegistration(CodeCommands.SetCodeInfo) { (scope, info) =>
          CodeEditing.setInfo(scope, info)
        },
        CommandRegistration(CodeCommands.IndentLine) { (scope, _) =>
          CodeEditing.indentLine(scope)
        },
        CommandRegistration(CodeCommands.OutdentLine) { (scope, _) =>
          CodeEditing.outdentLine(scope)
        },
        CommandRegistration(
          RichText.InsertParagraph,
          CommandPriority.High,
          (scope, _) => CodeEditing.insertParagraph(scope, generator)
        )
      )
    )

object CodeExtension:

  def apply(generator: NodeIdGenerator): CodeExtension = new CodeExtension(generator)

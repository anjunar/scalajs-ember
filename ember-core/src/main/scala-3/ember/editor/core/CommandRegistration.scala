package ember.editor.core

/** Ein Command samt Prioritaet und Handler, wie eine Extension ihn beitraegt. */
final case class CommandRegistration[A](
    command: EditorCommand[A],
    priority: CommandPriority,
    handler: (TransformScope, A) => CommandResult
)

object CommandRegistration:

  /** Bequemer Konstruktor mit Normalprioritaet. */
  def apply[A](command: EditorCommand[A])(
      handler: (TransformScope, A) => CommandResult
  ): CommandRegistration[A] =
    CommandRegistration(command, CommandPriority.Normal, handler)

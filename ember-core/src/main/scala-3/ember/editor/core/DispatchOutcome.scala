package ember.editor.core

/** Was ein Dispatch ausserhalb einer Transaktion ergeben hat.
  *
  * Beides wird gebraucht und ist nicht dasselbe: ein Handler kann uebernehmen, ohne etwas zu
  * aendern (`Handled` mit `commit.isNoOp`), und ein Dispatch kann etwas aendern, ohne dass
  * irgendjemand uebernommen hat -- dann naemlich, wenn gar kein Handler registriert war und die
  * Transaktion leer blieb.
  */
final case class DispatchOutcome(result: CommandResult, commit: Commit):
  def wasHandled: Boolean = result == CommandResult.Handled

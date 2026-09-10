package ember.editor.history

import ember.editor.core.*

/** Die Commands, die eine [[History]] beitraegt.
  *
  * Werte, keine Namen (§12): Dispatch laeuft ueber Objektidentitaet, nicht ueber einen String.
  * Wer sie an eine Taste bindet -- das tut `ember-browser-support` ab P22 --, dispatcht diese
  * beiden und nicht `"undo"`.
  *
  * §12 nennt sie ausdruecklich: "`historyUndo/historyRedo` werden mit dem eigenen History-Modul
  * verbunden, nicht mit einer parallelen Browser-History."
  */
object HistoryCommands:

  val Undo: EditorCommand[Unit] = EditorCommand.unit("history.undo")

  val Redo: EditorCommand[Unit] = EditorCommand.unit("history.redo")

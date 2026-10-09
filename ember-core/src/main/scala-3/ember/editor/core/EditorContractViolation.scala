package ember.editor.core

/** Verletzung eines Aufrufvertrags: falscher Zustand, abgelaufenes Handle, Reentranz.
  *
  * Kein Datenfehler und deshalb bewusst kein [[EditorError]]. `IllegalStateException` folgt
  * `ui.core.render.HostWriteBlocked` im Nachbar-Repo, damit Aufrufer beide Faelle mit derselben
  * Erwartung behandeln koennen.
  */
final class EditorContractViolation(message: String) extends IllegalStateException(message)

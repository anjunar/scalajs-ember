package ember.editor.browser

import ember.editor.core.*

/** A change that is not the running composition's, refused before it commits.
  *
  * §15.3: "Waehrend Composition werden '''alle unabhaengigen Dokumenttransaktionen''', auch
  * ausserhalb des geschuetzten Bereichs, vor Commit als `CompositionBusy` abgewiesen oder als
  * expliziter Intent mit Bookmark in eine begrenzte Queue gelegt."
  *
  * Outside the protected area too, and that is the part worth explaining: the reason is not the DOM
  * but the history. §14 records a composition as '''one''' group, and a snapshot history undoes a
  * group whole. An independent commit landing inside that group would be taken back with it --
  * someone else's edit, undone by a keystroke that had nothing to do with it. Allowing them needs
  * selective history, and §15.3 rules that out for the MVP by name.
  */
final case class CompositionBusy(session: Long, override val path: DiagnosticPath)
    extends EditorError:
  def message: String =
    s"Eine Texteingabe laeuft (Composition $session); unabhaengige Aenderungen sind so lange " +
      "nicht erlaubt."

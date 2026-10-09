package ember.editor.core

/** What a history restore does with a field's value.
  *
  * Deliberately a decision of the field, not of the history: only the field knows whether its value
  * follows from the document -- and a history that guessed would guess wrong for exactly the fields
  * where it matters.
  */
enum HistoryRestorePolicy:

  /** The value follows from the restored state and is not carried in the snapshot. */
  case Recompute

  /** The value belongs to the snapshot and comes back with it. */
  case Restore

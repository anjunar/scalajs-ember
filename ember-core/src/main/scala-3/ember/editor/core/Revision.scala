package ember.editor.core

/** Fortlaufende Nummer eines Dokumentstands.
  *
  * Hier eingefuehrt, weil [[Bookmark]] ohne sie nicht sagen kann, worauf es sich bezieht. P04
  * verwendet denselben Typ in `EditorState`; Revisionen steigen auch bei Undo, der
  * wiederhergestellte Inhalt bekommt also eine neue Nummer (§9).
  */
opaque type Revision = Long

object Revision:
  val initial: Revision = 0L

  def apply(value: Long): Revision = value

  given Ordering[Revision] = Ordering.Long

  extension (revision: Revision)
    def value: Long                        = revision
    def next: Revision                     = revision + 1
    def isBefore(other: Revision): Boolean = revision < other

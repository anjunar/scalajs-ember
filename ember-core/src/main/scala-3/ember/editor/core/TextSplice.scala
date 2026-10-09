package ember.editor.core

/** Eine Textaenderung an einer Stelle, in UTF-16-Koordinaten.
  *
  * Genau die Form, die `ui.core` als `TextNode.spliceText(start, deleteCount, inserted)` erwartet.
  * Die Projektion kann sie deshalb unveraendert durchreichen, statt den neuen Text vollstaendig zu
  * schreiben -- das ist der Unterschied zwischen einem gezielten `CharacterData.replaceData` und
  * einem Neuschreiben des ganzen Knotens bei jedem Tastendruck.
  */
final case class TextSplice(start: Int, deleteCount: Int, inserted: String):
  def insertedLength: Int = inserted.length
  def delta: Int          = inserted.length - deleteCount

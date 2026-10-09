package ember.editor.foreign

import ember.editor.core.*

/** Eine eigene Auswahlart des Fremdmoduls.
  *
  * Stellvertreter fuer die Tabellenauswahl aus X01: weder ein Textbereich noch eine Knotenmenge.
  * Belegt, dass §11s offener Auswahlvertrag traegt -- ohne ihn muesste X01 den Kern aufmachen.
  */
final case class CellRangeSelection(from: NodeId, to: NodeId) extends Selection

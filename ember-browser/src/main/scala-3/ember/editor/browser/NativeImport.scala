package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

/** What a native change turned out to be. */
enum NativeImport:

  /** The DOM says what the model already says. */
  case Nothing

  /** One text run differs by one splice. */
  case Text(node: NodeId, splice: TextSplice)

  /** The run's text changed, and the browser also left more than one text node in its wrapper.
    *
    * Firefox does this when an astral character is inserted natively: "Hallo Welt" becomes three
    * nodes, "Hallo" + the emoji + " Welt". The '''text''' is importable -- a run's content is the
    * concatenation, and the marks did not change -- but the view needs rebuilding afterwards, or
    * the next splice writes into one node while the others stand (§15.4).
    */
  case SplitRun(node: NodeId, splice: TextSplice)

  /** The DOM changed in a way this reader cannot express as a document change.
    *
    * §15.4: "Rohtext bzw. der letzte Source-Draft bleibt fuer Recovery verfuegbar." So the text
    * that was found travels with the report -- losing what someone just typed because its structure
    * was unexpected is the one outcome worth any amount of trouble to avoid.
    *
    * Repairing it is P23's Recovery. What P22 owes is to notice and to say so.
    */
  case Unimportable(reason: String, salvage: Option[String])

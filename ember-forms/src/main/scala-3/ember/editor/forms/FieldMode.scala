package ember.editor.forms

import ember.editor.core.*

import scala.collection.mutable

/** Which of the two views owns the value right now. */
enum FieldMode:

  /** The document is authoritative. The textarea is hidden but '''not disabled''' -- §16 asks for
    * exactly one successful named field, and a disabled control submits nothing.
    */
  case Rich

  /** The textarea is authoritative. The rich view goes readonly (§16). */
  case Source

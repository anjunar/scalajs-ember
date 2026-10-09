package ember.editor.browser

import ember.editor.core.*

/** The area a running composition owns.
  *
  * §15.3: "Die Schreibsperre schuetzt den vollstaendigen anfaenglichen Ersetzungsbereich
  * einschliesslich aller betroffenen Leaves, Marks, Atomgrenzen und Bloecke sowie benoetigter
  * struktureller Vorfahren. Bei einer kollabierten Range ist dies mindestens der aktive Block.
  * Reicht eine lokale Schutzgrenze nicht, wird der ganze Editing-Host geschuetzt; ein
  * blockuebergreifender Start darf nicht als Ein-Leaf-Fall behandelt werden."
  */
enum ProtectedRegion:

  /** The blocks the composition may touch. Protecting a block protects everything inside it. */
  case Blocks(nodes: Vector[NodeId])

  /** Everything. For a composition whose start cannot be narrowed to blocks. */
  case WholeHost

  def isWhole: Boolean = this == ProtectedRegion.WholeHost

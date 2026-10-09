package ember.editor.codehighlighting

import ember.editor.code.CodeBlockNode
import ember.editor.core.*

import scala.collection.mutable

/** Where results go. The browser paints them; a test records them. */
trait HighlightSink:

  /** Shows a current result.
    *
    * `false` means the view cannot show it yet -- the DOM text is not the committed text, which is
    * exactly the state of a block during a composition (§15.3). The block is asked for again after
    * the next projection, and nothing is written in the meantime.
    */
  def show(result: HighlightResult): Boolean

  def clear(block: NodeId): Unit

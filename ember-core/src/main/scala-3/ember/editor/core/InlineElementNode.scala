package ember.editor.core

/** A container within a text block, such as a link. Editing crosses its boundary without treating
  * it as a paragraph. Its descriptor still owns reconstruction.
  */
trait InlineElementNode extends ElementNode

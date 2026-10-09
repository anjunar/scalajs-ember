package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** Whether a node stands between blocks or inside a line.
  *
  * §8.2 makes the distinction and gives the example: "Links sind Inline-Container, keine
  * Text-Mark." A link has children and a target, so it is not a mark -- but it also does not end a
  * paragraph, so it is not a block. Without this, an imported link ended a paragraph and lost
  * itself: every `<a>` came back as bare text, which a browser test of the import duly showed.
  */
enum NodeLevel:
  case Block, Inline

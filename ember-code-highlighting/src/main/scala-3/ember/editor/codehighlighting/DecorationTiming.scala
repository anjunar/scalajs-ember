package ember.editor.codehighlighting

import ember.editor.code.CodeBlockNode
import ember.editor.core.*
import ember.editor.ui.{DocumentView, TextRunElement}
import org.scalajs.dom
import ui.core.render.DomNodes

import scala.collection.mutable
import scala.scalajs.js

/** When results are requested after a change. */
enum DecorationTiming:

  /** In the next animation frame. Several commits in one frame -- a paste, a burst of keys -- cost
    * one request per block.
    */
  case NextFrame

  /** Directly after the projection. For tests, and for callers that paint synchronously. */
  case AfterProjection

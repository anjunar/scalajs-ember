package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** What a write did. */
enum SelectionWrite:
  case Written
  case Skipped(reason: SkipReason)
  case Failed(problem: PositionProblem)

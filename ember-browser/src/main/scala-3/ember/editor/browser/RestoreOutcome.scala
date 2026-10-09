package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** What a restore did. */
enum RestoreOutcome:

  case Restored(selection: RangeSelection, focused: Boolean)

  /** The bookmark can no longer be mapped to this document (§11). */
  case Expired(error: ExpiredBookmark)

  /** The selection could not be written. The bookmark itself was fine. */
  case NotWritten(write: SelectionWrite)

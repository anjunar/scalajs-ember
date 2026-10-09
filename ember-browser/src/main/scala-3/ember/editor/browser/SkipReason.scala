package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Why a write did not happen. Every one of them is a rule, not a failure. */
enum SkipReason:

  /** The projection has not caught up with the state (§11: write only at a matching revision).
    *
    * Writing earlier would address nodes by a document that the DOM does not show yet.
    */
  case StaleProjection

  /** The focus is not inside the host, and the write did not insist (§22).
    *
    * "Hintergrundupdates stehlen weder Page- noch Textarea-Fokus." A selection write into an
    * unfocused host is visible and unasked for.
    */
  case NotFocused

  /** There is no selection API for this host -- a shadow root without `getSelection` (§15.4). */
  case Unsupported

  /** The DOM already says exactly this. Writing would fire `selectionchange` for nothing. */
  case AlreadyThere

  /** The model has no selection. Clearing the browser's would take the user's caret away. */
  case NoSelection

  def render: String = this match
    case StaleProjection => "Die Projektion zeigt noch eine aeltere Revision."
    case NotFocused      => "Der Fokus liegt nicht im Editing-Host."
    case Unsupported     => "Fuer diesen Host gibt es keine lesbare Selection."
    case AlreadyThere    => "Die Auswahl steht bereits so im DOM."
    case NoSelection     => "Das Modell hat keine Auswahl."

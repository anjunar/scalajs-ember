package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** When focus may be moved, as a rule rather than as a sequence of calls.
  *
  * The interesting half is negative, and it is the one §22 spends its sentence on: a background
  * update must not take the focus, and neither must a dialog that was never given it. Getting this
  * wrong is not a visual glitch -- it moves the keyboard away from wherever someone was actually
  * typing.
  */
object FocusPolicy:

  def mayTakeFocus(
      intent: FocusIntent,
      bookmarkHadFocus: Boolean,
      focusWithin: Boolean,
      focusable: Boolean
  ): Boolean =
    if focusWithin then false     // already here; moving it again would only scroll
    else if !focusable then false // §22: focusability is a separate decision from editability
    else
      intent match
        case FocusIntent.SelectionOnly => false
        case FocusIntent.IfItWasOurs   => bookmarkHadFocus
        case FocusIntent.Always        => true

  /** Which write intent a restore implies.
    *
    * A restore that is allowed to take the focus writes the selection whether or not the focus has
    * arrived yet; one that is not may only write into a host that is already focused.
    */
  def writeIntent(takesFocus: Boolean): WriteIntent =
    if takesFocus then WriteIntent.Explicit else WriteIntent.FollowFocus

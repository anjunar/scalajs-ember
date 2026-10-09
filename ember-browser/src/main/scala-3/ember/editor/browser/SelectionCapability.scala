package ember.editor.browser

import org.scalajs.dom

import scala.scalajs.js

/** What a scope can do about selection. Reported, never assumed. */
enum SelectionCapability:

  /** The ordinary case: a host in a document with a window. */
  case Document

  /** A host in a shadow root whose engine offers `ShadowRoot.getSelection` (Chromium today). */
  case ShadowNative

  /** A host in a shadow root without one. Selection inside it cannot be read (§15.4). */
  case ShadowUnsupported

  /** A detached host, or a document without a window. */
  case Detached

  def isSupported: Boolean = this == Document || this == ShadowNative

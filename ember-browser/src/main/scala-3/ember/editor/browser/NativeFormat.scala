package ember.editor.browser

import ember.editor.core.*

/** A formatting request the browser made, by its own name.
  *
  * Deliberately not a [[TextMark]]. A mark is a feature's idea (§8.2), and `ember-browser` is not
  * allowed to know the features (§7). The binding turns `Bold` into whatever the profile in use
  * calls bold -- and a profile without one simply has no binding for it.
  */
enum NativeFormat:
  case Bold, Italic, Underline, StrikeThrough, Superscript, Subscript

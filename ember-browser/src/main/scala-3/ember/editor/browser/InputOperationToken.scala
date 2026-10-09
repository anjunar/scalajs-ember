package ember.editor.browser

/** One native operation, identified well enough to be processed exactly once.
  *
  * The `sequence` is what makes two consecutive backspaces two operations rather than one. An
  * `inputType` alone would collapse them, and the second `input` event would then be taken for the
  * echo of the first.
  */
final case class InputOperationToken(inputType: String, sequence: Long)

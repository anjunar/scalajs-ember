package ember.editor.richtext

import ember.editor.core.*

/** Which kind of line break this is.
  *
  * §8.2 is explicit that the two must not collapse into one: "SoftBreak und HardBreak bleiben
  * unterscheidbar, damit Markdown und semantisches HTML ihre Bedeutung erhalten."
  *
  * The difference is real in both formats. In Markdown a soft break is a newline that renders as a
  * space; a hard break is two trailing spaces or a backslash and renders as `<br>`. Storing them as
  * the same node would make the export a guess, and §18 does not promise a source-identical round
  * trip but does promise to keep the supported semantics.
  */
enum BreakKind:

  /** A line break in the source that is not one in the output. */
  case Soft

  /** A break the author asked for. */
  case Hard

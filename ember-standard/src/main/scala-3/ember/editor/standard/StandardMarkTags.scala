package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*
import ember.editor.richtext.*

/** How the built-in marks become HTML tags.
  *
  * ==Why the mapping lives here==
  *
  * `ember-html` does not know what "strong" means, and `ember-rich-text` does not know what HTML
  * is. §6 puts the place where both meet in `standard`, and this is the smallest possible example
  * of it: five marks, five tags, one table.
  *
  * ==Why `strong` and `em` and not `b` and `i`==
  *
  * §16 asks for semantic HTML. `b` and `i` say how something looks; `strong` and `em` say what it
  * means, and that is what survives a stylesheet and reaches a screen reader.
  *
  * Underline has no semantic element in HTML -- `u` is presentational, and that is exactly what it
  * means here too. It is in the profile because §8.2 lists it, not because HTML has a good answer
  * for it.
  */
object StandardMarkTags:

  private val tags: Map[MarkId, String] = Map(
    StandardMarks.Strong.markId     -> "strong",
    StandardMarks.Emphasis.markId   -> "em",
    StandardMarks.Underline.markId  -> "u",
    StandardMarks.Strike.markId     -> "s",
    StandardMarks.InlineCode.markId -> "code"
  )

  /** The nesting, outermost first.
    *
    * The order follows [[MarkSet]]'s own: sorted by `MarkId`, and stable for that reason. Which of
    * `<strong><em>` and `<em><strong>` comes out does not change the meaning, but it has to be the
    * *same* one every time -- otherwise the SSR output would differ from the browser's for no
    * reason anyone could see, and every round-trip fixture would be worthless.
    *
    * An unregistered mark contributes no tag. A foreign module may add marks (§8.2); rendering its
    * text without its formatting is a loss, but a silent `<undefined>` would be worse, and the
    * module that brought the mark brings its own adapter.
    */
  def tagsFor(marks: MarkSet): Vector[String] =
    marks.marks.flatMap(mark => tags.get(mark.markId))

package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.image.*
import ember.editor.json.*
import ember.editor.link.*
import ember.editor.list.{ListKind, ListItemNode, ListNode}
import ember.editor.richtext.*

/** Ready-made bundles. A convenience over [[StandardJsonCodecs]], never the only way in. */
object StandardJsonSupport:

  /** The five built-in marks. */
  val marks: MarkSupport = MarkSupport.of(
    StandardJsonCodecs.strong,
    StandardJsonCodecs.emphasis,
    StandardJsonCodecs.underline,
    StandardJsonCodecs.strike,
    StandardJsonCodecs.inlineCode
  )

  /** Core plus the rich-text profile: paragraphs, headings, quotes, breaks and the marks. */
  val richText: JsonSupport =
    (CoreJsonSupport.all ++ JsonSupport.of(
      StandardJsonCodecs.paragraph,
      StandardJsonCodecs.heading,
      StandardJsonCodecs.quote,
      StandardJsonCodecs.breaks,
      StandardJsonCodecs.thematicBreak
    )).withMarks(marks)

  /** Everything P12 to P16 built.
    *
    * The policies have no defaults on purpose: a caller reaching for "everything" should still have
    * to say which URLs it trusts. A payload is exactly as untrusted as a dialog (§19.1).
    */
  def everything(
      links: LinkUrlPolicy = LinkUrlPolicy.default,
      media: MediaUrlPolicy = MediaUrlPolicy.default
  ): JsonSupport =
    richText ++ JsonSupport.of(
      StandardJsonCodecs.list,
      StandardJsonCodecs.listItem,
      StandardJsonCodecs.link(links),
      StandardJsonCodecs.code,
      ImageJsonSupport.codec(media)
    )

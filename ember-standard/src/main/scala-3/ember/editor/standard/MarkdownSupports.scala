package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.image.*
import ember.editor.link.*
import ember.editor.list.{ListKind as DocumentListKind, ListItemNode, ListNode}
import ember.editor.markdown.*
import ember.editor.richtext.*

/** Ready-made sets, for an application that does not want to list rules itself. */
object MarkdownSupports:

  /** Paragraphs, headings, quotes, breaks, text and the two writable marks. */
  val richText: MarkdownSupport = MarkdownSupport.of(
    MarkdownRules.paragraph,
    MarkdownRules.heading,
    MarkdownRules.quote,
    MarkdownRules.thematicBreak,
    MarkdownRules.rawHtml,
    MarkdownRules.text,
    MarkdownRules.breaks,
    MarkdownRules.strong,
    MarkdownRules.emphasis,
    MarkdownRules.inlineCode,
    MarkdownRules.unwritableMarks
  )

  /** Everything §18.2 lists, with the application's policies.
    *
    * The policies have no defaults here on purpose -- a caller that reaches for "everything" should
    * still have to say which URLs it trusts.
    */
  def everything(
      links: LinkUrlPolicy = LinkUrlPolicy.default,
      media: MediaUrlPolicy = MediaUrlPolicy.default
  ): MarkdownSupport =
    richText ++ MarkdownSupport.of(
      MarkdownRules.list,
      MarkdownRules.code,
      MarkdownRules.links(links),
      MarkdownRules.images(media)
    )

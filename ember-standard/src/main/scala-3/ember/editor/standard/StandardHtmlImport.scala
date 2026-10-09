package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeInfo}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageNode, MediaReference, MediaUrlPolicy, PositivePixels}
import ember.editor.link.{LinkNode, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*

/** The assembled profiles, the counterpart to [[RichTextSupport]] on the export side. */
object StandardHtmlImport:

  /** Paragraphs, headings, quotes, breaks and marks. No lists, links, code or images. */
  val richText: HtmlImportSupport =
    HtmlImportSupport.of(
      ParagraphHtmlImport.profile,
      (ParagraphHtmlImport.rules ++ RichTextHtmlImport.rules)*
    )

  /** Everything this repository can read.
    *
    * The policies come from the caller for the reason §19.1 gives: an imported URL goes through the
    * same policy a typed one does, and a library that shipped its own would be a way around the
    * application's.
    */
  def everything(links: LinkUrlPolicy, media: MediaUrlPolicy): HtmlImportSupport =
    HtmlImportSupport.of(
      ParagraphHtmlImport.profile,
      (ParagraphHtmlImport.rules ++
        RichTextHtmlImport.rules ++
        ListHtmlImport.rules ++
        LinkHtmlImport.rules(links) ++
        CodeHtmlImport.rules ++
        ImageHtmlImport.rules(media))*
    )

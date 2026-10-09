package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** A remembered selection with the state it belonged to, plus whether the editor had focus.
  *
  * Two bookmarks and not one: §11 maps anchor and focus '''independently''', and a pair that had
  * been collapsed into a single position would come back as a caret even where the user had a
  * range. The focus flag is what makes [[FocusIntent.IfItWasOurs]] answerable at all.
  */
final case class SelectionBookmark(anchor: Bookmark, focus: Bookmark, hadFocus: Boolean):

  def revision: Revision = anchor.revision

  /** Brings the bookmark forward to the document the mapping ends at.
    *
    * Uses [[Bookmark.resolveOrFallback]] -- the variant that accepts the replacement boundary. Its
    * own documentation names this case: "Fuer Aufrufer, denen eine ungefaehre Stelle genuegt --
    * etwa das Wiederherstellen des Fokus nach einem Dialog (§22)." An insertion may not do that;
    * putting a caret back may.
    */
  def resolve(mapping: RevisionMapping): Either[ExpiredBookmark, RangeSelection] =
    for
      anchorPoint <- anchor.resolveOrFallback(mapping)
      focusPoint  <- focus.resolveOrFallback(mapping)
    yield RangeSelection(anchorPoint, focusPoint)

object SelectionBookmark:

  /** Takes a bookmark of a range at a revision. */
  def of(selection: RangeSelection, revision: Revision, hadFocus: Boolean): SelectionBookmark =
    SelectionBookmark(
      Bookmark(selection.anchor, revision),
      Bookmark(selection.focus, revision),
      hadFocus
    )

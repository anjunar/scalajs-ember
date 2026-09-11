package ember.editor.browser

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The rules of §11 and §22 that do not need an engine (P21).
  *
  * ==What is here and what is in the browser gate==
  *
  * The mapping table itself is about a real DOM -- where a comment anchor sits, what a mark chain
  * looks like, whether a caret survives a formatting change. Those need an engine and live in
  * `selection.spec.mjs` and `focus.spec.mjs`.
  *
  * What is here is the part that is a decision: may this selection be written, may the focus be
  * taken, does this bookmark still mean anything. Every one of those is a function of values the
  * caller already has, and a function is better tested as one.
  *
  * The same split as `HydrationBoundarySpec` makes for §17, for the same reason.
  */
final class SelectionPolicySpec extends AnyFlatSpec with Matchers {

  private def decide(
      projected: Long = 7,
      state: Long = 7,
      focusWithin: Boolean = true,
      intent: WriteIntent = WriteIntent.FollowFocus,
      capability: SelectionCapability = SelectionCapability.Document
  ): Option[SkipReason] =
    SelectionWriteGate.decide(
      Revision(projected),
      Revision(state),
      focusWithin,
      intent,
      capability
    )

  // ---------------------------------------------------------------------------------------
  // Schreiben
  // ---------------------------------------------------------------------------------------

  "A write" should "happen when projection, focus and capability all agree" in {
    decide() shouldBe None
  }

  it should "wait while the projection lags behind the state" in {
    // §11: "nur nach passender Projection-Revision schreiben". A point names nodes of a document
    // the DOM does not show yet -- the write would land on the previous arrangement.
    decide(projected = 6, state = 7) shouldBe Some(SkipReason.StaleProjection)
  }

  it should "refuse a projection that is somehow ahead, too" in {
    // Not "at least as new": a projection ahead of the state cannot happen, and treating it as
    // acceptable would hide the day it does.
    decide(projected = 8, state = 7) shouldBe Some(SkipReason.StaleProjection)
  }

  it should "leave an unfocused host alone" in {
    // §22: "Hintergrundupdates stehlen weder Page- noch Textarea-Fokus." A selection written into
    // a host nobody is in is visible, unasked for, and scrolls the page to it.
    decide(focusWithin = false) shouldBe Some(SkipReason.NotFocused)
  }

  it should "write into an unfocused host when asked explicitly" in {
    // "Select all" from a menu is a real request. It still does not move the focus -- that is a
    // separate decision, and `FocusPolicy` makes it.
    decide(focusWithin = false, intent = WriteIntent.Explicit) shouldBe None
  }

  it should "refuse where there is no selection API at all" in {
    // §15.4: a shadow root without `getSelection`. The document's selection would report the
    // shadow host and hide everything inside -- a value that looks plausible and means nothing.
    decide(capability = SelectionCapability.ShadowUnsupported) shouldBe
      Some(SkipReason.Unsupported)
    decide(capability = SelectionCapability.Detached) shouldBe Some(SkipReason.Unsupported)
  }

  it should "work in a shadow root that has one" in {
    decide(capability = SelectionCapability.ShadowNative) shouldBe None
  }

  it should "report the missing capability before anything else" in {
    // Order is a message. "Not focused" invites the caller to focus and try again; with no
    // selection API that would be an endless invitation.
    decide(
      projected = 1,
      state = 7,
      focusWithin = false,
      capability = SelectionCapability.ShadowUnsupported
    ) shouldBe Some(SkipReason.Unsupported)
  }

  // ---------------------------------------------------------------------------------------
  // Fokus
  // ---------------------------------------------------------------------------------------

  "The focus" should "never move on a background restore" in {
    // §22, and the sentence this whole type exists for.
    FocusPolicy.mayTakeFocus(
      FocusIntent.SelectionOnly,
      bookmarkHadFocus = true,
      focusWithin = false,
      focusable = true
    ) shouldBe false
  }

  it should "come back to a dialog that took it, and not to one that did not" in {
    // §22: "Schliessen stellt Fokus nur im passenden Interaktionskontext wieder her." A dialog
    // opened from a toolbar while the caret sat in the text should give it back; one opened from
    // a sidebar the user was working in should not.
    FocusPolicy.mayTakeFocus(
      FocusIntent.IfItWasOurs,
      bookmarkHadFocus = true,
      focusWithin = false,
      focusable = true
    ) shouldBe true

    FocusPolicy.mayTakeFocus(
      FocusIntent.IfItWasOurs,
      bookmarkHadFocus = false,
      focusWithin = false,
      focusable = true
    ) shouldBe false
  }

  it should "follow an explicit request regardless of what came before" in {
    FocusPolicy.mayTakeFocus(
      FocusIntent.Always,
      bookmarkHadFocus = false,
      focusWithin = false,
      focusable = true
    ) shouldBe true
  }

  it should "stay put when it is already inside" in {
    // Re-focusing an element that has focus is not free: browsers scroll it into view.
    FocusPolicy.mayTakeFocus(
      FocusIntent.Always,
      bookmarkHadFocus = true,
      focusWithin = true,
      focusable = true
    ) shouldBe false
  }

  it should "not be forced onto a host that cannot take it" in {
    // §22: "Fokusfaehigkeit und Editierbarkeit sind getrennte Entscheidungen." A readonly view
    // without a tabindex is not a place to put the keyboard.
    FocusPolicy.mayTakeFocus(
      FocusIntent.Always,
      bookmarkHadFocus = true,
      focusWithin = false,
      focusable = false
    ) shouldBe false
  }

  "A restore that takes the focus" should "write the selection without waiting for it" in {
    // The focus call and the selection write happen in the same turn; `focusWithin` is still
    // false when the gate runs. Asking it to follow focus here would skip every restore.
    FocusPolicy.writeIntent(takesFocus = true) shouldBe WriteIntent.Explicit
    FocusPolicy.writeIntent(takesFocus = false) shouldBe WriteIntent.FollowFocus
  }

  // ---------------------------------------------------------------------------------------
  // Bookmarks
  // ---------------------------------------------------------------------------------------

  private val run   = NodeId("t1")
  private val other = NodeId("t2")

  private def bookmark(
      anchorOffset: Int = 2,
      focusOffset: Int = 5,
      revision: Long = 3,
      hadFocus: Boolean = true
  ): SelectionBookmark =
    SelectionBookmark.of(
      RangeSelection(Point.textBefore(run, anchorOffset), Point.textBefore(run, focusOffset)),
      Revision(revision),
      hadFocus
    )

  "A selection bookmark" should "come back unchanged through an unchanged document" in {
    val mapping = RevisionMapping.identity(Revision(3))

    bookmark().resolve(mapping) shouldBe
      Right(RangeSelection(Point.textBefore(run, 2), Point.textBefore(run, 5)))
  }

  it should "map anchor and focus independently" in {
    // §11 says exactly that, and it is why this holds two bookmarks. A pair collapsed into one
    // position would come back as a caret where the user had a range.
    val mapping = RevisionMapping(
      Revision(3),
      Revision(4),
      PositionMapping.of() {
        case Point.Text(node, offset, affinity) if offset == 5 =>
          MappedPoint.Preserved(Point.Text(node, 9, affinity))
        case point => MappedPoint.Preserved(point)
      }
    )

    bookmark().resolve(mapping) shouldBe
      Right(RangeSelection(Point.textBefore(run, 2), Point.textBefore(run, 9)))
  }

  it should "accept the replacement boundary, unlike an upload bookmark" in {
    // The difference `MappedPoint.Displaced` exists for: "Ein Caret darf auf die Grenze
    // zurueckfallen -- der Cursor muss irgendwo stehen. Ein Upload-Bookmark darf das nicht."
    val boundary = Point.childrenBefore(other, 0)
    val mapping  = RevisionMapping(
      Revision(3),
      Revision(4),
      PositionMapping.of(Set(run))(_ => MappedPoint.Displaced(boundary))
    )

    bookmark().resolve(mapping) shouldBe Right(RangeSelection(boundary, boundary))
    Bookmark(Point.textBefore(run, 2), Revision(3)).resolve(mapping).isLeft shouldBe true
  }

  it should "expire against a mapping that starts somewhere else" in {
    // §11: "wenn benoetigte Maps nicht mehr verfuegbar sind, entsteht ein expliziter
    // ExpiredBookmark statt einer falschen Einfuegung."
    bookmark(revision = 3).resolve(RevisionMapping.identity(Revision(4))).isLeft shouldBe true
  }

  it should "remember whether the editor had the focus" in {
    bookmark(hadFocus = true).hadFocus shouldBe true
    bookmark(hadFocus = false).hadFocus shouldBe false
  }

  it should "carry the revision it was taken at" in {
    bookmark(revision = 11).revision shouldBe Revision(11)
  }

  // ---------------------------------------------------------------------------------------
  // Diagnosen
  // ---------------------------------------------------------------------------------------

  "A position problem" should "name the node it is about" in {
    PositionProblem.NotProjected(run).render should include("t1")
    PositionProblem.InsideAtom(run).render should include("t1")
    PositionProblem.NoSuchNode(run).render should include("t1")
  }

  it should "say plainly when a position is not the editor's" in {
    PositionProblem.OutsideHost.render should include("ausserhalb")
    PositionProblem.Unowned.render should include("kein Dokumentknoten")
  }

  "A skipped write" should "give a reason a reader can act on" in {
    SkipReason.StaleProjection.render should include("Projektion")
    SkipReason.NotFocused.render should include("Fokus")
    SkipReason.AlreadyThere.render should include("bereits")
  }
}

package ember.editor.richtext

import ember.editor.core.*

/** Adjacent runs that mean the same thing grow back together.
  *
  * §8.2 states the rule and the reason in one line: "Direkt benachbarte, zusammenfuehrbare
  * TextNodes desselben Parents mit identischen normalisierten Marks und sonstigen semantischen
  * Eigenschaften werden zu einem maximalen Textlauf normalisiert."
  *
  * Without it, formatting would be a one-way street. Bolding a word cuts one run into three;
  * un-bolding it puts the marks back but leaves the cuts, and the next cycle cuts again. §8.2 names
  * that outcome directly: "Wiederholtes Formatieren/Entformatieren darf daher keine anwachsende
  * Fragmentierung hinterlassen; erneute Normalisierung ist ein No-op."
  *
  * ==Why a transform and not a step in the formatting code==
  *
  * §8.2, last sentence: "Dies wird durch eine Transform-Regel im Rich-Text-Modul umgesetzt, nicht
  * durch eine DOM-Heuristik." As a transform it runs in the same transaction as whatever caused the
  * fragmentation -- so the merge is part of the same commit, and un-formatting does not produce a
  * second undo step (P12, acceptance). It also runs after *every* edit, not only after formatting:
  * a delete that joins two blocks leaves the same kind of seam.
  *
  * ==What is not merged==
  *
  * §8.2: "Nicht zusammengefuehrt wird ueber Paragraph-, Link-, Break- oder Atomgrenzen hinweg, bei
  * verschiedenen Marks/semantischen Eigenschaften oder bei einem Node-Typ mit ausdruecklich nicht
  * zusammenfuehrbarer Semantik."
  *
  * All four fall out of one rule: only two [[TextNode]]s that are *directly* adjacent children of
  * the same parent and carry equal [[MarkSet]]s are merged. A different parent fails the first
  * test; a break or an atom between them fails adjacency; different marks fail the last. Nothing
  * here needs to know what a link or an atom is -- which is why P14 and P16 will not have to come
  * back and amend it.
  *
  * ==What it costs, and who pays==
  *
  * `MergeText` keeps the left ID and shifts points from the right one (§11). Carets, backward
  * ranges and bookmarks therefore survive; a caret that was in the right-hand run comes out at the
  * same character. That is a property of the primitive, not of this rule -- this rule only has to
  * pick the right pairs.
  *
  * Deferring the merge during a protected composition (§8.2, §15.3) is not here: composition is
  * P23, and there is nothing yet to defer for.
  */
private[richtext] object TextRunNormalization:

  /** Merges a changed run with a neighbour that means the same thing.
    *
    * ==Why the rule hangs on the run and not on its block==
    *
    * The obvious shape would be "for each paragraph, walk its children" -- and it would never run.
    * §3.4 is explicit that ancestors merely on the path of a change are not transform candidates:
    * `ChangeSet.touchedAncestors` exists precisely to keep them out. Changing a run's marks does
    * not make its paragraph dirty, so a rule bound to the paragraph would sit there through every
    * formatting change without ever being asked.
    *
    * Bound to the run, it is asked exactly when something happened to a run -- which is also the
    * only time a new seam can appear.
    *
    * ==One merge per pass==
    *
    * `mergeText` changes the child list underneath, so a loop here would be walking a stale vector.
    * It merges once and lets the transform loop come back: the merged node stays dirty, §10 re-runs
    * the rule on it, and the result is the maximal run §8.2 asks for. Re-running a rule until
    * nothing changes is what that loop is for.
    *
    * Merging leftwards first is not arbitrary either -- `MergeText` keeps the left ID (§11), so
    * going left keeps the ID that has been there longest.
    */
  val rule: Transform[TextNode] = new Transform[TextNode]:
    val name     = "rich-text.merge-adjacent-runs"
    val nodeType = TextNode

    /** After the rules that create and remove runs. Merging first and splitting again would be work
      * done twice, and §10's budget counts rounds.
      */
    override val phase = TransformPhase.Late

    def transform(node: TextNode, scope: TransformScope): Unit =
      // §8.2 and §15.3: not during a protected composition. A merge replaces the inner text node
      // of a run, and a browser composing into that node loses the composition -- with no event
      // and no way back. The seam is closed when the session ends, where the same rule runs
      // again on a state nobody is typing into.
      if scope.meta.tags.contains(TransactionMeta.CompositionTag) then ()
      else
        val document = scope.document

        val siblings = document.parentOf(node.id).map(document.childrenOf).getOrElse(Vector.empty)
        val index    = siblings.indexOf(node.id)

        if index >= 0 then
          val left  = if index > 0 then siblings.lift(index - 1) else None
          val right = siblings.lift(index + 1)

          if left.exists(mergeable(document, _, node.id)) then
            scope.mergeText(left.get, node.id): Unit
          else if right.exists(mergeable(document, node.id, _)) then
            scope.mergeText(node.id, right.get): Unit

  /** Two children that may become one.
    *
    * Note what is *not* asked: whether either is empty. An empty run next to a non-empty one is
    * removed by `DropRedundantEmptyText`, and merging it here would do the same job in a way that
    * also moves a caret sitting in it.
    */
  /** Whether two children may become one run.
    *
    * Visible in the package because a caller that '''creates''' a seam has to close it. Removing a
    * node from between two runs changes neither of them, so this rule -- bound to `TextNode` -- is
    * never asked about it; see [[TextEditing.removeAtom]].
    */
  private[richtext] def mergeable(document: DocumentRead, left: NodeId, right: NodeId): Boolean =
    (document.node(left), document.node(right)) match
      case (Some(first: TextNode), Some(second: TextNode)) => first.marks == second.marks
      case _                                               => false

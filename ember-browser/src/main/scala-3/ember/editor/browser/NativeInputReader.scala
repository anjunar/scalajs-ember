package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

/** What a native change turned out to be. */
enum NativeImport:

  /** The DOM says what the model already says. */
  case Nothing

  /** One text run differs by one splice. */
  case Text(node: NodeId, splice: TextSplice)

  /** The run's text changed, and the browser also left more than one text node in its wrapper.
    *
    * Firefox does this when an astral character is inserted natively: "Hallo Welt" becomes three
    * nodes, "Hallo" + the emoji + " Welt". The '''text''' is importable -- a run's content is the
    * concatenation, and the marks did not change -- but the view needs rebuilding afterwards, or
    * the next splice writes into one node while the others stand (§15.4).
    */
  case SplitRun(node: NodeId, splice: TextSplice)

  /** The DOM changed in a way this reader cannot express as a document change.
    *
    * §15.4: "Rohtext bzw. der letzte Source-Draft bleibt fuer Recovery verfuegbar." So the text
    * that was found travels with the report -- losing what someone just typed because its
    * structure was unexpected is the one outcome worth any amount of trouble to avoid.
    *
    * Repairing it is P23's Recovery. What P22 owes is to notice and to say so.
    */
  case Unimportable(reason: String, salvage: Option[String])

/** Reads a change the browser already made.
  *
  * ==When this runs==
  *
  * §15.2, for a `beforeinput` that was not cancelable or did not arrive: "Native Aenderung
  * beobachten; `input` liest begrenzten betroffenen Bereich und erzeugt eine NativeInput-Tx." The
  * DOM is ahead of the model at that point, and the model has to be brought to it -- not the
  * other way round, which would throw away what the user just typed.
  *
  * ==Why the affected area is one run==
  *
  * Because that is what a normal native edit touches, and because more would be a guess. The
  * caret is in a run, the browser changed that run's text, and a splice says the difference
  * exactly. Anything wider -- a new element, a split wrapper, a `<br>` the browser felt like
  * adding -- is not a text change at all, and pretending it is would produce a document that says
  * something nobody typed. Those cases are reported, with the text, for P23's recovery.
  */
final class NativeInputReader(positions: DomPositionMap, scope: BrowserScope):

  /** Compares the run the caret sits in against the document. */
  def read(document: DocumentRead): NativeImport =
    scope.selection.flatMap(native => Option(native.anchorNode)) match
      case None => NativeImport.Nothing
      case Some(anchor) =>
        if !scope.contains(anchor) then NativeImport.Nothing
        else
          positions.nodeAt(anchor, document) match
            case None => NativeImport.Nothing
            case Some(id) => readRun(id, document)

  /** Compares one named run. For a caller that knows which one changed. */
  def readRun(id: NodeId, document: DocumentRead): NativeImport =
    document.node(id) match
      case Some(run: TextNode) =>
        (positions.textNodeOf(id), positions.hostOf(id)) match
          case (Right(textNode), Right(wrapper)) =>
            val inNode = textNode.data
            val whole  = wrapper.textContent

            // The projection owns exactly one text node per run (§15.1). If the wrapper now holds
            // more than it, the browser built structure, and a splice would leave that structure
            // standing next to a corrected model.
            if whole != inNode then
              // Only text below the wrapper: the run's content is still just text, so it can be
              // imported -- the view is what needs putting back together.
              if onlyText(wrapper) then
                NativeInputReader.delta(run.text, whole) match
                  case None         => NativeImport.Nothing
                  case Some(splice) => NativeImport.SplitRun(id, splice)
              else
                NativeImport.Unimportable(
                  s"`${id.value}` enthaelt mehr als Text.",
                  Some(whole)
                )
            else
              NativeInputReader.delta(run.text, inNode) match
                case None         => NativeImport.Nothing
                case Some(splice) => NativeImport.Text(id, splice)

          case _ =>
            NativeImport.Unimportable(s"`${id.value}` ist nicht projiziert.", None)

      case Some(_) =>
        // Typing into an empty paragraph makes the browser create a text node under the block
        // itself -- there is no run to splice, and inventing one here would guess at marks.
        NativeImport.Unimportable(
          s"Die native Aenderung liegt in `${id.value}` und nicht in einem Textlauf.",
          textUnder(id)
        )

      case None => NativeImport.Unimportable(s"`${id.value}` gibt es im Dokument nicht.", None)

  private def textUnder(id: NodeId): Option[String] =
    positions.hostOf(id).toOption.map(_.textContent)

  /** Whether an element holds nothing but text nodes, however many. */
  private def onlyText(element: dom.Element): Boolean =
    var index = 0
    var plain = true
    while index < element.childNodes.length do
      if !DomKinds.isText(element.childNodes(index)) then plain = false
      index += 1
    plain

object NativeInputReader:

  /** The one splice that turns `before` into `after`.
    *
    * ==Why minimal and not "replace everything"==
    *
    * §15.1 spends its argument on exactly this: a splice reaches `CharacterData.replaceData` for
    * the changed range, a full assignment rewrites the whole run. In a long paragraph that is the
    * difference the whole projection was built for -- and it is also the difference between a
    * caret that stays put and one that jumps to the end.
    *
    * ==Surrogate pairs==
    *
    * The boundaries are pulled back off a surrogate pair. UTF-16 is the offset measure (§11), and
    * an offset in the middle of a pair addresses half a codepoint: the splice would produce two
    * broken strings, and `Point.validateIn` would rightly reject every position in the run
    * afterwards.
    *
    * This is not grapheme segmentation -- combining marks, ZWJ sequences and flags are split
    * happily here, because a splice does not have to respect them. Only the UTF-16 encoding has
    * to stay well formed.
    */
  def delta(before: String, after: String): Option[TextSplice] =
    if before == after then None
    else
      val shorter = math.min(before.length, after.length)

      var prefix = 0
      while prefix < shorter && before.charAt(prefix) == after.charAt(prefix) do prefix += 1
      // A high surrogate just before the boundary means the boundary is inside a pair: in well
      // formed text a high surrogate is never the last unit of a string.
      if prefix > 0 && Character.isHighSurrogate(before.charAt(prefix - 1)) then prefix -= 1

      var suffix = 0
      while suffix < shorter - prefix &&
        before.charAt(before.length - 1 - suffix) == after.charAt(after.length - 1 - suffix)
      do suffix += 1
      // Symmetrically: a low surrogate as the first unit of the common suffix.
      if suffix > 0 && Character.isLowSurrogate(before.charAt(before.length - suffix)) then
        suffix -= 1

      Some(
        TextSplice(
          start = prefix,
          deleteCount = before.length - prefix - suffix,
          inserted = after.substring(prefix, after.length - suffix)
        )
      )

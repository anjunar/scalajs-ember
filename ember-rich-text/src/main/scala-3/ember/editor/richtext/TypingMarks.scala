package ember.editor.richtext

import ember.editor.core.*

/** What the next keystroke will be marked with.
  *
  * §11 defines it precisely: "`TypingMarks` ist ein transaktionales StateField des
  * Rich-Text-Moduls, mit `Inherit` oder explizitem `MarkSet` an einer gemappten Caretposition."
  */
enum TypingMarksValue:

  /** Take whatever the run at the caret already has. The state after every caret jump. */
  case Inherit

  /** A choice the user made, at the position they made it.
    *
    * The [[at]] point is not decoration. Without it the field could not tell "the caret moved
    * itself because I typed" from "the user clicked somewhere else" -- and §11 requires exactly
    * that distinction: "Ein expliziter Caretsprung oder Range-Wechsel setzt wieder auf
    * kontextabhaengiges Inherit; blosses Mapping desselben Carets durch eigene Eingabe erhaelt die
    * explizite Wahl."
    */
  case Explicit(marks: MarkSet, at: Point)

/** The marks that apply to the next insertion at a collapsed caret.
  *
  * ==Why this is a state field and not a variable somewhere==
  *
  * Because it has to survive a commit, take part in mapping, and come back with an undo. §9 calls
  * that a `StateField`; anything else would be a second kind of state next to the session, and §5
  * has only one.
  *
  * ==Why it is restored by history==
  *
  * §11, last sentence: "Undo/Redo darf die fuer die naechste Eingabe wirksamen Marks nicht
  * zufaellig aus der DOM-Darstellung ableiten." A field that is not restored leaves exactly that
  * guess as the only option -- so this one declares [[HistoryRestorePolicy.Restore]], and
  * `ember-history` carries its value in every snapshot.
  *
  * ==Why `Keep` and not `Reset` on document change==
  *
  * `Reset` would clear the choice on the very next keystroke, which is the one it was made for. The
  * decision belongs in [[reduce]], where the mapping is available and the field can tell a caret
  * that moved by itself from one that was moved.
  */
object TypingMarks extends StateField[TypingMarksValue]:

  val name: String = "rich-text.typing-marks"

  val initial: TypingMarksValue = TypingMarksValue.Inherit

  override val onDocumentChange: DocumentChangePolicy = DocumentChangePolicy.Keep

  override val onHistoryRestore: HistoryRestorePolicy = HistoryRestorePolicy.Restore

  /** Keeps an explicit choice exactly as long as the caret stayed where it was made.
    *
    * The stored point is mapped through the transaction's own mapping first. That is the whole
    * trick: typing a character moves the caret from offset 4 to 5 *and* maps the stored point from
    * 4 to 5, so the two still agree and the choice survives. Clicking somewhere else moves the
    * caret without mapping anything, the two disagree, and the choice is gone.
    */
  override def reduce(
      current: TypingMarksValue,
      candidate: CommitCandidate
  ): Either[EditorError, TypingMarksValue] =
    current match
      case TypingMarksValue.Inherit             => Right(TypingMarksValue.Inherit)
      case TypingMarksValue.Explicit(marks, at) =>
        val mapped = candidate.mapping.map(at).point
        caretOf(candidate.selection) match
          case Some(caret) if caret == mapped => Right(TypingMarksValue.Explicit(marks, mapped))
          case _                              => Right(TypingMarksValue.Inherit)

  /** The marks a new run at `caret` should carry.
    *
    * An explicit choice wins. Otherwise the run at the caret decides -- that is what "inherit"
    * means, and it is context-dependent by design (§11).
    */
  def marksFor(
      value: TypingMarksValue,
      document: DocumentRead,
      caret: Option[Point]
  ): MarkSet =
    value match
      case TypingMarksValue.Explicit(marks, _) => marks
      case TypingMarksValue.Inherit            => inherited(document, caret)

  /** The marks of the run the caret stands in. Empty where there is no run. */
  private def inherited(document: DocumentRead, caret: Option[Point]): MarkSet =
    caret
      .collect { case Point.Text(node, _, _) => node }
      .flatMap(document.node)
      .collect { case run: TextNode => run.marks }
      .getOrElse(MarkSet.empty)

  private def caretOf(selection: Option[Selection]): Option[Point] =
    selection.collect { case range: RangeSelection if range.isCollapsed => range.focus }

  /** Reads the field and resolves it against the draft. The form command handlers want. */
  private[richtext] def effective(scope: TransformScope): MarkSet =
    marksFor(scope.field(TypingMarks), scope.document, caretOf(scope.selection))

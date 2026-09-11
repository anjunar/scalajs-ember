package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

/** What was on the page before anything was claimed.
  *
  * ==Why this is captured and not read later==
  *
  * §17 step 2 is emphatic about the timing: "'''Vor dem ersten Claim''', der Werte ueberschreiben
  * koennte, erfasst die aeussere Form-Boundary die tatsaechliche `textarea.value`,
  * `selectionStart`, `selectionEnd`, `selectionDirection` und Fokus. Attribute oder `defaultValue`
  * reichen dafuer nicht."
  *
  * The distinction between `value` and `defaultValue` is the whole point. A user who typed into the
  * textarea before the script ran changed `value`; the attribute still holds what the server sent.
  * Reading the attribute would silently discard their input, and reading `value` '''after''' a
  * claim would read what the claim wrote.
  *
  * @param sourceValue
  *   the live `textarea.value`, not the attribute.
  * @param focused
  *   whether the field had focus. §17 step 7: only then may a translated selection be written into
  *   the rich view -- "Ansonsten keine Fokus-/Selection-Schreibaktion."
  * @param composing
  *   whether an IME composition was in progress. Only ever `true` when a composition started
  *   '''after''' the listeners were attached; see [[HydrationSnapshot.unknownInputSession]].
  */
final case class HydrationSnapshot(
    sourceValue: String,
    selectionStart: Int,
    selectionEnd: Int,
    selectionDirection: SelectionDirection,
    focused: Boolean,
    composing: Boolean
):

  /** Whether the user changed the source before the script ran.
    *
    * §17 step 4: "Falls Source seit SSR geaendert wurde, bleibt sie zunaechst unangetastet."
    */
  def differsFrom(served: String): Boolean = sourceValue != served

  /** The selection as a range, if the field was focused. */
  def selection: Option[(Int, Int)] = Option.when(focused)((selectionStart, selectionEnd))

object HydrationSnapshot:

  private def directionOf(textarea: dom.HTMLTextAreaElement): String =
    val raw = textarea.asInstanceOf[scala.scalajs.js.Dynamic].selectionDirection
    if scala.scalajs.js.isUndefined(raw) || raw == null then "none" else raw.toString

  /** Reads the live state of a textarea.
    *
    * Takes the element rather than looking it up: the boundary hands over the host it is about to
    * bind, and looking one up by selector would be a second answer to a question the caller already
    * has.
    */
  def of(textarea: dom.HTMLTextAreaElement): HydrationSnapshot =
    val focused = textarea.ownerDocument.activeElement eq textarea

    HydrationSnapshot(
      sourceValue = textarea.value,
      selectionStart = textarea.selectionStart,
      selectionEnd = textarea.selectionEnd,
      // `scalajs-dom` 2.8.1 kennt `selectionDirection` nicht -- die Eigenschaft gibt es in
      // jeder Ziel-Engine, nur die Fassade hat sie nicht. Der dynamische Zugriff ist hier die
      // schmale Stelle und keine allgemeine Gewohnheit.
      selectionDirection = TextSelectionDirection.parse(directionOf(textarea)),
      focused = focused,
      // Eine Composition, die '''vor''' dem Attach begann, laesst sich nicht nachtraeglich
      // abfragen (§17.2). `false` hiesse hier "keine" und waere geraten -- deshalb steht die
      // Unsicherheit in [[unknownInputSession]] und nicht in diesem Feld.
      composing = false
    )

  /** Whether the field has to be treated as if a composition might be running.
    *
    * §17 step 2: "Eine bereits vor Attach begonnene Composition laesst sich nicht zuverlaessig
    * nachtraeglich abfragen. Deshalb wird ein bereits fokussiertes Source-Feld konservativ erst
    * nach Blur oder einer ausdruecklichen Wechselaktion erweitert."
    *
    * So focus alone is enough to defer. Not because focus means composing, but because a focused
    * field is the only place a composition could already be running, and there is no way to ask.
    * Guessing "no" would mean replacing text mid-composition.
    */
  def unknownInputSession(snapshot: HydrationSnapshot): Boolean =
    snapshot.focused || snapshot.composing

/** Reads the DOM's `selectionDirection` as the core's [[SelectionDirection]].
  *
  * The core already has the concept (§11), and a second enum for the same thing would need a
  * conversion at every use -- which is where the two would eventually disagree. What differs is
  * only the vocabulary: the DOM says `"none"` where the core says `Collapsed`, and for a textarea
  * selection the two mean the same thing.
  *
  * §17 step 7 asks for the direction to survive into the rich view, and P20 names it in its test
  * list. Dropping it would turn every restored selection forward, and the next arrow key would move
  * the wrong end.
  */
object TextSelectionDirection:

  def parse(raw: String): SelectionDirection = raw match
    case "forward"  => SelectionDirection.Forward
    case "backward" => SelectionDirection.Backward
    case _          => SelectionDirection.Collapsed

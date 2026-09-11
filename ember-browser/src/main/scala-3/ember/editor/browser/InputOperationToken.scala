package ember.editor.browser

/** One native operation, identified well enough to be processed exactly once.
  *
  * The `sequence` is what makes two consecutive backspaces two operations rather than one. An
  * `inputType` alone would collapse them, and the second `input` event would then be taken for the
  * echo of the first.
  */
final case class InputOperationToken(inputType: String, sequence: Long)

/** Which native operations have already been taken over.
  *
  * ==The problem==
  *
  * §15.2 asks for two things that are the same thing seen from two sides:
  *
  *   - `input`: "bereits bearbeitete Aktionen deduplizieren."
  *   - Copy/Cut/Paste: "dieselbe Aktion aus Clipboard-Event und `beforeinput` genau einmal
  *     verarbeiten."
  *
  * One user action reaches the editor more than once. A paste arrives as `paste` '''and''' as
  * `beforeinput[insertFromPaste]`; a keystroke arrives as `beforeinput` and then as `input`.
  * Handling both would insert the text twice, and the second insertion looks exactly like a
  * deliberate edit.
  *
  * ==Why not a boolean==
  *
  * Because events are not reliably paired. A `beforeinput` that is not cancelable is followed by an
  * `input` that '''must''' be imported; a cancelable one that was handled is followed by an `input`
  * that must be ignored. And a browser may fire several `beforeinput` events before one `input`
  * (§15.2 names autocorrect as that case). A flag cannot tell those apart; a small log of claims
  * can.
  *
  * The log is bounded. An unmatched claim -- a `beforeinput` whose `input` never came -- would
  * otherwise sit there and swallow the next real event of the same type.
  */
final class InputOperationLog(limit: Int = InputOperationLog.DefaultLimit):

  private var claims   = Vector.empty[InputOperationToken]
  private var sequence = 0L

  /** Records that this operation was handled, and hands back its token. */
  def record(inputType: String): InputOperationToken =
    sequence += 1
    val token = InputOperationToken(inputType, sequence)
    claims = (claims :+ token).takeRight(limit)
    token

  /** Takes back the oldest claim for this type. `true` when there was one.
    *
    * Oldest first, because events arrive in order: the `input` that follows belongs to the earliest
    * `beforeinput` that has not been answered yet.
    */
  def consume(inputType: String): Boolean =
    claims.indexWhere(_.inputType == inputType) match
      case -1    => false
      case index =>
        claims = claims.patch(index, Nil, 1)
        true

  /** Consumes any claim at all, whatever its type.
    *
    * For the engines that report a different `inputType` on `input` than on `beforeinput` -- or
    * none. Used only where the controller already knows an operation is outstanding.
    */
  def consumeAny(): Boolean =
    if claims.isEmpty then false
    else
      claims = claims.tail
      true

  def outstanding: Int = claims.length

  def clear(): Unit = claims = Vector.empty

object InputOperationLog:

  /** Enough for the longest burst a browser produces for one user action, and no more. */
  val DefaultLimit: Int = 8

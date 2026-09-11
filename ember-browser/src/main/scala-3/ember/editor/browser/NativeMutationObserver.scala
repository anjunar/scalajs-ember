package ember.editor.browser

import org.scalajs.dom

import scala.scalajs.js

/** What a batch of DOM mutations amounted to.
  *
  * Counts and kinds, not the records themselves. What the editor does with them is to ask whether
  * the DOM still says what the document says -- and for that question the records are a trigger,
  * not evidence.
  */
final case class MutationSummary(
    characterData: Int,
    childList: Int,
    attributes: Int
):
  def total: Int      = characterData + childList + attributes
  def isEmpty: Boolean = total == 0

  def ++(other: MutationSummary): MutationSummary =
    MutationSummary(
      characterData + other.characterData,
      childList + other.childList,
      attributes + other.attributes
    )

object MutationSummary:
  val empty: MutationSummary = MutationSummary(0, 0, 0)

/** Watches the editing host for changes nobody announced.
  *
  * ==Why an observer at all, when `input` already reports==
  *
  * Because `input` reports what the browser did on purpose. §15.4 is about the rest: an extension
  * that writes into the page, a translation tool, a password manager, a browser feature nobody
  * documented. "Die Runtime darf von nativen Mutationen getrennte oder ersetzte Hosts nicht weiter
  * als gueltig behandeln."
  *
  * ==Why the records are only a trigger==
  *
  * §15.2 rules out the obvious approach in one line: "ein synchrones Boolean `suppress`
  * unterscheidet eigene und native Mutationen nicht zuverlaessig", because delivery is
  * asynchronous -- our own writes and a native one can arrive in the same batch, long after the
  * flag was reset.
  *
  * Predicting the exact list of writes a projection will make and subtracting it is the other
  * obvious approach, and it is worse: it is a second model of the renderer, and when the two
  * disagree the editor believes the prediction.
  *
  * So the records answer only "did anything happen", and the '''document''' answers "is the view
  * still right" -- through [[EditorHydration.check]], the same comparison that decides whether a
  * server-rendered page may be claimed. One description of what the DOM should look like, used in
  * both places.
  */
final class NativeMutationObserver(host: dom.Element):

  private var observer: dom.MutationObserver = null
  private var pending: MutationSummary       = MutationSummary.empty

  def isObserving: Boolean = observer != null

  def start(): Unit =
    if observer == null then
      observer = new dom.MutationObserver((records, _) => collect(records))
      observer.observe(
        host,
        new dom.MutationObserverInit {
          subtree = true
          childList = true
          characterData = true
          attributes = true
        }
      )

  /** Everything seen since the last call, including what is still queued for delivery.
    *
    * `takeRecords` is what makes the "vor/nach Projektion" of §15.2 possible at all: it drains the
    * queue synchronously, so a caller can draw a line before its own writes and another after
    * them without waiting for a microtask.
    */
  def take(): MutationSummary =
    if observer != null then collect(observer.takeRecords())
    val seen = pending
    pending = MutationSummary.empty
    seen

  /** Drops what has been seen without reporting it. For the start of an operation. */
  def drain(): Unit = take(): Unit

  def stop(): Unit =
    if observer != null then
      observer.disconnect()
      observer = null
      pending = MutationSummary.empty

  private def collect(records: js.Array[dom.MutationRecord]): Unit =
    var index         = 0
    var characterData = 0
    var childList     = 0
    var attributes    = 0

    while index < records.length do
      records(index).`type` match
        case "characterData" => characterData += 1
        case "childList"     => childList += 1
        case _               => attributes += 1
      index += 1

    pending = pending ++ MutationSummary(characterData, childList, attributes)

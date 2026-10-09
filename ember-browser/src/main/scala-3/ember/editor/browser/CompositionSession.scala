package ember.editor.browser

import ember.editor.core.*

/** One run of native text input, from `compositionstart` to its completion.
  *
  * ==Why a session and not a flag==
  *
  * §15.3 asks for four things a boolean cannot carry: "Die laufende Composition besitzt eine
  * Session-ID, Ausgangsrevision, gemappte Selection und die letzte erfasste native Eingabe."
  *
  * The revision is what makes the completion check possible -- "ein revisionierter Vergleich des
  * erfassten Textes verhindert doppelte Einfuegung". The id is what lets a late event be recognised
  * as belonging to a composition that has already ended, which is the shape of most of the browser
  * bugs §15.2 lists.
  *
  * @param captured
  *   the last text read out of the DOM, and the node it came from. Compared on completion so that a
  *   final `input` after `compositionend` does not insert the same text twice.
  */
final case class CompositionSession(
    id: Long,
    baseRevision: Revision,
    region: ProtectedRegion,
    selection: Option[Selection],
    captured: Option[(NodeId, String)]
):

  def withCapture(node: NodeId, text: String): CompositionSession =
    copy(captured = Some((node, text)))

  /** Whether this text has already been taken from this node. */
  def alreadyCaptured(node: NodeId, text: String): Boolean = captured.contains((node, text))

  /** The nodes the write barrier covers, empty when the whole host is protected. */
  def protectedNodes: Vector[NodeId] = region match
    case ProtectedRegion.Blocks(nodes) => nodes
    case ProtectedRegion.WholeHost     => Vector.empty

object CompositionSession:

  private var counter: Long = 0L

  /** Opens a session for the state a composition starts in. */
  def start(
      document: DocumentRead,
      selection: Option[Selection],
      revision: Revision
  ): CompositionSession =
    counter += 1
    CompositionSession(
      id = counter,
      baseRevision = revision,
      region = CompositionRegion.of(document, selection),
      selection = selection,
      captured = None
    )

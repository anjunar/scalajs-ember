package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import ui.core.render.{DomNodes, HostMutationGuard}
import ui.core.state.Disposable

/** The write barrier around a running composition.
  *
  * ==What it is and what it is not==
  *
  * §15.3: "Der UI-HostMutationGuard verhindert Textschreibzugriffe, Moves, Remounts und
  * Entfernung in diesem Bereich vor Seiteneffekten, der SelectionPort verhindert
  * Selection-Writes."
  *
  * So this is a lease on a region of the '''view''', not a lock on the document and not a
  * scheduler. It stops the projection from writing where a browser is composing text -- because a
  * write there destroys the composition, and the browser gives no warning and no way back.
  *
  * The document is guarded elsewhere and earlier: [[CompositionGate]] refuses independent
  * transactions before they commit. This is the second line, for anything that reaches a host
  * without going through a commit -- a feature's own view, a late animation, a mistake.
  *
  * ==Why it protects blocks and not the whole host by default==
  *
  * Because a composition in one paragraph should not stop the rest of the document from updating.
  * A remote change three paragraphs down is not the composition's business, and §15.3 draws the
  * barrier around "den vollstaendigen anfaenglichen Ersetzungsbereich" -- not around everything.
  * Where that area cannot be named, [[ProtectedRegion.WholeHost]] says so and the whole host goes
  * under the lease.
  */
final class ProjectionWriteGuard(view: DocumentView, scope: BrowserScope):

  private var leases = Vector.empty[Disposable]

  def isActive: Boolean = leases.nonEmpty

  /** Takes the lease. Idempotent: a second call while one is held changes nothing. */
  def protect(region: ProtectedRegion): Unit =
    if leases.isEmpty then
      leases = region match
        case ProtectedRegion.WholeHost     => Vector(HostMutationGuard.protect(DomNodes.wrap(scope.host)))
        case ProtectedRegion.Blocks(nodes) =>
          val hosts = nodes.flatMap(hostOf)
          // A block that is not projected cannot be protected, and a barrier with holes in it is
          // worse than none: it would look like protection while the gap is exactly where the
          // projection writes. So an incomplete set falls back to the whole host.
          if hosts.length == nodes.length && hosts.nonEmpty then hosts.map(HostMutationGuard.protect)
          else Vector(HostMutationGuard.protect(DomNodes.wrap(scope.host)))

  /** Releases it. Safe to call when nothing is held.
    *
    * §15.3's order matters here: "Release the lease before retrying a projection or unmounting its
    * host" -- ui-core's own words. Everything that follows a composition, the final projection
    * included, happens after this.
    */
  def release(): Unit =
    leases.foreach(_.dispose())
    leases = Vector.empty

  private def hostOf(node: NodeId): Option[ui.core.render.HostNode] =
    view.componentFor(node).filter(_.isBound).map(_.host)

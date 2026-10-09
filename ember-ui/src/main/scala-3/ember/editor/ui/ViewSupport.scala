package ember.editor.ui

import ember.editor.core.*
import ember.editor.html.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, DomNodes, HostElement, HostNode}

/** Registry der Adapter. Heterogen, mit dem Deskriptor als Typzeugen. */
final class ViewSupport private (val views: Vector[NodeView[?]]):

  def viewFor(node: EditorNode): Option[NodeView[?]] =
    views.find(_.nodeType.project(node).isDefined)

  def ++(other: ViewSupport): ViewSupport = new ViewSupport(views ++ other.views)

  private[ui] def create(node: EditorNode, profile: RenderProfile): AbstractComponent =
    viewFor(node) match
      case Some(view) => build(view, node, profile)
      case None       =>
        throw EditorContractViolation(
          s"Keine NodeView fuer `${node.id.value}` (${node.getClass.getSimpleName}) registriert."
        )

  /** Brings a component to a node. `false` means it no longer fits and must be replaced. */
  private[ui] def update(
      component: AbstractComponent,
      node: EditorNode,
      profile: RenderProfile
  ): Boolean =
    viewFor(node).exists(refresh(_, component, node, profile))

  private def build[N <: EditorNode](
      view: NodeView[N],
      node: EditorNode,
      profile: RenderProfile
  ): AbstractComponent =
    view.nodeType
      .project(node)
      .map(view.create(_, profile))
      .getOrElse(
        throw EditorContractViolation(s"Der Adapter weist `${node.id.value}` zurueck.")
      )

  private def refresh[N <: EditorNode](
      view: NodeView[N],
      component: AbstractComponent,
      node: EditorNode,
      profile: RenderProfile
  ): Boolean =
    view.nodeType.project(node).exists { typed =>
      if view.accepts(component, typed, profile) then
        view.update(component, typed, profile)
        true
      else false
    }

object ViewSupport:

  val empty: ViewSupport = new ViewSupport(Vector.empty)

  def of(views: NodeView[?]*): ViewSupport = new ViewSupport(views.toVector)

  /** Leitet die Adapter aus einer Menge semantischer Beschreibungen ab. */
  def semantic(support: HtmlSupport): ViewSupport =
    new ViewSupport(support.entries.map(fromSemantics))

  private def fromSemantics[N <: EditorNode](semantics: HtmlSemantics[N]): NodeView[?] =
    NodeView.semantic(semantics)

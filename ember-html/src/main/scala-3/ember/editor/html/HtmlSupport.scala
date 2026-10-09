package ember.editor.html

import ember.editor.core.*

/** Registry der semantischen Beschreibungen.
  *
  * Wie [[Schema]] heterogen und ohne oeffentliches `Any`: der Typzeuge ist der Deskriptor des
  * jeweiligen Eintrags.
  */
final class HtmlSupport private (val entries: Vector[HtmlSemantics[?]]):

  /** Die Gestalt eines Knotens, oder `None`, wenn keine Beschreibung registriert ist. */
  def shapeOf(node: EditorNode, profile: RenderProfile): Option[HtmlShape] =
    entries.iterator.map(describe(_, node, profile)).collectFirst { case Some(shape) => shape }

  def knows(node: EditorNode): Boolean = shapeOf(node, RenderProfile.Content).isDefined

  def ++(other: HtmlSupport): HtmlSupport = new HtmlSupport(entries ++ other.entries)

  private def describe[N <: EditorNode](
      semantics: HtmlSemantics[N],
      node: EditorNode,
      profile: RenderProfile
  ): Option[HtmlShape] =
    semantics.nodeType.project(node).map(semantics.shapeOf(_, profile))

object HtmlSupport:

  val empty: HtmlSupport = new HtmlSupport(Vector.empty)

  def of(entries: HtmlSemantics[?]*): HtmlSupport = new HtmlSupport(entries.toVector)

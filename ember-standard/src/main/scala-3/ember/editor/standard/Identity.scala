package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*
import ember.editor.richtext.*

/** Whether a node carries its ID into the output.
  *
  * §19.1: browser-side wrappers and editor attributes are removed on exchange. Instead of taking
  * them out again afterwards, they never appear in the content version.
  */
private[standard] object Identity:

  def of(id: NodeId, profile: RenderProfile): Vector[HtmlAttribute] =
    profile match
      case RenderProfile.Content => Vector.empty
      case RenderProfile.Editor  => Vector(HtmlAttribute.editor("node", id.value))

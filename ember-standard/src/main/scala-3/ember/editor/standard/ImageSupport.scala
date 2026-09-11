package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.*
import ember.editor.ui.*

/** Images as HTML.
  *
  * ==The attributes, and why exactly these==
  *
  * `src` and `alt` always -- `alt` even when it is empty, because `alt=""` is what tells a
  * screen reader that a picture is decorative, and leaving the attribute out tells it to read
  * the file name instead (§20). `title`, `width` and `height` only when the document has them.
  *
  * `width` and `height` are worth stating whenever they are known: §20 says absolute values
  * "koennen Layoutspruenge reduzieren", and a browser that knows the ratio reserves the space
  * before the picture arrives. They are already validated -- [[PositivePixels]] cannot hold a
  * zero or a negative -- so nothing here has to check them again.
  *
  * ==What does not happen==
  *
  * No load, no probe, no measurement. §20: "keine externe URL wird vom Parser oder SSR-Server
  * automatisch abgerufen", and P16's acceptance repeats it for rendering. This adapter turns a
  * node into attributes; whether the picture exists is the browser's question, asked later, and
  * SSR never asks it at all.
  */
object ImageSupport:

  val image: HtmlSemantics[ImageNode] = new HtmlSemantics[ImageNode]:
    val nodeType: NodeType[ImageNode] = ImageNode

    def shapeOf(node: ImageNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element(
        "img",
        Identity.of(node.id, profile) ++
          Vector(
            HtmlAttribute("src", node.src.value),
            HtmlAttribute("alt", node.alt)
          ) ++
          node.title.map(HtmlAttribute("title", _)).toVector ++
          node.width.map(pixels => HtmlAttribute("width", pixels.value.toString)).toVector ++
          node.height.map(pixels => HtmlAttribute("height", pixels.value.toString)).toVector
      )

  val semantics: HtmlSupport = HtmlSupport.of(image)

  /** Every standard adapter: rich text, lists, links, code and images. */
  val everything: HtmlSupport = CodeSupport.everything ++ semantics

  val views: ViewSupport = ViewSupport.semantic(everything)

# scalajs-ember-html

The semantic HTML contract of the Ember editor: how a node type looks as HTML, and an immutable
fragment representation for import and export. Headless — no DOM, no UI runtime.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-html` |
| Scala package | `ember.editor.html` |
| Production dependencies | `scalajs-ember-core` |

## Overview

`HtmlSemantics[N]` describes what a single node type looks like, without its children; both the
editable surface ([`ember-ui`](../ember-ui/README.md)) and the delivered HTML are derived from the
same description, so they cannot drift apart the way two independent renderers could. This module
also provides a bounded, headless HTML fragment parser and import rules for pasted content — it
claims no full HTML5 tree-construction algorithm.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-html" % "1.0.1"
```

## One description, two outputs

```scala
val paragraph: HtmlSemantics[ParagraphNode] = new HtmlSemantics[ParagraphNode]:
  val nodeType: NodeType[ParagraphNode] = ParagraphNode
  def shapeOf(node: ParagraphNode, profile: RenderProfile): HtmlShape =
    HtmlShape.Element("p")
```

Describing only the node itself (not its children) matters: if a shape included its children, it
would be a view tree that something would then have to diff — a second rendering system, which is
exactly what this design avoids. Whoever holds the children is the UI runtime;
[`ember-ui`](../ember-ui/README.md) derives its `NodeView`s from a `HtmlSupport` with
`ViewSupport.semantic(support)`.

## Render profiles

| Profile | For |
| --- | --- |
| `RenderProfile.Content` | what a reader receives and what gets exported |
| `RenderProfile.Editor` | the same, plus editor metadata — node IDs, later `contenteditable`/ARIA |

Editor-only attributes never exist in `RenderProfile.Content` in the first place, rather than
being stripped afterward — so exchanged content is safe by construction.

## A text run is an element, never raw text

```scala
case Element(tag: String, attributes: Vector[HtmlAttribute] = Vector.empty)
case TextRun(tag: String, value: String, attributes: Vector[HtmlAttribute] = Vector.empty)
```

`TextRun` is a stable wrapper with exactly one text child: it avoids adjacent SSR text nodes
merging together, and gives every run a unique ID → text-point mapping that hydration relies on.
Keyed children also need a physical element component to hang an order on — a bare text node has
no host of its own.

## Attributes are narrow

`HtmlAttribute` is its own type rather than `(String, String)`, so the check lives in one place.
Allowed: `id`, `lang`, `dir`, `href`, `title`, `alt`, `src`, `width`, `height`, and anything
prefixed `data-ember-`. `onclick` and `style` are not attributes here — they are an error, since
event handlers and arbitrary CSS strings are excluded from the document format entirely.

```scala
HtmlAttribute.parse("href", "/a")   // Some(...)
HtmlAttribute.parse("onclick", "x") // None
HtmlAttribute("onclick", "x")       // throws EditorContractViolation
HtmlAttribute.editor("node", "p0")  // data-ember-node="p0"
```

## HtmlFragment does not live

A fragment is not a diffable view tree and has no mount/update API — it is produced from
something and turned into something.

```scala
HtmlFragment.render(
  HtmlFragment.Element("p", Vector.empty, Vector(HtmlFragment.Text("Hello")))
) // <p>Hello</p>
```

Output is deliberately compact and unindented, since it is compared against the browser's own
output, and any inserted whitespace would be a text node the document does not have. Void
elements (`br`, `img`, ...) with children are an error, not a cosmetic mistake.

## Tests

`HtmlParserSpec` covers the fragment profile; [`ember-standard`](../ember-standard/README.md) adds
semantic and security-focused import cases (`HtmlImportSpec`, `HtmlSecuritySpec`) and both render
profiles through the real projection (`ProjectionSpec`):

```bash
sbt --server "scalajs-ember-html/Test/testOnly *" "scalajs-ember-standard/Test/testOnly *"
```

## Related modules

- [`ember-core`](../ember-core/README.md) — the document model this contract describes.
- [`ember-ui`](../ember-ui/README.md) — derives its view adapters from these semantics.
- [`ember-standard`](../ember-standard/README.md) — registers HTML semantics and import rules for concrete feature node types.
</content>

# scalajs-ember-ui

The document view of the Ember editor: a keyed projection of the document onto the UI component
tree. The one published module that knows UI.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-ui` |
| Scala package | `ember.editor.ui` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-html`, `com.anjunar:scalajs-ui-core` |

## Overview

`ui-core` is consumed as a published Maven Central artifact. This is enforced strictly: the
dependency boundary lint blocklists `scalajs-ui` as a whole and reopens exactly
`scalajs-ui-core` through an explicit allowlist entry — an accidental `ui-forms` or `ui-viewport`
import fails the build with a clear message rather than slipping through.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-ui" % "1.0.3"
```

## Quick start

```scala
val view = DocumentView.mount(session, DomCursor.root(container), ParagraphSupport.views)

view.componentFor(NodeId("t0")) // Option[AbstractComponent]
view.projectedRevision          // what is currently shown
view.onProjected(revision => ...) // and when it changes
view.dispose()
```

The same call with an `SsrCursor` produces the server-side output. Where no live document is
needed, use:

```scala
DocumentView.renderToHtml(document, ParagraphSupport.views) // Content profile
```

SSR renders a `Document` without local selection, history or focus — which is exactly why this
method takes a `Document`, not a session.

## Two hosts the selection port needs

`ContainerElement.contentHost` and `TextRunElement.textHost` say where a node's children, or its
text node, actually hang in the DOM. For `<pre><code>`, the children are not in the node's own
host but in the inner tag; for a marked run, the text node sits under the chain of mark tags. Both
could be counted from outside by tag depth — and that would be a second description of the same
structure that drifts the first time a mark doesn't render as exactly one element. The component
already knows; it is simply asked.

## What the browser needs

`TextRunElement.resetText` rebuilds a run's DOM from a known value and discards anything the
projection did **not** write there — the mechanism that lets the view rebuild a region from valid
state, needed because a browser can leave more in the wrapper than the one text node (Firefox
splits a run into three when a character outside the BMP is inserted natively).

`DocumentView.rebuild` is the larger repair: a node's components are torn down and rebuilt from
the document. It lives here rather than in the browser module because mount/unmount runs through
the UI runtime, and the projection alone owns that. The root is excluded — rebuilding it would be
replacing the whole view.

`DocumentProjection` also does **not** apply a text splice when the DOM already shows the committed
text. A native input is read from the DOM, so the text is already there when the commit arrives;
applying the splice again would insert it a second time (visible as `aababc` after typing `abc`) —
the same no-op contract required for unchanged nodes, one level up.

## What is deliberately absent

No second renderer, no VDOM, no scheduler. `DocumentProjection` creates no DOM element itself and
moves none — it maps node IDs to components and calls `Runtime` APIs. Ownership, insertion,
movement and removal belong to UI alone. The index is a mapping, not a second ownership list: it
says which component belongs to which ID, not who owns it.

## Commit and projection are two different moments

The kernel publishes a state; whether a view shows it is a separate question. A commit listener may
read `commit.current`, but that does not mean anything has rendered.

| | |
| --- | --- |
| `onProjected(listener)` | reports the shown revision **after** projection |
| `projectedRevision` | the same, readable on demand |

Nothing is reported on mount — there can be no listener yet, since the view is returned only
afterward. Anyone registering later has missed nothing: `projectedRevision` says what is currently
shown.

## KeyedChildren, and why building is a recursion

Every container gets a `KeyedChildren` group, keyed on `NodeId` — reorder reconciliation is
therefore tested code from `ui-core`, not hand-written here, and a node whose value hasn't changed
is never touched. `build` attaches a container's group **before** mount; the container mounts it in
its own `compose`, and the group calls `build` again. The whole tree is built in one pass, in
document order, with the cursor positioned exactly where each child belongs.

Applying one commit, in order: take removed nodes out of the index (groups clean up their own);
transfer moved nodes between groups (before reordering, or the target group would see the node as
new and create it twice); apply text splices (also before reordering, so the group's subsequent
value comparison finds no difference and doesn't write the text twice); reorder changed child
lists; refresh updated nodes. The text-splice step matters concretely: a `spliceText` writes
`CharacterData.replaceData` for just the changed range, while a full `setText` rewrites the whole
run — the difference that matters for a long paragraph.

## What an adapter may do

A `NodeView` receives immutable node data and a rendering context, never unrestricted DOM write
access. `NodeView.create` returns a component, `update` refreshes it — both without a cursor,
without access to siblings, without a way to mount into the tree. The normal case does not write
this contract by hand:

```scala
ViewSupport.semantic(ParagraphSupport.semantics)
```

Hand-written adapters are meant for atoms whose interior is not a text region — an image with a
selection frame, an embedded diagram. Three component kinds result: `SemanticElement` (tag and
checked attributes; never rewrites what hasn't changed), `ContainerElement` (adds the child group),
`TextRunElement` (adds one text child that is never swapped out). A node with children whose
`NodeView` does not produce a `ContainerElement` is a contract violation and is reported as one,
rather than silently rendered without children — the same for a tag change under an unchanged ID,
which is an explicit view replacement this module does not yet have.

## EditorProperties

A read-only bridge from session state to UI properties — for a toolbar showing "can undo," or a
status line with a word count.

```scala
val (document, cleanup) = EditorProperties.document(session)
```

Read-only only, deliberately: a property is neither a transaction nor history. It notifies
synchronously and individually; a writable adapter would route a change around the commit boundary
and bypass atomicity, the change set and position mapping. Code that wants to change something uses
`session.update` or a command. The returned cleanup action must be called, or the registration
outlives the component that created it.

## Tests

This module has no test suite of its own — a projection without node types has nothing to show.
It is tested where both sides meet:

```bash
sbt --server "scalajs-ember-standard/Test/testOnly *"   # headless, against an SsrCursor
cd ember-integration/browser && npm run verify           # DOM identity, write scope
```

## Related modules

- [`ember-html`](../ember-html/README.md) — the semantic descriptions this module's adapters derive from.
- [`ember-browser`](../ember-browser/README.md) — reads positions from this projection and repairs it.
- [`ember-standard`](../ember-standard/README.md) — registers the view adapters for concrete node types.
</content>

# scalajs-ember-list

Ordered and unordered lists: indent/outdent, Enter and Backspace at list boundaries,
normalization. Headless and optional.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-list` |
| Scala package | `ember.editor.list` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text` |

## Overview

`ember-list` adds `ListNode`/`ListItemNode`, three list commands, list-aware Enter/Backspace
handling, and four normalization rules to the [rich-text](../ember-rich-text/README.md) profile.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-list" % "1.0.3"
```

## Quick start

```scala
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), ListExtension(generator)))
  .getOrElse(...)

session.dispatch(ListCommands.ToggleList, ListKind.Unordered)
session.dispatch(ListCommands.Indent)
session.dispatch(ListCommands.Outdent)
```

## Document shape

```text
ul
  li
    "First item"
    ul
      li
        "Nested"
  li
    "Second item"
    "Another paragraph in the same item"
```

An item holds **blocks**, not inline content — which is why an item can contain two paragraphs, a
nested list, or a quote, all things real documents do. The price is one extra nesting level
compared to a naive model, paid at exactly one place: an empty item gets a paragraph so there is
a caret position. Start number and tightness are stored on the document, not derived — a Markdown
list starting at 3 must not silently renumber on a round trip, and tightness would otherwise
change the instant someone adds a second paragraph to an item.

## Everything is a move

Indenting and outdenting never rebuild a node — they move it, so every point inside it (a caret
in the third word of an indented paragraph) survives unmoved.

**Indent** moves the item into the item *above* it — the first item cannot be indented, since
there is nothing above it. If the item above already ends with a list of the same kind, the item
joins it; otherwise one is created there.

**Outdent** has two distinct cases: nested (the item becomes the next sibling of the item that
contained its list) and top-level (there is no containing item, so the blocks leave the list
entirely). In both cases, everything that followed the outdented item comes with it — an item
leaves its list from the bottom, i.e. behind everything still in it; leaving the followers behind
would read them out of order. A split numbered list keeps counting: outdenting the second of
three items leaves `1.` and `2.`, not `1.` and `1.`.

## Enter and Backspace

| | |
| --- | --- |
| Enter in a non-empty item | splits it; the part after the caret becomes a new item |
| Enter in an empty item | outdents one level; at the top level, ends the list |
| Backspace at the start of the first block | outdents instead of deleting |
| Backspace elsewhere | behaves normally |

These handlers register at `CommandPriority.High`, above the rich-text handlers, and return
`Pass` as soon as the caret is not in a list — this module does not replace paragraph splitting,
it takes precedence only where lists are involved. Splitting itself reuses the rich-text split
function directly (not a dispatched command — a command handler's `TransformScope` deliberately
cannot start another dispatch).

## Normalization

| Rule | For |
| --- | --- |
| `looseChildNeedsItem` | A block dropped straight into a list gets wrapped in an item |
| `emptyItemNeedsBlock` | An empty item gets a paragraph — a caret needs a text position |
| `adjacentListsJoin` | Two adjacent lists of the same kind become one |
| `emptyListGoes` | A list with no items disappears |

These repair rather than reject: any module or paste that moves a node into a list should not
have to itself keep the shape legal. `adjacentListsJoin` looks backward deliberately — wrapping a
paragraph creates a *new* list beside an existing one, and the new list is the dirty node; the
existing one is never a transform candidate. All four rules are guaranteed to terminate: each
either removes a node or reduces the count of misplaced ones, and none creates work for another.

## Tests

```bash
sbt --server "scalajs-ember-list/Test/testOnly *"
```

`ListEditingSpec` covers the commands, `ListNormalizationSpec` the document invariants — a
foreign module, a paste, or a later feature can move nodes anywhere, and the invariants must hold
regardless. The semantic HTML export and the proof that projection moves rather than rebuilds
live in [`ember-standard`](../ember-standard/README.md)'s `ListProjectionSpec`.

## Related modules

- [`ember-rich-text`](../ember-rich-text/README.md) — the profile this module extends.
- [`ember-browser-support`](../ember-browser-support/README.md) — keyboard bindings for list commands.
- [`ember-standard`](../ember-standard/README.md) — HTML/JSON/Markdown adapters for lists.
</content>

# scalajs-ember-rich-text

The rich-text profile of the Ember editor: paragraphs, marks, text editing, and Unicode grapheme
boundaries. Headless, like the kernel — no DOM, no UI runtime.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-rich-text` |
| Scala package | `ember.editor.richtext` |
| Production dependencies | `scalajs-ember-core` |

## Overview

`ember-rich-text` is the first profile built on [`ember-core`](../ember-core/README.md): it
contributes the block types (`ParagraphNode`, `HeadingNode`, `QuoteNode`, `BreakNode`,
`ThematicBreakNode`), the five built-in marks, range formatting, typing-mark tracking, text-run
normalization, and UAX #29 grapheme-cluster segmentation. Everything that builds a document with
editable text — lists, links, code, tables — depends on this module.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-rich-text" % "1.0.3"
```

## Quick start

```scala
val generator = NodeIdGenerator.sequential()
val resolved  = ExtensionResolver.resolve(Vector(RichText(generator))).getOrElse(...)
val document  = RichText.emptyDocument(resolved.schema, generator).getOrElse(...)
val editor    = EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(...)

editor.update(_.setSelection(RichText.caretAtStart(editor.document)))
editor.dispatch(RichText.InsertText, "Hello")
editor.dispatch(RichText.InsertParagraph)
editor.dispatch(RichText.DeleteBackward)
```

## Marks

Five built-in marks: `Strong`, `Emphasis`, `Underline`, `Strike`, `InlineCode` — case objects
with no payload, so an arbitrary CSS string can never become a document format. `InlineCode`
excludes the other four, in both directions: a code span is meant to be verbatim, and a run with
both would be a document Markdown cannot export losslessly. Links are **not** a mark — they are
an inline container with children and a target (see [`ember-link`](../ember-link/README.md)).

`RangeFormatting.toggleMark` cuts runs at both range boundaries and writes marks on the pieces
between them; toggling over a mixed selection always sets the mark everywhere, rather than
inverting each run independently. At a collapsed caret, toggling writes to the `TypingMarks`
state field instead of creating text. `RangeFormatting.activeMarks(state)` reads what a toolbar
should show as active, from a published `EditorState` rather than an in-flight transaction.

`TypingMarks` tracks `Inherit` or an explicit `MarkSet` **at a mapped caret position** — mapped
through the transaction before comparison, so a typed character (which both moves the caret and
maps the tracked point) keeps the two in sync, while a click (which only moves the caret)
correctly drops the field. It declares `HistoryRestorePolicy.Restore`, since deriving active
marks from rendering after an undo would make them a guess.

## Text-run normalization

Adjacent text runs of the same parent with equal marks merge into a single maximal run, as a
**transform** rather than a step inside formatting — so the merge shares its undo step with
whatever caused it, and also fires after an edit that joins two blocks. The rule is bound to the
run, not the block: a mark change makes the run dirty, never its parent, and merely-touched
ancestors are never transform candidates.

```text
Start:                 Text("Hello World!", {})
"World" made bold:      Text("Hello ", {}), Text("World", {Strong}), Text("!", {})
Bold removed again:     Text("Hello World!", {})
```

Runs do not merge across block, break, or atom boundaries, or when marks differ — one rule,
independent of what a link or an atom is.

## Block types

| Type | Notes |
| --- | --- |
| `HeadingNode` | A container plus a typed `HeadingLevel` (six levels, not a raw `Int`) |
| `QuoteNode` | Holds blocks, not a flag — quoting moves a paragraph into a container |
| `BreakNode` | `Soft` and `Hard` stay distinct so Markdown and HTML keep their meaning; an atom, not a literal `\n`, so a caret can stand on either side |
| `ThematicBreakNode` | Block-level, so it gets its own node type rather than a third break kind |

Document shape is `root > paragraph* > text*`; `TextEditing` works in terms of "the block
containing a run" rather than `ParagraphNode` directly, so lists, quotes and headings extend it
without special-casing.

### Normalization

Three transforms keep the surface editable: the root always needs a block (otherwise an emptied
document has no valid caret position), a block always needs a text run, and redundant empty runs
left over after a merge are removed — but only when the block still has a non-empty run (to
avoid an infinite loop with the previous rule), and never touching the run the selection points
at.

## Atoms in the flow

An atom (an image, for example) sits between text runs, and must be reachable and deletable by
keyboard. Backspace/Delete/Remove behave as follows:

| Caret | Backspace | Delete |
| --- | --- | --- |
| directly after an atom | removes the atom | acts on text as usual |
| directly before an atom | acts on text as usual | removes the atom |
| in the middle of text | one grapheme cluster | one grapheme cluster |
| node selection | removes the selected nodes | same |

A range that only touches a single block removes only what lies strictly between its endpoints —
selecting an atom no longer removes the rest of the run behind it. Merges (including the seam
left behind when a node between two runs is removed) are deferred while a protected composition
is in progress, coordinated through a `TransactionMeta` tag that `ember-browser` sets and this
module reads, without either module importing the other.

## Unicode boundaries

`UnicodeTextBoundaries` implements UAX #29 grapheme-cluster rules GB1–GB13, including the two
context-dependent ones: GB11 (emoji ZWJ sequences stay together — without it, 👨‍👩‍👧 falls apart
into five pieces) and GB12/13 (regional indicators pair up, so four of them are two flags, not
one or four). `unicodeVersion` reports `"16.0.0 (subset, without GB9c)"` — a value for comparing
fixtures, not a conformance claim. Known gaps: GB9c (Indic conjunct break, Unicode 15.1) is not
implemented; `Extended_Pictographic` is approximated via maintained ranges rather than generated
from `emoji-data.txt`; word-boundary detection is a documented simplification, not full UAX #29
§4. An `Intl.Segmenter`-backed adapter can be added later without changing the contract.

## Tests

```bash
sbt --server "scalajs-ember-rich-text/Test/testOnly *"
```

`UnicodeBoundarySpec` covers the cases a naive approach fails on: surrogate pairs, combining
mark stacks, ZWJ families, skin tones, flag pairing, CRLF, Hangul. `TextEditingSpec` runs
complete editing sequences and validates independently against the kernel's `DocumentValidator`
and selection contract after each step. `TextRunNormalizationSpec`, `RangeFormattingSpec`,
`TypingMarksSpec` and `RichTextStructureSpec` cover the rest. There is no undo/redo test here —
[`ember-history`](../ember-history/README.md) sits alongside the profile, not underneath it, and
this module cannot link it.

## Related modules

- [`ember-core`](../ember-core/README.md) — the document model this profile builds on.
- [`ember-list`](../ember-list/README.md), [`ember-link`](../ember-link/README.md), [`ember-code`](../ember-code/README.md), [`ember-table`](../ember-table/README.md) — feature modules built on this profile.
</content>

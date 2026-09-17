# scalajs-ember-history

Undo and redo for the Ember editor: structurally shared snapshots, explicit grouping rules, and
retention limits. Headless and optional — an application without undo never links this module.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-history` |
| Scala package | `ember.editor.history` |
| Production dependencies | `scalajs-ember-core` |

## Overview

`History` is an `Extension` that tracks document snapshots outside of `EditorState`, groups
consecutive edits by explicit rules (not by labels a command attaches), and restores a prior
state as a single commit. It depends only on the kernel.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-history" % "1.0.1"
```

## Quick start

```scala
val history  = new History(HistoryConfig.default)
val resolved = ExtensionResolver.resolve(Vector(RichText(generator), history)).getOrElse(...)
val session  = EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(...)

history.canUndo  // Boolean
history.undo()   // Either[UpdateError, Boolean] — false if there was nothing to undo
history.redo()
history.reset()
```

A `History` is mutable and belongs to exactly one session; installing it twice is a caller
contract error, and the kernel refuses to produce a session at all. For keyboard bindings, use
the commands instead: `session.dispatch(HistoryCommands.Undo)`.

## Why history is not session state

If `History` were a `StateField`, it would live inside `EditorState` — and every snapshot would
then have to include all previous snapshots. It therefore lives in an object beside the session
instead, and is the one place in the editor that holds mutable state across commits, deliberately
and visibly.

## What an undo is

It installs an earlier state; it does not turn back a clock. Both revisions increase on undo too
— the restored content is a *new* state. Document and selection come back in a single commit, so
no observer ever sees an intermediate state that never existed. The mechanism is
`Transaction.restore` in the kernel: unlike an `Operation` (which knows its own effect and
produces `ChangeSet`/mapping itself), a restore only has two states, and the difference is
computed via `DocumentDiff`. The kernel deliberately scopes history to snapshots; an
operation-based history is left to a future extension.

## Grouping

`HistoryGrouping` decides, without needing a session:

| Rule | How it is decided |
| --- | --- |
| Consecutive typing merges | same node, and the new splice starts where the last one ended |
| ... with the same mark configuration | the affected run's marks are equal |
| ... within the merge window | `mergeWindowMillis`, measured against an injected clock |
| Backspace and Delete are separate | see below |
| Structural edits, formatting, and range replacement are boundaries | `EditKind.Structural` never merges |
| A selection jump ends the group | a selection-only commit closes the group without removing it |

Grouping is **derived, not reported** — a command could label itself ("that was Backspace"), but
the label would be a second truth alongside what actually happened, and the two would eventually
disagree. What happened is read from the `ChangeSet`.

### Backspace vs. Delete

At the result they are indistinguishable: at caret 5, Backspace deletes `[4,5)` and leaves the
caret at 4; at caret 4, Delete deletes `[4,5)` and leaves it at 4 too. The difference is entirely
in the caret **before** the edit, which is exactly what `HistoryGrouping` reads
(`commit.previous.selection`).

### Explicit groups

```scala
history.beginGroup(Some("composition"))
// ... several transactions ...
history.endGroup()
```

Everything in between becomes one step, with the state before `beginGroup` as its baseline. If
nothing changed, nothing is recorded. This is the contract an IME composition session uses (see
[`ember-browser`](../ember-browser/README.md)) — usable and testable without a browser.

## Typed metadata

`Origin` and `HistoryPolicy` live in the kernel as part of `TransactionMeta`; the kernel carries
them without interpreting them.

| | |
| --- | --- |
| `Origin.History` | never recorded |
| `Origin.Import` | resets history (configurable via `HistoryConfig.resetOnImport`) |
| `HistoryPolicy.Push` | forces its own step |
| `HistoryPolicy.Merge` | merges even where the rules would have separated |
| `HistoryPolicy.Ignore` | not recorded at all |

The policy is the exception, not the default rule — when absent, the grouping rules decide.

## Limits

`HistoryLimits` caps the number of steps and an **estimated** retained-byte budget. The estimate
is deliberately not a heap size: structural sharing means `before`/`after` share almost all
nodes, and one step's `after` is routinely the same object as the next step's `before`, so
summing document sizes would overstate memory by orders of magnitude. What is counted instead is
what a step **additionally** retains — the nodes where its two states differ.

The newest step is always kept, even if it alone exceeds the budget; the alternative would be a
history that cannot undo its very last action. A single huge import is handled separately (an
import resets history anyway).

## Tests

```bash
sbt --server "scalajs-ember-history/Test/testOnly *"
```

`HistorySpec` covers undo, redo, every grouping rule, the typed metadata, the commands and
explicit groups. `HistoryRetentionSpec` checks trimming, the budget, and release. The clock is
injected (`HistoryClock.Fake`), so the merge window is testable without waiting on real time.
`LocalHistoryCostSpec` checks that local push, merge and explicit groups never traverse a full
node map even at 100,001 nodes.

## Related modules

- [`ember-core`](../ember-core/README.md) — the kernel this module depends on exclusively.
- [`ember-browser-support`](../ember-browser-support/README.md) — binds undo/redo to keyboard shortcuts and groups IME compositions.
</content>

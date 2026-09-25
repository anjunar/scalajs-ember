# scalajs-ember-core

The headless kernel of the Ember editor: an immutable document model, selections, transactions,
commands and extensions — no DOM, no UI runtime, no forms, no browser.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-core` |
| Scala package | `ember.editor.core` |
| Production dependencies | none beyond the Scala / Scala.js standard library |

## Overview

Every other Ember module builds on this one, and this one depends on nothing else in the
project. It defines the document tree, the primitive operations that change it, the session
that owns a mutable pointer to an immutable state, and the extension mechanism that lets
feature modules (lists, links, tables, ...) plug in node types, commands and normalization
rules without the kernel knowing they exist.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-core" % "1.0.3"
```

## Document model

| Type | Role |
| --- | --- |
| `EditorNode` / `ElementNode` / `AtomNode` | Open node contract, closed structural categories |
| `RootNode`, `TextNode` | The two node kinds the kernel itself contributes |
| `NodeType[N]` / `ElementNodeType[N]` | Type witness (`project`) plus reconstruction (`rekey`, `withChildren`) |
| `TextMark` / `MarkSet` | Open mark contract; a normalized, order-independent set |
| `Schema` | Registry of node kinds, without a public `Map[String, Any]` |
| `Document` / `DocumentRead` | A structurally valid immutable tree, privately constructed |
| `DocumentValidator` / `Violation` | Five-stage validation, closed result ADT |
| `NodeIdGenerator` | Injected ID source, deterministic and testable |
| `Point` / `Affinity` | A logical position (text or child offset) plus which side it sticks to |
| `Selection` / `SelectionSupport` | Open selection contract; range and node selections built in |
| `Operation` / `OperationError` | Seven primitives: Insert, Remove, Move, Replace, SpliceText, SplitText, MergeText |
| `PositionMapping` / `MappedPoint` | Composable position tracking across an edit |
| `ChangeSet` / `TextSplice` | What actually changed, distinct from merely-touched ancestors |
| `Bookmark` / `RevisionMapping` | A remembered position with an explicit expiry contract |
| `EditorState` / `Commit` | Published session state, carrying two revision counters |
| `EditorSession` / `SessionConfig` | Owns the state; the single commit boundary |
| `Transaction` | A private draft with a latched error and a bounded lifetime |
| `StateField` / `StateFields` | Typed session fields with a pure, rejecting reducer |
| `PreCommitRule` | A synchronous verdict over the finished candidate |
| `UpdateError` / `Subscription` | Why a commit did not happen; a cancellable registration |
| `EditorCommand` / `CommandRegistry` | Intents with instance identity, priorities, pass/handled results |
| `Transform` / `TransformScope` | Fixpoint normalization with restricted access |
| `Extension` / `ExtensionResolver` | Declarative contributions, dependency order, install rollback |

A `Document` only ever comes from `Document.build`, and once built it already satisfies its
invariants: exactly one root, unique IDs, every node reachable, no cycles, one parent per node,
schema-conformant content. Holding a value of this type means nothing more needs checking.

The parent index is **derived** — a projection of the child lists, never a second source of
truth. Children are referenced IDs, never embedded objects, so an edit deep in the tree costs
the affected string and a few index entries, not a recursive copy of every ancestor. All
traversals are iterative; `DocumentSpec` builds a tree 25,000 levels deep to prove it.

### Five-stage validation

Identity → references → cycles → reachability → schema. The check stops at the first failing
stage, because a single dangling child reference otherwise cascades into reachability and
schema errors that only obscure the cause. Every `Violation` names a path, an ID and a reason:

```
<root>#root.children[0]: `root` references the unknown node `ghost` at position 0.
```

## Operations and position mapping

Every operation returns three things at once: the new document, a `ChangeSet`, and a
`PositionMapping`. They are produced together because computing them separately would let them
drift apart.

```scala
document.applyOperation(Operation.SpliceText(t1, 5, 0, "!")) // Either[OperationError, OperationResult]
document.applyAll(Seq(...))                                  // the same, composed
```

Atomic: on failure the starting document is unchanged. There is nothing to roll back — every
intermediate state is its own immutable value. Operations validate their preconditions and
construct the result so invariants hold, rather than fully re-validating the whole document on
every keystroke (which would be linear in document size). `DocumentOperationModelSpec` checks
this claim directly: 30 rounds of 40 random operations, verified after every step against a
from-scratch `DocumentValidator` rebuild, an independent reference model, and carried points
that must remain representable in the new document.

`MappedPoint` distinguishes `Preserved` from `Displaced`, and the distinction matters: a caret
may fall back onto a boundary, but an upload bookmark may not — otherwise the finished image
lands at an arbitrary position. Displacement is contagious: what vanished in one step does not
reappear in the next. `Affinity` decides which side a point sticks to when an insert happens
exactly at its position — a child position is a boundary between siblings, not a number that
stays fixed.

## Session and commit boundary

```scala
val editor = EditorSession.create(document, SessionConfig(fields = Vector(TypingMarks)))

editor.update { tx =>
  tx.spliceText(t1, 5, 0, "!")
  tx.select(RangeSelection.caret(Point.textAfter(t1, 6)))
} // Either[UpdateError, Commit]
```

A transaction is a private draft: it latches its first failure and rejects everything after,
and its handle is only valid inside the closure — using it later throws, because keeping it
alive in a `Future` is a programming error, not a data error.

**Two revisions.** `revision` increases on every publish; `documentRevision` only on an actual
document change. Code that persists compares the second one, so a moved cursor never triggers a
write. **A commit is not a render** — the kernel publishes a state, and whether a view shows it
is a separate question answered by `DocumentView.onProjected`/`projectedRevision` in
[`ember-ui`](../ember-ui/README.md). **No re-entrancy** — calling `update` from inside `update`
returns `UpdateError.NestedUpdate`; code that needs to change state from a listener uses
`enqueueUpdate` instead (FIFO, run once notification is done). A listener that throws is
reported to the error sink without unwinding an already-published commit.

`StateField[A]` is not a freely writable cell: its reducer runs during commit against the
finished candidate and may **reject** it — this is what lets a form field refuse a
`ToggleUnderline` in a strict CommonMark profile before a commit happens, rather than leaving a
stale form value behind.

## Commands, extensions and transforms

```scala
val Bold = EditorCommand.unit("bold")

val resolved = ExtensionResolver.resolve(Vector(CoreNodes, RichText(), Lists())).getOrElse(...)
val document = Document.build(resolved.schema, rootId, nodes).getOrElse(...)
val editor   = EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(...)

editor.dispatch(Bold) // Either[UpdateError, DispatchOutcome]
```

A command is looked up by **reference identity**, never by string — no name collisions between
modules, no typos that only surface at runtime, and the compiler checks the payload type.
Handlers run from `Critical` down to `Fallback`, in registration order at equal priority; the
first `Handled` ends the chain. `Pass` must be side-effect free — `SessionConfig.strictCommands`
(on by default) enforces it.

**Transforms** establish invariants before anything becomes visible, running after the
transaction body and before rules and reducers. Order is phase (`Early`/`Normalize`/`Late`),
then node depth (deepest first), then registration; merely-touched ancestors are never
transform candidates, so a single keystroke does not re-normalize the path to the root. A
transform receives a `TransformScope`, not the whole transaction — no `dispatch`, no field
access. If the loop never settles, the transaction fails naming every transform involved,
rather than silently truncating after a fixed budget.

**Extensions** resolve, validate, install, and (on failure) dispose in reverse order. Resolution
checks everything — duplicate extensions, missing dependencies, cycles, duplicate wire names,
conflicting replacements — before anything is built; a failed install unwinds everything already
installed and no session is produced. Contributions are purely declarative: an extension factory
is a value that describes what it contributes and does not act on its own, which is what lets a
configuration be resolved and checked server-side without a browser.

Feature nodes require no kernel change: `rekey` and `withChildren` are how a node type
reconstructs itself without the kernel needing to guess a foreign case class's `copy` signature
(see [`ForeignNodes.scala`](src/test/scala-3/ember/editor/foreign/ForeignNodes.scala) for a
container, an atom and a mark, all defined outside the kernel's package).

## Dependency boundary

The kernel has no `org.scalajs.dom`, no UI dependency, and no forms/viewport import — enforced
as a build gate, not a style guideline. `boundaryCheck` in [`build.sbt`](../build.sbt) checks, on
every compile: project dependencies against an allowlist (empty, for the kernel), resolved
artifacts against a blocklist (`scalajs-dom`, `scalajs-ui`, `scalajs-lexical`), and imports
against forbidden package prefixes — including modules that build on the kernel later
(`ember.editor.ui`, `.browser`, `.forms`, `.toolbar`, `.html`, `.markdown`, `.json`). Violations
fail the build before compilation:

```
Dependency boundary of scalajs-ember-core violated:
Disallowed project dependencies:
  probe-dummy
Forbidden artifacts on the classpath:
  org.scala-js:scalajs-dom_sjs1_3
Forbidden imports:
  Probe.scala:3  import org.scalajs.dom  (forbidden: org.scalajs.dom)
```

## Error convention

Two kinds of failure, distinguishable by signature (see
[`package.scala`](src/main/scala-3/ember/editor/core/package.scala)):

- **Expected failures are values.** `Either[E, A]` with `E <: EditorError` — an invalid
  document, a rejected transaction, an undecodable wire format. `update` returns
  `Either[UpdateError, Commit]`.
- **Contract violations by the caller throw.** `EditorContractViolation` for an expired
  transaction handle, a nested `update`, or a call after `dispose` — an `Either` would suggest a
  caller could meaningfully react to it.

`EditorError` is an open trait so foreign feature modules can contribute their own errors
without changing the kernel. `DiagnosticPath` renders a path, an ID and a reason:

```scala
DiagnosticPath.field("children").index(2).node("p-17").field("text").render
// <root>.children[2]#p-17.text
```

## Tests

```bash
sbt --server "scalajs-ember-core/Test/testOnly *"
```

`CoreEnvironmentSpec` checks that the kernel loads in an environment without `window` or
`document`, and exercises the error convention. The dependency boundary is not tested here — a
linked Scala.js module has neither a classpath nor a filesystem; `boundaryCheck`, hanging off
`Compile / sources`, runs ahead of every test regardless.

## Related modules

- [`ember-rich-text`](../ember-rich-text/README.md) is the first profile built on top: paragraphs, marks and text editing.
- [`ember-history`](../ember-history/README.md), [`ember-json`](../ember-json/README.md) and [`ember-html`](../ember-html/README.md) each depend only on the kernel.
</content>

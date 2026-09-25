# scalajs-ember-browser

The editor in the browser: claim an already-delivered page without destroying what was on it;
map logical and browser selection in both directions; turn keystrokes into commands; and leave a
running text composition unharmed, even if that means not writing to it for a while.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-browser` |
| Scala package | `ember.editor.browser` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-html`, `scalajs-ember-ui`, `com.anjunar:scalajs-ui-core` |

## Overview

`ember-browser` connects a live DOM to an `EditorSession`. Which feature command a given browser
intent turns into is deliberately **not** decided here — that lives in
[`ember-browser-support`](../ember-browser-support/README.md), so an editor without lists never
breaks on `insertUnorderedList`; it simply has no binding for that intent and the browser handles
it natively. The reusable hydration boundary itself comes from `ui-core`; this module supplies
what the editor additionally knows about it.

| Area | Key types |
| --- | --- |
| Hydration | `HydrationSnapshot` — what was on the page before anything was claimed; `EditorHydration` — the comparison a structural check alone cannot do; `EditorActivation` — whether editing may begin, and why not yet |
| Selection and focus | `BrowserScope` — which document, window, selection, and whether they exist; `DomPositionMap` — the explicit mapping table, both directions; `SelectionPort` — read, write, observe, without a feedback loop; `FocusController` — who has focus, what is remembered, when it returns; `DomKinds` — `nodeType` instead of `instanceof` |
| Input | `InputIntent` — what the browser wants, in its own vocabulary; `BeforeInputAdapter` — the table from `inputType` to intent; `BrowserInputController` — the state machine: events in, commands out; `NativeInputReader` — what the browser already did, read as a document change; `InputOperationLog` — exactly once, even if an action arrives twice; `KeyboardBindings` — the shortcut table, including the Tab rule |
| Composition and recovery | `CompositionSession` — a running input: ID, starting revision, protected region, captured text; `CompositionRegion` — which blocks a composition owns; `CompositionGate` — the pre-commit rule that rejects independent changes; `ProjectionWriteGuard` — the view's write lease; `DeferredIntentQueue` — what had to wait, re-validated rather than replayed; `NativeMutationObserver` — that something happened at all; `RecoveryController` — rebuild the view from the document, bounded |

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-browser" % "1.0.3"
```

## Capturing before claiming

Before the first claim that could overwrite values, the outer hydration boundary captures the
actual `textarea.value`, `selectionStart`, `selectionEnd`, `selectionDirection` and focus —
attributes or `defaultValue` are not enough. The distinction between `value` and `defaultValue` is
the whole point: whoever typed before the script ran changed `value`; the attribute still carries
what the server sent. Reading it would silently discard that input; reading `value` **after** a
claim would read back what the claim itself wrote.

A composition that began **before** attach cannot be queried afterward. The `composing` field
therefore asserts nothing definite; the uncertainty is captured in
`HydrationSnapshot.unknownInputSession`, and an already-focused field is enough to defer — not
because focus implies composition, but because a focused field is the only place one could be
running, and guessing "no" risks replacing text mid-composition.

## The check that structural comparison alone can't do

The hydrating cursor checks only whether it finds the tag it expects — necessary but not
sufficient: a `<p>` with the wrong `data-ember-node`, or the right ID and different text, passes a
pure structural check and then silently becomes a document that says something other than what was
delivered. `EditorHydration.preflight` compares against the **same** `HtmlSupport` that produced
the SSR output, since a second description of "what the server should have sent" would be a second
truth that can drift from the first — and its drift would look exactly like a hydration mismatch.

Two deliberate restrictions: only the attributes the semantics actually name (a page may add its
own — a layout class, an analytics attribute — and an editor that broke on those would be unusable
in any real application), and only element children (the UI runtime's keyed-group comment anchors
are not document nodes, and counting them would make every container appear to mismatch). At most
eight mismatches are collected — one high up usually makes everything below it mismatch too, and a
ten-thousand-line diagnostic is not one.

## Activating — two conditions, not one

A boundary can have claimed its own subtree while the page around it is still being adopted.
`EditorActivation.decide` is a pure function of its inputs rather than a stateful controller, so
"repeated enhancement is idempotent" holds for free.

| Result | When |
| --- | --- |
| `Failed` | the local claim failed — an end, not a wait |
| `Deferred(PageHydrating)` | outer page hydration is still running |
| `Deferred(InputSession)` | an input session is open, or might be |
| `Deferred(UnimportedSource)` | the source text changed since it was rendered |
| `Active` | editable |

Order matters: a failed claim is reported before any deferral, never as "deferred," since nothing
is coming. `mayRestoreSelection` is the stricter, negative half: a selection may only be written
back if the user had actually focused the field — an editor that focuses itself on load would
steal focus from wherever the user actually was.

## Positions: a table that counts nothing

Under a container sit things the document has no word for: the runtime's group anchors, a code
block's inner `<code>`, later a placeholder `<br>`. Counting raw DOM children and calling the
result a child offset is wrong by a different amount at every node type. So nothing here counts;
every offset comes from the position of the **hosts of the document's children**, resolved through
the projection:

| Point | DOM position |
| --- | --- |
| `Text(run, o)` | the run's text node, offset `o` — both count UTF-16 |
| `Children(p, i)`, `i < n` | inside the content element of `p`, before the host of child `i` |
| `Children(p, n)` | same place, after the host of the last child |
| `Children(p, 0)`, `p` empty | same place, offset 0 |

Two accessors in [`ember-ui`](../ember-ui/README.md) supply the starting points
(`ContainerElement.contentHost`, `TextRunElement.textHost`); the component knows both, and
recomputing them from outside by counting tags would be a second description of the same
structure that drifts the first time a mark doesn't render as exactly one element.

Finding the document node for a DOM node goes the other way, **down** from the document: `nodeAt`
walks the tree asking only `Node.contains` per level, rather than maintaining a second DOM→ID
index (which would be a second ownership list). `data-ember-node` plays no part in this — it is a
render-profile decision, and a port that parsed it would work only for that profile.

## Writing is the dangerous direction

Reading is cheap and always allowed; writing can pull a caret away, steal focus, and trigger the
very event it caused. Every write therefore passes through `SelectionWriteGate` first:

| Result | Reason |
| --- | --- |
| `StaleProjection` | write only at a matching projection revision |
| `NotFocused` | background updates never steal focus |
| `Unsupported` | a Shadow Root without `getSelection` |
| `AlreadyThere` | the DOM already says so; a write would only fire an event |
| `NoSelection` | the model has none — clearing the browser selection would remove the caret |

`WriteIntent.Explicit` bypasses only the focus condition, never the others, and it does not mean
"without touching focus" — writing a selection into a `contenteditable` focuses it in Chromium
regardless, which is why it is not the default. Own writes are recognized by revision **and**
actual value together — a synchronous flag alone doesn't work, since `selectionchange` is
delivered asynchronously; a revision alone doesn't work either, since the user can move the caret
without anything changing.

An active element inside an atom means the document selection is not the editor's — `read()`
reports `Foreign`. This check comes before endpoints are even inspected: a Firefox run showed why —
while a native field inside an atom has focus, Firefox sets the document selection to the start of
the editing host, and checking only the endpoints would have imported a caret at the document start
while the user was elsewhere entirely.

## Focus, kept separate from the port

So that "clear the selection without taking focus" is expressible — exactly what a toolbar needs.
`FocusController` watches `focusin`/`focusout` (which bubble) rather than `focus`/`blur`, since an
editor contains focusable things of its own — a media control in an atom, a nested field — and
focus there is still focus in the editor. `capture()` remembers the selection as two independent
bookmarks plus whether the editor had focus, which drives the rule that closing something restores
focus only in the matching interaction context:

| `FocusIntent` | Takes focus |
| --- | --- |
| `SelectionOnly` | never — what a background update may do |
| `IfItWasOurs` | only if the editor had focus when it was captured — the dialog case |
| `Always` | yes, on an explicit gesture |

## One document is not *the* document

`BrowserScope` is the one place that decides which document/window/selection apply — nothing else
reaches for a global window. `SelectionCapability` distinguishes the ordinary case (`Document`)
from a Shadow Root whose engine supports `getSelection` (`ShadowNative`, Chromium today) from one
that doesn't (`ShadowUnsupported`, which reads **nothing** rather than something misleading) from a
detached host (`Detached`).

`DomKinds` exists because of a bug only an iframe revealed: a Scala.js facade type check on
`Text`/`Element` compiles to `instanceof`, which tests against *this* window's constructor — a text
node from an iframe's document is an instance of a different constructor and never passes,
reporting "not projected" while the right elements sit right there in the DOM. `nodeType` is a
number from the spec and means the same thing in every realm.

## Input: three ways in, and why there are three

| | |
| --- | --- |
| `beforeinput`, cancelable | the good case — intent is known **before** anything happens; a command runs, the native action is prevented |
| `input` | what remains when `beforeinput` was not cancelable or didn't arrive; the DOM is ahead, and `NativeInputReader` pulls the model forward |
| `keydown` | shortcuts and structural keys only, never text — a keydown table sees neither dictation nor autocorrect nor a mobile keyboard |

`preventDefault` follows the takeover, not the mere existence of a handler: prevented on
`TakenOver` and on `Refused`, never otherwise — a refusal without `preventDefault` would let the
browser change a document the model said no to, and a `preventDefault` without a takeover would
swallow an input nobody processed. `InputOperationLog` deduplicates the same user action arriving
twice (`beforeinput` then `input`, or `paste` then `beforeinput`) — a log, not a flag, because
events aren't reliably paired and a browser may send several `beforeinput`s before one `input`
(autocorrect is the documented case).

`NativeInputReader` compares the run the caret is in against the document and returns the smallest
splice: `Text` (one run, one splice — the normal case), `SplitRun` (the text matches but the
browser left several text nodes in the wrapper — Firefox does this when a character outside the
BMP is inserted natively; the view is rebuilt, the caret is computed rather than read), or
`Unimportable` (structure no splice can express — the controller goes into `Recovering` and does
not guess).

## Readonly and focus

Focusability and editability are kept as separate decisions: a readonly editor stays focusable,
selectable, and readable — it simply doesn't change. Two concrete consequences, both forced by
browser tests: `contenteditable="false"` removes an element from tab order, so the host gets
`tabindex="0"` in **both** modes; and WebKit navigates back on Backspace when a focused element
isn't editable, so a readonly editor explicitly refuses editing keys rather than hoping.

## Composition

A running composition is the one state in which the editor is **not** in charge of its own DOM.
Three consequences follow directly. The protected region is always a block, never a leaf — a
collapsed range protects at least the active block, since the write lock must cover every leaf,
mark and atom boundary inside whatever it protects. Independent changes are rejected **before**
commit, via a `PreCommitRule` (`CompositionGate`) rather than a controller check, because a
feature command, a timer, or an arriving upload can reach the session directly without ever passing
through the controller — and because an unrelated commit landing inside the composition's history
group would be undone along with it. What is allowed: the composition's own intermediate states,
and pure selection/view/effect changes; everything else is refused or deferred (as a thunk, never
a pre-computed value, since where a deferred change applies must be resolved against the document
that exists when it finally runs). On completion: release the lease, read once (revisioned, since
`compositionend` is often followed by another `input`), run the deferred merges, repair if the
input method left unsplice-able structure, write the selection back only if focus is still in the
editor, then release the queue with every entry re-validated.

## Foreign mutations and recovery

Not the browser while typing — that's the native input path — but everything else that writes into
a page: an extension, a translation tool, a password manager. `NativeMutationObserver` answers only
one question: did something happen at all. Whether the view still matches is answered by the
**document**, through the same comparison that also decides whether a server-rendered page may be
claimed. `RecoveryController` rebuilds what doesn't match **from the document** — a run gets its
text back (the wrapper survives, and with it the node identity), everything else is remounted, and
exactly one retry is attempted (a rebuild is itself a mutation; a second attempt after a failed one
produces the same records and the same failure, only faster).

## Tests

```bash
sbt --server "scalajs-ember-browser/Test/testOnly *"
```

`HydrationBoundarySpec`, `SelectionPolicySpec`, `InputPipelineSpec` and `CompositionSpec` check the
rules as rules: when activation, writing and focus are allowed, what an expired bookmark is, what
intent an `inputType` means, the smallest splice between two strings, which blocks a composition
owns, and what is refused while it runs. What only a live page can answer is covered by the browser
harness in [`ember-integration`](../ember-integration/README.md).

## Related modules

- [`ember-ui`](../ember-ui/README.md) — the projection this module reads positions from and repairs.
- [`ember-browser-support`](../ember-browser-support/README.md) — wires feature commands to the intents defined here.
- [`ember-integration`](../ember-integration/README.md) — the browser harness this module is proven against.
</content>

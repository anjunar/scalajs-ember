# scalajs-ember-forms

The editor as a form field: exactly one named textarea carries the value, a protected source
draft, and a path that works without JavaScript.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-forms` |
| Scala package | `ember.editor.forms` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-markdown`, `scalajs-ember-json`, `scalajs-ember-html`, `scalajs-ember-ui`, `scalajs-ember-browser`, `scalajs-ember-image`, `scalajs-ember-clipboard` (`scalajs-ember-history` for tests) |

## Overview

The textarea comes from `ui-core`, not from a forms runtime — this module has no dependency on
one. `MediaService`, `MediaCoordinator`, `MediaStatus` and `BrowserMediaPicker` provide a shared
upload path for picker/paste/drop, mapped targets, cancellation and resource release.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-forms" % "1.0.1"
```

## Quick start

```scala
val field   = EditorFields.markdown("body", rules, generator)
val session = EditorSession.create(document, extensions, config) // with FormFieldExtension(field)
val binding = new EditorFormBinding(session, field)

binding.submitValue      // always well-defined
binding.enterSource()    // the textarea takes over
binding.editDraft(text)  // unconfirmed input
binding.applyDraft()     // atomic against the baseline
```

`EditorFields.json(name, support)` is the other choice — there is no default and no automatic
switch; the application must decide explicitly.

## The format boundary sits **before** commit

This is the part of the contract that is easy to miss and carries everything else: the boundary is
checked before commit, even for programmatic commands. A `ToggleUnderline` in a strict CommonMark
field cannot produce a canonical state whose form value is stale — it produces no state at all.
Document, form value and history all stay untouched; a test asserts each of the three
independently ("only disable the UI" would be a different, weaker guarantee).

The kernel already had the shape for this: `StateField.reduce` runs against the fully normalized
candidate during commit and a `Left` rejects the transaction. That is why "the representability
rule" and "the derived state field" are one object here, not two — a separate `PreCommitRule`
asking the same question would either duplicate the encode step or contradict it. The derived
field is **not** persisted and is recomputed on undo — a cached encode result inside a history
snapshot would be a cache nobody invalidates.

## The source draft

`SourceDraft` holds the edited string, the baseline revision, and the textarea selection. It is
**not** a second document — the document→form projection must never overwrite it, and the draft is
an unconfirmed input value, never a second canonical representation.

The two failure modes this guards against are symmetric: the document silently overwrites what
someone typed, or the draft is mistaken for the truth and silently discards what it never saw. The
**baseline** makes the second one visible — a draft against revision 7, applied at revision 9, is
stale, and `FieldError.StaleDraft` says so instead of guessing.

A failed import **keeps the visible string** — whether the text doesn't parse, the document has
moved on, or the result has no representation in the format. In all three, the mode stays and the
text remains. Discarding is an **explicit** form action, never an implicit side effect.

### Foreign changes during editing

The application chooses via `IntentPolicy`:

| | |
| --- | --- |
| `Defer` | hold back; **re-validated**, not replayed, once the import runs |
| `Reject` | refuse now, with `FieldError.SourceBusy` |

Deferred intents are therefore **thunks**, not values — they must see the document as it is after
import, not the one they were written against. Upload completion is the case that makes this
necessary.

## Without JavaScript

The textarea is visible, named, focusable and normally submittable without JavaScript. It is only
hidden *after* successful activation — that ordering is the entire no-JS contract. A server-
rendered page whose textarea already vanishes at render time has a form nobody can fill out (found
first by a browser test, not an SSR test, since the HTML itself was correct).

**Hidden, yes — `disabled`, never.** A disabled control is not a *successful* form field; the form
would submit nothing for that name. The preview carries **no** form name of its own — two values
for one name would be unresolvable by a server. Action, method, CSRF, validation, persistence and
error reporting all belong to the application; the editor invents no HTTP endpoint for any of it.

## Hydration: what's inside the boundary, and what's outside

The fallback sits outside the swappable rich-view boundary and survives its failure. This is why
`EditorFieldView` composes the textarea **first and outside** the hydration boundary, with only the
preview inside it — if it were inside, a failed claim would take the textarea (and whatever was
typed into it) down with it. Three callbacks connect the field to hydration: `capture` reads the
**textarea**, even though the boundary hands over the preview host, since what must be rescued
can't live in what can fail; `preflight` runs `EditorHydration.preflightContent` against the same
`HtmlSupport` that rendered the page; `onRecovery` records a failed claim so `activation` reports
`Failed`.

`activate()` and `activation(pageHydrated)` are kept separate: one is the decision
([`ember-browser`](../ember-browser/README.md)), the other the action. `importCapturedSource()`
claims against the server snapshot first, then imports the typed source — a parse failure leaves
the draft editable and blocks enhancement.

## The cost, stated plainly

Materializing/assigning the full form string costs at least its length. This cost is measured
separately from core/projection locality and is not presented as O(1): a keystroke touches a
handful of components in the projection, **and** additionally encodes the whole document to a
string — linear in document size, on every commit. That is not a bug to optimize away; it is what
"one form value" means.

## Tests

```bash
sbt --server "scalajs-ember-forms/Test/testOnly *"
```

`EditorFieldSpec` covers the contract headless: ownership, baseline, atomicity, the format
boundary, and intent policy. `EditorFieldViewSpec` renders through an `SsrCursor` — the same path a
server takes. The node types used by these suites are local, on purpose — this module gets no
rich-text profile, and its own block type is the proof it needs none.

What only a real engine can answer lives in the browser harness
([`ember-integration`](../ember-integration/README.md)): the no-JS submission path, the source-mode
path, and hydration of a field whose textarea already has typed content.

## Related modules

- [`ember-markdown`](../ember-markdown/README.md) / [`ember-json`](../ember-json/README.md) — the two field codecs.
- [`ember-clipboard`](../ember-clipboard/README.md) — file intents routed to the media pipeline.
- [`ember-browser`](../ember-browser/README.md) — the hydration boundary this module's field sits on.
</content>

# scalajs-ember-browser-support

Where browser intents meet feature commands. A keystroke is not yet a `ToggleMark` — this module
decides that it becomes one.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-browser-support` |
| Scala package | `ember.editor.browsersupport` |
| Production dependencies | `scalajs-ember-core`, `-rich-text`, `-list`, `-link`, `-code`, `-table`, `-history`, `-ui`, `-browser` |

## Overview

[`ember-browser`](../ember-browser/README.md) is not allowed to import a feature module, and the
rule earns its place exactly here. An input intent is browser vocabulary — `formatBold` is named
that way in the Input Events specification, not in the editor — and which mark it becomes, if any,
is a decision only a profile can make. The payoff: an editor without lists is not an editor that
breaks on `insertUnorderedList` — it simply has no binding for that intent, and the controller lets
the event run natively. The same mechanism carries every intent nobody claims.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-browser-support" % "1.0.3"
```

## Quick start

```scala
val bindings = HistoryBindings.input ++ ListBindings.input ++ RichTextBindings.input
val keys     = EditorBindings.everythingKeyboard

BrowserInputController.attachTo(session, view, port, bindings, keys)
```

## Order is a decision

`InputBindings` and `KeyboardBindings` resolve on the first match. When two modules answer the
same intent — a list wants to split the item on Enter, rich text wants to split the block — whoever
is listed first wins. That is an application-level decision and belongs in the list passed to
`attachTo`, not in a second priority scheme alongside the one commands already have.
`HistoryBindings.input` is listed first: `historyUndo` must never fall through to anything, since
what it would fall through to is the browser's own undo stack.

## What is deliberately not bound

| | |
| --- | --- |
| `Transfer` (paste, drop, cut) | belongs to [`ember-clipboard`](../ember-clipboard/README.md); a binding here that inserted plain text would be a paste implementation without its sanitizing. Until then, paste stays native and is imported. |
| `insertLink` | a link needs a URL, and a `beforeinput` carries none an editor should trust. Asking belongs to the application; `LinkBindings.keyboardWith` takes its dialog as a function. |
| `ReplaceText` (autocorrect) | replaces a range the user did not select — it comes from `getTargetRanges`. Until a DOM comparison makes that safe, the honest path is to let the browser act and import the result. |
| `Superscript`, `Subscript` | the mark set is a profile decision; an unoffered format is not a gap, it's an editor that doesn't offer it. |

## Tab

Deliberately strict, and rightly so — it is the one key that can trap a keyboard user: leaving the
normal editing surface is the default; list indentation or code-block Tab is explicitly opted-in
behavior with a reachable way out. `ListBindings.keyboard` and `CodeBindings.keyboard` therefore do
**not** bind Tab. Whoever wants indentation on Tab additionally takes `tabIndentation` **and** sets
`TabPolicy.IndentsUntilEscape` on the controller — the policy is the exit, and binding the key
without it would build the very trap being avoided. Indenting also always works without Tab
(`Ctrl/Cmd+]` and `Ctrl/Cmd+[`), so the Tab binding stays genuinely optional.

## Shift+Enter is on the keyboard table, Enter is not

Text generally goes through the input pipeline, and that holds for Enter too — a keydown table
sees neither dictation nor autocorrect nor a mobile keyboard, so `insertParagraph` means the same
thing everywhere. Shift+Enter does not: WebKit reports `insertParagraph` for it too, so the intent
path would split the block instead of inserting a break. A prevented `keydown` produces no
`beforeinput` at all, which makes the keyboard route the one place all three engines agree — found
by a browser test, not by reading a spec.

## A composition is one undo step

`HistoryBindings.groupCompositions` listens to the controller's composition notifications and
opens/closes a history group accordingly. History cannot notice this by itself: it groups by what
actually happened, which is correct for typing, but an input method produces intermediate states
that look like independent changes even though the user pressed one key. A discarded composition
closes the group too — an open group left open would swallow everything typed after it.

## Undo belongs to the model

Native and model-based undo stacks must never contradict each other — two stacks over one document
is the kind of bug that looks like data loss, since the browser remembers DOM states the model
never produced. The browser's stack is never used: `historyUndo` is taken over (which also prevents
the native action), and the keyboard shortcut is bound in addition, since a `beforeinput`
feature-test does not guarantee every input type.

## Tests

This module is a table, and tables are tested where they act: in the browser gate
([`ember-integration`](../ember-integration/README.md), `editing.spec.mjs`). The resolution rules
themselves — first match wins, no binding means native — are covered headless in
[`ember-browser`](../ember-browser/README.md)'s `InputPipelineSpec`.

## Related modules

- [`ember-browser`](../ember-browser/README.md) — the neutral input pipeline this module wires into feature commands.
- [`ember-table`](../ember-table/README.md) — `TableBindings` and `TableSelectionView` (drag-to-select) live here too.
</content>

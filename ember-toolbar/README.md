# scalajs-ember-toolbar

An optional accessible command toolbar plus native Link/Image editing dialogs.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-toolbar` |
| Scala package | `ember.editor.toolbar` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-history`, `scalajs-ember-link`, `scalajs-ember-image`, `scalajs-ember-clipboard`, `scalajs-ember-ui`, `scalajs-ember-browser`, `com.anjunar:scalajs-ui-core` |

## Overview

The module consumes typed core commands; document changes only ever run through transactions.
[`ember-core`](../ember-core/README.md), [`ember-ui`](../ember-ui/README.md),
[`ember-browser`](../ember-browser/README.md) and [`ember-forms`](../ember-forms/README.md) do not
need this module — there is no back-edge and no additional UI runtime. Components use the
published `scalajs-ui-core`; a native `<dialog>` handles modality and background inertness, with
Tab wrapped at both dialog edges and Escape handled explicitly.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-toolbar" % "1.0.1"
```

## Assembling a toolbar

`ToolbarAction.command[A]` binds a typed command with its payload and a `CommandState` query;
`ToolbarAction` can also represent actions like opening a dialog. The caller decides order, names
and which features are offered. Bound commands are not auto-registered — the corresponding
RichText/History/Link/Image extension must already be installed in the session. Replacing a text
selection with an image needs `ClipboardExtension`; without it, the whole transaction fails without
losing the text.

```scala
val bold = ToolbarAction.command(
  "bold", "Bold", session, RichText.ToggleMark, StandardMarks.Strong,
  () => ToolbarState.mark(session.state, StandardMarks.Strong, editable())
)
val toolbar = new EditorToolbar(session, selectionPort, Vector(bold))
Runtime.mount(toolbar, DomCursor.root(toolbarContainer))

val service = new EditorDialogService(session, generator, () => editable())
val dialogs = new EditorDialogHost(service, selectionPort, dialogContainer, toolbar.announce)
val links   = new LinkDialog(service, dialogs)
val images  = new ImageDialog(service, dialogs, pickFile = applicationFilePicker)
```

`editable()` must reflect the actual application state: editable mode, not source mode, and no
active composition/recovery. Call `toolbar.refresh()` after changes to these externally-held
states — session commits already refresh it automatically. The dialog service also checks
editability on commit; the browser/core rules stay active in addition. Undo/redo state comes from
the session's `History` instance.

`ToolbarState.mark` reads effective `TypingMarks` at a caret, or the covered runs across a range.
`aria-pressed` distinguishes `true`, `false` and `mixed`. Native buttons carry names and `disabled`;
the bar has one tab stop, with arrow-left/right, Home and End moving between enabled buttons; Tab
leaves the bar. Only a primary mouse-down originating from the focused editor prevents a focus
change — keyboard activation stays on the button. Background commits move neither focus nor native
selection.

The [stylesheet](src/main/resources/ember-toolbar.css) ships as a module resource for the
application to include; it uses visible borders, underlines and system colors under Forced
Colors, and disables motion under Reduced Motion. The library installs no global styles and reads
neither `window` nor `document` at import time — toolbar and dialog host are only created in the
browser, with the container and `SelectionPort` that belong to the editing surface.

## Dialog contract

`EditorDialogService.capture()` creates a session-bound, single-use `DialogTarget`. Anchor and
focus are resolved independently via mapping since capture; inserting at a replaced boundary is
excluded. A deleted target, expired mapping history, undo/redo, or document replacement all
invalidate it. Switching records with identical document content needs `service.invalidate()`.
Errors stay visible as text in the alert region; input stays correctable. Only a successful commit
consumes the target — cancelling changes no content.

Link dialogs set, change and remove links. An empty caret outside a link cannot create a new,
unlabelled link — the application disables that action. The image dialog replaces a text selection
atomically, or edits a selected image; both a single node selection and a range covering exactly
one image are recognized. Alt may be explicitly empty. Changing alt keeps the node ID and media
metadata; changing the source replaces the media reference. Every successful dialog change is its
own undo step.

On close, mouse activation returns the previously focused editor's mapped selection; keyboard
activation returns focus to the triggering button. Return only happens while the dialog still holds
focus. An expired target is reported rather than inventing a new insertion point.

## File callback and lifecycle

`ImageDialog` optionally takes `(DialogTarget, String /* alt */) => Either[EditorError, Unit]`. This
callback runs within the file button's user activation and must resolve the target with
`service.resolve(target)` **before returning**, handing it to the application's own picker or a
[`ember-forms`](../ember-forms/README.md) `MediaCoordinator.capture(range)` — only then may it open
the native picker. On `Right(())` the dialog closes; the `DialogTarget` is invalid afterward. The
upload owns its own target and lifecycle; an aborted file dialog produces no upload. Upload success
and failure are reported by the application via `toolbar.announce(...)` in the visible status
region. The file callback is not offered while editing an existing image.

On editor removal, in order: `dialogs.dispose()` closes the dialog and removes its listeners;
`service.dispose()` invalidates targets and removes the commit subscription; the application
disposes its own pickers/coordinators; then `Runtime.unmount(toolbar)`, followed by input,
selection, view and session disposal.

## Tests

Automated tests cover commands, targets, mouse/keyboard navigation, focus return, real file
selection/HTTP upload, readonly, Forced Colors, Reduced Motion and narrow windows. The composition
case tests the controller protocol; a physical mobile IME/touch or screen-reader acceptance is out
of scope for this module. A standalone demo view is reachable at
`http://127.0.0.1:4188/toolbar` after linking [`ember-integration`](../ember-integration/README.md)
and starting its server.

## Related modules

- [`ember-link`](../ember-link/README.md) / [`ember-image`](../ember-image/README.md) — the node types the dialogs edit.
- [`ember-history`](../ember-history/README.md) — undo/redo state shown by the toolbar.
- [`ember-ui`](../ember-ui/README.md) — the runtime the toolbar and dialogs render through.
</content>

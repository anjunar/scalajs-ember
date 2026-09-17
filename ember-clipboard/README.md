# scalajs-ember-clipboard

Validated document fragments, copy/cut/paste, and structured drag-and-drop.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-clipboard` |
| Scala package | `ember.editor.clipboard` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-json`, `scalajs-ember-html`, `scalajs-ember-browser` |

## Overview

`ember-clipboard` defines a validated, portable document-fragment representation, encodes and
decodes it against multiple clipboard MIME formats with priority-based fallback, and wires that to
real `copy`/`cut`/`paste`/`drag`/`drop` events through [`ember-browser`](../ember-browser/README.md)'s
ownership and dedup mechanisms. Extraction, encoding and decoding are pure and DOM-independent and
run in plain Node.js tests; only the browser-facing port and drop controller touch real clipboard
and drag APIs. The module knows neither the concrete standard adapters nor forms.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-clipboard" % "1.0.1"
```

## Wiring it up

Register `ClipboardExtension(generator)` alongside `RichText(generator)`. Build a
`ClipboardCodec(profile, schema, jsonSupport, htmlSupport, htmlImportSupport)` with the
application's own adapters. `ClipboardService(session, codec)` provides the headless operations.
`BrowserClipboardController(session, selectionPort, inputController, codec, report, files)` binds
the browser events; dispose it before the underlying controllers.

`report` receives success (with import diagnostics) or a `ClipboardError`. The injected `files`
callback receives a `ClipboardFileIntent` carrying files, a target bookmark and the event source —
the connection point for an application's upload pipeline (see
[`ember-forms`](../ember-forms/README.md)'s `MediaCoordinator`). This module never uploads a file
or embeds file data in the document; without a callback, files are rejected with a diagnostic.

```scala
val codec = ClipboardCodec(profile = "my-app", schema, json, html, htmlImport)
val clipboard = new BrowserClipboardController(
  session, selectionPort, browserInputController, codec,
  report = {
    case Left(error)        => showStatus(error.message)
    case Right(diagnostics) => diagnostics.foreach(showWarning)
  },
  files = intent => mediaService.upload(intent.files, intent.destination)
)
// later
clipboard.dispose()
```

Headless, without any browser events:

```scala
val service = new ClipboardService(session, codec)
val data    = service.copy().toOption.get
val decoded = service.paste(data)
```

## Exchange contract

- **Format priority**: `application/x-ember-editor+json`, `text/html`, `text/plain`. Every branch
  is validated independently; an invalid branch can fall back to a valid, lower-priority one.
  Diagnostics name which formats were discarded.
- **Internal envelope**: format `ember-fragment`, version `1`, an application profile string,
  `openStart`, `openEnd`, and a versioned `DocumentJson` document. Schema, codecs and URL policies
  come from the receiver, not the payload.
- **Limits**: by default at most 1,048,576 UTF-16 code units per source format; JSON and HTML are
  limited to 20,000 nodes and depth 64. Plain text is capped at 20,000 nodes before being built.
  CRLF/CR are normalized to LF; every line, including a trailing empty one, becomes a paragraph.
- **Copy** preserves marks, inline wrappers and partial text. Open boundaries count containers
  under the synthetic root. **Paste** splits the target path and merges matching open containers by
  descriptor; different metadata (e.g. a link target) is never merged. A single open paragraph made
  of text/atoms is inserted directly into the existing text context, keeping its surrounding
  heading/list/link containers.
- Copies get new IDs; internal moves of whole nodes use `Operation.Move` and keep their IDs.
  Partial text is cut, its target tracked through primitive mappings, and the fragment inserted in
  the same transaction. An invalid target structure aborts the whole transaction.
- Semantic HTML is exported from `HtmlSupport` without editor attributes. A format is simply
  omitted where no JSON/HTML adapter is available. Atom plain text defaults to U+FFFC; an
  application can inject `atomText` (e.g. an image's alt text). No document DOM is ever read.

## Cut, events and drag

`PendingCut` is single-use: a rejected write deletes nothing. After a successful write, the
document revision must be unchanged (also after edit + undo); pure selection changes may still
resolve through the original bookmarks. `AsyncClipboardPort` is an injectable interface with
tested future/exception/conflict paths — there is no built-in `navigator.clipboard` adapter.

The event adapter confirms that `DataTransfer` echoes back every format it was given — a
confirmation of the event's own store, not an independent confirmation of the OS clipboard (see the
WebKit limitation below).

Cut, paste and move each set `HistoryPolicy.Push`. Clipboard events claim their corresponding
`beforeinput` path only within the same dispatch turn; the claim is released on a microtask, so a
later paste is never suppressed by a stale claim. Unconfirmed `deleteByCut` is rejected;
`deleteByDrag` is prevented, since internal moves already change the source and foreign editors
receive a copy. Non-cancelable input events fall back to the existing native-input/recovery
protocol.

A local drag token is created in the host's window and associated only with its own active
controller, source selection and document revision — a foreign editor always receives a copy;
Ctrl/Alt-drag also copies. The drop target comes from the native coordinate query and the existing
`DomPositionMap`. Native controls inside atoms keep their own events. Readonly and an active
composition block writing clipboard/drop operations.

## Tests and known browser limitation

```bash
sbt --server "scalajs-ember-clipboard/Test/testOnly *"
```

The standard-profile tests live in `ember-standard`'s `ClipboardSpec` and `AsyncClipboardSpec` (a
test-only dependency, to avoid a back-edge from clipboard to standard); `TransactionSpec` covers
draft bookmarks and atomic rejection. The browser harness adds `clipboard.spec.mjs` and
`drop.spec.mjs`.

Real keyboard copy/cut/paste passes under Chromium and Firefox. Under Windows WebKit in the current
Playwright harness, event-written clipboard data is lost on the transition to the native clipboard
— reproducible even with a bare native textarea and no editor adapter. Both keyboard cases are
marked as **expected failures** on that combination only; a fix producing an unexpected pass forces
re-evaluation. Native copy/cut/paste is not certified there. The remaining browser cases test the
event protocol explicitly with synthetic clipboard/drag events; physical drags, mobile clipboard
menus and real IME are outside that scope.

## Related modules

- [`ember-browser`](../ember-browser/README.md) — event ownership and dedup this module reuses.
- [`ember-json`](../ember-json/README.md) — the wire format for the internal fragment envelope.
- [`ember-forms`](../ember-forms/README.md) — the upload pipeline behind the `files` callback.
</content>

# scalajs-ember-demo

A standalone Scala.js showcase application for the Ember editor, structured like the demo in
`scalajs-ui`: navigation, real UI components, sample documents, and viewport windows. **Not
published** — an application, not a library.

The demo links the **local Ember modules** directly; it needs neither a checkout of the neighbor
repository nor a prior release of Ember. Only `scalajs-ui-core` and `scalajs-ui-viewport` are
resolved as published Maven artifacts.

## Running it

Prerequisites: a JDK, sbt, and Node.js.

From the repository root:

```bash
npm --prefix ember-demo run dev
```

This runs `sbt --server "scalajs-ember-demo/fastLinkJS"` and then starts
[http://127.0.0.1:4200](http://127.0.0.1:4200). No `npm install` is needed for a normal start —
the small build/server scripts only need Node.js itself.

Alternatively, split into two terminals for a faster edit loop:

```bash
sbt --server "~scalajs-ember-demo/fastLinkJS"
```

```bash
npm --prefix ember-demo run serve
```

Reload the page after a Scala build; CSS is served directly from the repository on every load.
`EMBER_DEMO_PORT` sets a different port.

## Examples and controls

- **Write an article** — headings, text formatting, quotes, a numbered list and a link.
- **Notes & lists** — nested lists with indent/outdent.
- **Code & media** — a Scala code block with syntax highlighting, an image from the repository,
  and a divider. The "Code" ribbon entry opens language selection: create a block with a
  language, change the language, or lift a code block back to text.
- **Empty document** — a genuinely editable blank page.

Navigation uses hash-route links, including browser back/forward. Each example keeps its
document, selection and undo history across navigation. Reloading the page discards changes —
there is no backend and no autosave. JSON and Markdown can be downloaded from the source view.

The ribbon groups history, font, paragraph, lists and insert actions. Active formats are shown;
unavailable actions are disabled. Keyboard: one tab stop for the ribbon, arrow keys/Home/End
between its actions; Ctrl+Z / Ctrl+Shift+Z drive undo/redo. Inside the editor, Tab indents lists
and code; Escape then Tab leaves the editing surface.

Links and images are edited in ordinary `Viewport.WindowConf` windows. Selection and focus are
preserved across opening/closing. Addresses are checked against the Ember policies. Images can be
edited by URL, alt text, title and width. File uploads need an application service and are not
wired up in this local demo.

Other features: light/dark, reading mode, reset with confirmation, and a toggleable live view for
Markdown, JSON, HTML and the document tree. On narrow screens, navigation and ribbon stay
horizontally scrollable, and the source view moves below the editor.

## Format limits

The demo uses the native CommonMark profile, not an extended Markdown dialect. Tables and raw HTML
are not part of this example profile. Image widths are preserved in the document and in JSON.

## Build and CI

```bash
npm --prefix ember-demo run build       # fastLinkJS build
npm --prefix ember-demo run build:full  # fullLinkJS build
npm --prefix ember-demo run verify      # full build + Playwright tests
```

The published showcase lives at
[anjunar.github.io/scalajs-ember](https://anjunar.github.io/scalajs-ember/). Every push to
`master` builds the optimized application, checks it under its real subpath (`/scalajs-ember/`),
and then publishes the Pages artifact. Pull requests build and check the same artifact without
publishing it — a pre-rendered HTML page in Node, hydrated in the browser.

## Related modules

- [`ember-standard`](../ember-standard/README.md), [`ember-toolbar`](../ember-toolbar/README.md), [`ember-code-highlighting`](../ember-code-highlighting/README.md) — the feature set this demo assembles.
- [`ember-integration`](../ember-integration/README.md) — the separate, unpublished browser test harness (not a demo).
</content>

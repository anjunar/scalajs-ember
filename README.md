# Ember

Ember is a modular, headless-first rich-text editor engine for Scala.js. A single document model
and set of feature modules drive both server-rendered HTML and a live, hydrated browser editor —
there is no separate view model to keep in sync and no `contenteditable`/`execCommand` layer
underneath.

## Overview

Ember keeps an immutable document tree as the source of truth. Every edit goes through a
transaction that produces a new document, a change set, and a position mapping in one atomic
step; commands turn user intent (a keystroke, a toolbar click) into transactions without ever
touching the DOM directly. The same document, the same node-type registry, and the same HTML
semantics drive server-side rendering and the browser's editable surface — hydration claims a
delivered page instead of re-rendering it, and a text edit in the browser writes exactly one
`characterData` mutation.

```text
Document (immutable) --apply operation--> Document', ChangeSet, PositionMapping
       |
       +--> ember-html / ember-ui  --> SSR string / live editable surface
       +--> ember-json             --> versioned wire format
       +--> ember-markdown         --> CommonMark source text
```

The view layer is built on the separately published Scala.js UI runtime,
[`scalajs-ui-core`](https://github.com/anjunar/scalajs-ui) — Ember does not implement its own
renderer, VDOM, or scheduler.

## Installation

Enable Scala.js in `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
```

Add the modules your document needs. A minimal rich-text editor:

```scala
enablePlugins(ScalaJSPlugin)
scalaVersion := "3.3.8"
libraryDependencies ++= Seq(
  "com.anjunar" %% "scalajs-ember-core"      % "1.0.1",
  "com.anjunar" %% "scalajs-ember-rich-text" % "1.0.1",
  "com.anjunar" %% "scalajs-ember-html"      % "1.0.1",
  "com.anjunar" %% "scalajs-ember-ui"        % "1.0.1"
)
```

## Quick start

Build a document, mount it, and dispatch a command:

```scala
import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.standard.ParagraphSupport
import ember.editor.ui.DocumentView
import ui.core.render.DomCursor
import org.scalajs.dom

val generator = NodeIdGenerator.sequential()
val resolved  = ExtensionResolver.resolve(Vector(RichText(generator))).getOrElse(sys.error("extensions"))
val document  = RichText.emptyDocument(resolved.schema, generator).getOrElse(sys.error("document"))
val session   = EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(sys.error("session"))

val view = DocumentView.mount(session, DomCursor.root(dom.document.getElementById("root")), ParagraphSupport.views)

session.update(_.setSelection(RichText.caretAtStart(session.document)))
session.dispatch(RichText.InsertText, "Hello, Ember")
session.dispatch(RichText.ToggleMark, StandardMarks.Strong)
```

For SSR, call `DocumentView.renderToHtml(document, views)` on a plain `Document` — no session, no
selection, no history; browser hydration then claims that same markup through
[`ember-browser`](ember-browser/README.md). See
[`ember-demo`](ember-demo/README.md) for a complete, runnable application (navigation, a toolbar,
light/dark themes, and a pre-rendered-then-hydrated Pages build), and
[`ember-forms`](ember-forms/README.md) for using the editor as a plain HTML form field with a
working no-JavaScript fallback.

## Modules

Ember follows one naming convention throughout: directory `ember-<module>`, sbt ID and artifact
`scalajs-ember-<module>`, Scala package `ember.editor.<module>`. A dependency-boundary lint
(`boundaryCheck` in [`build.sbt`](build.sbt)) enforces the allowed edges between modules as a
build gate, not just a convention.

| Module | Package | Depends on | Responsibility |
| --- | --- | --- | --- |
| [`ember-core`](ember-core/README.md) | `core` | — | Immutable document model, transactions, commands, extensions |
| [`ember-rich-text`](ember-rich-text/README.md) | `richtext` | core | Paragraphs, headings, marks, text editing, Unicode boundaries |
| [`ember-list`](ember-list/README.md) | `list` | core, rich-text | Ordered/unordered lists, indent/outdent |
| [`ember-link`](ember-link/README.md) | `link` | core, rich-text | Typed inline links with a validated URL policy |
| [`ember-image`](ember-image/README.md) | `image` | core | Reference-based media as inline atoms |
| [`ember-code`](ember-code/README.md) | `code` | core, rich-text | Code blocks with typed language metadata |
| [`ember-table`](ember-table/README.md) | `table` | core, rich-text | Tables with rectangular cell selection |
| [`ember-history`](ember-history/README.md) | `history` | core | Undo/redo with explicit grouping rules |
| [`ember-json`](ember-json/README.md) | `json` | core | Versioned JSON wire format, schema migration |
| [`ember-html`](ember-html/README.md) | `html` | core | Semantic HTML contract, fragment import/export |
| [`ember-markdown`](ember-markdown/README.md) | `markdown` | core | CommonMark 0.31.2 parser, writer, source maps |
| [`ember-ui`](ember-ui/README.md) | `ui` | core, html, `scalajs-ui-core` | Keyed document projection onto the UI runtime |
| [`ember-browser`](ember-browser/README.md) | `browser` | core, rich-text, html, ui | Hydration, selection mapping, input pipeline |
| [`ember-browser-support`](ember-browser-support/README.md) | `browsersupport` | browser + feature modules | Concrete keyboard/input bindings per feature |
| [`ember-clipboard`](ember-clipboard/README.md) | `clipboard` | core, rich-text, json, html, browser | Copy/cut/paste and structured drag-and-drop |
| [`ember-code-highlighting`](ember-code-highlighting/README.md) | `codehighlighting` | core, code, ui | Syntax highlighting as a pure view decoration |
| [`ember-forms`](ember-forms/README.md) | `forms` | core, markdown, json, html, ui, browser, image, clipboard | The editor as a form field, with a no-JS fallback |
| [`ember-toolbar`](ember-toolbar/README.md) | `toolbar` | core, rich-text, history, link, image, clipboard, ui, browser | Optional accessible command toolbar and dialogs |
| [`ember-standard`](ember-standard/README.md) | `standard` | nearly everything | The one place node types and renderers meet |
| [`ember-demo`](ember-demo/README.md) | `demo` | most modules + `scalajs-ui-viewport` | Runnable showcase application. **Not published.** |
| [`ember-integration`](ember-integration/README.md) | `integration` | every module | Real-browser test harness. **Not published.** |

`ember-core`, `ember-rich-text`, `ember-history`, `ember-json`, `ember-html` and `ember-markdown`
are headless — no DOM, no UI runtime — and can run in a plain server-side JVM/Node process. Only
`ember-ui`, `ember-code-highlighting`, `ember-browser`, `ember-browser-support`, `ember-clipboard`,
`ember-forms`, `ember-toolbar`, `ember-standard`, `ember-demo` and `ember-integration` know about
`org.scalajs.dom` and/or `scalajs-ui-core`.

## Dependency on scalajs-ui

The generic editing/rendering primitives Ember's view layer sits on — keyed children, text
splicing, hydration boundaries, host mutation guards — live in the neighbor project
[`scalajs-ui`](https://github.com/anjunar/scalajs-ui) and are consumed as a published Maven
Central artifact:

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ui-core" % "1.0.1"
```

Only `ember-ui`, `ember-standard`, `ember-code-highlighting`, `ember-browser`,
`ember-browser-support`, `ember-toolbar`, `ember-integration` and `ember-demo` depend on it (the
last two additionally on `scalajs-ui-viewport`). `ember-core` and `ember-rich-text` stay
completely headless. The dependency boundary lint blocklists `scalajs-ui` as a whole and reopens
exactly `scalajs-ui-core` through an explicit allowlist entry, so an accidental `ui-forms` or
`ui-viewport` import fails the build immediately rather than slipping onto the classpath.

## Build and tests

Requires a JDK and sbt; the browser harness additionally needs Node/npm.

```bash
sbt --server "Test/testOnly *"
```

runs the complete headless Scala test suite. Use `sbt --server` — the `sbtn` thin client does not
start reliably on every machine, and plain `sbt test` delegates to `testQuick` in sbt 2, which is
not a suitable acceptance gate.

To see the editor running, start the demo (builds the local Ember sources, no prior release
needed):

```bash
npm --prefix ember-demo run dev
```

then open [http://127.0.0.1:4200](http://127.0.0.1:4200) — see
[`ember-demo`](ember-demo/README.md) for details. The `master` workflow publishes the same
optimized build to [anjunar.github.io/scalajs-ember](https://anjunar.github.io/scalajs-ember/) as
pre-rendered HTML followed by hydration.

To run the real-browser acceptance suite (Chromium, Firefox, WebKit):

```bash
sbt --server "scalajs-ember-integration/fullLinkJS"
cd ember-integration/browser && npm ci && npm run verify
```

See [`ember-integration`](ember-integration/README.md) for the full suite list, and its README
for a Windows-specific Firefox launch workaround
(`EMBER_FIREFOX_CHANNEL=moz-firefox npm run test:browser`).

### Publishing

Library modules publish as `com.anjunar:scalajs-ember-*`; the demo, harness and benchmark
profiles are `publish / skip`. Releases are signed with `sbt-pgp`, staged under
`target/sona-staging`, and uploaded to the Central Portal:

```powershell
.\scripts\publish-central.ps1
```

```bash
scripts/publish-central.sh
```

Requires a release version in `build.sbt` (a `-SNAPSHOT` is rejected), a GPG signing key, and
credentials in `~/.sbt/sonatype_central_credentials` or
`SONATYPE_CENTRAL_USERNAME`/`SONATYPE_CENTRAL_PASSWORD`.

## Project status and license

The repository is on the `1.0.1` line and under active development. The full Scala suite and the
browser harness run in CI on every push and pull request. Source, releases and issue tracking live
in the [GitHub repository](https://github.com/anjunar/scalajs-ember).

Ember is available under the [MIT License](LICENSE). `ember-markdown` contains a ported rule
structure from commonmark.js (BSD-2-Clause, Copyright (c) 2014 John MacFarlane); the full license
text is in [`ember-markdown/NOTICE`](ember-markdown/NOTICE).
</content>

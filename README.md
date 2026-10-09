# Scala JS Ember

A headless-first rich-text editor engine for Scala.js. One immutable document drives server-rendered HTML and a
hydrated editor in the browser; there is no `contenteditable` command layer underneath.

| Version | Platform | Scala | License |
| --- | --- | --- | --- |
| 1.0.3 | Scala.js | 3.3 | MIT |

Documentation: [English](https://docs.anjunar.com/en/scalajs-ember) · [Deutsch](https://docs.anjunar.com/de/scalajs-ember)
Website: [English](https://anjunar.com/en/scalajs-ember) · [Deutsch](https://anjunar.com/de/scalajs-ember)
Demo: [anjunar.github.io/scalajs-ember](https://anjunar.github.io/scalajs-ember/)

## Installation

Modules are published one by one. A minimal rich-text editor needs the kernel, the rich-text profile, the HTML
contract and the view. Enable Scala.js in `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
```

```scala
enablePlugins(ScalaJSPlugin)

libraryDependencies ++= Seq(
  "com.anjunar" %% "scalajs-ember-core"      % "1.0.3",
  "com.anjunar" %% "scalajs-ember-rich-text" % "1.0.3",
  "com.anjunar" %% "scalajs-ember-html"      % "1.0.3",
  "com.anjunar" %% "scalajs-ember-ui"        % "1.0.3"
)
```

## First example

Resolve the extensions, build an empty document, open a session, mount the view and dispatch a command:

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

For SSR, `DocumentView.renderToHtml(document, views)` renders a plain `Document` – no session, no selection, no
history; [`ember-browser`](ember-browser/README.md) then claims that same markup in the browser. The overview page of
the documentation runs this editor live, with an inspector that shows the document as Markdown, JSON, HTML and tree.

## The principle

**01 / Document – Immutable and always valid.** A `Document` only comes from `Document.build` and already satisfies
its invariants. Holding one means nothing more needs checking.

**02 / Transaction – Every change is one commit.** Commands turn intent into transactions. A transaction yields the
new document, a change set and a position mapping in one step.

**03 / View – Rendered once, claimed by the browser.** The same semantics render the page on the server and the
editable surface in the browser. Hydration claims the page instead of rebuilding it.

```text
Document (immutable) --apply operation--> Document', ChangeSet, PositionMapping
       |
       +--> ember-html / ember-ui  --> SSR string / live editable surface
       +--> ember-json             --> versioned wire format
       +--> ember-markdown         --> CommonMark source text
```

## Contents

The model pages explain what an edit is; the format pages how a document travels; the browser pages how it becomes
editable.

**Document**
- [Document model](https://docs.anjunar.com/en/scalajs-ember/document) – an immutable, always valid tree of nodes referenced by ID
- [Sessions and commands](https://docs.anjunar.com/en/scalajs-ember/session) – how every change becomes one transaction and one commit
- [Extensions](https://docs.anjunar.com/en/scalajs-ember/extensions) – feature modules that contribute node types, commands and rules

**Editing**
- [Rich text](https://docs.anjunar.com/en/scalajs-ember/rich-text) – paragraphs, headings and marks through typed commands
- [Lists](https://docs.anjunar.com/en/scalajs-ember/lists) – create, indent and outdent ordered or unordered lists
- [Links](https://docs.anjunar.com/en/scalajs-ember/links) – validated link targets inside inline content
- [Code](https://docs.anjunar.com/en/scalajs-ember/code) – code blocks, language metadata and line indentation
- [Images](https://docs.anjunar.com/en/scalajs-ember/images) – inline media references, alt text and URL policies
- [Tables](https://docs.anjunar.com/en/scalajs-ember/tables) – rows, columns, headers and rectangular cell selection
- [History](https://docs.anjunar.com/en/scalajs-ember/history) – undo and redo with explicit grouping
- [Selection](https://docs.anjunar.com/en/scalajs-ember/selection) – carets, ranges and table cells that survive edits

**Integration**
- [Clipboard](https://docs.anjunar.com/en/scalajs-ember/clipboard) – copy, cut and paste structured document fragments
- [Form field](https://docs.anjunar.com/en/scalajs-ember/form-field) – a rich editor with one reliable submitted value
- [Custom node types](https://docs.anjunar.com/en/scalajs-ember/custom-nodes) – extend the schema and add format and view adapters

**Formats**
- [Markdown](https://docs.anjunar.com/en/scalajs-ember/markdown) – a CommonMark parser and writer, with losses reported instead of dropped
- [JSON](https://docs.anjunar.com/en/scalajs-ember/json) – the versioned wire format, strict for untrusted input
- [HTML](https://docs.anjunar.com/en/scalajs-ember/html) – one semantic description for the editable surface and the delivered page

**Browser**
- [Rendering and hydration](https://docs.anjunar.com/en/scalajs-ember/rendering) – the same view on the server and in the browser, claimed instead of rebuilt
- [Input and toolbar](https://docs.anjunar.com/en/scalajs-ember/input) – browser intents, key bindings and an accessible command toolbar

**Reference**
- [Modules](https://docs.anjunar.com/en/scalajs-ember/modules) – every module, its package and what it is responsible for

## Limits

- Scala.js only. The headless modules run in a plain Node process on the server; the view needs the browser or the
  Scala.js UI runtime's SSR.
- Ember has no renderer, VDOM or scheduler of its own. The view layer is built on
  [`scalajs-ui-core`](https://github.com/anjunar/scalajs-ui).
- Underline, strikethrough, image dimensions and media IDs have no CommonMark form. Under `LossPolicy.Strict` the
  Markdown export fails; under `AllowLossy` each loss is reported in `EncodedMarkdown.losses`, never dropped silently.
- The stored JSON is the document only: no selection, history or revision.
- Tab leaves the editor by default. Indenting on Tab is opt-in (`EditorBindings.tabIndentation` with
  `TabPolicy.IndentsUntilEscape`); Ctrl+] and Ctrl+[ indent without Tab.

## Modules

Directory `ember-<module>`, sbt ID and artifact `scalajs-ember-<module>`, Scala package `ember.editor.<module>`. A
dependency-boundary lint (`boundaryCheck` in [`build.sbt`](build.sbt)) enforces the allowed edges between modules as a
build gate.

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
| [`ember-demo`](ember-demo/README.md) | `demo` | most modules + `scalajs-ui-viewport` | Runnable showcase application, not published |
| [`ember-integration`](ember-integration/README.md) | `integration` | every module | Real-browser test harness, not published |

`ember-core`, `ember-rich-text`, `ember-history`, `ember-json`, `ember-html` and `ember-markdown` are headless – no
DOM, no UI runtime. Of the rest, only `ember-ui`, `ember-standard`, `ember-code-highlighting`, `ember-browser`,
`ember-browser-support`, `ember-toolbar`, `ember-integration` and `ember-demo` depend on `scalajs-ui-core` (1.0.6),
the last two also on `scalajs-ui-viewport`. The boundary lint blocks `scalajs-ui` as a whole and reopens exactly
`scalajs-ui-core`, so an accidental `ui-forms` or `ui-viewport` import fails the build.

## Development

Requires a JDK and sbt 2; the browser harness also needs Node/npm.

```bash
sbt --server "Test/testOnly *"
```

runs the complete headless Scala test suite. Use `sbt --server`: the `sbtn` thin client does not start reliably on
every machine, and plain `sbt test` is incremental in sbt 2, which is no acceptance gate.

The demo builds the local sources, no prior release needed; it serves on
[http://127.0.0.1:4200](http://127.0.0.1:4200):

```bash
npm --prefix ember-demo run dev
```

The real-browser acceptance suite runs in Chromium, Firefox and WebKit:

```bash
sbt --server "scalajs-ember-integration/fullLinkJS"
cd ember-integration/browser && npm ci && npm run verify
```

See [`ember-integration`](ember-integration/README.md) for the suite list and a Windows workaround for Firefox
(`EMBER_FIREFOX_CHANNEL=moz-firefox npm run test:browser`).

### Releasing

Library modules publish as `com.anjunar:scalajs-ember-*`; the demo, harness and benchmark profiles are
`publish / skip`. The script signs with `sbt-pgp`, stages under `target/sona-staging` and uploads to the Central
Portal:

```powershell
.\scripts\publish-central.ps1
```

```bash
scripts/publish-central.sh
```

It needs a release version in `build.sbt` (a `-SNAPSHOT` is rejected), a GPG signing key, and credentials in
`~/.sbt/sonatype_central_credentials` or `SONATYPE_CENTRAL_USERNAME`/`SONATYPE_CENTRAL_PASSWORD`.

## License

Scala JS Ember is available under the [MIT License](LICENSE). `ember-markdown` contains a ported rule structure from
commonmark.js (BSD-2-Clause, Copyright (c) 2014 John MacFarlane); the full license text is in
[`ember-markdown/NOTICE`](ember-markdown/NOTICE).

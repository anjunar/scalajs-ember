# scalajs-ember-code-highlighting

Syntax highlighting for code blocks — a derived view state, not a document. Optional.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-code-highlighting` |
| Scala package | `ember.editor.codehighlighting` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-code`, `scalajs-ember-ui` |

## Overview

A line-based lexer with a state stack, incremental relexing, grammars for **Scala, JavaScript /
TypeScript, JSON, HTML/XML, CSS, Shell and Markdown**, a swappable `Highlighter` service with
revisioned results, plus `HighlightScheduler` and `CodeDecorations` for the browser. More languages
can be added without changing this contract.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-code-highlighting" % "1.0.1"
```

## Usage

```scala
val view        = DocumentView.mount(session, cursor, CodeSupport.views)
val decorations = CodeDecorations.attach(session, view)
// ...
decorations.dispose()
```

Once per page, in the stylesheet:

```css
::highlight(ember-tok-keyword) { color: #a0461d; }
::highlight(ember-tok-string)  { color: #4a7524; }
::highlight(ember-tok-comment) { color: #858b82; }
```

For output without a live editor — export, a read page, an email — the same colors as classes:

```scala
StaticHighlight.html(code, Some("scala")) // <span class="ember-tok-keyword">val</span> ...
```

## Why there is no DOM

A code block is exactly one text node, and the editor stands on that: position mapping counts
UTF-16 offsets in that one node, recovery compares it against the document, and a running
composition cannot tolerate the DOM around it being rebuilt. Token spans would split that node on
every keystroke — precisely the risk highlighting must avoid: "several text spans change DOM
offsets."

The **CSS Custom Highlight API** colors `Range`s over the existing text instead. No element is
created, no node is split, a `MutationObserver` sees nothing. Live ranges track the text they
cover, so between an edit and the next result the old colors slide along rather than landing on
the wrong characters.

Painting only happens while the DOM text is **exactly** the text a result was computed for. During
a composition it is not — the browser is ahead of the model — and the block simply waits for the
next commit.

## Design

`HighlightScheduler` holds no document-affecting state: it tracks dirty code blocks from
`session.onCommit`, requests highlights only for code blocks, discards stale answers (wrong
revision or text no longer matching), and can be rebuilt from the document at any time.
`LocalHighlighter` runs synchronously in-process; the `Highlighter` interface is deliberately
narrow enough that an async, worker-based implementation could be dropped in later without any
caller change. The lexer is intentionally line-oriented and incremental rather than a real parser,
since it must re-run on every keystroke and resume mid-block; Markdown's fenced code blocks are the
one case where a grammar dynamically selects another grammar, based on the matched fence's info
string.

## Tests

```bash
sbt --server "scalajs-ember-code-highlighting/Test/testOnly *"
```

`LexerSpec` and `IncrementalLexingSpec` cover the grammars and incremental relexing;
`HighlightSchedulerSpec` covers dirty tracking, staleness and dispose.

## Related modules

- [`ember-code`](../ember-code/README.md) — the node type this module decorates.
- [`ember-ui`](../ember-ui/README.md) — the projection this module attaches to.
</content>

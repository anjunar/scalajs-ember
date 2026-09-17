# scalajs-ember-standard

The standard adapters of the Ember editor: the one place node types and renderers know each
other. Individually selectable, never registered as one bundle.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-standard` |
| Scala package | `ember.editor.standard` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text`, `scalajs-ember-list`, `scalajs-ember-link`, `scalajs-ember-code`, `scalajs-ember-image`, `scalajs-ember-table`, `scalajs-ember-markdown`, `scalajs-ember-json`, `scalajs-ember-html`, `scalajs-ember-ui` |

## Overview

`ParagraphSupport` (root, paragraph, text run), `RichTextSupport` (heading, quote, breaks) with
`StandardMarkTags` (the table that turns the five built-in marks into HTML tags), `ListSupport`
(`ul`/`ol`/`li` with start number), `LinkSupport` (`a`, including the `target`/`rel` decision),
`CodeSupport` (`pre`/`code` with a language class) and `ImageSupport` (`img` as a void element).
`TableSupport` adds `table`/`tbody`/`tr` and `th`/`td` with `align`, JSON codecs for table/row/cell,
a GFM block rule (read with `MarkdownProfile.commonMarkSafeWithTables`; a headerless table or a
cell with more than one block are reported losses), and HTML import rules (header from the first
row's `th`, `caption` dropped with a diagnostic). No `everything` bundle includes it — an
application opts tables in explicitly (see [`ember-table`](../ember-table/README.md)); the linker
only pulls in table code when `TableSupport` is actually reachable.

Two more adapter families deliberately do **not** produce HTML, and live here for the same reason
the others do — this is the one place both sides sit on the classpath together:

| | |
| --- | --- |
| `MarkdownRules` / `MarkdownSupports` | syntax → node types |
| `StandardJsonCodecs` / `StandardJsonSupport` | the built-in JSON codecs |

Both are **individually selectable**. Eager collective registrations are explicitly forbidden — if
`StandardJsonCodecs` lived in `ember-json`, choosing JSON would drag in lists, links, code and
images whether the application has them or not. A test enforces this: `StandardJsonSupport.richText`
rejects a document containing a list.

### Markdown: three kinds of rule

A block becomes a node, an inline becomes a node — and an inline becomes a **mark**, not a node.
The third is the one that's easy to miss: `*a*` is not a node wrapped around a run, it is a run
with a property. Policies travel with the rules: `MarkdownSupports.everything(linkPolicy,
mediaPolicy)` takes the same two policies as the commands do — there is no second path to a
`LinkUrl` or `MediaUrl`, so import and dialog **cannot** diverge.

What Markdown cannot write is reported, not silently dropped: underline and strikethrough (outside
CommonMark's own guarantees), image dimensions, a media ID. Under `Strict` the export fails; under
`AllowLossy` it appears in `EncodedMarkdown.losses`. A rejected **link target** leaves the text in
place; a rejected **image source** does not — losing the words of a sentence over a bad address
would be the worse outcome, while an image with no source is nothing at all.

`strong` and `em`, not `b` and `i` — semantic HTML says what is meant, not how it looks. Underline
gets `u`, not because HTML has a good answer for it, but because the mark exists.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-standard" % "1.0.1"
```

## Why this is a separate module

`standard` is deliberately an optional integration module: the node modules know neither Markdown
nor UI, and the format SPIs know no concrete feature nodes. Both sides meet **only** here —
[`ember-rich-text`](../ember-rich-text/README.md) knows nothing of HTML,
[`ember-html`](../ember-html/README.md) nothing of paragraphs. An application processing its
document only as JSON never links this module at all.

Every adapter is its own value, precisely to avoid eager collective registration:

```scala
ParagraphSupport.root       // article
ParagraphSupport.paragraph  // p
ParagraphSupport.text       // span with one text child

ParagraphSupport.semantics  // all three as HtmlSupport
ParagraphSupport.views      // the same as ViewSupport for ember-ui
```

`semantics` and `views` are a convenience, not a requirement — an app that only needs paragraphs
can take the individual entries.

## The decisions behind it

**`article`, not `div`, for the root.** It is self-contained content, and the delivered version
requires semantic HTML: a reader without a stylesheet and a screen reader should encounter the same
document.

**`span` around every text run, in both profiles.** The wrapper avoids merged adjacent SSR text
nodes and enables a unique ID → text-point mapping — two runs side by side would otherwise become a
single text node in the output.

**`data-ember-node` only in the editor view.** Browser-side wrappers and editor attributes are
stripped on exchange; rather than removing them afterward, the content profile never produces them.

**`alt=""` is written, not omitted.** A decorative image uses explicitly empty alt text; omitting
the attribute would have a screen reader read the filename instead — empty means one thing,
missing means another.

**`width` and `height`, whenever the document has them.** Absolute values reduce layout jumps; a
browser that knows the ratio reserves the space before the image arrives. The adapter checks
nothing extra here — `PositivePixels` cannot carry a zero.

**No fetching, no re-measuring.** No external URL is ever fetched by the parser or the SSR server.
The adapter turns a node into attributes; whether the image exists is the browser's question, and
SSR never asks it.

**Decoding is exactly as strict as the command.** `ImageJsonSupport.codec(policy)` takes the same
`MediaUrlPolicy` as `ImageExtension` — a source from a payload is exactly as unchecked as one from a
dialog, and there is no second path to a `MediaUrl`.

## Quick start

```scala
// Editing surface
DocumentView.mount(session, DomCursor.root(container), ParagraphSupport.views)

// Delivered HTML
DocumentView.renderToHtml(document, ParagraphSupport.views)
```

## Tests

```bash
sbt --server "scalajs-ember-standard/Test/testOnly *"
```

`MarkdownDocumentSpec` runs source → syntax → nodes and back in one pass, with no HTML and no DOM
in between. `StandardJsonRoundTripSpec` checks the built-in codecs via round-trips rather than
expected payloads — a round trip proves encoder and decoder agree, which is the property a stored
document actually depends on.

`ProjectionSpec` runs headless against an `SsrCursor` — the same path `DocumentView` takes with any
cursor, so SSR and browser producing the same output is not a coordination between two
implementations but the same code. It checks index and order, both render profiles, instance
retention across text edits and moves, index cleanup on removal, two sessions sharing IDs,
`onProjected` against `projectedRevision`, and that a text edit in a 4001-node document touches
fewer than five components. What is not verifiable there — DOM identity and the scope of writes —
lives in the browser harness's `projection.spec.mjs`.

## Related modules

- [`ember-list`](../ember-list/README.md), [`ember-link`](../ember-link/README.md), [`ember-code`](../ember-code/README.md), [`ember-image`](../ember-image/README.md), [`ember-table`](../ember-table/README.md) — the feature node types wired here.
- [`ember-markdown`](../ember-markdown/README.md), [`ember-json`](../ember-json/README.md), [`ember-html`](../ember-html/README.md), [`ember-ui`](../ember-ui/README.md) — the format/renderer sides wired here.
</content>

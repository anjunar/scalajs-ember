# scalajs-ember-markdown

A CommonMark parser and writer in Scala. No DOM, no HTML intermediate, no JavaScript dependency.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-markdown` |
| Scala package | `ember.editor.markdown` |
| Production dependencies | `scalajs-ember-core` |

## Overview

The parser covers CommonMark **0.31.2** — blocks and inlines alike: paragraphs, headings, quotes,
lists, code, thematic breaks, HTML blocks, emphasis, links (including reference definitions),
autolinks, images, code spans, escapes, and character references.

**How far that reaches is measured, not asserted:** the parser reproduces **651 of the 652**
examples in the official conformance suite character-for-character, and **628 of 652** survive
writing and re-parsing unchanged. Both numbers are checked exactly, in CI, not as a lower bound —
so a regression is as visible as an improvement nobody recorded.

The typed SPI that maps syntax onto registered node types (`MarkdownRule`, `MarkdownCodec`,
`DocumentSourceMap`) lives here too; the rules themselves live in
[`ember-standard`](../ember-standard/README.md) — nothing in this module knows what a
`ParagraphNode` is. `MarkdownProfile` states exactly what is covered:

```scala
MarkdownProfile.commonMarkSafe.conformance  // Conformance.Inlines
MarkdownProfile.blocksOnly.conformance      // Conformance.BlocksOnly
```

Until a profile's fixture cases pass in full, only the actually-tested subset is advertised —
a comment would be a promise; a field is a value an application can read, and what `Inlines` is
worth is a number in the test suite.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-markdown" % "1.0.1"
```

## Quick start

```scala
Markdown.parseSyntax(source, MarkdownProfile.commonMarkSafe) match
  case Right(result) =>
    result.document.children.foreach {
      case MarkdownBlock.Heading(_, span, level, _, inlines) => ...
      case MarkdownBlock.CodeBlock(_, _, literal, fence)     => ...
      case _                                                 => ...
    }
  case Left(error) => showError(error.render)
```

`MarkdownProfile.untrustedPaste` is the same rule set under paste-sized limits, for source text the
user did not write themselves.

## A syntax tree, not a document

The syntax AST is immutable and only an import/export value, never a second, permanently
synchronized editor state. Nothing here knows what a `ParagraphNode` is, and nothing here is
editable — which is why the module depends only on the kernel: a parser that produced
`ParagraphNode` directly would have to know the rich-text profile, and an application with its own
block types could never use it. The rules that map syntax onto registered node types live in the
integration module.

Link/image destinations are plain `String`s here, not `LinkUrl`/`MediaUrl` — those types live in
[`ember-link`](../ember-link/README.md) and [`ember-image`](../ember-image/README.md), and this
module gets only the kernel. The "the type is the door" pattern still applies one layer up:
[`ember-standard`](../ember-standard/README.md) runs the application's policy exactly where syntax
becomes a document. The parser normalizes; the adapter decides.

## Origin and license

`BlockParser.scala` and `InlineParser.scala` are **ports of the rule structure** from
`lib/blocks.js` and `lib/inlines.js` of commonmark.js 0.31.2 (BSD-2-Clause, Copyright (c) 2014 John
MacFarlane; full license text in [NOTICE](NOTICE)). What was carried over is the *form*: the order
of block starts, each container's continuation condition, the lazy-continuation rule, and list-
marker padding arithmetic — the parts that constitute real parsing work, and re-deriving them from
the specification would produce a worse parser with the same rules.

The code itself was not carried over. The differences are deliberate:

| | |
| --- | --- |
| **Offsets instead of line/column** | commonmark.js reports `sourcepos` as tab-expanded line/column; this editor counts UTF-16 ranges everywhere, and converting afterward is exactly where an off-by-one hides. |
| **A budget** | this module bounds work steps; the original has no such concept. |
| **No smart punctuation** | the original can round quotes and turn `--` into dashes; there is no toggle for that here — a round trip that preserves meaning must not rewrite the author's punctuation. |
| **A named entity subset** | see [Entities](#entities). |
| **An immutable result** | the mutable tree of open blocks lives only during parsing; nothing outside the file can observe a half-finished block. |

The block-start order is **load-bearing**, not cosmetic: a Setext underline must be tried before
the thematic break, or `---` ends the paragraph above it instead of turning it into a heading; a
list item must come after the thematic break, or `- - -` starts three nested lists. Reordering it
is not a refactor.

## Limits

| | Default | `paste` |
| --- | --- | --- |
| `maxSourceChars` | 4 MiB | 256 KiB |
| `maxLines` | 200,000 | 10,000 |
| `maxDepth` | 100 | 24 |
| `maxBlocks` | 100,000 | 5,000 |
| `maxSteps` | 20,000,000 | 1,000,000 |

**Why a step budget and not just a depth limit.** Expensive input isn't always deep: `"> " * 50000`
is 100 kB and nests fifty thousand quotes — `maxDepth` catches that. But a line of ten thousand
backticks is neither large nor deep and still costs real work. The budget is charged per line and
per attempted block start, so anything that turns linear input into superlinear work is caught by
the budget, not the clock. Two equally-sized sources make the difference visible: `"text\n" * 30`
costs 30 steps, `"*x*\n" * 30` costs 60, because `*` passes the pre-filter and every block start is
attempted.

Recursion happens at exactly one place — building the immutable tree — and `maxDepth` bounds it, so
a deeply nested input returns `ParseError.LimitExceeded` instead of crashing.

**GFM tables only on request.** `MarkdownProfile.tables` is off in every plain CommonMark profile;
`commonMarkSafeWithTables` turns pipe tables on. A paragraph whose second line is a matching
alignment row becomes `MarkdownBlock.Table` with `TableRow`/`TableCell` and alignment; `|` stays a
plain character in a cell. The writer emits tables with outer pipes and escapes `|`. The
conformance run itself always runs without the extension, so its numbers stay unaffected.

A syntax error is not one of the cases handled here, and that's not an oversight: CommonMark has no
invalid input — every string is a valid document, so anything that can go wrong here is a resource
limit.

## Source spans

Every block carries its `SourceSpan` in UTF-16 units. `SourceMap` is the reverse index — offset to
block:

```scala
result.sourceMap.blockAt(result.document, offset) // the innermost block there
result.sourceMap.spanOf(block.id)                 // and back
result.sourceMap.lineAt(offset)                   // line number, found by binary search
```

A block boundary belongs to what **follows**: `SourceSpan.contains` is half-open, so a caret
between two paragraphs lands in the second — the documented affinity syntactic delimiters need, and
it matches how a caret behaves everywhere else in the editor. Inlines carry spans too, down to the
individual delimiter — the source model this was ported from only tracks positions at block level.
The case a naive recalculation fails on is covered by a test: a quote's content is not a plain
substring of the source, because `> ` is dropped during block parsing, so every block carries its
own line map back to the source.

## Entities

Numeric character references are **complete** — `&#35;`, `&#x1F600;`, U+0000 mapped to U+FFFD — no
table is needed for those, so none is a compromise. Named references are a **named subset**, and
that is a decision: CommonMark points at the WHATWG list of 2231 names, most of which never appear
in an editor document; shipping all of them would add roughly 150 kB of table to every browser
bundle that links this module.

`EntityTable.common` covers what shows up in prose instead: the five XML names, the entire Latin-1
supplement, quotation marks, dashes, currency, legal symbols, and common math/arrow symbols — about
150 entries. An unknown name is left **unchanged**, so a round trip never loses it, and an
application that needs more supplies more:

```scala
MarkdownProfile.commonMarkSafe.copy(entities = EntityTable.common.and("Dcaron" -> "Ď"))
MarkdownProfile.commonMarkSafe.copy(entities = EntityTable.numericOnly)
```

The same pattern as `LinkUrlPolicy`/`MediaUrlPolicy`: the library picks a defensible default, the
application overrides it and knows that it did. This is also the one conformance case, of 652, that
does not pass.

## Writing

`MarkdownWriter.write` turns a syntax tree back into Markdown, always successfully. What it
promises is precise, and the precision matters, because the obvious expectation is the wrong one:

| | |
| --- | --- |
| **Guaranteed** | `decode(encode(x)) ≃ x` — write, re-parse, the same tree |
| **Not guaranteed** | `encode(decode(source)) == source` |

The writer picks one canonical spelling — a list written with `+` comes back with `-`. Preserving
the input spelling would mean threading it through the document model, and the model holds what
text **means**, not how someone typed it. `HeadingStyle` is the one exception, since it costs a
single field and otherwise couldn't be reconstructed later.

What the writer does guarantee: a **safe fence length** (content with three backticks gets four,
and the same one level down for code spans, with padding if the content starts with a backtick),
**position-dependent escaping** (a `#` at line start is escaped, one mid-line is not — escaping too
much would just make the output unreadable), and a **backslash instead of two trailing spaces** for
a hard break, since invisible trailing whitespace does not survive an editor that trims it.

## Document and back

`MarkdownCodec` is the path between source text and a document — with no HTML/DOM step in between:
`source → syntax → nodes` and back.

```scala
MarkdownCodec.decode(source, schema, rules, generator, rootId) // Either[MarkdownError, DecodedDocument]
MarkdownCodec.encode(document, rules, LossPolicy.Strict)       // Either[MarkdownError, EncodedMarkdown]
```

### Three kinds of rule, because syntax has three shapes

| | |
| --- | --- |
| `MarkdownBlockRule` | a block becomes a node — paragraph, heading, quote, list, code |
| `MarkdownInlineRule` | an inline becomes one or more nodes — text, image, link |
| `MarkdownMarkRule` | an inline becomes a **mark**, not a node — emphasis, strong, inline code |

The third is the one that's easy to miss and impossible to retrofit later: `*a*` is not a node
wrapped around a run, it is a run with a mark. The codec carries a `MarkSet` down the inline tree
instead of building a wrapper — a rule that wanted a wrapper would produce a document the
rich-text profile rejects.

### Loss is a decision, not a surprise

`Strict` refuses information loss; `AllowLossy` must be chosen deliberately by the application.
What Markdown cannot write is enumerated and reported, never silently dropped:

| | |
| --- | --- |
| Underline, strikethrough | no CommonMark guarantee |
| Image dimensions, media ID | same |
| A link target the policy rejects | the **text** survives, the target does not |

Under `Strict`, each of these is a `MarkdownError.WouldLose` and no export is produced. Under
`AllowLossy` one is produced, and `EncodedMarkdown.losses` says what's missing.

### Policies travel with it

`MarkdownSupports.everything(linkPolicy, mediaPolicy)` takes the same two policies as the commands
— there is no second path to a `LinkUrl` or `MediaUrl`, so import and a dialog **cannot** diverge.

### Document positions

`DecodedDocument.sourceMap` is the node-side counterpart of the syntax source map:

```scala
result.sourceMap.spanOf(node.id) // where this node came from
result.sourceMap.nodeAt(offset)  // which node sits here
```

A paragraph and its one run cover **the same** characters, so a tie is resolved deterministically:
it goes to the first-recorded node, which — since the codec decodes children before their parents —
is always the deeper one. That's why this is a `Vector`, not a `Map`: a map would answer the same
question differently depending on hashing.

## Tests

```bash
sbt --server "scalajs-ember-markdown/Test/testOnly *"
```

Five suites, each asking a different question:

| Suite | Question |
| --- | --- |
| `MarkdownBlockSpec` | Is the block structure right, case by case? |
| `MarkdownInlineSpec` | Is the inline structure right, case by case? |
| `CommonMarkConformanceSpec` | **How much** of the specification is right? |
| `MarkdownRoundTripSpec` | Does a tree survive writing and re-parsing? |
| `MarkdownSourceMapSpec` | Are the source spans right — and does the SPI run without a profile? |

`MarkdownSourceMapSpec` builds its own node types and marks — not a workaround, but the proof: if
`MarkdownCodec` ever had to know what a `ParagraphNode` is, this file would stop compiling. The
standard profile's own rules are tested where they live, in
`ember-standard`'s `MarkdownDocumentSpec`.

The first two check **structure**, not rendered HTML — the tree is what the rest of the editor
consumes. The last two run the official conformance suite in full, all **652 examples**, versioned
under `src/test/resources/markdown/spec-0.31.2.txt`.

### What the numbers say

| | |
| --- | --- |
| **651 of 652** | reproduced character-for-character by the parser |
| **628 of 652** | survive writing and re-parsing unchanged in shape |

Both are checked **exactly**, not as a floor, so a regression is as visible as an improvement
nobody recorded. On a failure the suite prints the count per section, since one number never says
**what** moved. 628 being lower than 651 is expected — round-tripping is a strictly stronger
property; an input the parser reads correctly can still come back from the writer in a spelling
that reads back differently (unusually indented list items are the most common case). Each such
difference is a finding, not a broken promise.

### What the suite does not claim

Full CommonMark conformance is never asserted from a handful of happy-path tests — it measures
three separate things: **robustness** (all 652 examples parse, within default limits, without an
exception), **well-formed spans** (every span of every parse lies within its parent's and within
the source — a source map with a span outside its block is worse than no source map), and
**coverage as a number**.

### Why the fixtures become Scala source

Because there is nothing to read at runtime — the tests run as a Scala.js module, with no
filesystem and no classpath. `project/MarkdownSpecFixtures.scala` translates `spec.txt` into a
Scala file at build time, so the specification version lands in the file name and as a constant in
the generated code, and the example count falls out of generation rather than being a claim in a
comment.

## Related modules

- [`ember-core`](../ember-core/README.md) — the only module this depends on.
- [`ember-standard`](../ember-standard/README.md) — the concrete `MarkdownRule`s that map syntax to node types.
- [`ember-forms`](../ember-forms/README.md) — a Markdown-backed form field built on `MarkdownCodec`.
</content>

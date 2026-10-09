# scalajs-ember-code

Code blocks with typed language metadata — independent of highlighting. Headless and optional.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-code` |
| Scala package | `ember.editor.code` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text` |

## Overview

`ember-code` adds `CodeBlockNode`, typed `CodeInfo`/`CodeLanguage`, four commands, Enter handling
and two normalization rules to the [rich-text](../ember-rich-text/README.md) profile.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-code" % "1.0.3"
```

## Quick start

```scala
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), CodeExtension(generator)))
  .getOrElse(...)

session.dispatch(CodeCommands.ToggleCodeBlock, CodeInfo.of("scala"))
session.dispatch(CodeCommands.SetCodeInfo, CodeInfo.parse("scala {highlight=3-5}"))
session.dispatch(CodeCommands.IndentLine)
session.dispatch(CodeCommands.OutdentLine)
```

## No highlighter

There is no syntax highlighter and no CodeMirror dependency here, on purpose. The language is
**metadata** — it says what the text is, not how it looks. Visible highlighting must never force a
persistent mark decomposition of every code line; a document where each token were a marked run
could be read neither as a document nor written as a fence. What this module guarantees is exactly
what a highlighter and a Markdown fence both need: the content, verbatim, including its blank
lines. [`ember-code-highlighting`](../ember-code-highlighting/README.md) colors the view without
touching the document or the DOM text node.

## Content model

Exactly one `TextNode`, whose text may contain newlines, without marks. The single run is not an
implementation accident: a code block's content is one string — what a fence writes, what a
compiler reads, what an author copies. Multiple runs would need their own merge rule and leave a
door open for marks that this node type is meant to keep shut.

| Rule | Bound to | For |
| --- | --- | --- |
| `contentIsOneRun` | `CodeBlockNode` | multiple children become one run; a paragraph moved in loses its wrapper and keeps its text; an empty block gets a run |
| `runInCodeIsPlain` | `TextNode` | a run in a code block carries no marks |

The split matters: a mark change touches the **run**, not the block, and a merely-touched ancestor
is never a transform candidate — a rule bound to the block would never be asked. Content that
wanders into a code block is meant to *become* code: rejecting it would fail the whole transaction
on a paste, discarding it would lose text. Runs join without a separator (two side by side were one
line); whole blocks join with a newline (two paragraphs that wander in were two lines).

## Info string

Markdown writes an info string; its first word is the language, the rest is whatever the author's
tooling wanted:

```text
```scala {highlight=3-5}
     ^^^^^ language   ^^^^^^^^^^^^^^ meta
```

Keeping the two separate lets an HTML adapter write `language-scala` without inventing a parser,
and lets the info string be written back unchanged. `CodeLanguage` rejects what a fence cannot
write — whitespace, control characters, backticks. An info string that names no valid language is
not an error — it is an info string without a language, and its text survives as `meta` so a round
trip never loses it.

## Enter

Inside a code block, Enter inserts a newline — splitting the block would turn one listing into
two, and the content type allows newlines specifically so it doesn't have to. The one exception:
Enter on an empty trailing line leaves the block, the same convention every editor with code blocks
uses, since a code block otherwise has no edge a caret could step over. The newline that led there
is removed — it was the request to leave, not part of the code. Outside a code block the handler
returns `Pass`, and the rich-text handler splits the block as usual.

## Indent and outdent

Optional. A unit is **two spaces**, never a tab — a tab renders at whatever width the reader's
viewer chooses, which is exactly what code indentation cannot tolerate. Outdent removes as much as
is there; a line indented by three spaces loses two, not three, so a single press of its
counterpart can always be undone.

## Converting

A paragraph becomes a code block via `Replace`: ID and children are kept, so every point inside it
survives. Converting back turns **one line into one paragraph** — a literal `\n` inside a paragraph
run would render as a space in HTML and silently change meaning; code lines are lines, and
converting a listing back is expected to produce paragraphs.

## Tests

```bash
sbt --server "scalajs-ember-code/Test/testOnly *"
```

`CodeSpec` covers the info string, the content model and its repairs, Enter and exit, indent and
outdent, and — as its own assertion — that exactly one unmarked run remains after any sequence of
commands. The `pre`/`code` rendering, and the proof that a language change is a view replacement
while a bare attribute change is not, live in
[`ember-standard`](../ember-standard/README.md)'s `CodeProjectionSpec`.

## Related modules

- [`ember-rich-text`](../ember-rich-text/README.md) — the profile this module extends.
- [`ember-code-highlighting`](../ember-code-highlighting/README.md) — optional syntax coloring as a pure view decoration.
</content>

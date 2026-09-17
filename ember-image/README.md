# scalajs-ember-image

External images as inline atoms with a validated media policy. No upload, no file dialog, no
object URL.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-image` |
| Scala package | `ember.editor.image` |
| Production dependencies | `scalajs-ember-core` |

## Overview

`ember-image` sits directly on the kernel, not on [rich-text](../ember-rich-text/README.md) — an
image needs nothing from that profile. It has no marks, no children, and no paragraph to live in;
it is an atom that sits between two characters. `ImageNodeSpec` builds its own local `BlockNode`
for exactly this reason: `ParagraphNode` is not even on its classpath.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-image" % "1.0.1"
```

## Quick start

```scala
val policy   = MediaUrlPolicy.default
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), ImageExtension(generator, policy)))
  .getOrElse(...)

policy.parse(input) match
  case Right(src) =>
    val image = ImageNode(generator.nextFor(session.document), MediaReference(src), "Alt text")
    session.dispatch(ImageCommands.InsertImage, image)
  case Left(error) => showError(error.render)

session.dispatch(ImageCommands.UpdateImage, (_: ImageNode).copy(alt = "Better description"))
```

## What is deliberately absent

A picker, an upload, a progress bar, an `AbortSignal`. Uploads are an application service; browser
file handling, progress and cancellation belong to a browser/forms port
([`ember-forms`](../ember-forms/README.md)'s `MediaService`), not to the core node. What this
module accepts is a finished `MediaReference` — something that already exists at an address. An
upload only produces a validated `MediaReference` after durable storage succeeds, and even a
direct external URL never forces an upload.

The consequence is worth stating plainly: **inserting an image is an ordinary document change** —
a history step, no lifecycle — and an undo removes the node without touching any file. **No file
data lives in the document**: a `MediaReference` is an address and an optional identifier, nothing
else; there is no field a Base64 string or a `blob:` URL could go in. **No fetching**: neither the
parser nor an SSR server ever retrieves an external URL — whether the image exists is a question
for the browser, asked later, never during SSR.

## `MediaUrl` — the type is the door

Same pattern as `LinkUrl` in [`ember-link`](../ember-link/README.md): there is no way to build an
`ImageNode` without a `MediaUrl`, and no way to get a `MediaUrl` without a policy. The command
path and the decode path cannot diverge — `ImageJsonSupport.codec(policy)` takes the same policy
and runs the same check.

### Stricter than the link policy, and why

| | Link | Media |
| --- | --- | --- |
| `https` | allowed | allowed |
| `http` | allowed | only after an explicit opt-in |
| relative | allowed | allowed |
| `mailto`, `tel` | allowed | refused |
| `data:`, `blob:`, `javascript:`, `file:` | refused | refused |
| protocol-relative (`//host/...`) | refused | refused |
| host allowlist | — | optional |

A link is followed by a reader who chose to; an image is loaded by the page itself, from a host
the author named, carrying the reader's address along. `http` on an `https` page is also mixed
content that browsers block anyway; `MediaUrlPolicy.allowingHttp` exists regardless — an intranet
editor may decide that for itself.

Normalization is the same as `ember-link` (entities first, then whitespace/control characters
before the colon, then scheme case only) — including invisible characters that `isWhitespace` and
`isControl` alone miss (zero-width space, protected space, byte-order mark, word joiner), covered
by the same predicate in both modules.

### Host allowlist

`MediaUrlPolicy(hosts = Some(Set("cdn.example.com")))` — an editor may link anywhere but load only
from its own CDN. Comparison ignores case and port and **skips userinfo**: a URL like
`https://cdn.example.com@evil.example/x.png` loads from `evil.example`, and reading only up to the
first `@` would see the allowed host and miss the real one. Relative paths have no host and are
unaffected by the allowlist — they load from the page itself.

## The node

| Field | Type | |
| --- | --- | --- |
| `source` | `MediaReference` | address plus an optional application `MediaId` |
| `alt` | `String` | may be empty, **and that means something** |
| `title` | `Option[String]` | whitespace-only is rejected |
| `width`, `height` | `Option[PositivePixels]` | 1 to 100,000 |

**Empty alt text is not missing alt text** — a decorative image uses an explicitly empty alt text,
not a filename by default. An HTML adapter writes `alt=""` even when there is nothing, since
omitting the attribute would have a screen reader read the filename instead. `PositivePixels` is
validated because a size is meant to reserve layout space: zero reserves nothing, and a value in
the millions just signals a bug upstream.

## Inserting and updating

| Case | What happens |
| --- | --- |
| Caret mid-run | the run is split, the image sits between the halves |
| Caret at start or end | the image sits before or after |
| Caret at a child position | the image is inserted there |
| No caret | `Pass` |

`UpdateImage` runs through `Replace`, preserving identity — a bookmark on the image survives an
alt-text change, which is the most common edit. There is no normalization transform in this
module: an image has no children that could sit wrong, and its fields are already typed so that an
invalid one cannot be built.

## Tests

```bash
sbt --server "scalajs-ember-image/Test/testOnly *"
```

`MediaUrlPolicySpec` is the suite that matters most — every case is one a browser executes.
`ImageNodeSpec` covers insert, update, and pixel dimensions. The `<img>` rendering and JSON codec
live in [`ember-standard`](../ember-standard/README.md)'s `ImageAdapterSpec`.

## Related modules

- [`ember-core`](../ember-core/README.md) — the only module this depends on.
- [`ember-link`](../ember-link/README.md) — the same "typed URL" pattern with a more permissive policy.
- [`ember-forms`](../ember-forms/README.md) — the upload lifecycle that produces a `MediaReference`.
</content>

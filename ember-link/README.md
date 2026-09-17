# scalajs-ember-link

Typed inline links with a validated URL policy. Headless and optional — no dialog required.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-link` |
| Scala package | `ember.editor.link` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text` |

## Overview

`ember-link` adds `LinkNode`, a `LinkUrl` opaque type that can only be constructed through a
`LinkUrlPolicy`, `SetLink`/`RemoveLink` commands, and three normalization rules to the
[rich-text](../ember-rich-text/README.md) profile.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-link" % "1.0.1"
```

## Quick start

```scala
val policy   = LinkUrlPolicy.default
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), LinkExtension(generator, policy)))
  .getOrElse(...)

policy.parse(input) match
  case Right(url)  => session.dispatch(LinkCommands.SetLink, LinkTarget(url, Some("Title")))
  case Left(error) => showError(error.render)

session.dispatch(LinkCommands.RemoveLink)
```

No dialog is required: everything here takes a `LinkTarget` and produces a document change. Where
the target comes from — a dialog, a paste, a Markdown import, a test — is a separate concern; a
dialog can sit in front of this module (see [`ember-toolbar`](../ember-toolbar/README.md)) without
anything here changing.

## A container, not a mark

A link is an inline container with children and a target, not a text mark — a mark has no
children and no data beyond its own identity, and links deliberately have both. This also means
formatting and linking never need to know about each other: bold inside a link is a mark on a run
inside the link, and neither rule had to be told about the other.

## `LinkUrl` — the type is the door

There is no way to build a `LinkNode` without a `LinkUrl`, and no way to get a `LinkUrl` without a
policy. Command and import paths **cannot** diverge, because there is only one door. The value
carried is the normalized form, never the raw typed string.

### Normalization order is fixed

A plain `startsWith("javascript:")` check fails against every one of these, and browsers execute
all of them:

```text
" javascript:alert(1)"        leading whitespace
"java\tscript:alert(1)"       tab inside the scheme
"java\nscript:alert(1)"       newline inside the scheme
"JaVaScRiPt:alert(1)"         mixed case
"&#106;avascript:alert(1)"    HTML entity for the first letter
"java&#9;script:alert(1)"     entity for the tab
```

So the order is fixed and not reorderable: entities are decoded first (an entity can encode a
space or a scheme letter), then whitespace/control characters are stripped everywhere before the
colon (that is where a scheme can be torn apart), then only the scheme is lowercased. Only the
scheme is touched — paths and queries stay case-sensitive, and the host is left alone (canonicalizing
it would mean deciding about ports, IDNA and userinfo, and getting that half right is worse than
not trying).

### What passes

| | |
| --- | --- |
| Default | `http`, `https`, `mailto`, `tel`, and relative addresses |
| `LinkUrlPolicy.internalOnly` | relative only |
| `LinkUrlPolicy(allowRelative = false)` | absolute only |
| custom | `LinkUrlPolicy(schemes = Set("https", "ftp"))` |

What is allowed is an application decision — an intranet editor and a public one won't agree on
`http`. This is a **different** policy from media: see [`ember-image`](../ember-image/README.md)'s
`MediaUrlPolicy`. A relative target that looks like it starts with a scheme
(`page:with:colon`) is rejected, per RFC 3986 — the way out is a leading `./`.

## Setting and removing

| Case | What happens |
| --- | --- |
| Range selected | the covered runs are cut at both ends and wrapped |
| Caret inside a link | its target changes |
| Caret elsewhere | `Pass` — nothing to wrap |
| Range spanning multiple blocks | one link per block; a link is inline, a node has one parent |

Cutting is handled by the shared `RangeFormatting` machinery from
[`ember-rich-text`](../ember-rich-text/README.md). Unlinking is also a move: runs keep their IDs,
text and marks, and text-run normalization then re-merges what belongs together.

## Normalization

| Rule | For |
| --- | --- |
| `noNestedLinks` | A link contains no other links; the inner one loses its wrapper, the outer keeps its target since it covers more |
| `emptyLinkGoes` | An anchor with no content is invisible, unclickable, and survives no export |
| `adjacentLinksJoin` | Two adjacent links to the same target become one |

`adjacentLinksJoin` looks backward, for the same reason as the list-joining rule: the newly
created link is the dirty node, and a forward-looking rule would only ever ask the one with
nothing behind it.

## Tests

```bash
sbt --server "scalajs-ember-link/Test/testOnly *"
```

`LinkUrlPolicySpec` is the suite that matters most here — every case in it is one a browser
executes and a naive `startsWith("javascript:")` lets through. `LinkSpec` covers setting,
removing, cross-block ranges and normalization. The semantic anchor rendering (including the
`target`/`rel` decision) lives in
[`ember-standard`](../ember-standard/README.md)'s `LinkProjectionSpec`.

## Related modules

- [`ember-rich-text`](../ember-rich-text/README.md) — the profile this module extends.
- [`ember-image`](../ember-image/README.md) — a stricter, separate URL policy for media.
- [`ember-toolbar`](../ember-toolbar/README.md) — an optional link dialog built on this module.
</content>

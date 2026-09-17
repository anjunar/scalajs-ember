# scalajs-ember-json

The versioned JSON wire format of the Ember editor: a wire ADT, node codecs, schema migration,
and full validation of untrusted payloads. Headless — no DOM, no UI runtime, no renderer.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-json` |
| Scala package | `ember.editor.json` |
| Production dependencies | `scalajs-ember-core` |

## Overview

`ember-json` reads and writes a `Document` to and from a JSON envelope, with strict limits on
untrusted input and an explicit, pure migration path between schema versions. It depends only on
the kernel and knows no feature node types — their codecs live in
[`ember-standard`](../ember-standard/README.md).

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-json" % "1.0.1"
```

## Quick start

```scala
val support = CoreJsonSupport.all ++ JsonSupport.of(paragraphCodec)

DocumentJson.encodeToString(document, support)     // Either[Vector[EncodeError], String]
DocumentJson.decodeString(source, schema, support)  // Either[Vector[DecodeError], DecodeResult]
```

`DecodeResult` carries a `Document` and diagnostics — deliberately not an `EditorSession`: SSR
renders a document without local selection, history or focus, so what gets persisted is the
document, not the state of an in-progress edit. Accordingly the payload has no `selection`,
`history`, or `revision` field.

## The envelope

```json
{
  "format": "ember-document",
  "formatVersion": 1,
  "schemaVersion": 1,
  "root": "root",
  "nodes": [
    {"id":"root","type":"ember.core.root/1","codecVersion":1,"children":["p0"]},
    {"id":"p0","type":"ember.core.text/1","codecVersion":1,"text":"Hello"}
  ]
}
```

**Nodes are an array, not an object keyed by ID.** A keyed object would be more compact — and
blind to exactly the error that matters most: `js.JSON.parse` silently merges duplicate keys, so
a duplicate node ID would vanish without a trace and the document would look valid. As an array,
it stays visible and becomes `DuplicateNodeId`.

**Three versions, three responsibilities.**

| Field | Increases when |
| --- | --- |
| `formatVersion` | the envelope shape itself changes. Never migrated — a newer format needs a newer reader. |
| `schemaVersion` | an application's document structure changes. Lifted via `SchemaMigrations`. |
| `codecVersion` | the fields of **one** node type change. Kept independent of `schemaVersion` on purpose. |

A codec may read older versions — it receives the found version in `DecodeContext` and branches.
A **newer** payload is rejected outright: it may carry fields whose meaning this build does not
know, and reading it as an older one would be silent data loss.

## What a codec describes

Only a node's own fields. `id`, `type`, `codecVersion` and `children` are written and read by the
envelope for every kind alike:

```scala
val text: NodeJsonCodec[TextNode] = new NodeJsonCodec[TextNode]:
  val nodeType = TextNode
  def encode(node, context) = Right(Vector("text" -> JsonValue.Str(node.text)))
  def decode(id, payload, context) = payload.string("text", context.path).map(TextNode(id, _))
```

Children are always referenced IDs, never a node type's own payload — so a codec cannot forget to
write them, because it is never asked to. Marks are open, and the kernel knows none, yet
`MarkJsonCodec` already exists — without it, a marked text run could not round-trip losslessly.

## Where `js.JSON` is used

At exactly one place, `JsonText`. Everything above operates on the closed `JsonValue` ADT — a
codec working on `js.Dynamic` only checks what it explicitly checks, and what it forgets only
surfaces once a foreign payload exploits it. A `JsonValue` in hand is a value whose depth and size
have already been checked against the limits.

**Serialization is hand-written**, not `js.JSON.stringify`, for two practical reasons: byte-stable
output for round-trip fixtures (nodes are written in document order), and embeddability — the
payload can land inside a `<script>` tag, so `<`, `>`, `&`, U+2028 and U+2029 are always escaped.
The result stays ordinary JSON.

## Limits

`DecodeLimits` caps source length, JSON depth, array/object size, node count, child count, text
length and document depth. Without them, the sender decides how much memory and CPU the receiver
spends before any domain check runs. Defaults are unreachable for a document a human wrote; they
are a value, not a constant, so an application can raise them deliberately.

## Unknown nodes

Default is `UnknownNodePolicy.Strict`: rejection with path and type ID. `Preserve` keeps the node
as an `UnsupportedNode` — with its original wire ID, payload, children, and a text fallback; the
payload is never interpreted (no code, no HTML, no auto-deserialized class), so a round-trip
through an editor without the relevant feature module leaves it byte-for-byte unchanged.
`UnsupportedNode` is a **container** deliberately — an unknown table can still hold known
paragraphs, and without a child list they would become unreachable and fail validation. A
**known** kind with no registered codec stays an error even under `Preserve` — that is an
application wiring mistake, not unknown data.

## Migration

```scala
val chain = SchemaMigrations.unsafe(
  SchemaMigration(1, 2, envelope => Right(renameType(envelope, "app.section/1", "app.block/1")))
)
DocumentJson.decodeString(source, schema, support, DecodeConfig(schemaVersion = 2, migrations = chain))
```

Steps are pure — `JsonValue.Obj => Either[String, JsonValue.Obj]` — because a migration needing a
session would really be an edit, running before any valid document exists. It works on the whole
envelope, since renames, splits and added children are none of them node-local. At most one step
per source version, forward only; a missing path is an error, never a silent pass-through.

## Tests

```bash
sbt --server "scalajs-ember-json/Test/testOnly *"
```

`DocumentJsonSpec` runs round-trips, both policies, every limit and invalid input against local
typed test nodes — deliberately not `ParagraphNode`, since this module sits beside the node
modules, not above them. `SchemaMigrationSpec` checks the chain and that it runs before decoding.

## Related modules

- [`ember-core`](../ember-core/README.md) — the document model this format serializes.
- [`ember-standard`](../ember-standard/README.md) — codecs for the built-in feature node types.
</content>

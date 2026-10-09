package ember.editor.markdown

/** How much of the syntax a profile actually resolves.
  *
  * Ordered by strength, and there are deliberately only two: what exists now, and what P18 will
  * add. A third value for "we think it is mostly right" would be exactly the claim §18.1 forbids.
  */
enum Conformance:

  /** Block structure per CommonMark; inline content left as source.
    *
    * Emphasis, links, code spans, entities and backslash escapes are '''not''' resolved -- a
    * paragraph holds a single [[MarkdownInline.Text]] with the raw source. Link reference
    * definitions are likewise left standing, because resolving them needs the inline parser.
    *
    * Useful for a cheap outline: a table of contents, a search index, a document map.
    */
  case BlocksOnly

  /** Blocks and inlines, to the extent the conformance suite records.
    *
    * The name states the ambition, not a proof. What this profile actually reproduces is a number,
    * measured against the versioned corpus and asserted exactly in `CommonMarkConformanceSpec`.
    * §18.1 asks for the number and not for the word.
    */
  case Inlines

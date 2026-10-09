package ember.editor.browser

import ember.editor.core.*

/** How much a deletion takes.
  *
  * The vocabulary of the Input Events specification, not of the editor. What a word boundary is
  * belongs to the `TextBoundaryService` (§11); what the browser '''asked for''' belongs here.
  */
enum Granularity:
  case Character, Word, Line, Selection

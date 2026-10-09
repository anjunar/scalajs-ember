package ember.editor.browser

import ember.editor.core.*

/** How content arrived from outside the document. */
enum TransferKind:
  case Paste, Drop, Cut

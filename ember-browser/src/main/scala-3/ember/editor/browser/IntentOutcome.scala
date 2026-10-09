package ember.editor.browser

import ember.editor.core.*

/** What a released intent came to. */
enum IntentOutcome:
  case Applied(label: String)
  case Rejected(label: String, error: String)

  /** The bookmark no longer points at anything (§11). The change did not happen. */
  case Expired(label: String)

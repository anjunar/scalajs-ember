package ember.editor.forms

import ember.editor.core.*

enum MediaPhase:
  case Uploading
  case Waiting(reason: MediaAvailability)
  case Inserted(node: NodeId)
  case Failed(reason: String)
  case Cancelled
  case Discarded(reason: String)

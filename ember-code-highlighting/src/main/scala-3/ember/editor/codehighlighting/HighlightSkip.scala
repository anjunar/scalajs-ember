package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

/** Why a block has no colours. */
enum HighlightSkip:
  case NoLanguage
  case UnknownLanguage(name: String)
  case TooLarge(chars: Int, limit: Int)

package ember.editor.markdown

import scala.collection.mutable
private[markdown] enum SlotKind:
  case Text
  case Code(literal: String)
  case SoftBreak
  case HardBreak
  case Html(literal: String)
  case Emphasis
  case Strong
  case Link(destination: String, title: Option[String])
  case Image(destination: String, title: Option[String])

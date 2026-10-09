package ember.editor.core

/** A point tracked from the current draft position through subsequent primitives. Like Transaction,
  * this handle is valid only inside its synchronous update.
  */
final class DraftBookmark private[core] (point: Point, checkAlive: () => Unit):
  private var value: MappedPoint = MappedPoint.Preserved(point)
  def current: MappedPoint       =
    checkAlive()
    value
  private[core] def advance(mapping: PositionMapping): Unit = value = value.flatMap(mapping.map)

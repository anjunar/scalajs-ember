package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** Where the lexer is at the start of a line. The top of the stack is the head of the list. */
final case class LexState(frames: List[Frame]):

  def top: Frame = frames.head

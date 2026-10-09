package ember.editor.image

import ember.editor.core.*

/** The commands an image contributes. */
object ImageCommands:

  /** Inserts an image at the caret. */
  val InsertImage: EditorCommand[ImageNode] = EditorCommand.of[ImageNode]("image.insert")

  /** Replaces the image at the caret -- new alt text, new size, new source. */
  val UpdateImage: EditorCommand[ImageNode => ImageNode] =
    EditorCommand.of[ImageNode => ImageNode]("image.update")

package ember.editor.core

/** Warum ein Bookmark nicht mehr aufloesbar ist. */
final case class ExpiredBookmark(reason: String, override val path: DiagnosticPath)
    extends EditorError:
  def message: String = reason

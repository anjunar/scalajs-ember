package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.image.*

/** File picking is injected by the application, keeping forms out of this module. */
final class ImageDialog(
    service: EditorDialogService,
    host: EditorDialogHost,
    pickFile: Option[(DialogTarget, String) => Either[EditorError, Unit]] = None
):
  def open(): Either[EditorError, Unit] =
    val image = service.selectedImage
    host.open(
      if image.nonEmpty then "Bild bearbeiten" else "Bild einfügen",
      Vector(
        "Bildadresse"                                 -> image.map(_.src.value).getOrElse(""),
        "Alternativtext (leer für dekorative Bilder)" -> image.map(_.alt).getOrElse("")
      )
    )(
      (target, values) => service.insertImage(target, values(0), values(1)),
      pickFile
        .filter(_ => image.isEmpty)
        .map(callback =>
          "Datei auswählen" -> ((target: DialogTarget, values: Vector[String]) =>
            callback(target, values(1))
          )
        )
    )

package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.link.*

final class LinkDialog(service: EditorDialogService, host: EditorDialogHost):
  def open(): Either[EditorError, Unit] =
    val link = service.session.selection
      .collect { case r: RangeSelection => r.focus }
      .flatMap(Links.linkAt(service.session.document, _))
    host.open(
      "Link bearbeiten",
      Vector(
        "Adresse" -> link.map(_.target.url.value).getOrElse(""),
        "Titel"   -> link.flatMap(_.target.title).getOrElse("")
      )
    )(
      (target, values) => service.setLink(target, values(0), values(1)),
      Option.when(link.nonEmpty)(
        "Link entfernen" -> ((target: DialogTarget, _: Vector[String]) =>
          service.removeLink(target)
        )
      )
    )

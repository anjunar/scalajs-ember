package ember.editor.image

import ember.editor.core.*

/** Where a picture comes from.
  *
  * §20 gives it two halves and both earn their place: the `src` is what a browser loads, the
  * [[MediaId]] is what the application knows about the file behind it. A document that carries only
  * the URL cannot be migrated when the storage moves; one that carries only the id cannot be
  * rendered by anything that does not know the application.
  *
  * '''What is not in here''': file data, an `ObjectURL`, an upload handle, a progress value. §20 is
  * explicit -- "Lokale Object-URL-Previews werden freigegeben und niemals serialisiert", and P16's
  * acceptance repeats it: "Keine Dateidaten oder Object-URL im Document." Those live in the effect
  * state of whoever is uploading, and they die with it.
  */
final case class MediaReference(src: MediaUrl, mediaId: Option[MediaId] = None)

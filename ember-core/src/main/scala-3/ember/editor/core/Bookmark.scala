package ember.editor.core

/** Eine gemerkte Position samt dem Stand, auf den sie sich bezieht.
  *
  * Der Anwendungsfall, der die Form bestimmt, ist der asynchrone Upload (§20): waehrend eine Datei
  * hochlaedt, bearbeitet der Benutzer weiter. Wenn das Ergebnis eintrifft, muss die Einfuegestelle
  * nachgefuehrt werden -- '''oder die Einfuegung muss unterbleiben'''. Ein Bookmark, das bei
  * geloeschtem Ziel stillschweigend auf eine Grenze zurueckfaellt, wuerde das Bild an einer voellig
  * anderen Stelle einsetzen. Deshalb ist das Ablaufen ein Ergebnis und kein Randfall.
  */
final case class Bookmark(point: Point, revision: Revision):

  /** Fuehrt das Bookmark nach.
    *
    * Abgelaufen ist es in zwei Faellen: die Abbildung passt nicht zu seinem Ausgangsstand, oder der
    * Inhalt, auf den es zeigte, existiert nicht mehr. Beides liefert einen Fehler statt einer
    * falschen Position (§11).
    */
  def resolve(mapping: RevisionMapping): Either[ExpiredBookmark, Point] =
    if mapping.from != revision then
      Left(
        ExpiredBookmark(
          s"Bookmark aus Revision ${revision.value}, Abbildung beginnt bei ${mapping.from.value}.",
          DiagnosticPath.node(point.owner.value)
        )
      )
    else
      mapping.mapping.map(point) match
        case MappedPoint.Preserved(mapped) => Right(mapped)
        case MappedPoint.Displaced(_)      =>
          Left(
            ExpiredBookmark(
              "Der Inhalt an der gemerkten Position wurde entfernt.",
              DiagnosticPath.node(point.owner.value)
            )
          )

  /** Wie [[resolve]], nimmt aber die Ersatzgrenze in Kauf.
    *
    * Fuer Aufrufer, denen eine ungefaehre Stelle genuegt -- etwa das Wiederherstellen des Fokus
    * nach einem Dialog (§22). Ausdruecklich '''nicht''' fuer Einfuegungen.
    */
  def resolveOrFallback(mapping: RevisionMapping): Either[ExpiredBookmark, Point] =
    resolve(mapping) match
      case Right(point)                        => Right(point)
      case Left(_) if mapping.from == revision => Right(mapping.mapping.map(point).point)
      case Left(expired)                       => Left(expired)

package ember.editor.core

/** Fortlaufende Nummer eines Dokumentstands.
  *
  * Hier eingefuehrt, weil [[Bookmark]] ohne sie nicht sagen kann, worauf es sich bezieht. P04
  * verwendet denselben Typ in `EditorState`; Revisionen steigen auch bei Undo, der
  * wiederhergestellte Inhalt bekommt also eine neue Nummer (§9).
  */
opaque type Revision = Long

object Revision:
  val initial: Revision = 0L

  def apply(value: Long): Revision = value

  given Ordering[Revision] = Ordering.Long

  extension (revision: Revision)
    def value: Long                        = revision
    def next: Revision                     = revision + 1
    def isBefore(other: Revision): Boolean = revision < other

/** Warum ein Bookmark nicht mehr aufloesbar ist. */
final case class ExpiredBookmark(reason: String, override val path: DiagnosticPath)
    extends EditorError:
  def message: String = reason

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

/** Eine Abbildung mit den Staenden, zwischen denen sie vermittelt.
  *
  * Ohne die beiden Revisionen liesse sich nicht erkennen, ob eine Abbildung zu einem Bookmark passt
  * -- und §11 verlangt genau dafuer einen expliziten Fehler statt einer stillen Fehleinfuegung.
  */
final case class RevisionMapping(from: Revision, to: Revision, mapping: PositionMapping):

  /** Verkettet zwei aufeinanderfolgende Abschnitte. Luecken werden abgewiesen. */
  def andThen(next: RevisionMapping): Either[ExpiredBookmark, RevisionMapping] =
    if to != next.from then
      Left(
        ExpiredBookmark(
          s"Luecke in der Abbildungskette: ${to.value} gefolgt von ${next.from.value}.",
          DiagnosticPath.Root
        )
      )
    else Right(RevisionMapping(from, next.to, mapping andThen next.mapping))

object RevisionMapping:

  def identity(at: Revision): RevisionMapping =
    RevisionMapping(at, at, PositionMapping.identity)

  /** Verkettet einen zusammenhaengenden Abschnitt. Eine Luecke ist ein Fehler, keine Warnung. */
  def composeAll(steps: Seq[RevisionMapping]): Either[ExpiredBookmark, RevisionMapping] =
    steps match
      case Seq()            => Left(ExpiredBookmark("Leere Abbildungskette.", DiagnosticPath.Root))
      case Seq(single)      => Right(single)
      case Seq(head, tail*) =>
        tail.foldLeft[Either[ExpiredBookmark, RevisionMapping]](Right(head)) {
          (accumulated, step) =>
            accumulated.flatMap(_ andThen step)
        }

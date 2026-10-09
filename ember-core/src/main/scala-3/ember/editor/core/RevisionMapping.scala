package ember.editor.core

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

package ember.editor.core

/** Richtung eines Bereichs in Dokumentordnung. */
enum SelectionDirection:
  case Forward, Backward, Collapsed

/** Eine Auswahl im Dokument.
  *
  * '''Offener Erweiterungsvertrag''' (§11): Tabellen brauchen spaeter einen rechteckigen
  * Zellbereich, der weder ein Textbereich noch eine Knotenmenge ist. Waere `Selection` geschlossen,
  * muesste X01 den Kern aufmachen -- und §8.1 haelt fest, dass eine Tabellenauswahl gerade kein
  * Sonderfall des Kerns werden soll.
  *
  * Jede Art braucht dafuer einen registrierten [[SelectionMapper]]. Ohne ihn koennte eine Auswahl
  * eine Dokumentaenderung nicht ueberleben.
  */
trait Selection

/** Ein Textbereich zwischen zwei Punkten.
  *
  * `anchor` ist der zuerst gesetzte Punkt, `focus` der bewegliche. Die beiden werden '''niemals'''
  * zugunsten sortierter Endpunkte vertauscht (§11): eine rueckwaerts gezogene Auswahl bleibt
  * rueckwaerts, sonst springt der Cursor beim naechsten Tastendruck ans falsche Ende. Welcher Punkt
  * im Dokument frueher steht, beantwortet [[direction]] anhand der tatsaechlichen Baumordnung --
  * nie anhand eines lexikographischen ID-Vergleichs.
  */
final case class RangeSelection(anchor: Point, focus: Point) extends Selection:

  /** Ein Caret ist eine kollabierte Range, kein eigener Typ. */
  def isCollapsed: Boolean = anchor == focus

  def direction(document: DocumentRead): SelectionDirection =
    math.signum(document.comparePoints(anchor, focus)) match
      case 0          => SelectionDirection.Collapsed
      case n if n < 0 => SelectionDirection.Forward
      case _          => SelectionDirection.Backward

  /** Die Endpunkte in Dokumentordnung, ohne `anchor`/`focus` zu vertauschen. */
  def ordered(document: DocumentRead): (Point, Point) =
    if document.comparePoints(anchor, focus) <= 0 then (anchor, focus) else (focus, anchor)

object RangeSelection:
  def caret(point: Point): RangeSelection = RangeSelection(point, point)

/** Eine Menge ganzer Knoten, etwa mehrere ausgewaehlte Bilder.
  *
  * Ausdruecklich nicht dasselbe wie mehrere unabhaengige Text-Carets und nicht dasselbe wie eine
  * Tabellenauswahl (§3.2). Normalisiert: ein Nachfahre eines bereits ausgewaehlten Vorfahren ist
  * redundant und wird entfernt.
  */
final case class NodeSelection(nodes: Set[NodeId]) extends Selection:

  def isEmpty: Boolean = nodes.isEmpty

  /** Entfernt Nachfahren bereits ausgewaehlter Vorfahren (§11). */
  def normalized(document: DocumentRead): NodeSelection =
    NodeSelection(nodes.filterNot(node => document.ancestorsOf(node).exists(nodes.contains)))

/** Bildet eine Auswahlart durch eine Dokumentaenderung hindurch ab und prueft sie.
  *
  * Spiegelt bewusst [[NodeType]]: `project` ist der Typzeuge, und alles Verhalten haengt am
  * Deskriptor statt an der Auswahlklasse.
  *
  * @tparam S
  *   die konkrete Auswahlart
  */
trait SelectionMapper[S <: Selection]:

  def project(selection: Selection): Option[S]

  /** Fuehrt die Auswahl nach. `None` heisst: es ist keine gueltige Auswahl uebrig geblieben. */
  def map(selection: S, mapping: PositionMapping, after: DocumentRead): Option[Selection]

  /** Prueft, ob die Auswahl in diesem Dokument ueberhaupt darstellbar ist. */
  def validate(selection: S, document: DocumentRead): Vector[Violation]

/** Eingebauter Mapper fuer [[RangeSelection]]. */
object RangeSelectionMapper extends SelectionMapper[RangeSelection]:

  def project(selection: Selection): Option[RangeSelection] = selection match
    case range: RangeSelection => Some(range)
    case _                     => None

  /** §11: "Anchor und Focus werden unabhaengig gemappt."
    *
    * Unabhaengig heisst wirklich unabhaengig -- es gibt keine gemeinsame Korrektur, die beide ans
    * selbe Ende zoege. Faellt einer der beiden auf eine im neuen Dokument nicht darstellbare
    * Position, gibt es keine Auswahl mehr; eine halb gueltige waere schlimmer als keine.
    */
  def map(
      selection: RangeSelection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    val mapped =
      RangeSelection(mapping.map(selection.anchor).point, mapping.map(selection.focus).point)
    Option.when(validate(mapped, after).isEmpty)(mapped)

  def validate(selection: RangeSelection, document: DocumentRead): Vector[Violation] =
    Vector(selection.anchor, selection.focus).flatMap(_.validateIn(document))

/** Eingebauter Mapper fuer [[NodeSelection]]. */
object NodeSelectionMapper extends SelectionMapper[NodeSelection]:

  def project(selection: Selection): Option[NodeSelection] = selection match
    case nodes: NodeSelection => Some(nodes)
    case _                    => None

  /** §11: geloeschte IDs entfernen, verschobene bewahren, Nachfahren normalisieren.
    *
    * Verschobene Knoten bleiben ausgewaehlt, weil sie noch existieren -- ihre Identitaet ueberlebt
    * einen Move (§8.3). Nur `removedNodes` der Abbildung entscheidet.
    */
  def map(
      selection: NodeSelection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    val surviving = NodeSelection(
      (selection.nodes -- mapping.removedNodes).filter(after.contains)
    ).normalized(after)
    Option.when(surviving.nodes.nonEmpty)(surviving)

  def validate(selection: NodeSelection, document: DocumentRead): Vector[Violation] =
    selection.nodes.toVector.sorted
      .filterNot(document.contains)
      .map(node =>
        Violation.NodeRejected(
          node,
          "Ausgewaehlter Knoten fehlt im Dokument.",
          DiagnosticPath.node(node.value)
        )
      )

/** Registry der bekannten Auswahlarten.
  *
  * Getrennt von [[Schema]], weil sie unabhaengig waechst: ein Modul kann Node-Arten beitragen, ohne
  * eine neue Auswahlart zu brauchen, und X01 braucht beides.
  */
final class SelectionSupport private (val mappers: Vector[SelectionMapper[?]]):

  def mapperFor(selection: Selection): Option[SelectionMapper[?]] =
    mappers.find(_.project(selection).isDefined)

  /** Fuehrt eine Auswahl beliebiger Art nach.
    *
    * Eine Auswahl ohne registrierten Mapper geht verloren statt unveraendert weitergereicht zu
    * werden. Unveraendert weiterreichen hiesse, sie zeige noch auf denselben Inhalt -- und genau
    * das kann niemand behaupten, der die Art nicht kennt.
    */
  def map(selection: Selection, mapping: PositionMapping, after: DocumentRead): Option[Selection] =
    mapperFor(selection).flatMap(mapWith(_, selection, mapping, after))

  def validate(selection: Selection, document: DocumentRead): Vector[Violation] =
    mapperFor(selection) match
      case Some(mapper) => validateWith(mapper, selection, document)
      case None         =>
        Vector(
          Violation.NodeRejected(
            document.rootId,
            s"Keine registrierte Auswahlart fuer ${selection.getClass.getSimpleName}.",
            DiagnosticPath.Root
          )
        )

  def extendedWith(additional: SelectionMapper[?]*): SelectionSupport =
    new SelectionSupport(mappers ++ additional)

  private def mapWith[S <: Selection](
      mapper: SelectionMapper[S],
      selection: Selection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    mapper.project(selection).flatMap(mapper.map(_, mapping, after))

  private def validateWith[S <: Selection](
      mapper: SelectionMapper[S],
      selection: Selection,
      document: DocumentRead
  ): Vector[Violation] =
    mapper.project(selection).fold(Vector.empty)(mapper.validate(_, document))

object SelectionSupport:

  def of(mappers: SelectionMapper[?]*): SelectionSupport = new SelectionSupport(mappers.toVector)

  /** Range und Node sind eingebaut (§11). */
  val core: SelectionSupport = of(RangeSelectionMapper, NodeSelectionMapper)

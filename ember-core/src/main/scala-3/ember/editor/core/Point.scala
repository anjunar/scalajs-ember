package ember.editor.core

/** An welcher Seite ein Punkt klebt, wenn genau an seiner Position eingefuegt wird.
  *
  * Ohne diese Angabe waere eine Einfuegung an der Position eines Carets mehrdeutig: der Caret
  * muesste entweder immer vor oder immer hinter dem neuen Inhalt landen, und eines von beiden waere
  * in der Haelfte aller Faelle falsch.
  *
  *   - [[Before]]: der Punkt haelt sich an das, was '''vor''' ihm steht. Neuer Inhalt an seiner
  *     Position erscheint dahinter, der Punkt bleibt liegen.
  *   - [[After]]: der Punkt haelt sich an das, was '''nach''' ihm kommt. Neuer Inhalt erscheint
  *     davor, der Punkt wandert hinter ihn.
  */
enum Affinity:
  case Before, After

/** Eine logische Position im Dokument.
  *
  * Unabhaengig vom DOM (§11). Eine DOM-Range gibt es nur im Browsermodul; hier stehen Dokument-IDs
  * und Offsets, die eine Serialisierung und eine Prozessgrenze ueberleben.
  */
enum Point:

  /** Eine Position innerhalb eines Textlaufs, gemessen in UTF-16-Einheiten.
    *
    * UTF-16 ist das ausdrueckliche Offsetmass -- dasselbe wie DOM-`Text`, sodass der spaetere
    * SelectionPort ohne Umrechnung auskommt. Es ist ausdruecklich '''kein''' Mass fuer
    * Benutzerzeichen: Graphem- und Wortgrenzen liefert der [[TextBoundaryService]], und ein
    * gueltiger UTF-16-Offset allein beweist noch keine korrekte Unicode-Bearbeitung (§11).
    */
  case Text(node: NodeId, utf16Offset: Int, affinity: Affinity)

  /** Eine Position zwischen zwei Kindern, gemessen in Kindpositionen.
    *
    * `childOffset` = 0 steht vor dem ersten Kind, `= children.length` hinter dem letzten.
    */
  case Children(parent: NodeId, childOffset: Int, affinity: Affinity)

object Point:

  def textBefore(node: NodeId, offset: Int): Point = Point.Text(node, offset, Affinity.Before)
  def textAfter(node: NodeId, offset: Int): Point  = Point.Text(node, offset, Affinity.After)

  def childrenBefore(parent: NodeId, offset: Int): Point =
    Point.Children(parent, offset, Affinity.Before)

  def childrenAfter(parent: NodeId, offset: Int): Point =
    Point.Children(parent, offset, Affinity.After)

  extension (point: Point)

    /** Der Knoten, auf den sich der Punkt bezieht: der Textlauf bzw. der Container. */
    def owner: NodeId = point match
      case Point.Text(node, _, _)       => node
      case Point.Children(parent, _, _) => parent

    def affinity: Affinity = point match
      case Point.Text(_, _, affinity)     => affinity
      case Point.Children(_, _, affinity) => affinity

    def offset: Int = point match
      case Point.Text(_, offset, _)     => offset
      case Point.Children(_, offset, _) => offset

    def withAffinity(affinity: Affinity): Point = point match
      case Point.Text(node, offset, _)       => Point.Text(node, offset, affinity)
      case Point.Children(parent, offset, _) => Point.Children(parent, offset, affinity)

    /** Prueft, ob der Punkt im Dokument ueberhaupt existiert und im Wertebereich liegt. */
    def validateIn(document: DocumentRead): Option[Violation] = point match
      case Point.Text(node, offset, _) =>
        document.node(node) match
          case Some(text: TextNode) =>
            if offset < 0 || offset > text.text.length then
              Some(
                Violation.NodeRejected(
                  node,
                  s"Textoffset $offset liegt ausserhalb von 0..${text.text.length}.",
                  document.pathTo(node)
                )
              )
            else if splitsSurrogatePair(text.text, offset) then
              Some(
                Violation.NodeRejected(
                  node,
                  s"Textoffset $offset teilt ein Surrogatpaar.",
                  document.pathTo(node)
                )
              )
            else None
          case Some(_) =>
            Some(
              Violation.NodeRejected(
                node,
                "Textpunkt auf einem Knoten ohne Text.",
                document.pathTo(node)
              )
            )
          case None =>
            Some(
              Violation.NodeRejected(
                node,
                "Textpunkt auf einem unbekannten Knoten.",
                DiagnosticPath.node(node.value)
              )
            )

      case Point.Children(parent, offset, _) =>
        document.node(parent) match
          case Some(element: ElementNode) =>
            if offset < 0 || offset > element.children.length then
              Some(
                Violation.NodeRejected(
                  parent,
                  s"Kindoffset $offset liegt ausserhalb von 0..${element.children.length}.",
                  document.pathTo(parent)
                )
              )
            else None
          case Some(_) =>
            Some(
              Violation.NodeRejected(
                parent,
                "Kindpunkt auf einem Knoten ohne Kinder.",
                document.pathTo(parent)
              )
            )
          case None =>
            Some(
              Violation.NodeRejected(
                parent,
                "Kindpunkt auf einem unbekannten Knoten.",
                DiagnosticPath.node(parent.value)
              )
            )

  /** Ob ein Offset mitten in ein Surrogatpaar faellt.
    *
    * Das ist keine Unicode-Segmentierung, sondern eine UTF-16-Gueltigkeitsfrage: ein Schnitt an
    * dieser Stelle erzeugt zwei Strings mit je einem halben Codepoint. Graphemcluster --
    * kombinierende Zeichen, ZWJ-Sequenzen, Flaggen -- pruefen wir hier ausdruecklich nicht; das ist
    * Sache des [[TextBoundaryService]] und der Commands, die ihn benutzen.
    */
  def splitsSurrogatePair(text: String, offset: Int): Boolean =
    offset > 0 && offset < text.length &&
      Character.isHighSurrogate(text.charAt(offset - 1)) &&
      Character.isLowSurrogate(text.charAt(offset))

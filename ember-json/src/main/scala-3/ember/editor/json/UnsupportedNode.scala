package ember.editor.json

import ember.editor.core.*

/** Ein Knoten, dessen Art das Schema nicht kennt, unter [[UnknownNodePolicy.Preserve]] erhalten.
  *
  * §19.2: "Ein expliziter Preservation-Modus kann den begrenzten JSON-Wert als `UnsupportedNode`
  * mit sicherem Textfallback erhalten; er fuehrt keinen Code oder HTML aus. Roundtrip erhaelt dann
  * den Payload, Editing der unbekannten Struktur bleibt gesperrt."
  *
  * ==Warum ein Container==
  *
  * Weil seine Kinder sonst verschwaenden. Ein unbekannter Knoten kann durchaus bekannte Knoten
  * enthalten -- eine Tabelle mit ganz gewoehnlichen Absaetzen darin. Wuerde dieser Typ die
  * Kindliste nicht tragen, waeren die Absaetze nach dem Dekodieren unerreichbar, und der
  * `DocumentValidator` lehnte das Dokument mit `UnreachableNode` ab. Der Preservation-Modus haette
  * dann genau das zerstoert, wozu es ihn gibt.
  *
  * ==Was hier nicht passiert==
  *
  * [[payload]] wird nicht interpretiert. Er ist ein [[JsonValue.Obj]] -- ein bereits konvertierter,
  * gegen die Limits gehaltener Wert, kein `js.Any`, keine deserialisierte Klasse, kein HTML.
  * [[fallbackText]] ist reiner Text und wird von einem Renderer als Text dargestellt, nie als
  * Markup.
  *
  * @param typeId
  *   die '''urspruengliche''' Wire-ID. Sie wird beim Kodieren wieder geschrieben -- ein Roundtrip
  *   durch einen Editor ohne das betreffende Feature-Modul aendert am Payload nichts.
  */
final case class UnsupportedNode(
    id: NodeId,
    typeId: NodeTypeId,
    codecVersion: Int,
    payload: JsonValue.Obj,
    children: Vector[NodeId],
    fallbackText: String
) extends ElementNode

object UnsupportedNode extends ElementNodeType[UnsupportedNode]:

  /** Der Deskriptor traegt eine eigene Wire-ID, geschrieben wird aber die des Knotens.
    *
    * Diese hier steht nie in einem Dokument. Sie existiert, weil ein [[NodeType]] einen Namen
    * braucht und weil eine Diagnose ihn nennen koennen soll.
    */
  val typeId: NodeTypeId = NodeTypeId("ember.json.unsupported/1")

  def project(node: EditorNode): Option[UnsupportedNode] = node match
    case unsupported: UnsupportedNode => Some(unsupported)
    case _                            => None

  def rekey(node: UnsupportedNode, id: NodeId): UnsupportedNode = node.copy(id = id)

  def withChildren(node: UnsupportedNode, children: Vector[NodeId]): UnsupportedNode =
    node.copy(children = children)

  /** Der Textfallback aus dem Payload.
    *
    * Ein `text`-Feld, wenn es eines gibt, sonst leer. Bewusst keine Rekursion durch den ganzen
    * Payload: was ein fremdes Feature als Text meint, weiss dieses Modul nicht, und geratener Text
    * ist schlechter als keiner.
    */
  private[json] def fallbackOf(payload: JsonValue.Obj): String =
    payload.get("text") match
      case Some(JsonValue.Str(text)) => text
      case _                         => ""

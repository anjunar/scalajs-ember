package ember.editor.json

import ember.editor.core.*

/** Wie eine Knotenart als JSON aussieht.
  *
  * ==Was ein Codec beschreibt und was nicht==
  *
  * Nur die '''eigenen Felder''' des Knotens. `id`, `type`, `codecVersion` und `children` schreibt
  * und liest das Envelope ([[DocumentJson]]) fuer alle Arten gleich. Das ist keine Bequemlichkeit:
  * Kinder sind laut §8.2 ausschliesslich referenzierte IDs, und die Kindliste eines Containers ist
  * damit eine strukturelle Eigenschaft des Dokuments, keine Nutzlast einer Knotenart. Ein Codec,
  * der sie selbst schriebe, koennte sie auch selbst vergessen.
  *
  * Deshalb bekommt [[decode]] die bereits geprueft eingelesene ID und liefert einen Knoten mit
  * leerer Kindliste; das Envelope setzt sie ueber [[ElementNodeType.withChildren]] ein.
  *
  * ==Zwei Versionen, die nichts miteinander zu tun haben==
  *
  * §19.2: "Node-Codec-Version und Dokumentformat-Version sind getrennt." [[codecVersion]] gehoert
  * dieser Knotenart: sie steigt, wenn sich ihre Felder aendern, und sagt nichts ueber das Envelope.
  * Umgekehrt bleibt ein Dokument der Formatversion 1 lesbar, wenn eine einzelne Knotenart ihre
  * Codec-Version erhoeht.
  *
  * @tparam N
  *   die Knotenart
  */
trait NodeJsonCodec[N <: EditorNode]:

  def nodeType: NodeType[N]

  /** Version der Feldbelegung dieser Knotenart. Beginnt bei 1. */
  def codecVersion: Int = 1

  /** Die eigenen Felder des Knotens, in fester Reihenfolge.
    *
    * Fest, weil die Ausgabe bei gleichem Dokument byteweise gleich sein muss -- sonst sind
    * Roundtrip-Fixtures wertlos (dieselbe Ueberlegung wie bei [[MarkSet]] im Kern).
    */
  def encode(node: N, context: EncodeContext): Either[EncodeError, Vector[(String, JsonValue)]]

  /** Baut den Knoten aus seinen Feldern. Ohne Kinder -- die setzt das Envelope. */
  def decode(
      id: NodeId,
      payload: JsonValue.Obj,
      context: DecodeContext
  ): Either[DecodeError, N]

/** Was ein Codec beim Kodieren ueber seine Umgebung wissen darf. */
final case class EncodeContext(nodeId: NodeId, path: DiagnosticPath, marks: MarkSupport)

/** Was ein Codec beim Dekodieren ueber seine Umgebung wissen darf.
  *
  * `codecVersion` ist die im Payload genannte, nicht die des Codecs -- ein Codec, der aeltere
  * Staende lesen will, verzweigt hier. Das Envelope hat vorher geprueft, dass sie nicht
  * '''groesser''' als die eigene ist: ein neuerer Payload kann Felder enthalten, deren Bedeutung
  * dieser Stand nicht kennt, und ihn stillschweigend als alten zu lesen waere Datenverlust.
  */
final case class DecodeContext(
    codecVersion: Int,
    path: DiagnosticPath,
    limits: DecodeLimits,
    marks: MarkSupport
)

/** Wie eine Markierungsart als JSON aussieht.
  *
  * Marks sind offen (§8.2) und der Kern kennt keine einzige -- Strong, Emphasis und die uebrigen
  * kommen aus `ember-rich-text` (P12). Damit ein Textlauf trotzdem heute schon verlustfrei
  * persistierbar ist, gibt es die SPI jetzt und nicht erst mit ihren ersten Nutzern.
  */
trait MarkJsonCodec[M <: TextMark]:

  def markId: MarkId

  /** Der Typzeuge, wie [[NodeType.project]]. */
  def project(mark: TextMark): Option[M]

  /** Die eigenen Felder der Markierung. Leer, wenn sie keine Nutzdaten traegt. */
  def encode(mark: M): Vector[(String, JsonValue)]

  def decode(payload: JsonValue.Obj, at: DiagnosticPath): Either[DecodeError, M]

/** Registry der Markierungscodecs. */
final class MarkSupport private (val codecs: Vector[MarkJsonCodec[?]]):

  private val byId: Map[MarkId, MarkJsonCodec[?]] =
    codecs.map(codec => codec.markId -> codec).toMap

  def ++(other: MarkSupport): MarkSupport = new MarkSupport(codecs ++ other.codecs)

  private[json] def encode(
      mark: TextMark,
      nodeId: NodeId,
      at: DiagnosticPath
  ): Either[EncodeError, JsonValue] =
    codecs.find(_.project(mark).isDefined) match
      case Some(codec) => Right(write(codec, mark))
      case None        => Left(EncodeError.NoMarkCodec(nodeId, mark.markId, at))

  private def write[M <: TextMark](codec: MarkJsonCodec[M], mark: TextMark): JsonValue =
    val fields = codec.project(mark).map(codec.encode).getOrElse(Vector.empty)
    JsonValue.Obj(("mark" -> JsonValue.Str(codec.markId.value)) +: fields)

  private[json] def decode(
      value: JsonValue,
      at: DiagnosticPath
  ): Either[DecodeError, TextMark] =
    for
      payload <- value.asObject(at)
      name    <- payload.string("mark", at)
      markId  <- MarkId
        .parse(name)
        .toRight(DecodeError.InvalidValue(s"Keine gueltige MarkId: `$name`", at))
      codec <- byId.get(markId).toRight(DecodeError.UnknownMark(name, at))
      mark  <- codec.decode(payload, at)
    yield mark

object MarkSupport:

  val empty: MarkSupport = new MarkSupport(Vector.empty)

  def of(codecs: MarkJsonCodec[?]*): MarkSupport = new MarkSupport(codecs.toVector)

/** Registry der Knotencodecs.
  *
  * Heterogen und ohne oeffentliches `Any`, wie [[Schema]] im Kern und `HtmlSupport` in
  * `ember-html`: der Typzeuge ist der Deskriptor des jeweiligen Eintrags.
  */
final class JsonSupport private (
    val codecs: Vector[NodeJsonCodec[?]],
    val marks: MarkSupport
):

  def ++(other: JsonSupport): JsonSupport =
    new JsonSupport(codecs ++ other.codecs, marks ++ other.marks)

  /** Ergaenzt Markierungscodecs, ohne die Knotencodecs anzufassen. */
  def withMarks(additional: MarkSupport): JsonSupport =
    new JsonSupport(codecs, marks ++ additional)

  def codecFor(node: EditorNode): Option[NodeJsonCodec[?]] =
    codecs.find(_.nodeType.project(node).isDefined)

  def codecFor(typeId: NodeTypeId): Option[NodeJsonCodec[?]] =
    codecs.find(_.nodeType.typeId == typeId)

  private[json] def encode(
      node: EditorNode,
      at: DiagnosticPath
  ): Either[EncodeError, (NodeTypeId, Int, Vector[(String, JsonValue)])] =
    codecFor(node) match
      case Some(codec) => write(codec, node, at)
      case None        =>
        Left(EncodeError.NoCodec(node.id, node.getClass.getSimpleName, at))

  private def write[N <: EditorNode](
      codec: NodeJsonCodec[N],
      node: EditorNode,
      at: DiagnosticPath
  ): Either[EncodeError, (NodeTypeId, Int, Vector[(String, JsonValue)])] =
    codec.nodeType.project(node) match
      case Some(typed) =>
        codec
          .encode(typed, EncodeContext(node.id, at, marks))
          .map((codec.nodeType.typeId, codec.codecVersion, _))
      case None =>
        // Kann nur eintreten, wenn `codecFor` und `project` sich uneins sind -- also nie.
        Left(EncodeError.NoCodec(node.id, node.getClass.getSimpleName, at))

  private[json] def decode(
      codec: NodeJsonCodec[?],
      id: NodeId,
      payload: JsonValue.Obj,
      codecVersion: Int,
      limits: DecodeLimits,
      at: DiagnosticPath
  ): Either[DecodeError, EditorNode] =
    codec.decode(id, payload, DecodeContext(codecVersion, at, limits, marks))

object JsonSupport:

  val empty: JsonSupport = new JsonSupport(Vector.empty, MarkSupport.empty)

  def of(codecs: NodeJsonCodec[?]*): JsonSupport =
    new JsonSupport(codecs.toVector, MarkSupport.empty)

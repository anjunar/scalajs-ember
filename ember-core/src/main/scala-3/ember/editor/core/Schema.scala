package ember.editor.core

/** Fehler beim Aufbau eines [[Schema]]. Geschlossen wie alle Validierungsergebnisse (§8.1). */
sealed trait SchemaError extends EditorError

object SchemaError:

  /** Zwei Deskriptoren beanspruchen denselben Wire-Namen. */
  final case class DuplicateTypeId(typeId: NodeTypeId) extends SchemaError:
    def message: String =
      s"Die NodeTypeId `${typeId.value}` ist mehrfach registriert."
    override def path: DiagnosticPath = DiagnosticPath.field(typeId.value)

/** Registry der bekannten Node-Arten.
  *
  * Heterogen, aber ohne oeffentliche `Map[String, Any]`: nach aussen sichtbar sind nur
  * [[NodeType]]-Werte mit existenziellem Parameter, und jeder typisierte Zugriff laeuft ueber
  * [[NodeType.project]] als Typzeugen (§8.1).
  *
  * Das Schema einer Session ist fest. Ein Schemawechsel verlangt Document-Migration und eine neue
  * bzw. kontrolliert rekonfigurierte Session (§13) -- deshalb ist ein Schema ein Wert und hat keine
  * Registrierungsmethode.
  */
final class Schema private (
    val types: Vector[NodeType[?]],
    private val aliases: Map[NodeTypeId, NodeTypeId]
):

  private val byTypeId: Map[NodeTypeId, NodeType[?]] =
    types.map(descriptor => descriptor.typeId -> descriptor).toMap

  /** Der Deskriptor zu einem Wire-Namen.
    *
    * Beruecksichtigt Ersetzungen (§8.3): der Name einer ersetzten Art zeigt auf ihren Ersatz, damit
    * bereits gespeicherte Dokumente weiter dekodierbar bleiben.
    */
  def byId(typeId: NodeTypeId): Option[NodeType[?]] =
    byTypeId.get(typeId).orElse(aliases.get(typeId).flatMap(byTypeId.get))

  /** Wire-Namen, die auf einen anderen Deskriptor umgeleitet werden. */
  def aliasedTypeIds: Set[NodeTypeId] = aliases.keySet

  private[core] def withAliases(additional: Map[NodeTypeId, NodeTypeId]): Schema =
    if additional.isEmpty then this else new Schema(types, aliases ++ additional)

  def knows(typeId: NodeTypeId): Boolean = byId(typeId).isDefined

  /** Der zustaendige Deskriptor eines Knotens, ueber dessen Typzeugen ermittelt.
    *
    * Lineare Suche in Registrierungsreihenfolge; der erste passende Deskriptor gewinnt. Bei einer
    * ueberschaubaren Zahl von Node-Arten ist das ein `isInstanceOf` je Kandidat. Ein Klassen-Cache
    * waere leicht nachzuruesten, wird aber erst bei gemessener Last eingefuehrt (§8.3) -- nicht auf
    * Verdacht.
    *
    * Wenn zwei Deskriptoren denselben Knoten beanspruchen koennen, etwa ueber eine
    * Vererbungsbeziehung, entscheidet die Registrierungsreihenfolge. Das laesst sich beim Aufbau
    * nicht pruefen; das gezielte Ersetzen eingebauter Arten bekommt in P05 einen eigenen, explizit
    * validierten Vertrag.
    */
  def descriptorFor(node: EditorNode): Option[NodeType[?]] =
    types.find(_.project(node).isDefined)

  /** Ergaenzt weitere Arten. Schlaegt fehl, wenn ein Wire-Name doppelt vorkaeme. */
  def extendedWith(additional: NodeType[?]*): Either[Vector[SchemaError], Schema] =
    Schema.of((types ++ additional)*)

  override def toString: String =
    types.map(_.typeId.value).sorted.mkString("Schema(", ", ", ")")

object Schema:

  /** Baut ein Schema aus genau diesen Deskriptoren. */
  def of(descriptors: NodeType[?]*): Either[Vector[SchemaError], Schema] =
    val duplicates = descriptors
      .groupBy(_.typeId)
      .collect {
        case (typeId, entries) if entries.sizeIs > 1 => SchemaError.DuplicateTypeId(typeId)
      }
      .toVector
      .sortBy(_.typeId.value)

    if duplicates.nonEmpty then Left(duplicates)
    else Right(new Schema(descriptors.toVector, Map.empty))

  /** Wie [[of]], wirft aber bei Fehlern. Fuer im Code feststehende Schemata und Tests. */
  def unsafe(descriptors: NodeType[?]*): Schema =
    of(descriptors*) match
      case Right(schema) => schema
      case Left(errors)  =>
        throw EditorContractViolation(
          errors.map(_.render).mkString("Ungueltiges Schema:\n", "\n", "")
        )

  /** Die Arten, die der Kern selbst mitbringt: Wurzel und Textlauf.
    *
    * Paragraph, Heading, Liste, Link und Bild gehoeren ausdruecklich nicht dazu -- sie kommen aus
    * ihren Feature-Modulen (§6).
    */
  val core: Schema = unsafe(RootNode, TextNode)

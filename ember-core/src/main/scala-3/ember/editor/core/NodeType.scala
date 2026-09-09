package ember.editor.core

/** Versionierter Name einer Node-Art, wie er in Wire-Formaten steht.
  *
  * Wire-IDs sind versionierte Namen (§8.1). Commands verwenden davon unabhaengig Objektidentitaet
  * -- ein `NodeTypeId` ist also kein Dispatch-Schluessel, sondern ein Persistenz- und
  * Diagnosebezeichner.
  */
opaque type NodeTypeId = String

object NodeTypeId:

  def apply(value: String): NodeTypeId =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige NodeTypeId: `$value`")
    )

  def parse(value: String): Option[NodeTypeId] =
    if value.nonEmpty && !value.exists(c => c.isWhitespace || c.isControl) then Some(value)
    else None

  given Ordering[NodeTypeId] = Ordering.String

  extension (id: NodeTypeId) def value: String = id

/** Deskriptor einer Node-Art: bindet Registry, Transform, Codec und View an denselben Scala-Typ
  * (§8.1).
  *
  * ==Warum ein Deskriptor und nicht Methoden auf dem Node==
  *
  * Lexical mischt Modell- und DOM-Verhalten in die Node-Klassen. Hier nicht: ein serverseitig
  * verwendeter `ImageNode` braucht weder `createDOM` noch einen aktiven Editor (§3.3). Der Node ist
  * reine Datenstruktur, alles Verhalten haengt am Deskriptor -- und zwar getrennt je
  * Zustaendigkeit, sodass ein JSON-Codec oder eine NodeView den Kern nicht anfassen muss.
  *
  * @tparam N
  *   der konkrete Node-Typ, den dieser Deskriptor beschreibt
  */
trait NodeType[N <: EditorNode]:

  /** Name fuer Persistenz und Diagnose. Innerhalb eines [[Schema]] eindeutig. */
  def typeId: NodeTypeId

  /** Der Typzeuge. `Some`, wenn dieser Deskriptor fuer den Node zustaendig ist.
    *
    * Jeder typisierte Aufruf laeuft ueber diese Pruefung -- deshalb braucht der Kern weder
    * Reflection noch eine oeffentliche `Map[String, Any]` (§8.1).
    */
  def project(node: EditorNode): Option[N]

  /** Erzeugt denselben Node mit anderer ID, '''unter Erhalt aller uebrigen Felder'''.
    *
    * Unveraenderlicher Rekonstruktionsvertrag. Der Kern kann die `copy`-Signatur einer fremden Case
    * Class nicht erraten und darf es auch nicht versuchen; ohne diese Methode waere generisches
    * Einfuegen, Verschieben und Paste fuer Fremdtypen unmoeglich (§8.1).
    *
    * Ein Deskriptor, der hier Zusatzfelder verliert, ist fehlerhaft -- `SchemaSpec` prueft das.
    */
  def rekey(node: N, id: NodeId): N

  /** Fachliche Pruefung dieses Nodes im Kontext des Dokuments.
    *
    * Laeuft nach den strukturellen Invarianten, also erst wenn Kinder aufloesbar und Zyklen
    * ausgeschlossen sind. Default: nichts zu beanstanden.
    */
  def validate(node: N, document: DocumentRead): Vector[Violation] = Vector.empty

/** Deskriptor einer Node-Art mit Kindern.
  *
  * Ohne diesen Vertrag kann der Kern einen Container nicht generisch umbauen. Ein [[ElementNode]],
  * dessen Deskriptor nur ein [[NodeType]] ist, gilt deshalb als Schemafehler und wird bei der
  * Validierung als [[Violation.MissingElementDescriptor]] gemeldet (§8.1, letzter Absatz).
  */
trait ElementNodeType[N <: ElementNode] extends NodeType[N]:

  /** Erzeugt denselben Node mit anderer Kindliste, unter Erhalt aller uebrigen Felder. */
  def withChildren(node: N, children: Vector[NodeId]): N

package ember.editor.core

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

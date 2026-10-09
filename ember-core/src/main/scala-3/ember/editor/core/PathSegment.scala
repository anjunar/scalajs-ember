package ember.editor.core

/** Ein Schritt in einem [[DiagnosticPath]].
  *
  * Geschlossen: die Struktur eines Pfades ist eine Kerneigenschaft und keine Erweiterung.
  * Feature-Module bilden ihre Begriffe auf diese drei Formen ab.
  */
enum PathSegment:

  /** Benanntes Feld eines Nodes oder eines Wire-Objekts, etwa `children` oder `alt`. */
  case Field(name: String)

  /** Position in einer Kindliste oder einem Array, nullbasiert. */
  case Index(value: Int)

  /** Ein Node, adressiert ueber seine Dokument-ID.
    *
    * Bewusst `String` und nicht `NodeId`: dieser Typ entsteht erst in P02, und die Fehlerkonvention
    * darf nicht auf das Dokumentmodell zurueckhaengen.
    */
  case Node(id: String)

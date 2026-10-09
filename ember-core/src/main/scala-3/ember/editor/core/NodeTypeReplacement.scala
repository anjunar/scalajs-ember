package ember.editor.core

/** Ersetzt eine eingebaute Knotenart durch eine spezialisierte.
  *
  * §8.3: eingebaute Typen spezialisieren, ohne jede Aufrufstelle anzupassen. Die Ersetzung betrifft
  * '''kuenftige Erzeugung''' -- bestehende Knoten aendert nur eine ausdrueckliche Migration.
  *
  * Der [[replacement]]-Deskriptor tritt an die Stelle des ersetzten. Beide Wire-Namen zeigen danach
  * auf ihn, damit bereits gespeicherte Dokumente weiter dekodierbar bleiben; ein Rendererwechsel
  * allein migriert kein Dokument.
  *
  * Zwei Ersetzungen derselben Art sind ein Aufloesungsfehler, kein Rennen um den letzten Eintrag
  * (§13).
  */
final case class NodeTypeReplacement(replaced: NodeTypeId, replacement: NodeType[?])

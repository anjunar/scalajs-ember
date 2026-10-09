package ember.editor.html

import ember.editor.core.*

/** Wofuer gerendert wird.
  *
  * §15.1: getrennte Content- und Editor-Renderprofile. Dieselbe Semantik, aber die Editieransicht
  * ergaenzt Metadaten und Interaktion -- Knoten-IDs als Attribute, spaeter `contenteditable`,
  * ARIA-Rollen. Die ausgelieferte HTML-Fassung traegt davon nichts: §19.1 verlangt, dass
  * browserseitige Wrapper und Editor-Attribute beim Austausch verschwinden.
  */
enum RenderProfile:

  /** Reines Dokument-HTML. Was ein Leser bekommt, und was exportiert wird. */
  case Content

  /** Dasselbe plus das, was die Editierflaeche braucht. */
  case Editor

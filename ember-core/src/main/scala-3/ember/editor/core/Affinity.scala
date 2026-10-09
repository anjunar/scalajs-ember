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

package ember.editor.core

/** Woher eine Aenderung kommt.
  *
  * Typisierte Metadaten statt geratener Absicht (§14). Eine History, die aus Textdifferenzen
  * erschliessen muss, ob der Benutzer getippt oder ein Import geladen hat, liegt regelmaessig
  * daneben; der Ausloeser weiss es und sagt es.
  */
enum Origin:

  /** Eine unmittelbare Benutzeraktion. */
  case User

  /** Laden von Inhalt. Setzt die History spaeter zurueck, statt sie zu verlaengern (§14). */
  case Import

  /** Undo oder Redo. Wird nicht erneut aufgezeichnet. */
  case History

  /** Eine Aenderung eines anderen Teilnehmers. Braucht spaeter einen eigenen Undo-Vertrag (X03). */
  case Remote

  /** Eine Aenderung des Programms selbst: Normalisierung, Migration, abgeschlossener Upload. */
  case System

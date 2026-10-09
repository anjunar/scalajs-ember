package ember.editor.core

/** Eine Auswahl im Dokument.
  *
  * '''Offener Erweiterungsvertrag''' (§11): Tabellen brauchen spaeter einen rechteckigen
  * Zellbereich, der weder ein Textbereich noch eine Knotenmenge ist. Waere `Selection` geschlossen,
  * muesste X01 den Kern aufmachen -- und §8.1 haelt fest, dass eine Tabellenauswahl gerade kein
  * Sonderfall des Kerns werden soll.
  *
  * Jede Art braucht dafuer einen registrierten [[SelectionMapper]]. Ohne ihn koennte eine Auswahl
  * eine Dokumentaenderung nicht ueberleben.
  */
trait Selection

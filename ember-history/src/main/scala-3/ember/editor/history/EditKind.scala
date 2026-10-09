package ember.editor.history

import ember.editor.core.*

/** Was zuletzt passiert ist -- so genau, wie es sich aus dem Commit ablesen laesst.
  *
  * §14 verlangt, dass Gruppengrenzen "explizit testbar" sind. Sie aus Textdifferenzen zu erraten
  * waere das Gegenteil; hier steht deshalb, welche Form eine Aenderung hatte, abgeleitet aus dem
  * [[ChangeSet]] und der Auswahl davor -- nicht aus einem Vergleich zweier Texte.
  */
enum EditKind:

  /** Text eingefuegt: `[from, to)` ist neu. */
  case Insert(node: NodeId, from: Int, to: Int)

  /** Rueckwaerts geloescht: `[at, at + deleted)` ist weg, der Caret steht jetzt auf `at`. */
  case DeleteBackward(node: NodeId, at: Int, deleted: Int)

  /** Vorwaerts geloescht: `[at, at + deleted)` ist weg, der Caret stand und steht auf `at`. */
  case DeleteForward(node: NodeId, at: Int, deleted: Int)

  /** Alles andere: Struktur, Formatierung, Bereichsersetzung, mehrere Knoten.
    *
    * §14 zaehlt sie als Grenzen auf, und deshalb sind sie hier '''ein''' Fall: keine zwei
    * Strukturaenderungen verschmelzen, und es gibt nichts, was sie unterscheiden muesste.
    */
  case Structural

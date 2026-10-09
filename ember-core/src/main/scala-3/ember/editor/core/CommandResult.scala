package ember.editor.core

/** Ob ein Handler die Absicht uebernommen hat.
  *
  * Ausdruecklich nicht dasselbe wie `preventDefault`/`stopPropagation` (§12): das sind
  * Browserereignis-Operationen, ueber die allein der Browseradapter entscheidet -- und zwar danach,
  * ob er die native Aktion tatsaechlich ersetzt oder bewusst abgelehnt hat.
  */
enum CommandResult:

  /** Nicht zustaendig. Der naechste Handler kommt dran.
    *
    * '''Muss nebenwirkungsfrei sein.''' Ein Handler, der den Entwurf aendert und dann `Pass`
    * meldet, hinterlaesst einen Zustand, mit dem der naechste Handler nicht rechnet. Bei
    * `SessionConfig.strictCommands` wird das erkannt und gemeldet.
    */
  case Pass

  /** Uebernommen. Die Kette endet hier. */
  case Handled

package ember.editor.core

/** Ein unabhaengig installierbares Funktionsbuendel.
  *
  * ==Was eine Extension beim Erzeugen nicht tun darf==
  *
  * §13: kein DOM-Zugriff, keine globale Registrierung. Eine Extension-Fabrik ist ein Wert; sie
  * beschreibt, was beigetragen wird, und tut es nicht selbst. Deshalb laesst sich eine
  * Konfiguration serverseitig aufloesen, in Tests vergleichen und ohne Browser pruefen.
  *
  * ==Lebenszyklus==
  *
  * `resolve → validate → install → dispose in umgekehrter Reihenfolge`. [[contribute]] gehoert zur
  * Aufloesung und laeuft, bevor irgendetwas existiert. [[install]] laeuft erst gegen die fertige
  * Sitzung und liefert seine eigene Aufraeumaktion -- scheitert eine Installation, werden alle bis
  * dahin installierten in umgekehrter Reihenfolge wieder abgebaut.
  */
trait Extension:

  def id: ExtensionId

  /** Extensions, die vorher aufgeloest sein muessen. Fehlende und Zyklen sind Fehler (§13). */
  def dependsOn: Vector[ExtensionId] = Vector.empty

  /** Der rein deklarative Beitrag. Keine Seiteneffekte. */
  def contribute: ExtensionContributions = ExtensionContributions.empty

  /** Bindet Laufzeitressourcen an die fertige Sitzung.
    *
    * Der Regelfall braucht das nicht -- Commands, Transforms und Felder kommen ueber
    * [[contribute]]. Gedacht fuer das, was tatsaechlich eine lebende Sitzung braucht: ein Listener,
    * ein Zeitgeber, eine spaetere Browseranbindung.
    */
  def install(session: EditorSession): Subscription = Subscription.cancelled

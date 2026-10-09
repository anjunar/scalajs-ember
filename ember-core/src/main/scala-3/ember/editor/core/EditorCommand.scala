package ember.editor.core

/** Eine Benutzer- oder Anwendungsabsicht.
  *
  * ==Identitaet ist das Objekt, nicht sein Name==
  *
  * Ein Command wird ueber Referenzgleichheit nachgeschlagen, nie ueber einen String (§12). Damit
  * gibt es keine Namenskollisionen zwischen Modulen, keine Tippfehler, die erst zur Laufzeit
  * auffallen, und der Compiler prueft den Payload-Typ. [[name]] steht ausschliesslich in
  * Diagnosemeldungen -- er ist ausdruecklich '''keine''' Dispatch-API (§23).
  *
  * ==Nicht jedes Primitiv wird ein Command==
  *
  * §12: Commands sind Absichten, keine Operationen. `Bold`, `Undo`, `InsertParagraph` sind
  * Absichten; `SpliceText` ist es nicht. Wer alles zu einem Command macht, bekommt eine Registry
  * statt einer API.
  *
  * @tparam A
  *   der Payload-Typ. `Unit` fuer Commands ohne Argument.
  */
trait EditorCommand[A]:

  /** Nur fuer Diagnose. */
  def name: String

object EditorCommand:

  /** Ein Command ohne Payload. */
  def unit(commandName: String): EditorCommand[Unit] =
    new EditorCommand[Unit]:
      val name: String = commandName

  /** Ein Command mit Payload. */
  def of[A](commandName: String): EditorCommand[A] =
    new EditorCommand[A]:
      val name: String = commandName

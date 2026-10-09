package ember.editor.core

/** Die registrierten Handler einer Sitzung.
  *
  * ==Wie die Typsicherheit ohne Casts an der Aufrufstelle zustande kommt==
  *
  * Die Registry ist heterogen -- sie fuehrt Handler zu Commands verschiedener Payload-Typen in
  * einer Liste. Der Typzeuge ist das Command-Objekt selbst: ein Eintrag wurde mit genau diesem
  * `EditorCommand[A]` angelegt, und nachgeschlagen wird ueber Referenzgleichheit. Wer dieselbe
  * Referenz in der Hand hat, hat zwangslaeufig dasselbe `A`.
  *
  * Der eine `asInstanceOf` in [[handlersFor]] ist damit begruendet und bleibt auf diese Zeile
  * beschraenkt. Nach aussen gibt es kein `Any` (§8.1, P05-Risiken).
  *
  * Unveraenderlich: Registrieren liefert eine neue Registry. Die Sitzung tauscht ihre aus, und ein
  * laufender Dispatch arbeitet auf dem Schnappschuss, den er beim Start bekommen hat -- wer sich
  * waehrend eines Dispatch an- oder abmeldet, aendert erst den naechsten (§12).
  */
final class CommandRegistry private (
    private val entries: Vector[CommandRegistry.Entry],
    private val nextOrder: Long
):

  /** Die zustaendigen Handler in Ausfuehrungsreihenfolge.
    *
    * Critical zuerst, Fallback zuletzt; innerhalb einer Stufe in Registrierungsreihenfolge.
    */
  def handlersFor[A](command: EditorCommand[A]): Vector[(TransformScope, A) => CommandResult] =
    entries
      .filter(_.command eq command)
      .sortBy(entry => (entry.priority.ordinal, entry.order))
      .map(_.handler.asInstanceOf[(TransformScope, A) => CommandResult])

  def knows(command: EditorCommand[?]): Boolean = entries.exists(_.command eq command)

  def size: Int = entries.size

  private[core] def registered[A](registration: CommandRegistration[A]): CommandRegistry =
    new CommandRegistry(
      entries :+ CommandRegistry.Entry(
        registration.command,
        registration.priority,
        nextOrder,
        registration.handler
      ),
      nextOrder + 1
    )

  private[core] def registeredAll(
      registrations: Vector[CommandRegistration[?]]
  ): CommandRegistry =
    registrations.foldLeft(this)((registry, registration) => registry.registered(registration))

  /** Nimmt genau diesen Eintrag zurueck. Idempotent (§12). */
  private[core] def withoutOrder(order: Long): CommandRegistry =
    new CommandRegistry(entries.filterNot(_.order == order), nextOrder)

  private[core] def lastOrder: Long = nextOrder - 1

object CommandRegistry:

  private final case class Entry(
      command: EditorCommand[?],
      priority: CommandPriority,
      order: Long,
      handler: Any
  )

  val empty: CommandRegistry = new CommandRegistry(Vector.empty, 0L)

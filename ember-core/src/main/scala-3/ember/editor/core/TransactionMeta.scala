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

/** Was der Ausloeser einer Transaktion ueber sie sagt.
  *
  * Bewusst schmal. `HistoryPolicy` gehoert zu P11 und wird dort ueber [[tags]] angebunden, statt
  * diesen Typ jetzt um eine Entscheidung zu erweitern, die der Kern nicht trifft.
  */
final case class TransactionMeta(
    origin: Origin = Origin.User,
    label: Option[String] = None,
    tags: Set[String] = Set.empty
):

  def taggedWith(tag: String): TransactionMeta = copy(tags = tags + tag)

  def hasTag(tag: String): Boolean = tags.contains(tag)

object TransactionMeta:

  val user: TransactionMeta   = TransactionMeta(Origin.User)
  val system: TransactionMeta = TransactionMeta(Origin.System)

  def labelled(label: String, origin: Origin = Origin.User): TransactionMeta =
    TransactionMeta(origin, Some(label))

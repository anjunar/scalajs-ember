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

/** Was eine Aenderung fuer die History bedeuten soll.
  *
  * §14 fuehrt sie zusammen mit [[Origin]] als "typisierte Metadaten" auf. Der Kern wertet sie
  * nicht aus -- er traegt sie, wie er [[TransactionMeta.label]] traegt. Ausgewertet wird sie in
  * `ember-history`, und ohne dieses Modul ist sie folgenlos.
  *
  * Sie ist eine Ausnahme, kein Regelfall: fehlt sie, entscheiden die Gruppierungsregeln
  * aus §14 anhand dessen, was tatsaechlich passiert ist. Wer sie setzt, weiss etwas, das sich
  * am ChangeSet nicht ablesen laesst.
  */
enum HistoryPolicy:

  /** Eine eigene Undo-Stufe, auch wenn die Regeln verschmelzen wuerden. */
  case Push

  /** Mit der laufenden Gruppe verschmelzen, wenn es eine gibt. */
  case Merge

  /** Nicht aufzeichnen. Fuer Aenderungen, die niemand rueckgaengig machen koennen soll. */
  case Ignore

/** Was der Ausloeser einer Transaktion ueber sie sagt.
  *
  * Bewusst schmal, und jedes Feld hier steht in §14 als typisierte Metadatenangabe. Was der Kern
  * damit tut, ist: es weiterreichen. Er entscheidet weder ueber History noch ueber Herkunft --
  * er sorgt nur dafuer, dass die Angabe den Commit erreicht, statt aus Textdifferenzen erraten
  * werden zu muessen.
  */
final case class TransactionMeta(
    origin: Origin = Origin.User,
    label: Option[String] = None,
    history: Option[HistoryPolicy] = None,
    tags: Set[String] = Set.empty
):

  def taggedWith(tag: String): TransactionMeta = copy(tags = tags + tag)

  def hasTag(tag: String): Boolean = tags.contains(tag)

  def withHistory(policy: HistoryPolicy): TransactionMeta = copy(history = Some(policy))

object TransactionMeta:

  val user: TransactionMeta   = TransactionMeta(Origin.User)
  val system: TransactionMeta = TransactionMeta(Origin.System)

  /** Undo und Redo. §14: "History-origin wird nicht neu aufgezeichnet." */
  val history: TransactionMeta = TransactionMeta(Origin.History)

  def labelled(label: String, origin: Origin = Origin.User): TransactionMeta =
    TransactionMeta(origin, Some(label))

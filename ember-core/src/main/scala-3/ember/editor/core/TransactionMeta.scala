package ember.editor.core

/** Was der Ausloeser einer Transaktion ueber sie sagt.
  *
  * Bewusst schmal, und jedes Feld hier steht in §14 als typisierte Metadatenangabe. Was der Kern
  * damit tut, ist: es weiterreichen. Er entscheidet weder ueber History noch ueber Herkunft -- er
  * sorgt nur dafuer, dass die Angabe den Commit erreicht, statt aus Textdifferenzen erraten werden
  * zu muessen.
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

  /** The tag a protected native input session puts on its own transactions.
    *
    * The core does not interpret it -- it carries it, like every other tag. It is named here
    * because two modules that never meet have to agree on the word: the browser sets it (§15.3),
    * and the rich-text profile reads it to defer its optimising merges (§8.2: "Widersprueche ...
    * werden vom Profil bestimmt", and the merge is one of the things a composition may not see
    * happen underneath it).
    *
    * A tag rather than a field, because that is what §14 built tags for: something the trigger
    * knows and the core does not have to.
    */
  val CompositionTag: String = "ember.composition"

  val user: TransactionMeta   = TransactionMeta(Origin.User)
  val system: TransactionMeta = TransactionMeta(Origin.System)

  /** Undo und Redo. §14: "History-origin wird nicht neu aufgezeichnet." */
  val history: TransactionMeta = TransactionMeta(Origin.History)

  def labelled(label: String, origin: Origin = Origin.User): TransactionMeta =
    TransactionMeta(origin, Some(label))

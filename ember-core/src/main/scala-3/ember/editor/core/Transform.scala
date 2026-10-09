package ember.editor.core

/** A Regel, the vor the Commit Dokumentinvarianten herstellt.
  *
  * ==Wozu, and warum not in a Listener==
  *
  * §3.2: Transforms stellen Invarianten her, '''before''' etwas sichtbar is -- statt a Kaskade from
  * Listener-Updates auszuloesen, bei the each Zwischenstand short gilt. The Zusammenwachsen
  * getrennter Textlaeufe after the Entformatieren (§8.2, P12) is the Musterfall: it gehoert in
  * dieselbe Transaktion, not in a spaeteren DOM-Cleanup and not in a eigene Undo-Stufe.
  *
  * ==The Vertrag==
  *
  *   - '''Idempotent.''' A zweiter Lauf on demselben Node aendert nothing more. Ohne the is it no
  *     Fixpunkt, and the Arbeitsbudget schlaegt to.
  *   - '''Nur the Entwurf.''' No DOM, no Netz, no Seiteneffekte -- the Kandidat can unmittelbar
  *     then verworfen become.
  *   - '''Typisiert.''' Registriert against a [[NodeType]]; the Deskriptor liefert the Typzeugen,
  *     before [[transform]] runs.
  *
  * @tparam N
  *   the Knotenart, on the this Transform reagiert
  */
trait Transform[N <: EditorNode]:

  /** Nur for Diagnose -- under anderem in the Budgetmeldung. */
  def name: String

  def nodeType: NodeType[N]

  def phase: TransformPhase = TransformPhase.Normalize

  /** Bringt diesen Node in Normalform. Aendert nothing, if nothing to tun is. */
  def transform(node: N, scope: TransformScope): Unit

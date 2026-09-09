package ember.editor.core

import scala.util.Random

/** Quelle neuer Node-IDs.
  *
  * Injiziert, nie global (§8.3). Ein globaler Zaehler waere aus drei Gruenden falsch: er verknuepft
  * unabhaengige Dokumente, er macht Tests von der Ausfuehrungsreihenfolge abhaengig, und er
  * ueberlebt keinen Server-Neustart, obwohl SSR und Client dieselben IDs brauchen.
  *
  * ==Warum `isTaken` statt einer Reservierungsliste==
  *
  * §8.3 verlangt, dass der Generator vorhandene IDs reserviert. Statt ihm dafuer einen
  * veraenderlichen Reservierungszustand zu geben -- der mit dem Dokument aus dem Tritt geraten kann
  * -- fragt er den Aufrufer. Das Dokument ist die Wahrheit darueber, welche IDs vergeben sind; der
  * Generator muss sie nicht spiegeln.
  *
  * Wer mehrere IDs erzeugt, bevor er die Knoten einfuegt, muss die bereits ausgegebenen selbst
  * mitzaehlen -- oder [[nextBatch]] verwenden, das genau das tut.
  */
trait NodeIdGenerator:

  /** Liefert eine ID, fuer die `isTaken` `false` ergibt. */
  def next(isTaken: NodeId => Boolean): NodeId

  /** Liefert `count` untereinander verschiedene, noch nicht vergebene IDs. */
  final def nextBatch(count: Int, isTaken: NodeId => Boolean): Vector[NodeId] =
    if count < 0 then throw EditorContractViolation(s"Negative Anzahl IDs angefordert: $count")
    else
      val issued = scala.collection.mutable.HashSet.empty[NodeId]
      Vector.fill(count) {
        val id = next(candidate => issued.contains(candidate) || isTaken(candidate))
        issued.add(id)
        id
      }

  /** Liefert eine im Dokument noch nicht vergebene ID. */
  final def nextFor(document: DocumentRead): NodeId = next(document.contains)

object NodeIdGenerator:

  /** Nach so vielen Kollisionen in Folge gilt der Namensraum als erschoepft.
    *
    * Ohne Grenze wuerde ein zu kleiner Zufallsraum nicht auffallen, sondern die Anwendung
    * einfrieren -- ein Fehlerbild, das sich kaum zuordnen laesst.
    */
  private val MaxAttempts = 64

  /** Zufaellige IDs. Mit `seed` deterministisch und damit testtauglich.
    *
    * @param prefix
    *   vorangestellt, damit IDs in Diagnosen und Wire-Formaten erkennbar bleiben
    * @param length
    *   Anzahl der Zufallszeichen
    */
  def random(prefix: String = "n", length: Int = 10, seed: Option[Long] = None): NodeIdGenerator =
    new NodeIdGenerator:
      private val random   = seed.fold(new Random())(new Random(_))
      private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

      def next(isTaken: NodeId => Boolean): NodeId =
        var attempt = 0
        while attempt < MaxAttempts do
          val candidate = NodeId(
            prefix + Vector.fill(length)(alphabet(random.nextInt(alphabet.length))).mkString
          )
          if !isTaken(candidate) then return candidate
          attempt += 1
        throw EditorContractViolation(
          s"Nach $MaxAttempts Versuchen keine freie NodeId gefunden. Namensraum zu klein?"
        )

  /** Fortlaufende IDs `prefix1`, `prefix2`, ... Deterministisch, fuer Tests und Fixtures.
    *
    * Der Zaehler gehoert der Instanz, nicht dem Prozess. Zwei Generatoren stoeren einander nicht,
    * und ein Test bekommt bei jedem Lauf dieselben IDs.
    */
  def sequential(prefix: String = "n"): NodeIdGenerator =
    new NodeIdGenerator:
      private var counter = 0

      def next(isTaken: NodeId => Boolean): NodeId =
        var attempt = 0
        while attempt < MaxAttempts do
          counter += 1
          val candidate = NodeId(s"$prefix$counter")
          if !isTaken(candidate) then return candidate
          attempt += 1
        throw EditorContractViolation(
          s"Nach $MaxAttempts Versuchen keine freie NodeId gefunden. Namensraum zu klein?"
        )

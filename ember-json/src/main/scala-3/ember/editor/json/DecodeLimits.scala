package ember.editor.json

import ember.editor.core.*

/** Obergrenzen fuer das Dekodieren fremder Payloads.
  *
  * §19.2: "Decode prueft Typen, Zahlenbereiche, Limits, doppelte IDs, referenzielle Integritaet und
  * Schema, bevor eine Session oder View entsteht."
  *
  * ==Warum ueberhaupt Grenzen==
  *
  * Ein Dokument kommt aus dem Netz, aus der Zwischenablage oder aus einem Formularfeld. Ohne
  * Grenzen entscheidet der Absender, wie viel Speicher und Rechenzeit der Empfaenger aufwendet --
  * und zwar bevor irgendeine fachliche Pruefung greift. Die Werte hier sind bewusst grosszuegig
  * fuer echte Dokumente und knapp gegenueber dem, was ein Angreifer schickt.
  *
  * Sie sind ein Wert, keine Konstante: eine Anwendung, die 200 000 Knoten braucht, setzt sie hoch
  * und weiss dann, dass sie es getan hat.
  *
  * @param maxSourceChars
  *   Laenge des Quelltexts. Wird '''vor''' dem Parsen geprueft -- danach steht der Speicher schon.
  * @param maxJsonDepth
  *   Schachtelungstiefe des JSON-Werts. Schuetzt die Konvertierung, die rekursiv laeuft.
  * @param maxArrayLength
  *   Elemente eines einzelnen Arrays.
  * @param maxObjectFields
  *   Felder eines einzelnen Objekts.
  * @param maxNodes
  *   Knoten eines Dokuments.
  * @param maxChildren
  *   Kinder eines einzelnen Knotens.
  * @param maxTextChars
  *   Laenge eines einzelnen Textlaufs, in UTF-16-Einheiten wie ueberall im Modell (§11).
  * @param maxDocumentDepth
  *   Tiefe des Dokumentbaums.
  */
final case class DecodeLimits(
    maxSourceChars: Int = 8 * 1024 * 1024,
    maxJsonDepth: Int = 64,
    maxArrayLength: Int = 200_000,
    maxObjectFields: Int = 256,
    maxNodes: Int = 100_000,
    maxChildren: Int = 20_000,
    maxTextChars: Int = 1_000_000,
    maxDocumentDepth: Int = 100
)

object DecodeLimits:

  /** Die Voreinstellung. Fuer Dokumente, die ein Mensch geschrieben hat, nie erreichbar. */
  val default: DecodeLimits = DecodeLimits()

package ember.editor.json

import ember.editor.core.*

import scala.annotation.tailrec

/** Das Ergebnis eines erfolgreichen Dekodierens.
  *
  * §9: "SSR rendert ein Document ohne lokale Selection, History oder Fokus." Genau deshalb steht
  * hier ein [[Document]] und keine Sitzung -- Auswahl, History und View-State sind Zustand einer
  * Bearbeitung, nicht Inhalt einer Datei (§5). Wer eine Sitzung will, baut sie daraus.
  */
final case class DecodeResult(document: Document, diagnostics: Vector[DecodeDiagnostic])

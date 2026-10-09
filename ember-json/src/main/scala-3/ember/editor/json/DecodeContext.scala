package ember.editor.json

import ember.editor.core.*

/** Was ein Codec beim Dekodieren ueber seine Umgebung wissen darf.
  *
  * `codecVersion` ist die im Payload genannte, nicht die des Codecs -- ein Codec, der aeltere
  * Staende lesen will, verzweigt hier. Das Envelope hat vorher geprueft, dass sie nicht
  * '''groesser''' als die eigene ist: ein neuerer Payload kann Felder enthalten, deren Bedeutung
  * dieser Stand nicht kennt, und ihn stillschweigend als alten zu lesen waere Datenverlust.
  */
final case class DecodeContext(
    codecVersion: Int,
    path: DiagnosticPath,
    limits: DecodeLimits,
    marks: MarkSupport
)

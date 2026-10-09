package ember.editor.json

import ember.editor.core.*

/** Ein Fehlschlag beim Kodieren. */
sealed trait EncodeError extends EditorError

object EncodeError:

  final case class NoCodec(nodeId: NodeId, nodeClass: String, override val path: DiagnosticPath)
      extends EncodeError:
    def message: String =
      s"Fuer `${nodeId.value}` ($nodeClass) ist kein NodeJsonCodec registriert."

  final case class NoMarkCodec(nodeId: NodeId, markId: MarkId, override val path: DiagnosticPath)
      extends EncodeError:
    def message: String =
      s"Fuer die Markierung `${markId.value}` an `${nodeId.value}` ist kein MarkJsonCodec " +
        "registriert."

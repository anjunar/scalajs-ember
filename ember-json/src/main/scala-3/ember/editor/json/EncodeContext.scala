package ember.editor.json

import ember.editor.core.*

/** Was ein Codec beim Kodieren ueber seine Umgebung wissen darf. */
final case class EncodeContext(nodeId: NodeId, path: DiagnosticPath, marks: MarkSupport)

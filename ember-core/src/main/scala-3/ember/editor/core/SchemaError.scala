package ember.editor.core

/** Fehler beim Aufbau eines [[Schema]]. Geschlossen wie alle Validierungsergebnisse (§8.1). */
sealed trait SchemaError extends EditorError

object SchemaError:

  /** Zwei Deskriptoren beanspruchen denselben Wire-Namen. */
  final case class DuplicateTypeId(typeId: NodeTypeId) extends SchemaError:
    def message: String =
      s"Die NodeTypeId `${typeId.value}` ist mehrfach registriert."
    override def path: DiagnosticPath = DiagnosticPath.field(typeId.value)

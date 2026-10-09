package ember.editor.forms

import ember.editor.core.*

/** Installs a field in a session.
  *
  * §16: "Ein Form-Adapter '''installiert''' eine synchrone Darstellbarkeitsregel und ein
  * abgeleitetes `EncodedFieldValue`-StateField." Installing is what an [[Extension]] does (§13), so
  * this is one -- and it carries the field alone, because in this design the rule and the field are
  * the same object.
  *
  * An application that wants two fields on one session registers two extensions with two names. Two
  * extensions with the same field would be a duplicate id, and the resolver refuses that.
  */
final class FormFieldExtension(val field: EditorField) extends Extension:

  val id: ExtensionId = ExtensionId(s"ember.forms/${field.fieldName}")

  override def contribute: ExtensionContributions =
    ExtensionContributions(fields = Vector(field))

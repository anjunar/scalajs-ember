package ember.editor.core

/** Bezeichner einer Markierungsart, versioniert wie ein [[NodeTypeId]]. */
opaque type MarkId = String

object MarkId:

  def apply(value: String): MarkId =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige MarkId: `$value`")
    )

  def parse(value: String): Option[MarkId] =
    if value.nonEmpty && !value.exists(c => c.isWhitespace || c.isControl) then Some(value)
    else None

  given Ordering[MarkId] = Ordering.String

  extension (id: MarkId) def value: String = id

/** Eine typisierte Markierung auf einem Textlauf.
  *
  * Der Kern kennt *keine* konkreten Marks. Strong, Emphasis, Underline, Strike und InlineCode
  * gehoeren ins Rich-Text-Modul (§8.2, P12), damit eine reine Paragraph-Anwendung sie nicht
  * mitlinken muss und ein fremdes Modul eigene Marks beitragen kann.
  *
  * Offener Trait, aus demselben Grund wie [[EditorError]]: fachliche Typen offen, strukturelle
  * Kategorien geschlossen (§8.1).
  *
  * ==Warum kein blosser String und kein CSS==
  *
  * §8.2 verlangt typisierte, normalisierte Werte und schliesst beliebige CSS-Strings als
  * Dokumentformat aus. Eine Mark darf deshalb Nutzdaten tragen -- eine Sprachangabe, eine
  * Kommentar-Referenz --, aber ihre Identitaet ist [[markId]].
  *
  * ==Gleichheit==
  *
  * Implementierungen sind unveraenderliche Case Classes. Ihre Wertgleichheit entscheidet, ob zwei
  * Textlaeufe nach dem Entfernen einer Formatierung wieder zusammenwachsen (§8.2). Eine Mark mit
  * Referenzgleichheit wuerde diese Normalisierung stillschweigend verhindern.
  */
trait TextMark:

  /** Identitaet der Markierungsart. Innerhalb eines [[MarkSet]] hoechstens einmal vertreten. */
  def markId: MarkId

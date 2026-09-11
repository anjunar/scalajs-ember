package ember.editor.html

/** Eine begrenzte, unveraenderliche HTML-Beschreibung fuer Import und Export.
  *
  * §19.1 ist hier ungewoehnlich deutlich: das ist "'''kein diffbarer View-Baum'''" und hat
  * "keine Mount-/Update-API". Der Unterschied ist keine Formsache. Ein Fragment mit
  * Update-Laufzeit waere ein zweiter Renderer neben UI -- genau das, was §2 als Non-Goal
  * fuehrt und was §15.1 mit "Generische DOM-Erzeugung, Besitz, Einfuegen, Verschieben und
  * Entfernen gehoeren UI3" ausschliesst.
  *
  * Ein Fragment entsteht also aus etwas und wird zu etwas. Es lebt nicht.
  *
  * Der Importparser folgt mit P24. Bis dahin dient dieser Typ der Ausgabeseite: die
  * Serialisierung nach HTML, gegen die sich SSR und Browser vergleichen lassen.
  */
enum HtmlFragment:

  case Element(
      tag: String,
      attributes: Vector[HtmlAttribute] = Vector.empty,
      children: Vector[HtmlFragment] = Vector.empty
  )

  case Text(value: String)

object HtmlFragment:

  /** Elemente ohne Inhalt. Ein Endtag waere hier ein Parserfehler, kein Schoenheitsfehler. */
  private val voidTags = Set("area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "source", "track", "wbr")

  /** Serialisiert nach HTML.
    *
    * Die Ausgabe ist bewusst kompakt und ohne Einrueckung: sie wird mit der Browserausgabe
    * verglichen, und jedes eingefuegte Leerzeichen waere im Dokument ein Textknoten, den es
    * dort nicht gibt.
    */
  def render(fragment: HtmlFragment): String = fragment match
    case Text(value) => escapeText(value)
    case Element(tag, attributes, children) =>
      val open = s"<$tag${attributes.map(renderAttribute).mkString}>"
      if voidTags.contains(tag) then
        if children.nonEmpty then
          throw new IllegalArgumentException(
            s"<$tag> ist ein leeres Element und kann keine Kinder haben, hat aber ${children.length}."
          )
        else open
      else s"$open${children.map(render).mkString}</$tag>"

  private def renderAttribute(attribute: HtmlAttribute): String =
    s""" ${attribute.name}="${escapeAttribute(attribute.value)}""""

  /** Nur was der Parser als Markup lesen wuerde. `>` bleibt stehen -- es ist im Textinhalt
    * eindeutig und ein zusaetzliches `&gt;` machte den Vergleich mit der Browserausgabe
    * unnoetig schwer.
    */
  private def escapeText(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;")

  private def escapeAttribute(value: String): String =
    value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

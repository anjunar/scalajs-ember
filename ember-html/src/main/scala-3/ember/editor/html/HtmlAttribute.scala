package ember.editor.html

import ember.editor.core.*

/** Ein zulaessiges Attribut.
  *
  * Eigener Typ statt `(String, String)`, damit die Pruefung an einer Stelle steht. §19.1 schliesst
  * beliebige CSS-Strings und Eventattribute als Dokumentformat aus -- `onclick` und `style` sind
  * hier keine Attribute, sondern ein Fehler.
  */
final case class HtmlAttribute private (name: String, value: String)

object HtmlAttribute:

  /** Bewusst eng. Erweitert wird die Liste, wenn ein Knotentyp ein Attribut belegt braucht. */
  private val allowed = Set(
    "id",
    "lang",
    "dir",
    "href",
    "title",
    "alt",
    "src",
    "width",
    "height",
    "start",
    // A table column's alignment on `th`/`td` (X01). The attribute and not `style="text-align"`:
    // §19.1 rules out CSS strings, and `align` is what GFM renderers and a paste from them carry.
    "align",
    // `rel` und `target` gehoeren zusammen und kommen nur an externen Links vor -- siehe
    // `LinkSupport`. Sie stehen hier, weil §19.1 eine geschlossene Liste verlangt und nicht,
    // weil ein Adapter sie beliebig setzen duerfte.
    "rel",
    "target",
    // `class` traegt genau eine Sache: die Sprache eines Codeblocks als `language-<name>`
    // (`CodeSupport`). §19.1 schliesst "beliebige CSS-Strings als Dokumentformat" aus -- der
    // Name steht hier, der Wert kommt aus einem Adapter, und die Sprache selbst ist geprueft.
    // Ein Importparser (P24) entscheidet gesondert, was er davon uebernimmt.
    "class"
  )

  private val editorPrefix = "data-ember-"

  /** Prueft und baut, oder gibt `None` zurueck.
    *
    * Baut mit `new`, nicht mit `HtmlAttribute(...)`: das eigene [[apply]] verdeckt das synthetische
    * der Case-Klasse, ein Aufruf hier liefe im Kreis. Aus demselben Grund ist der Konstruktor
    * privat -- damit auch `copy` niemandem an der Pruefung vorbeihilft.
    */
  def parse(name: String, value: String): Option[HtmlAttribute] =
    if allowed.contains(name) || name.startsWith(editorPrefix) then
      Some(new HtmlAttribute(name, value))
    else None

  /** Wie [[parse]], wirft aber. Fuer im Code feststehende Attribute. */
  def apply(name: String, value: String): HtmlAttribute =
    parse(name, value).getOrElse(
      throw EditorContractViolation(
        s"`$name` ist kein zulaessiges Dokumentattribut. Eventattribute und freies CSS sind " +
          "als Dokumentformat ausgeschlossen (§19.1)."
      )
    )

  /** Ein Attribut, das nur die Editieransicht traegt. */
  def editor(name: String, value: String): HtmlAttribute =
    new HtmlAttribute(editorPrefix + name, value)

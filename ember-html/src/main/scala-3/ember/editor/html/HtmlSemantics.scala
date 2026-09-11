package ember.editor.html

import ember.editor.core.*

/** Wofuer gerendert wird.
  *
  * §15.1: getrennte Content- und Editor-Renderprofile. Dieselbe Semantik, aber die
  * Editieransicht ergaenzt Metadaten und Interaktion -- Knoten-IDs als Attribute, spaeter
  * `contenteditable`, ARIA-Rollen. Die ausgelieferte HTML-Fassung traegt davon nichts:
  * §19.1 verlangt, dass browserseitige Wrapper und Editor-Attribute beim Austausch verschwinden.
  */
enum RenderProfile:

  /** Reines Dokument-HTML. Was ein Leser bekommt, und was exportiert wird. */
  case Content

  /** Dasselbe plus das, was die Editierflaeche braucht. */
  case Editor

/** Ein zulaessiges Attribut.
  *
  * Eigener Typ statt `(String, String)`, damit die Pruefung an einer Stelle steht. §19.1
  * schliesst beliebige CSS-Strings und Eventattribute als Dokumentformat aus -- `onclick` und
  * `style` sind hier keine Attribute, sondern ein Fehler.
  */
final case class HtmlAttribute private (name: String, value: String)

object HtmlAttribute:

  /** Bewusst eng. Erweitert wird die Liste, wenn ein Knotentyp ein Attribut belegt braucht. */
  private val allowed = Set(
    "id", "lang", "dir", "href", "title", "alt", "src", "width", "height", "start",
    // `rel` und `target` gehoeren zusammen und kommen nur an externen Links vor -- siehe
    // `LinkSupport`. Sie stehen hier, weil §19.1 eine geschlossene Liste verlangt und nicht,
    // weil ein Adapter sie beliebig setzen duerfte.
    "rel", "target",
    // `class` traegt genau eine Sache: die Sprache eines Codeblocks als `language-<name>`
    // (`CodeSupport`). §19.1 schliesst "beliebige CSS-Strings als Dokumentformat" aus -- der
    // Name steht hier, der Wert kommt aus einem Adapter, und die Sprache selbst ist geprueft.
    // Ein Importparser (P24) entscheidet gesondert, was er davon uebernimmt.
    "class"
  )

  private val editorPrefix = "data-ember-"

  /** Prueft und baut, oder gibt `None` zurueck.
    *
    * Baut mit `new`, nicht mit `HtmlAttribute(...)`: das eigene [[apply]] verdeckt das
    * synthetische der Case-Klasse, ein Aufruf hier liefe im Kreis. Aus demselben Grund ist der
    * Konstruktor privat -- damit auch `copy` niemandem an der Pruefung vorbeihilft.
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

/** Die semantische Gestalt genau eines Knotens -- ohne seine Kinder.
  *
  * Die Beschraenkung auf einen Knoten ist der Kern der Sache. Wuerde eine Gestalt ihre Kinder
  * mitbeschreiben, waere sie ein View-Baum, und die Projektion muesste ihn diffen -- also
  * genau das zweite Rendering-System, das §2 ausschliesst. So beschreibt jeder Knoten sich
  * selbst, und wer die Kinder haelt, ist die UI-Runtime.
  */
enum HtmlShape:

  /** Ein Element mit Tag und Attributen. Kinder kommen von der Dokumentstruktur.
    *
    * `inner` sind zusaetzliche Tags 'innerhalb'' des aeusseren, von aussen nach innen; die Kinder
    * landen im innersten. `Element("pre", …, Vector("code"))` wird zu
    * `<pre><code>…Kinder…</code></pre>`.
    *
    * ==Warum ein Knoten mehrere Tags braucht==
    *
    * Weil HTML fuer manche Bedeutungen zwei verlangt. Ein Codeblock ist `<pre><code>` -- `pre`
    * erhaelt den Whitespace, `code` sagt, was der Inhalt ist --, und beide gehoeren demselben
    * Dokumentknoten. Ihn in zwei Knoten aufzuteilen hiesse, dem Dokument eine Struktur
    * anzudichten, die nur die Darstellung braucht.
    *
    * Dieselbe Form wie [[TextRun.marks]] und aus demselben Grund: der aeussere Tag traegt die
    * Identitaet des Knotens und ueberlebt, die inneren beschreiben nur.
    */
  case Element(
      tag: String,
      attributes: Vector[HtmlAttribute] = Vector.empty,
      inner: Vector[String] = Vector.empty
  )

  /** Ein Textlauf in seinem eigenen Wrapper.
    *
    * §15.1 verlangt genau das: "Ein Textleaf bekommt einen stabilen Wrapper mit einem
    * Textkind. Das vermeidet zusammengefasste benachbarte SSR-Textnodes und erlaubt eine
    * eindeutige ID->Textpunkt-Zuordnung." Deshalb steht hier kein roher Text: zwei Laeufe
    * nebeneinander waeren in der Ausgabe ein einziger Textknoten, und beim Hydrieren liesse
    * sich nicht mehr sagen, wo der eine aufhoert.
    *
    * `KeyedChildren` verlangt dasselbe von der anderen Seite -- "Keyed children require
    * physical element components": ein Textknoten hat keinen eigenen Host, an dem sich eine
    * Reihenfolge festmachen liesse.
    *
    * ==Marks als Innentags==
    *
    * `marks` sind die Tagnamen der Markierungen, von aussen nach innen -- `Vector("strong",
    * "em")` wird zu `<span><strong><em>Text</em></strong></span>`. §15.1 sieht genau das vor:
    * "Mark-Aenderungen koennen semantische Innentags ersetzen und benoetigen
    * Selection-Restoration."
    *
    * Sie stehen innen und nicht am Wrapper, weil der Wrapper die Identitaet des Laufs
    * traegt: er ueberlebt eine Formatierungsaenderung, die Innentags nicht. Wer das umdreht,
    * verliert bei jedem Fettschalten den Knoten, an dem die Knoten-ID haengt.
    */
  case TextRun(
      tag: String,
      value: String,
      attributes: Vector[HtmlAttribute] = Vector.empty,
      marks: Vector[String] = Vector.empty
  )

/** Wie eine Knotenart als HTML aussieht.
  *
  * ==Warum ein gemeinsamer Vertrag==
  *
  * §15.1: "Ein gemeinsamer semantischer Vertrag liefert sowohl eigenstaendige HTML-Ausgabe als
  * auch die Dokumentansicht." Zwei getrennte Beschreibungen -- eine fuer SSR, eine fuer den
  * Browser -- wuerden auseinanderlaufen, und der Unterschied faellt erst bei der Hydration auf,
  * also am spaetestmoeglichen Zeitpunkt. Hier gibt es nur eine.
  *
  * Spiegelt bewusst [[NodeType]]: `nodeType` ist der Typzeuge, und alles Verhalten haengt am
  * Deskriptor statt an der Knotenklasse.
  *
  * @tparam N
  *   die Knotenart
  */
trait HtmlSemantics[N <: EditorNode]:

  def nodeType: NodeType[N]

  def shapeOf(node: N, profile: RenderProfile): HtmlShape

/** Registry der semantischen Beschreibungen.
  *
  * Wie [[Schema]] heterogen und ohne oeffentliches `Any`: der Typzeuge ist der Deskriptor des
  * jeweiligen Eintrags.
  */
final class HtmlSupport private (val entries: Vector[HtmlSemantics[?]]):

  /** Die Gestalt eines Knotens, oder `None`, wenn keine Beschreibung registriert ist. */
  def shapeOf(node: EditorNode, profile: RenderProfile): Option[HtmlShape] =
    entries.iterator.map(describe(_, node, profile)).collectFirst { case Some(shape) => shape }

  def knows(node: EditorNode): Boolean = shapeOf(node, RenderProfile.Content).isDefined

  def ++(other: HtmlSupport): HtmlSupport = new HtmlSupport(entries ++ other.entries)

  private def describe[N <: EditorNode](
      semantics: HtmlSemantics[N],
      node: EditorNode,
      profile: RenderProfile
  ): Option[HtmlShape] =
    semantics.nodeType.project(node).map(semantics.shapeOf(_, profile))

object HtmlSupport:

  val empty: HtmlSupport = new HtmlSupport(Vector.empty)

  def of(entries: HtmlSemantics[?]*): HtmlSupport = new HtmlSupport(entries.toVector)

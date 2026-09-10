package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.jfx.*
import ember.editor.richtext.ParagraphNode

/** Die semantische Beschreibung der Knotenarten aus dem Rich-Text-Profil.
  *
  * ==Warum das ein eigenes Modul ist==
  *
  * §6: "`standard` ist bewusst ein optionales Integrationsmodul: Dadurch kennen die
  * Node-Module weder Markdown noch JFX und die Format-SPIs keine konkreten Feature-Nodes."
  *
  * Hier laufen beide Seiten zusammen -- und nur hier. `ember-rich-text` weiss nichts von HTML,
  * `ember-html` nichts von Absaetzen. Eine Anwendung, die ihr Dokument nur als JSON
  * verarbeitet, linkt diese Datei nie mit.
  *
  * ==Einzeln waehlbar, nicht als Sammelregistrierung==
  *
  * §6 nennt das Risiko ausdruecklich: "Eager Sammelregistrierungen halten optionale Module
  * fest." Deshalb ist jeder Adapter ein eigener Wert, und [[all]] ist eine Bequemlichkeit,
  * kein Zwang. Wer nur Absaetze braucht, nimmt die drei Eintraege und nicht mehr.
  */
object ParagraphSupport:

  /** Die Dokumentwurzel als `article`.
    *
    * Nicht `div`: die Wurzel ist ein in sich abgeschlossener Inhalt, und §16 verlangt fuer die
    * ausgelieferte Fassung semantisches HTML. Ein Leser ohne Stylesheet und ein Screenreader
    * sollen dasselbe Dokument vorfinden.
    */
  val root: HtmlSemantics[RootNode] = new HtmlSemantics[RootNode]:
    val nodeType: NodeType[RootNode] = RootNode

    def shapeOf(node: RootNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element("article", identify(node.id, profile))

  val paragraph: HtmlSemantics[ParagraphNode] = new HtmlSemantics[ParagraphNode]:
    val nodeType: NodeType[ParagraphNode] = ParagraphNode

    def shapeOf(node: ParagraphNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element("p", identify(node.id, profile))

  /** Ein Textlauf wird zu einem `span` mit einem Textkind.
    *
    * §15.1 verlangt den Wrapper ausdruecklich, und zwar fuer beide Profile: er "vermeidet
    * zusammengefasste benachbarte SSR-Textnodes und erlaubt eine eindeutige
    * ID->Textpunkt-Zuordnung". Zwei Laeufe nebeneinander waeren ohne ihn in der Ausgabe ein
    * einziger Textknoten -- die Grenze zwischen ihnen liesse sich beim Hydrieren nicht mehr
    * finden, und der SelectionPort (P21) faende sie ebenso wenig.
    *
    * `span` und nicht `div`: ein Textlauf steht im Fluss seines Absatzes.
    */
  val text: HtmlSemantics[TextNode] = new HtmlSemantics[TextNode]:
    val nodeType: NodeType[TextNode] = TextNode

    def shapeOf(node: TextNode, profile: RenderProfile): HtmlShape =
      HtmlShape.TextRun("span", node.text, identify(node.id, profile))

  /** Alle drei Beschreibungen. */
  val semantics: HtmlSupport = HtmlSupport.of(root, paragraph, text)

  /** Die daraus abgeleiteten JFX-Adapter. */
  val views: ViewSupport = ViewSupport.semantic(semantics)

  /** Traegt die Knoten-ID nur in der Editieransicht ein.
    *
    * §19.1: browserseitige Wrapper und Editor-Attribute werden beim Austausch entfernt. Statt
    * sie hinterher wieder herauszunehmen, entstehen sie in der Content-Fassung gar nicht erst.
    */
  private def identify(id: NodeId, profile: RenderProfile): Vector[HtmlAttribute] =
    profile match
      case RenderProfile.Content => Vector.empty
      case RenderProfile.Editor  => Vector(HtmlAttribute.editor("node", id.value))

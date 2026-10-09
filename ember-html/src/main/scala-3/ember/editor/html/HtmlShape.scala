package ember.editor.html

import ember.editor.core.*

/** Die semantische Gestalt genau eines Knotens -- ohne seine Kinder.
  *
  * Die Beschraenkung auf einen Knoten ist der Kern der Sache. Wuerde eine Gestalt ihre Kinder
  * mitbeschreiben, waere sie ein View-Baum, und die Projektion muesste ihn diffen -- also genau das
  * zweite Rendering-System, das §2 ausschliesst. So beschreibt jeder Knoten sich selbst, und wer
  * die Kinder haelt, ist die UI-Runtime.
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
    * Dokumentknoten. Ihn in zwei Knoten aufzuteilen hiesse, dem Dokument eine Struktur anzudichten,
    * die nur die Darstellung braucht.
    *
    * Dieselbe Form wie [[TextRun.marks]] und aus demselben Grund: der aeussere Tag traegt die
    * Identitaet des Knotens und ueberlebt, die inneren beschreiben nur.
    */
  case Element(
      tag: String,
      attributes: Vector[HtmlAttribute] = Vector.empty,
      inner: Vector[String] = Vector.empty,
      // Rendering metadata: inline-content blocks need spaces and empty caret lines.
      textBlock: Boolean = false
  )

  /** Ein Textlauf in seinem eigenen Wrapper.
    *
    * §15.1 verlangt genau das: "Ein Textleaf bekommt einen stabilen Wrapper mit einem Textkind. Das
    * vermeidet zusammengefasste benachbarte SSR-Textnodes und erlaubt eine eindeutige
    * ID->Textpunkt-Zuordnung." Deshalb steht hier kein roher Text: zwei Laeufe nebeneinander waeren
    * in der Ausgabe ein einziger Textknoten, und beim Hydrieren liesse sich nicht mehr sagen, wo
    * der eine aufhoert.
    *
    * `KeyedChildren` verlangt dasselbe von der anderen Seite -- "Keyed children require physical
    * element components": ein Textknoten hat keinen eigenen Host, an dem sich eine Reihenfolge
    * festmachen liesse.
    *
    * ==Marks als Innentags==
    *
    * `marks` sind die Tagnamen der Markierungen, von aussen nach innen -- `Vector("strong", "em")`
    * wird zu `<span><strong><em>Text</em></strong></span>`. §15.1 sieht genau das vor:
    * "Mark-Aenderungen koennen semantische Innentags ersetzen und benoetigen
    * Selection-Restoration."
    *
    * Sie stehen innen und nicht am Wrapper, weil der Wrapper die Identitaet des Laufs traegt: er
    * ueberlebt eine Formatierungsaenderung, die Innentags nicht. Wer das umdreht, verliert bei
    * jedem Fettschalten den Knoten, an dem die Knoten-ID haengt.
    */
  case TextRun(
      tag: String,
      value: String,
      attributes: Vector[HtmlAttribute] = Vector.empty,
      marks: Vector[String] = Vector.empty
  )

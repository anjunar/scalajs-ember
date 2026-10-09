package ember.editor.markdown

import ember.editor.core.*

/** A failure that produced no syntax tree.
  *
  * Closed: the ways parsing can fail are a property of this module, not an extension point. A
  * '''syntax''' error is not among them, and that is not an oversight -- CommonMark has no invalid
  * input. Every string is a valid document, so everything that can go wrong here is a resource
  * bound.
  */
sealed trait ParseError extends EditorError

object ParseError:

  final case class LimitExceeded(limit: String, allowed: Int, found: Int) extends ParseError:
    def message: String =
      s"Die Grenze `$limit` erlaubt $allowed, gefunden wurden $found. " +
        "Grenzen sind ein Wert (ParseLimits) -- eine Anwendung, die mehr braucht, setzt sie hoch."

  /** The step budget ran out. Deliberately says '''where''', because the answer is usually "one
    * line", and that is the difference between a big document and an attack.
    */
  final case class BudgetExhausted(allowed: Int, atLine: Int) extends ParseError:
    def message: String =
      s"Der Arbeitsschritt-Vorrat von $allowed war in Zeile $atLine aufgebraucht. " +
        "Das ist keine Groessengrenze -- eine kurze Eingabe kann ihn ebenso erschoepfen."

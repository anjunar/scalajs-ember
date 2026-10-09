package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.*
import org.scalajs.dom

import scala.collection.mutable

/** The failure a preflight throws.
  *
  * An exception and not an `Either`, because that is what `HydrationBoundary` takes: it turns a
  * throw into a scoped rebuild of exactly this boundary, with the fallback outside it intact (§17).
  * Returning a value would mean re-inventing that mechanism one level up.
  */
final class HydrationMismatch(val problems: Vector[HydrationProblem])
    extends RuntimeException(
      problems
        .map(_.render)
        .mkString("Die ausgelieferte Ansicht passt nicht zum Dokument: ", "; ", "")
    )

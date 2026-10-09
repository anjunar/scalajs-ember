package ember.editor.image

import ember.editor.core.*

/** A pixel measurement that is actually a measurement.
  *
  * §20 asks for "validierte positive Pixelmasse", and the type is where that lives. A width of zero
  * or minus one is not a small picture -- it is a document that no renderer can lay out, and one
  * that reached HTML would produce `width="-1"` for every reader to see.
  *
  * The upper bound is deliberate too. A stated size is meant to reserve layout space and stop the
  * page jumping (§20); a number in the millions reserves nothing and only says that something has
  * gone wrong upstream.
  */
opaque type PositivePixels = Int

object PositivePixels:

  /** Bigger than any real image and small enough to be obviously a mistake. */
  val max: Int = 100_000

  def parse(value: Int): Option[PositivePixels] =
    if value > 0 && value <= max then Some(value) else None

  def apply(value: Int): PositivePixels =
    parse(value).getOrElse(
      throw EditorContractViolation(
        s"Keine gueltige Pixelangabe: $value. Erlaubt ist 1 bis $max."
      )
    )

  given Ordering[PositivePixels] = Ordering.Int

  extension (pixels: PositivePixels) def value: Int = pixels

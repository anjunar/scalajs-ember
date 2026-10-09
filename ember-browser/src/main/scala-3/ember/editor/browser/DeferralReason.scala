package ember.editor.browser

import ember.editor.core.*

/** Why activation is waiting. */
enum DeferralReason:

  /** A composition might be running in the source field.
    *
    * §17: "Waehrend bekannter Textarea-Composition oder bei unbekannter Eingabesitzung eines
    * bereits fokussierten Felds wird der Wechsel aufgeschoben."
    */
  case InputSession

  /** The source differs from what the server rendered, and nobody has imported it.
    *
    * §17.4: "Falls Source seit SSR geaendert wurde, bleibt sie zunaechst unangetastet. […]
    * Parsingfehler lassen den Draft editierbar und verhindern Enhancement."
    */
  case UnimportedSource

  /** The page has not finished hydrating. */
  case PageHydrating

  def render: String = this match
    case InputSession     => "eine Eingabesitzung im Quelltextfeld ist offen oder koennte es sein"
    case UnimportedSource => "der Quelltext wurde seit dem Rendern geaendert"
    case PageHydrating    => "die Seite hydriert noch"

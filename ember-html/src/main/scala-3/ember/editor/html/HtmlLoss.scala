package ember.editor.html

/** What kind of thing was lost. */
enum HtmlLoss:

  /** An element the profile refuses outright -- a script, a style block, an active embed. */
  case DroppedElement

  /** An attribute that is not part of the document format (§19.1). */
  case DroppedAttribute

  /** An element nobody claimed; its children were kept. */
  case UnwrappedElement

  /** A URL the policy refused. The content around it usually survives. */
  case RefusedUrl

  /** A limit stopped the import. */
  case LimitExceeded

  def render: String = this match
    case DroppedElement   => "Element verworfen"
    case DroppedAttribute => "Attribut verworfen"
    case UnwrappedElement => "Element aufgeloest"
    case RefusedUrl       => "Adresse abgelehnt"
    case LimitExceeded    => "Grenze erreicht"

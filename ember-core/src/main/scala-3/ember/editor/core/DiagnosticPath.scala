package ember.editor.core

/** Ort einer Diagnose innerhalb eines Dokuments oder eines Wire-Formats.
  *
  * Architektur §8.2 und §19.2 verlangen, dass ungueltige Eingaben Pfad, ID und Grund liefern --
  * eine blosse Meldung reicht bei einem tief verschachtelten Dokument nicht aus, um die Stelle zu
  * finden.
  */
final case class DiagnosticPath(segments: Vector[PathSegment]):

  /** Haengt einen Schritt an. Die Wurzel steht links, die genaueste Angabe rechts. */
  def /(segment: PathSegment): DiagnosticPath = DiagnosticPath(segments :+ segment)

  def field(name: String): DiagnosticPath = this / PathSegment.Field(name)
  def index(value: Int): DiagnosticPath   = this / PathSegment.Index(value)
  def node(id: String): DiagnosticPath    = this / PathSegment.Node(id)

  /** Kompakte Darstellung, etwa `<root>.children[2]#p-17.text`. */
  def render: String = segments.foldLeft("<root>") { (rendered, segment) =>
    rendered + (segment match
      case PathSegment.Field(name) => s".$name"
      case PathSegment.Index(at)   => s"[$at]"
      case PathSegment.Node(id)    => s"#$id")
  }

object DiagnosticPath:

  /** Der leere Pfad: das Dokument als Ganzes, oder ein nicht lokalisierbarer Fehler. */
  val Root: DiagnosticPath = DiagnosticPath(Vector.empty)

  def field(name: String): DiagnosticPath = Root.field(name)
  def index(value: Int): DiagnosticPath   = Root.index(value)
  def node(id: String): DiagnosticPath    = Root.node(id)

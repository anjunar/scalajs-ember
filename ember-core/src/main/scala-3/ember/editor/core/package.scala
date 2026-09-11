package ember.editor.core

/** Kern des Ember-Editors: Dokument, Selection, Transaktionen, Commands.
  *
  * Dieses Paket ist headless. Es kennt kein DOM, keine UI-Runtime, kein Formular und keine UI; die
  * Abhaengigkeitsgrenze steht in UI_EDITOR_ARCHITECTURE.md §7 und wird beim Compile durch
  * `boundaryCheck` in build.sbt erzwungen. Es darf serverseitig ohne Browserglobals geladen werden
  * -- `CoreEnvironmentSpec` prueft genau das.
  *
  * Ausser der hier definierten Fehlerkonvention enthaelt das Paket noch nichts. Document, NodeType,
  * Selection und Transaction folgen ab P02; leere Platzhaltertypen werden bewusst nicht
  * vorweggenommen.
  *
  * ==Fehlerkonvention==
  *
  * Der Kern unterscheidet zwei Arten von Fehlschlaegen, und zwar an der Signatur erkennbar:
  *
  *   - '''Erwartete Fehler sind Werte.''' Ein ungueltiges Dokument, eine abgewiesene Transaktion,
  *     ein nicht dekodierbares Wire-Format: das sind vorgesehene Ergebnisse und werden als
  *     `Either[E, A]` mit `E <: EditorError` zurueckgegeben, nie geworfen. Die Architektur baut
  *     darauf auf -- `update` liefert laut §10 ein `Either[UpdateError, Commit]`, nicht einen
  *     Erfolgswert plus Exception-Risiko.
  *   - '''Vertragsverletzungen des Aufrufers fliegen.''' Ein abgelaufenes Tx-Handle, ein
  *     verschachteltes `update`, ein Aufruf nach `dispose`: das sind Programmierfehler, keine
  *     Datenfehler. Sie werden als [[EditorContractViolation]] geworfen. Sie in ein `Either` zu
  *     verpacken wuerde suggerieren, ein Aufrufer koenne sinnvoll darauf reagieren.
  *
  * [[EditorError]] ist bewusst ein offener Trait, kein `sealed`. Fremde Feature-Module -- auch in
  * fremden Repositories -- muessen eigene Fehler beitragen koennen, ohne diese Datei zu aendern.
  * Das ist dieselbe Entscheidung wie beim offenen Node-Vertrag in §8.1: fachliche Typen offen,
  * strukturelle Kategorien geschlossen. Geschlossen ist deshalb [[PathSegment]].
  *
  * Ein [[EditorError]] traegt Daten, kein `Throwable`. Er ist vergleichbar, serialisierbar und
  * ueberlebt eine Prozessgrenze; ein Stacktrace tut das alles nicht.
  */

/** Ein erwarteter, benannter Fehlschlag.
  *
  * Implementierungen sind unveraenderliche Case Classes im jeweils zustaendigen Modul. Die
  * Identitaet eines Fehlers ist sein Typ, nicht ein String-Code -- Aufrufer unterscheiden Faelle
  * per Pattern Match, nicht per Textvergleich.
  */
trait EditorError:

  /** Menschenlesbare Beschreibung des Fehlschlags, ohne Stacktrace und ohne Pfadangabe.
    *
    * Der Pfad steht getrennt in [[path]], damit ein Aufrufer beides unabhaengig darstellen kann.
    * [[render]] setzt sie fuer Logs und Diagnoseausgaben zusammen.
    */
  def message: String

  /** Wo der Fehler auftrat. [[DiagnosticPath.Root]], wenn er sich nicht lokalisieren laesst. */
  def path: DiagnosticPath = DiagnosticPath.Root

  /** Pfad und Beschreibung in einer Zeile, fuer Logs und Testmeldungen. */
  final def render: String = s"${path.render}: $message"

/** Verletzung eines Aufrufvertrags: falscher Zustand, abgelaufenes Handle, Reentranz.
  *
  * Kein Datenfehler und deshalb bewusst kein [[EditorError]]. `IllegalStateException` folgt
  * `ui.core.render.HostWriteBlocked` im Nachbar-Repo, damit Aufrufer beide Faelle mit derselben
  * Erwartung behandeln koennen.
  */
final class EditorContractViolation(message: String) extends IllegalStateException(message)

/** Ein Schritt in einem [[DiagnosticPath]].
  *
  * Geschlossen: die Struktur eines Pfades ist eine Kerneigenschaft und keine Erweiterung.
  * Feature-Module bilden ihre Begriffe auf diese drei Formen ab.
  */
enum PathSegment:

  /** Benanntes Feld eines Nodes oder eines Wire-Objekts, etwa `children` oder `alt`. */
  case Field(name: String)

  /** Position in einer Kindliste oder einem Array, nullbasiert. */
  case Index(value: Int)

  /** Ein Node, adressiert ueber seine Dokument-ID.
    *
    * Bewusst `String` und nicht `NodeId`: dieser Typ entsteht erst in P02, und die Fehlerkonvention
    * darf nicht auf das Dokumentmodell zurueckhaengen.
    */
  case Node(id: String)

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

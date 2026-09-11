package ember.editor.jfx

import ember.editor.core.*
import ember.editor.html.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.{Cursor, SsrCursor}

/** Die Dokumentansicht: haengt eine Sitzung an einen JFX-Cursor.
  *
  * ==Commit und Projektion sind zwei Zeitpunkte==
  *
  * §5 trennt beides ausdruecklich. Der Kern veroeffentlicht einen Zustand; ob eine Ansicht ihn
  * zeigt, ist eine andere Frage. Ein Commit-Konsument darf `commit.current` lesen, aber nicht
  * daraus schliessen, dass irgendetwas gerendert ist -- dafuer gibt es [[onProjected]], das
  * '''nach''' der Projektion mit der tatsaechlich dargestellten Revision meldet.
  *
  * ==Derselbe Weg fuer SSR und Browser==
  *
  * [[mount]] nimmt einen beliebigen `Cursor`. Mit einem `DomCursor` entsteht die
  * Editierflaeche, mit einem `SsrCursor` die serverseitige Ausgabe -- aus derselben
  * [[HtmlSemantics]] und demselben Code. Dass beide dasselbe liefern, ist damit keine
  * Absprache zwischen zwei Implementierungen, sondern eine Tautologie.
  */
final class DocumentView private (
    session: EditorSession,
    projection: DocumentProjection
):

  private var listeners     = Vector.empty[(Long, Revision => Unit)]
  private var nextHandle    = 0L
  private var commits       = Subscription.cancelled
  private var disposedFlag  = false

  private var rootComponent: AbstractComponent = null
  private var shown: Revision = Revision.initial

  /** Die Wurzelkomponente der Ansicht. */
  def root: AbstractComponent = rootComponent

  /** Die Revision, die gerade dargestellt ist (§5).
    *
    * Der Gegenwert zu [[onProjected]] fuer alle, die nicht von Anfang an zuhoeren konnten: eine
    * Registrierung nach dem Mount hat nichts verpasst, weil hier steht, was zu sehen ist.
    */
  def projectedRevision: Revision = shown


  /** Die Komponente zu einer Knoten-ID. Fuer Tests und spaeter fuer den SelectionPort (P21). */
  def componentFor(nodeId: NodeId): Option[AbstractComponent] = projection.componentFor(nodeId)

  /** Anzahl projizierter Knoten. */
  def size: Int = projection.size

  /** Rebuilds one text run's DOM from the current document (§15.4).
    *
    * For a caller that has found the browser leaving extra nodes in a run's wrapper. It is not a
    * repair of the '''document''' -- that has to be right already -- but of the view, and it is
    * the only way back to §15.1's "one stable wrapper with one text child" once something else
    * has written there.
    */
  def resetRun(nodeId: NodeId): Boolean =
    (projection.componentFor(nodeId), session.document.node(nodeId)) match
      case (Some(run: TextRunElement), Some(text: TextNode)) =>
        run.resetText(text.text)
        true
      case _ => false

  def isDisposed: Boolean = disposedFlag

  /** Meldet den Abschluss einer Projektion mit der dargestellten Revision (§5, §15.1). */
  def onProjected(listener: Revision => Unit): Subscription =
    if disposedFlag then Subscription.cancelled
    else
      nextHandle += 1
      val handle = nextHandle
      listeners = listeners :+ (handle, listener)
      Subscription(() => listeners = listeners.filterNot(_._1 == handle))

  def dispose(): Unit =
    if !disposedFlag then
      disposedFlag = true
      commits.dispose()
      projection.unmount()
      listeners = Vector.empty
      rootComponent = null

  private def mountInto(cursor: Cursor, parent: Option[AbstractComponent]): Unit =
    rootComponent = projection.mount(session.document, cursor, parent)
    shown = session.state.revision
    commits = session.onCommit { commit =>
      if !disposedFlag then
        projection.apply(commit)
        shown = commit.current.revision
        announce(shown)
    }
    // Hier wird bewusst nicht gemeldet. Ein Zuhoerer kann zu diesem Zeitpunkt nicht existieren
    // -- die Ansicht wird erst nach diesem Aufruf zurueckgegeben. Wer den Anfangsstand
    // braucht, liest [[projectedRevision]].

  /** Ein gescheiterter Beobachter reisst die uebrigen nicht mit -- dieselbe Regel wie fuer
    * Commit-Listener (§10, Schritt 7). Die Projektion ist zu diesem Zeitpunkt fertig.
    */
  private def announce(revision: Revision): Unit =
    listeners.foreach { (_, listener) =>
      try listener(revision)
      catch case _: Throwable => ()
    }

object DocumentView:

  /** Haengt eine Sitzung an einen Cursor. */
  /** Haengt eine Sitzung an einen Cursor.
    *
    * `parent` ist die Komponente, der die Ansicht gehoert. Ohne Angabe ist die Wurzel der
    * Ansicht selbst eine Wurzel -- richtig fuer eine Editierflaeche, die den Baum allein
    * ausmacht, und fuer SSR. Steht sie dagegen in einer groesseren Anwendung, gehoert sie
    * deren Komponente: dann raeumt ein `Runtime.unmount` dort auch die Ansicht ab, und
    * [[DocumentView.dispose]] bleibt trotzdem gefahrlos.
    */
  def mount(
      session: EditorSession,
      cursor: Cursor,
      views: ViewSupport,
      profile: RenderProfile = RenderProfile.Editor,
      parent: Option[AbstractComponent] = None
  ): DocumentView =
    val view = new DocumentView(session, new DocumentProjection(views, profile))
    view.mountInto(cursor, parent)
    view

  /** Rendert ein Dokument einmalig als HTML -- ohne Browser, ohne Sitzung.
    *
    * §9: "SSR rendert ein Document ohne lokale Selection, History oder Fokus." Genau deshalb
    * nimmt diese Methode ein [[Document]] und keine Sitzung: was hier entstehen soll, ist das
    * Dokument, nicht der Zustand einer Bearbeitung.
    *
    * Voreingestellt ist [[RenderProfile.Content]] -- die ausgelieferte Fassung traegt keine
    * Editor-Metadaten (§19.1).
    *
    * Die Gruppenanker der JFX-Runtime (`<!--jfx:KeyedChildren:start-->`) stehen in beiden
    * Profilen. Das ist Absicht: P20 braucht sie zum Hydrieren, und ein Kommentarknoten ist im
    * ausgelieferten Dokument weder sichtbar noch semantisch.
    */
  def renderToHtml(
      document: Document,
      views: ViewSupport,
      profile: RenderProfile = RenderProfile.Content
  ): String =
    val cursor     = new SsrCursor()
    val projection = new DocumentProjection(views, profile)
    val mounted    = projection.mount(document, cursor, None)
    try cursor.collectHtml()
    finally Runtime.unmount(mounted)
